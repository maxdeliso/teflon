package name.maxdeliso.teflon.net;

import com.google.gson.Gson;
import name.maxdeliso.teflon.config.Config;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.data.MessageMarshaller;
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
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * This class contains the main event select which checks in memory queues, and performs UDP sending/receiving.
 */
public class NetSelector {
    private static final Logger LOG = LoggerFactory.getLogger(NetSelector.class);

    private final AtomicBoolean alive;
    private final LinkedBlockingQueue<Message> outgoingMsgQueue;
    private final String localHostId;
    private final Config config;

    private final MessageMarshaller messageMarshaller;
    private final ByteBuffer incomingBuffer;
    private final InetSocketAddress multicastSendSocketAddress;
    private final InetSocketAddress multicastListenSocketAddress;
    private final Consumer<Message> incomingMessageConsumer;

    /**
     * Alternates between draining the outgoing message queue and receiving Messages and transferring
     * to the incoming message consumer.
     *
     * @param alive a flag that can be used to signal termination.
     * @param incomingMessageConsumer a function to process incoming messages.
     * @param outgoingMsgQueue an in-memory queue of messages that is drained into the network.
     * @param localHostId the current node's UUID.
     * @param config application level config.
     * @param messageMarshaller a marshaller for Messages.
     * @throws UnknownHostException if the configured address couldn't be resolved.
     */
    public NetSelector(final AtomicBoolean alive,
                       final Consumer<Message> incomingMessageConsumer,
                       final LinkedBlockingQueue<Message> outgoingMsgQueue,
                       final String localHostId,
                       final Config config,
                       final MessageMarshaller messageMarshaller) throws UnknownHostException {
        this.alive = alive;
        this.incomingMessageConsumer = incomingMessageConsumer;
        this.outgoingMsgQueue = outgoingMsgQueue;
        this.localHostId = localHostId;
        this.config = config;
        this.messageMarshaller = messageMarshaller;

        final InetAddress multicastGroupAddress = InetAddress.getByName(config.getMulticastGroup());

        this.incomingBuffer = ByteBuffer.allocate(config.getInputBufferLength());
        this.multicastSendSocketAddress = new InetSocketAddress(multicastGroupAddress, config.getUdpPort());
        this.multicastListenSocketAddress = new InetSocketAddress(config.getUdpPort());
    }

    /**
     * Main event processing select. This function busies the calling thread with the task of continual sending
     * and receiving as data arrives.
     */
    public void select() {
        try (final DatagramChannel datagramChannel = setupDatagramChannel();
             final Selector datagramChanSelector = Selector.open()) {

            datagramChannel.register(datagramChanSelector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            while (alive.get()) {
                datagramChanSelector.select(0);
                final Set<SelectionKey> selectionKeySet = datagramChanSelector.selectedKeys();

                for (final SelectionKey key : selectionKeySet) {
                    if (key.isReadable()) {
                        if(!handleRead(datagramChannel)) {
                            LOG.trace("read operation did not enqueue a message for display");
                        }
                    }

                    if (key.isWritable()) {
                        if(!handleWrite(datagramChannel)) {
                            LOG.trace("write did not send a message to the multicast address");
                        }
                    }
                }
            }
        } catch (IOException exc) {
            LOG.error("unexpected exception in main event select", exc);
        }
    }

    private boolean handleRead(final DatagramChannel datagramChannel) throws IOException {
        incomingBuffer.clear();
        final SocketAddress sender = datagramChannel.receive(incomingBuffer);
        byte[] receivedBytes = new byte[incomingBuffer.position()];
        incomingBuffer.rewind();
        incomingBuffer.get(receivedBytes);

        return messageMarshaller
                .bufferToMessage(receivedBytes)
                .filter(message -> {
                    boolean isLocal = localHostId.compareTo(message.senderId()) == 0;
                    return !isLocal;
                })
                .map(message -> {
                    LOG.info("received and parsed non local message {} from {}", message, sender);
                    incomingMessageConsumer.accept(message);
                    return true;
                })
                .orElse(false);
    }

    private boolean handleWrite(final DatagramChannel datagramChannel) {
        return Optional.ofNullable(outgoingMsgQueue.poll())
                .map(messageMarshaller::messageToBuffer)
                .map(outgoingBuffer -> {
                    try {
                        final int bufferLength = outgoingBuffer.array().length;
                        final int sentBytes = datagramChannel.send(outgoingBuffer, multicastSendSocketAddress);
                        LOG.debug("sent {} of {} bytes over the wire", sentBytes, bufferLength);
                        return bufferLength == sentBytes;
                    } catch (IOException exc) {
                        LOG.error("i/o exception while attempting to send", exc);
                        return false;
                    }
                })
                .orElse(false);
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
        udpSocket.bind(multicastListenSocketAddress);

        channel.join(group, multicastInterface);
        return channel;
    }
}
