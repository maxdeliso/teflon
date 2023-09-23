package name.maxdeliso.teflon.net;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
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
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * This class contains the main event selectLoop which checks in memory queues, and performs UDP
 * sending/receiving.
 */
public class NetSelector {
  private static final Logger LOG = LogManager.getLogger(NetSelector.class);
  private final int udpPort;
  private final int bufferLength;
  private final BiConsumer<SocketAddress, ByteBuffer> incomingByteBufferConsumer;
  private final List<InetAddress> groupAddresses;
  private final List<NetworkInterface> networkInterfaces;
  private final Supplier<ByteBuffer> outgoingMessageSupplier;

  public NetSelector(final int udpPort,
                     final int bufferLength,
                     final List<InetAddress> groupAddresses,
                     final List<NetworkInterface> networkInterfaces,
                     final BiConsumer<SocketAddress, ByteBuffer> incomingConsumer,
                     final Supplier<ByteBuffer> outgoingSupplier) {
    this.udpPort = udpPort;
    this.bufferLength = bufferLength;
    this.groupAddresses = groupAddresses;
    this.networkInterfaces = networkInterfaces;
    this.incomingByteBufferConsumer = incomingConsumer;
    this.outgoingMessageSupplier = outgoingSupplier;

    for(var address : groupAddresses) {
      if (!address.isMulticastAddress()) {
        throw new IllegalArgumentException(address + " must be a multicast address");
      }
    }
  }

  /**
   * Main event processing selectLoop. This function busies the calling thread with the task of
   * continual sending and receiving as data arrives.
   */
  public CompletableFuture<Void> selectLoop() {
    var dataBuffer = ByteBuffer.allocateDirect(bufferLength);

    if (networkInterfaces.size() > 1) {
      LOG.warn("multiple viable network interfaces located (will use the first one): {}",
          networkInterfaces.stream().map(NetworkInterface::getName).collect(Collectors.joining(", ")));
    }

    return CompletableFuture
        .supplyAsync(() -> networkInterfaces
            .stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("no viable network interfaces found")))
        .thenCompose(ni -> joinFirstGroup(ni, udpPort, groupAddresses, (dc, membershipKey) -> {
              var sendSockAddress = new InetSocketAddress(membershipKey.group(), udpPort);

              LOG.info("entering main select loop with interface {} and multicast send address {}",
                  ni.getDisplayName(), sendSockAddress);

              try (final var selector = Selector.open()) {
                dc.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

                while (membershipKey.isValid()) {
                  selector.select(0);

                  for (final var key : selector.selectedKeys()) {
                    if (key.isReadable()) {
                      var sender = dc.receive(dataBuffer);
                      dataBuffer.flip();
                      incomingByteBufferConsumer.accept(sender, dataBuffer.asReadOnlyBuffer());
                      dataBuffer.clear();
                    }

                    if (key.isWritable()) {
                      Optional
                          .ofNullable(outgoingMessageSupplier.get())
                          .filter(bb -> bb.array().length > 0)
                          .ifPresent(bb -> multicastSend(dc, bb, sendSockAddress));
                    }
                  }
                }
              } catch (IOException ioe) {
                throw new RuntimeException("I/O exception in main event loop", ioe);
              }
            }
        ));
  }

  private void multicastSend(DatagramChannel dc, ByteBuffer bb, InetSocketAddress isa) {
    try {
      final var bufferLength = bb.array().length;
      final var sentBytes = dc.send(bb, isa);

      if (bufferLength != sentBytes) {
        LOG.warn("only successfully sent {} of {} bytes", sentBytes, bufferLength);
      } else {
        LOG.debug("sent {} bytes", sentBytes);
      }
    } catch (IOException exc) {
      LOG.error("i/o exception while attempting to send", exc);
    }
  }

  private CompletableFuture<Void> joinFirstGroup(
      NetworkInterface networkInterface,
      int udpPort,
      List<InetAddress> groupAddressCandidates,
      BiConsumer<DatagramChannel, MembershipKey> channelMemberConsumer
  ) {
    return CompletableFuture.supplyAsync(
        () -> {
          for (InetAddress address : groupAddressCandidates) {
            try (final var dc = DatagramChannel.open(reflectProtocolFamily(address))) {
              dc.setOption(StandardSocketOptions.IP_MULTICAST_IF, networkInterface);
              dc.setOption(StandardSocketOptions.SO_REUSEADDR, true);
              dc.configureBlocking(false);
              dc.bind(new InetSocketAddress(udpPort)); // note: wildcard address
              var membershipKey = dc.join(address, networkInterface);
              channelMemberConsumer.accept(dc, membershipKey);
              return null;
            } catch (IOException ioe) {
              LOG.warn("failed to join group at address {}", address, ioe);
              // will continue to the next candidate address
            }
          }

          throw new RuntimeException("failed to set up any multicast channels");
        }
    );
  }

  private ProtocolFamily reflectProtocolFamily(InetAddress inetAddress) {
    if (inetAddress instanceof Inet4Address) {
      return StandardProtocolFamily.INET;
    } else if (inetAddress instanceof Inet6Address) {
      return StandardProtocolFamily.INET6;
    } else {
      LOG.error("invalid candidate address with unrecognized type: {}", inetAddress);
      throw new RuntimeException("invalid address type: " + inetAddress.getClass());
    }
  }
}
