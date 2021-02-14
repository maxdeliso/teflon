package name.maxdeliso.teflon.net;

import java.io.IOException;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This class contains the main event selectLoop which checks in memory queues, and performs UDP
 * sending/receiving.
 */
public class NetSelector {
  private static final Logger LOG = LogManager.getLogger(NetSelector.class);

  private final ByteBuffer incomingBuffer;
  private final InetSocketAddress multicastSendSocketAddress;
  private final InetSocketAddress multicastListenSocketAddress;
  private final BiConsumer<SocketAddress, byte[]> incomingByteBufferConsumer;
  private final InetAddress multicastGroupAddress;
  private final NetworkInterface multicastInterface;
  private final Supplier<ByteBuffer> outgoingMessageSupplier;

  private final AtomicBoolean alive = new AtomicBoolean(true);
  private MembershipKey membershipKey;

  public NetSelector(final int udpPort,
                     final int bufferLength,
                     final BiConsumer<SocketAddress, byte[]> incomingByteBufferConsumer,
                     final InetAddress multicastGroupAddress,
                     final NetworkInterface multicastInterface,
                     final Supplier<ByteBuffer> outgoingDataSupplier) {
    this.incomingByteBufferConsumer = incomingByteBufferConsumer;
    this.multicastGroupAddress = multicastGroupAddress;
    this.multicastInterface = multicastInterface;
    this.incomingBuffer = ByteBuffer.allocate(bufferLength);
    this.multicastSendSocketAddress = new InetSocketAddress(multicastGroupAddress, udpPort);
    this.multicastListenSocketAddress = new InetSocketAddress(udpPort);
    this.outgoingMessageSupplier = outgoingDataSupplier;
  }

  /**
   * Main event processing selectLoop. This function busies the calling thread with the task of
   * continual sending and receiving as data arrives.
   */
  public CompletableFuture<Void> selectLoop() {
    return setupDatagramChannel()
        .thenApply(datagramChannelOpt -> {
          try (final Selector datagramChanSelector = Selector.open()) {
            var datagramChannel = datagramChannelOpt.orElseThrow(
                () -> new IllegalStateException("failed to setup a datagram channel")
            );
            datagramChannel.register(datagramChanSelector,
                SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            while (alive.get() && membershipKey.isValid()) {
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
                  boolean writeSucceededCompletely =
                      Optional.ofNullable(outgoingMessageSupplier.get())
                          .filter(byteBuffer -> byteBuffer.array().length > 0)
                          .map(bufferToSend -> {
                            try {
                              final var bufferLength = bufferToSend.array().length;
                              final var sentBytes =
                                  datagramChannel.send(bufferToSend, multicastSendSocketAddress);
                              LOG.debug("sent {} of {} bytes over the wire",
                                  sentBytes,
                                  bufferLength);
                              return bufferLength == sentBytes;
                            } catch (IOException exc) {
                              LOG.error("i/o exception while attempting to send", exc);
                              return false;
                            }
                          })
                          .orElse(true);

                  if (!writeSucceededCompletely) {
                    LOG.warn("write failed at least partially");
                  }
                }
              }
            }
          } catch (IOException exc) {
            LOG.error("unexpected exception in main event selectLoop", exc);
          }
          return null;
        });
  }

  private CompletableFuture<Optional<DatagramChannel>> setupDatagramChannel() {
    return CompletableFuture.supplyAsync(() -> {
      try {
        final DatagramChannel channel = DatagramChannel.open(StandardProtocolFamily.INET6);
        final var udpSocket = channel.socket();
        channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, multicastInterface);
        channel.configureBlocking(false);
        udpSocket.setReuseAddress(true);
        udpSocket.setBroadcast(true);
        udpSocket.bind(multicastListenSocketAddress);
        this.membershipKey = channel.join(multicastGroupAddress, multicastInterface);
        LOG.debug("joined to group {}", this.membershipKey.group());
        return Optional.of(channel);
      } catch (IOException ioe) {
        LOG.error("failed to setup a datagram channel", ioe);
        return Optional.empty();
      }
    });
  }
}
