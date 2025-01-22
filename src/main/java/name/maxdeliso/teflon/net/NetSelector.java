package name.maxdeliso.teflon.net;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Manages network I/O using NIO selector for multicast communication.
 * Handles both reading incoming messages and sending outgoing messages.
 */
public class NetSelector {
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
     * Supplier for outgoing messages.
     */
    private final Supplier<ByteBuffer> outgoingMessageSupplier;

    /**
     * Future for tracking the selector loop completion.
     */
    private final CompletableFuture<Void> loopFuture = new CompletableFuture<>();

    /**
     * Creates a new network selector.
     *
     * @param bufferLength     Size of the network I/O buffer
     * @param connectionResult Connection details
     * @param incomingConsumer Consumer for handling incoming messages
     * @param outgoingSupplier Supplier for outgoing messages
     */
    public NetSelector(final int bufferLength,
                       final ConnectionResult connectionResult,
                       final BiConsumer<SocketAddress, ByteBuffer> incomingConsumer,
                       final Supplier<ByteBuffer> outgoingSupplier) {
        this.bufferLength = bufferLength;
        this.connectionResult = connectionResult;
        this.onIncomingMessage = incomingConsumer;
        this.outgoingMessageSupplier = outgoingSupplier;
    }

    /**
     * Runs the selector loop for handling network I/O.
     *
     * @return A future that completes when the loop ends
     * @throws IOException if an I/O error occurs
     */
    public CompletableFuture<Void> selectLoop() throws IOException {
        var dataBuffer = ByteBuffer.allocateDirect(bufferLength);
        var sendSockAddress = new InetSocketAddress(
                this.connectionResult.getMembershipKey().group(),
                this.connectionResult.getPort());
        var multicastSender = new MulticastSender(connectionResult.getDc(), sendSockAddress);

        try (final var selector = Selector.open()) {
            connectionResult.getDc().register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            while (this.connectionResult.getMembershipKey().isValid() && !Thread.interrupted()) {
                selector.select();
                var selectedKeys = selector.selectedKeys();
                var iterator = selectedKeys.iterator();

                while (iterator.hasNext()) {
                    SelectionKey key = iterator.next();
                    iterator.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isReadable()) {
                        handleRead(dataBuffer);
                    }

                    if (key.isWritable()) {
                        handleWrite(multicastSender);
                    }
                }
            }
        } catch (IOException ioe) {
            loopFuture.completeExceptionally(ioe);
            throw ioe;
        } finally {
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
            onIncomingMessage.accept(sender, dataBuffer.asReadOnlyBuffer());
            dataBuffer.clear();
        }
    }

    /**
     * Handles writing outgoing messages.
     *
     * @param multicastSender Sender for multicast messages
     */
    private void handleWrite(final MulticastSender multicastSender) {
        var outgoing = outgoingMessageSupplier.get();
        if (outgoing != null && outgoing.hasRemaining()) {
            multicastSender.send(outgoing);
        }
    }
}
