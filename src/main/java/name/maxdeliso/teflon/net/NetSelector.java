package name.maxdeliso.teflon.net;

import name.maxdeliso.teflon.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * This class contains the main event selectLoop which checks in memory queues, and performs UDP sending/receiving.
 */
public class NetSelector {
    private static final Logger LOG = LoggerFactory.getLogger(NetSelector.class);

    private final AtomicBoolean alive;
    private final ByteBuffer incomingBuffer;
    private final InetSocketAddress multicastSendSocketAddress;
    private final InetSocketAddress multicastListenSocketAddress;
    private final BiConsumer<SocketAddress, byte[]> incomingByteBufferConsumer;
    private final InetAddress multicastGroupAddress;
    private final NetworkInterface multicastInterface;
    private final Supplier<ByteBuffer> outgoingMessageSupplier;

    public NetSelector(final AtomicBoolean alive,
                       final BiConsumer<SocketAddress, byte[]> incomingByteBufferConsumer,
                       final InetAddress multicastGroupAddress,
                       final NetworkInterface multicastInterface,
                       final Supplier<ByteBuffer> outgoingDataSupplier,
                       final Config config) {
        this.alive = alive;
        this.incomingByteBufferConsumer = incomingByteBufferConsumer;
        this.multicastGroupAddress = multicastGroupAddress;
        this.multicastInterface = multicastInterface;
        this.incomingBuffer = ByteBuffer.allocate(config.getInputBufferLength());
        this.multicastSendSocketAddress = new InetSocketAddress(multicastGroupAddress, config.getUdpPort());
        this.multicastListenSocketAddress = new InetSocketAddress(config.getUdpPort());
        this.outgoingMessageSupplier = outgoingDataSupplier;
    }

    /**
     * Main event processing selectLoop. This function busies the calling thread with the task of continual sending
     * and receiving as data arrives.
     */
    public void selectLoop() {
        try (final DatagramChannel datagramChannel = setupDatagramChannel();
             final Selector datagramChanSelector = Selector.open()) {

            datagramChannel.register(datagramChanSelector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            while (alive.get()) {
                datagramChanSelector.select(0);
                final var selectionKeySet = datagramChanSelector.selectedKeys();

                for (final SelectionKey key : selectionKeySet) {
                    if (key.isReadable()) {
                        incomingBuffer.clear();
                        var sender = datagramChannel.receive(incomingBuffer);
                        var receivedBytes = new byte[incomingBuffer.position()];
                        incomingBuffer.rewind();
                        incomingBuffer.get(receivedBytes);

                        // NOTE: heavy compute in incoming consumer will add latency
                        incomingByteBufferConsumer.accept(sender, receivedBytes);
                    }

                    if (key.isWritable()) {
                        boolean writeSucceeded = Optional.ofNullable(outgoingMessageSupplier.get())
                                .map(bufferToSend -> {
                                    try {
                                        final var bufferLength = bufferToSend.array().length;
                                        final var sentBytes = datagramChannel
                                                .send(bufferToSend, multicastSendSocketAddress);
                                        LOG.debug("sent {} of {} bytes over the wire", sentBytes, bufferLength);
                                        return bufferLength == sentBytes;
                                    } catch (IOException exc) {
                                        LOG.error("i/o exception while attempting to send", exc);
                                        return false;
                                    }
                                })
                                .orElse(false);

                        LOG.trace("write success flag: {}", writeSucceeded);
                    }
                }
            }
        } catch (IOException exc) {
            LOG.error("unexpected exception in main event selectLoop", exc);
        }
    }

    private DatagramChannel setupDatagramChannel() throws IOException {
        final var channel = DatagramChannel.open(StandardProtocolFamily.INET6);

        channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, multicastInterface);
        channel.configureBlocking(false);

        final var udpSocket = channel.socket();

        udpSocket.setReuseAddress(true);
        udpSocket.setBroadcast(true);
        udpSocket.bind(multicastListenSocketAddress);

        channel.join(multicastGroupAddress, multicastInterface);
        return channel;
    }
}
