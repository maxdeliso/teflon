package name.maxdeliso.teflon.net;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Manages network I/O using NIO selector for multicast communication.
 * Handles both reading incoming messages and sending outgoing messages.
 */
public class NetSelector {
    private static final Logger LOG = LogManager.getLogger(NetSelector.class);
    /**
     * Length of the buffer for network I/O.
     */
    private final int bufferLength;
    /**
     * Connection details for multicast communication.
     */
    private final ConnectionResult connectionResult;
    /**
     * Consumer for handling incoming messages.
     */
    private final BiConsumer<SocketAddress, ByteBuffer> onIncomingMessage;
    /**
     * Source for outgoing messages.
     */
    private final MessageSource outgoingMessageSource;
    /**
     * Future for tracking the selector loop completion.
     */
    private final CompletableFuture<Void> loopFuture = new CompletableFuture<>();
    /**
     * Tracks whether there are pending messages to write.
     */
    private final AtomicBoolean hasOutgoingMessages = new AtomicBoolean(false);
    private volatile Selector selector;

    /**
     * Creates a new network selector.
     *
     * @param bufferLength     Size of the network I/O buffer
     * @param connectionResult Connection details
     * @param incomingConsumer Consumer for handling incoming messages
     * @param outgoingSource   Source for outgoing messages
     */
    public NetSelector(final int bufferLength,
                       final ConnectionResult connectionResult,
                       final BiConsumer<SocketAddress, ByteBuffer> incomingConsumer,
                       final MessageSource outgoingSource) {
        this.bufferLength = bufferLength;
        this.connectionResult = connectionResult;
        this.onIncomingMessage = incomingConsumer;
        this.outgoingMessageSource = outgoingSource;
    }

    /**
     * Updates the selector's interest in write events based on message availability.
     *
     * @param key         The selection key to update
     * @param hasMessages Whether there are messages to send
     */
    private void updateWriteInterest(SelectionKey key, boolean hasMessages) {
        if (hasMessages != hasOutgoingMessages.get()) {
            hasOutgoingMessages.set(hasMessages);
            int oldOps = key.interestOps();
            int newOps = SelectionKey.OP_READ;
            if (hasMessages) {
                newOps |= SelectionKey.OP_WRITE;
            }
            key.interestOps(newOps);
            LOG.debug("Updated write interest, ops changed from {} to {}", oldOps, newOps);
        }
    }

    /**
     * Wakes up the selector to process new messages.
     */
    public void wakeup() {
        if (selector != null) {
            selector.wakeup();
        }
    }

    /**
     * Runs the selector loop for handling network I/O.
     *
     * @return A future that completes when the loop ends
     * @throws IOException if an I/O error occurs
     */
    public CompletableFuture<Void> selectLoop() throws IOException {
        var dataBuffer = ByteBuffer.allocateDirect(bufferLength);
        var groupAddress = connectionResult.getMembershipKey().group();
        LOG.debug("Starting selector loop for group: {} on interface: {}",
                groupAddress, connectionResult.getMembershipKey().networkInterface().getName());

        var sendSockAddress = new InetSocketAddress(
                // For IPv6 link-local addresses, we need to include the scope ID
                groupAddress instanceof Inet6Address
                        ? Inet6Address.getByAddress(
                        null,
                        groupAddress.getAddress(),
                        connectionResult.getMembershipKey().networkInterface())
                        : groupAddress,
                this.connectionResult.getPort());
        LOG.debug("Created send socket address: {}", sendSockAddress);
        var multicastSender = new MulticastSender(connectionResult.getDc(), sendSockAddress);

        try (final var sel = Selector.open()) {
            selector = sel;
            SelectionKey key = connectionResult.getDc().register(selector, SelectionKey.OP_READ);
            LOG.debug("Registered channel with selector, initial ops: {}", key.interestOps());

            while (!Thread.interrupted() &&
                    connectionResult.getMembershipKey().isValid() &&
                    connectionResult.getDc().isOpen()) {

                // Check for pending messages before select()
                ByteBuffer peek = outgoingMessageSource.peek();
                if (peek != null && peek.hasRemaining()) {
                    LOG.debug("Found pending message with {} bytes remaining", peek.remaining());
                    updateWriteInterest(key, true);
                }

                int selected = selector.select();
                LOG.debug("Selector woke up, {} keys selected", selected);
                var selectedKeys = selector.selectedKeys();
                var iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        LOG.warn("Invalid selection key, skipping");
                        continue;
                    }

                    if (key.isReadable()) {
                        LOG.debug("Key is readable, attempting to read message");
                        handleRead(dataBuffer);
                    }

                    if (key.isWritable()) {
                        LOG.debug("Key is writable, attempting to send message");
                        handleWrite(key, multicastSender);
                    }
                }
            }
        } catch (IOException ioe) {
            LOG.error("Fatal error in selector loop: {}", ioe.getMessage(), ioe);
            loopFuture.completeExceptionally(ioe);
            throw ioe;
        } finally {
            LOG.info("Selector loop ending, channel open: {}, membership valid: {}, interrupted: {}",
                    connectionResult.getDc().isOpen(),
                    connectionResult.getMembershipKey().isValid(),
                    Thread.interrupted());
            selector = null;
            dataBuffer.clear();
            if (!loopFuture.isDone()) {
                loopFuture.complete(null);
            }
        }

        return loopFuture;
    }

    /**
     * Handles reading incoming messages.
     *
     * @param dataBuffer Buffer to read into
     * @throws IOException if an I/O error occurs
     */
    private void handleRead(final ByteBuffer dataBuffer) throws IOException {
        SocketAddress sender = connectionResult.getDc().receive(dataBuffer);
        if (sender != null) {
            dataBuffer.flip();
            // For IPv6, ensure we have the correct scope ID
            if (sender instanceof java.net.InetSocketAddress inetSender) {
                if (inetSender.getAddress() instanceof Inet6Address) {
                    // Create a new address with the correct scope ID
                    var ipv6Addr = Inet6Address.getByAddress(
                            null,
                            inetSender.getAddress().getAddress(),
                            connectionResult.getMembershipKey().networkInterface()
                    );
                    sender = new InetSocketAddress(ipv6Addr, inetSender.getPort());
                    LOG.debug("Adjusted IPv6 sender address with scope ID: {}", sender);
                }
            }
            onIncomingMessage.accept(sender, dataBuffer.asReadOnlyBuffer());
            dataBuffer.clear();
        }
    }

    /**
     * Handles writing outgoing messages.
     *
     * @param key             The selection key for updating interest ops
     * @param multicastSender Sender for multicast messages
     */
    private void handleWrite(final SelectionKey key, final MulticastSender multicastSender) {
        ByteBuffer peek = outgoingMessageSource.peek();
        if (peek != null && peek.hasRemaining()) {
            LOG.debug("Attempting to send message with {} bytes", peek.remaining());
            multicastSender.send(peek);
            // If message was sent successfully, consume it
            if (!peek.hasRemaining()) {
                LOG.debug("Message sent successfully, consuming from queue");
                outgoingMessageSource.poll();
                // Check if there are more messages
                ByteBuffer nextPeek = outgoingMessageSource.peek();
                updateWriteInterest(key, nextPeek != null && nextPeek.hasRemaining());
            } else {
                LOG.debug("Message not fully sent, {} bytes remaining", peek.remaining());
            }
        } else {
            // No messages to send, disable write interest
            LOG.debug("No messages to send, disabling write interest");
            updateWriteInterest(key, false);
        }
    }
}
