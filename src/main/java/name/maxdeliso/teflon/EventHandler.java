package name.maxdeliso.teflon;

import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.frames.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static name.maxdeliso.teflon.config.Config.INPUT_BUFFER_LEN;
import static name.maxdeliso.teflon.config.Config.IO_TIMEOUT_MS;
import static name.maxdeliso.teflon.config.Config.MULTICAST_GROUP;
import static name.maxdeliso.teflon.config.Config.TEFLON_PORT;

/**
 * This class contains the main event loop which checks in memory queues, and performs UDP sending/receiving.
 */
class EventHandler {
    private static final Logger LOG = LoggerFactory.getLogger(EventHandler.class);

    private final AtomicBoolean alive;
    private final MainFrame mainFrame;
    private final LinkedBlockingQueue<Message> outgoingMsgQueue;
    private final UUID localHostId;

    /**
     * The EventHandler constructor.
     *  @param alive            a flag which is set from AWT threads to signal graceful shutdown.
     * @param mainFrame        an AWT frame to display the frontend.
     * @param outgoingMsgQueue a message queue to hold messages prior to sending them over the network.
     * @param localHostId      a numeric host identifier.
     */
    EventHandler(final AtomicBoolean alive,
                 final MainFrame mainFrame,
                 final LinkedBlockingQueue<Message> outgoingMsgQueue,
                 final UUID localHostId) {
        this.alive = alive;
        this.mainFrame = mainFrame;
        this.outgoingMsgQueue = outgoingMsgQueue;
        this.localHostId = localHostId;
    }

    /**
     * Main event processing loop.
     */
    void loop() {
        try (final DatagramChannel datagramChannel = setupDatagramChannel();
            final Selector datagramChanSelector = Selector.open()) {
            datagramChannel.register(datagramChanSelector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            final ByteBuffer incomingPacketBuffer = ByteBuffer.allocate(INPUT_BUFFER_LEN);

            final InetAddress group = InetAddress.getByName(MULTICAST_GROUP);

            Message.messageToBuffer(new Message(localHostId, "joined"))
                   .ifPresent(bb -> {
                        try {
                            datagramChannel.send(
                                    bb,
                                    new InetSocketAddress(group, TEFLON_PORT));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                   });

            while (alive.get()) {
                datagramChanSelector.select(IO_TIMEOUT_MS);
                final Set<SelectionKey> selectionKeySet = datagramChanSelector.selectedKeys();
                final Iterator<SelectionKey> selectionKeyIterator = selectionKeySet.iterator();

                while (selectionKeyIterator.hasNext()) {
                    final SelectionKey key = selectionKeyIterator.next();

                    if (key.isReadable()) {
                        final SocketAddress sender = datagramChannel.receive(incomingPacketBuffer);
                        Message.bufferToMessage(incomingPacketBuffer)
                               .filter(message -> !message.senderId().equals(localHostId))
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
                                                                group,
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

    private DatagramChannel setupDatagramChannel() throws IOException {
        final DatagramChannel channel = DatagramChannel
                .open(StandardProtocolFamily.INET6);
        channel.configureBlocking(false);

        final DatagramSocket udpSocket = channel.socket();
        udpSocket.setSoTimeout(IO_TIMEOUT_MS);
        udpSocket.setReuseAddress(true);
        udpSocket.setBroadcast(true);
        udpSocket.bind(new InetSocketAddress(TEFLON_PORT));

        final InetAddress group = InetAddress.getByName(MULTICAST_GROUP);

        for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            try {
                channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, ni);
                final MembershipKey key = channel.join(group, ni);

                LOG.debug("joined group {}", key);
            } catch (SocketException se) {
                LOG.error("failed to set multicast option for interface {}",
                          ni,
                          se);
            }
        }

        return channel;
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
