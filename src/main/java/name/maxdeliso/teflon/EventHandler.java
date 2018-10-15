package name.maxdeliso.teflon;

import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.frames.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static name.maxdeliso.teflon.config.Config.INPUT_BUFFER_LEN;
import static name.maxdeliso.teflon.config.Config.IO_TIMEOUT_MS;
import static name.maxdeliso.teflon.config.Config.TEFLON_PORT;
import static name.maxdeliso.teflon.config.Config.TEFLON_SEND_ADDRESS;

/**
 * This class contains the main event loop which checks in memory queues, and performs UDP sending/receiving.
 */
class EventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(EventHandler.class);

    private final AtomicBoolean alive;
    private final MainFrame mainFrame;
    private final LinkedBlockingQueue<Message> outgoingMsgQueue;
    private final int localHostId;

    /**
     * The EventHandler constructor.
     *
     * @param alive            a flag which is set from AWT threads to signal graceful shutdown.
     * @param mainFrame        an AWT frame to display the frontend.
     * @param outgoingMsgQueue a message queue to hold messages prior to sending them over the network.
     * @param localHostId      a numeric host identifier.
     */
    EventHandler(final AtomicBoolean alive,
                 final MainFrame mainFrame,
                 final LinkedBlockingQueue<Message> outgoingMsgQueue,
                 final int localHostId) {
        this.alive = alive;
        this.mainFrame = mainFrame;
        this.outgoingMsgQueue = outgoingMsgQueue;
        this.localHostId = localHostId;
    }

    /**
     * Main event processing loop.
     */
    void loop() {
        try (final DatagramChannel datagramChannel = setupSocketChannel();
             final DatagramSocket ignored = setupDatagramSocket(datagramChannel);
             final Selector chanSelector = Selector.open()) {
            datagramChannel.register(chanSelector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            final ByteBuffer incomingPacketBuffer = ByteBuffer.allocate(INPUT_BUFFER_LEN);

            while (alive.get()) {
                chanSelector.select(IO_TIMEOUT_MS);
                final Set<SelectionKey> selectionKeySet = chanSelector.selectedKeys();
                final Iterator<SelectionKey> selectionKeyIterator = selectionKeySet.iterator();

                while (selectionKeyIterator.hasNext()) {
                    final SelectionKey key = selectionKeyIterator.next();

                    if (key.isReadable()) {
                        final SocketAddress sender = datagramChannel.receive(incomingPacketBuffer);
                        Message.bufferToMessage(incomingPacketBuffer)
                               .filter(message -> message.senderId() != localHostId)
                               .ifPresent(message -> {
                                   LOG.info("received message {} from {}", message, sender);
                                   mainFrame.queueMessageDisplay(message);
                               });
                        incomingPacketBuffer.flip();
                    }

                    if (key.isWritable()) {
                        pollForMessage()
                                .flatMap(Message::messageToBuffer)
                                .ifPresent(outgoingBuffer -> {
                                    try {
                                        final int bufferLength = outgoingBuffer.array().length;

                                        int sentBytes = datagramChannel
                                                .send(outgoingBuffer,
                                                        new InetSocketAddress(
                                                                InetAddress.getByAddress(TEFLON_SEND_ADDRESS),
                                                                TEFLON_PORT));

                                        LOG.debug("sent {} of {} bytes over the wire", sentBytes, bufferLength);

                                        // TODO: signal success back to caller with a future
                                    } catch (IOException exc) {
                                        LOG.error("i/o exception while attempting to send", exc);
                                    }
                                });
                    }

                    selectionKeyIterator.remove();
                }
            }
        } catch (IOException exc) {
            LOG.error("unexpected exception in main event loop", exc);
        } finally {
            mainFrame.dispose();
        }
    }

    private DatagramChannel setupSocketChannel() throws IOException {
        final DatagramChannel channel = DatagramChannel.open();
        channel.configureBlocking(false);
        return channel;
    }

    private DatagramSocket setupDatagramSocket(final DatagramChannel datagramChannel) throws SocketException {
        final DatagramSocket udpSocket = datagramChannel.socket();
        udpSocket.bind(new InetSocketAddress(TEFLON_PORT));
        udpSocket.setSoTimeout(IO_TIMEOUT_MS);
        udpSocket.setBroadcast(true);
        return udpSocket;
    }

    private Optional<Message> pollForMessage() {
        try {
            return Optional.ofNullable(outgoingMsgQueue.poll(IO_TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } catch (InterruptedException ie) {
            LOG.warn("interrupted while polling for outgoing messages", ie);
            return Optional.empty();
        }
    }
}
