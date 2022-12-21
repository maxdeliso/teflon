package name.maxdeliso.teflon.net;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * This class contains the main event selectLoop which checks in memory queues, and performs UDP
 * sending/receiving.
 */
public class NetSelector {
  private static final Logger LOG = LogManager.getLogger(NetSelector.class);
  private final int udpPort;
  private final ByteBuffer incomingBuffer;
  private final InetSocketAddress multicastListenSocketAddress;
  private final BiConsumer<SocketAddress, byte[]> incomingByteBufferConsumer;
  private final List<InetAddress> candidateBindAddresses;
  private final NetworkInterface multicastInterface;
  private final Supplier<ByteBuffer> outgoingMessageSupplier;

  private final AtomicBoolean alive = new AtomicBoolean(true);
  private MembershipKey membershipKey;

  public NetSelector(final int udpPort,
                     final int bufferLength,
                     final BiConsumer<SocketAddress, byte[]> incomingByteBufferConsumer,
                     final List<InetAddress> inetAddresses,
                     final NetworkInterface multicastInterface,
                     final Supplier<ByteBuffer> outgoingDataSupplier) {
    this.udpPort = udpPort;
    this.incomingByteBufferConsumer = incomingByteBufferConsumer;
    this.candidateBindAddresses = inetAddresses;
    this.multicastInterface = multicastInterface;
    this.incomingBuffer = ByteBuffer.allocate(bufferLength);
    this.multicastListenSocketAddress = new InetSocketAddress(udpPort); // note: wildcard address
    this.outgoingMessageSupplier = outgoingDataSupplier;
  }

  /**
   * Main event processing selectLoop. This function busies the calling thread with the task of
   * continual sending and receiving as data arrives.
   */
  public CompletableFuture<Void> selectLoop() {
    return setupMulticastDatagramAsync(candidateBindAddresses)
        .thenApply(datagramChannel -> {
          try (final Selector datagramChanSelector = Selector.open()) {
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
                  Optional.ofNullable(outgoingMessageSupplier.get())
                      .filter(byteBuffer -> byteBuffer.array().length > 0)
                      .ifPresent(bb -> runMulticastSend(bb, datagramChannel, membershipKey));
                }
              }
            }
          } catch (IOException exc) {
            LOG.error("unexpected exception in main event selectLoop", exc);
          }

          return null;
        });
  }

  private void runMulticastSend(ByteBuffer bb, DatagramChannel dc, MembershipKey mk) {
    try {
      final var sendAddress = new InetSocketAddress(mk.group(), udpPort);
      final var bufferLength = bb.array().length;
      final var sentBytes = dc.send(bb, sendAddress);

      if (bufferLength != sentBytes) {
        LOG.warn("only successfully sent {} of {} bytes", sentBytes, bufferLength);
      } else {
        LOG.debug("sent {} bytes", sentBytes);
      }
    } catch (IOException exc) {
      LOG.error("i/o exception while attempting to send", exc);
    }
  }


  private CompletableFuture<DatagramChannel> setupMulticastDatagramAsync(InetAddress bindAddress) {
    return CompletableFuture.supplyAsync(() -> {
      try {
        final StandardProtocolFamily spf;

        if (bindAddress instanceof java.net.Inet4Address) {
          spf = java.net.StandardProtocolFamily.INET;
        } else if (bindAddress instanceof java.net.Inet6Address) {
          spf = java.net.StandardProtocolFamily.INET6;
        } else {
          throw new RuntimeException("unrecognized inet address type");
        }

        final DatagramChannel channel = DatagramChannel.open(spf);

        CompletableFuture.runAsync(() -> {
          try {
            channel.setOption(StandardSocketOptions.IP_MULTICAST_IF, multicastInterface);
          } catch (IOException ioe) {
            throw new IllegalStateException("failed to set IP_MULTICAST_IF socket option", ioe);
          }
        }).thenRun(() -> {
          try {
            channel.configureBlocking(false);
          } catch (IOException ioe) {
            throw new IllegalStateException("failed to configure a non-blocking channel", ioe);
          }
        }).get();

        this.membershipKey = channel.join(bindAddress, multicastInterface);
        LOG.debug("joined to group {}", this.membershipKey.group());
        return channel;
      } catch (IOException | InterruptedException | ExecutionException eiieee) {
        LOG.error("failed to setup a datagram channel", eiieee);
        throw new RuntimeException("failed to setup a datagram channel", eiieee);
      }
    });

  }

  private CompletableFuture<DatagramChannel> setupMulticastDatagramAsync(List<InetAddress> bindAddresses) {
    var bindFs = bindAddresses
        .stream()
        .map(this::setupMulticastDatagramAsync)
        .toList();

    return CompletableFuture.allOf(bindFs.toArray(new CompletableFuture[0])).thenApply(ignored ->
        {
          var firstSetupF = bindFs
              .stream()
              .filter(datagramSetupF -> datagramSetupF.isDone() && !datagramSetupF.isCancelled())
              .findFirst()
              .orElseThrow();

          return firstSetupF.join();
        })
        .thenApply(datagramChannel -> {
          CompletableFuture
              .runAsync(() -> {
                try {
                  datagramChannel.socket().bind(multicastListenSocketAddress);
                } catch (SocketException se) {
                  throw new RuntimeException("failed to bind datagram channel to :"
                      + multicastListenSocketAddress);
                }
              }).join();

          return datagramChannel;
        });
  }
}
