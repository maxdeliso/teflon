package name.maxdeliso.teflon.net;

import com.google.gson.Gson;
import name.maxdeliso.teflon.config.Config;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.data.MessageMarshaller;
import name.maxdeliso.teflon.frames.MainFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class contains the main event loop which checks in memory queues, and performs UDP sending/receiving.
 */
public class NetSelector {
    private static final Logger LOG = LoggerFactory.getLogger(NetSelector.class);

    private final AtomicBoolean alive;
    private final MainFrame mainFrame;
    private final LinkedBlockingQueue<Message> outgoingMsgQueue;
    private final String localHostId;
    private final Config config;

    private final MessageMarshaller messageMarshaller;

    /**
     * The NetSelector constructor.
     *
     * @param alive            a flag which is set from AWT threads to signal graceful shutdown.
     * @param mainFrame        an AWT frame to display the frontend.
     * @param outgoingMsgQueue a message queue to hold messages prior to sending them over the network.
     * @param localHostId      a host identifier.
     * @param gson             a JSON parsing object.
     */
    public NetSelector(final AtomicBoolean alive,
                       final MainFrame mainFrame,
                       final LinkedBlockingQueue<Message> outgoingMsgQueue,
                       final String localHostId,
                       final Config config,
                       final Gson gson) {
        this.alive = alive;
        this.mainFrame = mainFrame;
        this.outgoingMsgQueue = outgoingMsgQueue;
        this.localHostId = localHostId;
        this.config = config;
        this.messageMarshaller = new MessageMarshaller(gson);
    }

    /**
     * Main event processing loop.
     */
    public void loop() {
        final ByteBuffer incomingPacketBuffer = ByteBuffer.allocate(config.getInputBufferLength());

        try (final DatagramChannel datagramChannel = setupDatagramChannel();
             final Selector datagramChanSelector = Selector.open()) {
            final InetAddress group = InetAddress.getByName(config.getMulticastGroup());
            datagramChannel.register(datagramChanSelector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            while (alive.get()) {
                datagramChanSelector.select(0);
                final Set<SelectionKey> selectionKeySet = datagramChanSelector.selectedKeys();

                for (final SelectionKey key : selectionKeySet) {
                    if (key.isReadable()) {
                        handleRead(incomingPacketBuffer, datagramChannel);
                    }

                    if (key.isWritable()) {
                        handleWrite(datagramChannel, group);
                    }
                }
            }
        } catch (IOException exc) {
            LOG.error("unexpected exception in main event loop", exc);
        } finally {
            mainFrame.dispose();
        }
    }

    private void handleRead(final ByteBuffer incomingPacketBuffer,
                            final DatagramChannel datagramChannel) throws IOException {
        final SocketAddress sender = datagramChannel.receive(incomingPacketBuffer);
        incomingPacketBuffer.flip();
        byte[] receivedBytes = new byte[incomingPacketBuffer.limit()];
        incomingPacketBuffer.get(receivedBytes);
        incomingPacketBuffer.clear();

        messageMarshaller
                .bufferToMessage(receivedBytes)
                .filter(message -> {
                    final String senderId = message.senderId();

                    boolean isLocal = localHostId.compareTo(senderId) == 0;

                    LOG.debug("received message with sender id {}, local id is {}, equality {}",
                            senderId, localHostId, isLocal);

                    return !isLocal;
                })
                .ifPresent(message -> {
                    LOG.info("received non local message {} from {}", message, sender);
                    mainFrame.queueMessageDisplay(message);
                });


    }

    private void handleWrite(
            final DatagramChannel datagramChannel,
            final InetAddress group) {
        Optional.ofNullable(outgoingMsgQueue.poll())
                .map(messageMarshaller::messageToBuffer)
                .ifPresent(outgoingBuffer -> {
                    try {
                        final int bufferLength = outgoingBuffer.array().length;

                        int sentBytes = datagramChannel
                                .send(outgoingBuffer, new InetSocketAddress(group, config.getUdpPort()));

                        LOG.debug("sent {} of {} bytes over the wire", sentBytes, bufferLength);
                    } catch (IOException exc) {
                        LOG.error("i/o exception while attempting to send", exc);
                    }
                });
    }


    private DatagramChannel setupDatagramChannel() throws IOException {
        final DatagramChannel channel = DatagramChannel.open(StandardProtocolFamily.INET6);
        final NetworkInterface multicastInterface = NetworkInterface.getByName(config.getInterfaceName());
        final InetAddress group = InetAddress.getByName(config.getMulticastGroup());

        channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, multicastInterface);
        channel.configureBlocking(false);

        final DatagramSocket udpSocket = channel.socket();

        udpSocket.setReuseAddress(true);
        udpSocket.setBroadcast(true);
        udpSocket.bind(new InetSocketAddress(config.getUdpPort()));

        final MembershipKey key = channel.join(group, multicastInterface);
        LOG.debug("joined group {}", key);

        return channel;
    }
}
