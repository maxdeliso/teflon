package name.maxdeliso.teflon.net;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.util.concurrent.CompletableFuture;

public class ConnectionManager {
    private static final Logger LOG = LogManager.getLogger(ConnectionManager.class);

    public CompletableFuture<ConnectionResult> connectMulticast(
            String ipAddress,
            int port,
            NetworkInterface networkInterface
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var addr = InetAddress.getByName(ipAddress);
                if (!addr.isMulticastAddress()) {
                    throw new IllegalArgumentException(addr + " must be a multicast address");
                }
                return addr;
            } catch (UnknownHostException uhe) {
                throw new RuntimeException(uhe);
            }
        }).thenCompose(inetAddress -> openBind(networkInterface, port, inetAddress).thenApply(datagramChannel -> {
            try {
                var key = datagramChannel.join(inetAddress, networkInterface);
                return new ConnectionResult(port, datagramChannel, key);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    private CompletableFuture<DatagramChannel> openBind(NetworkInterface iface, int udpPort, InetAddress addr) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                final var dc = DatagramChannel.open(reflectProtocolFamily(addr));
                dc.setOption(StandardSocketOptions.IP_MULTICAST_IF, iface);
                dc.setOption(StandardSocketOptions.SO_REUSEADDR, true);
                dc.configureBlocking(false);
                return dc.bind(new InetSocketAddress(udpPort));
            } catch (IOException ioe) {
                LOG.warn("failed to join {} {}", addr, iface, ioe);
                throw new RuntimeException(ioe);
            }
        });
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
