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
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.UnsupportedAddressTypeException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public class ConnectionManager {
    private static final Logger LOG = LogManager.getLogger(ConnectionManager.class);

    /**
     * Asynchronously connects to the given multicast address and port on
     * the specified network interface, returning a ConnectionResult.
     */
    public CompletableFuture<ConnectionResult> connectMulticast(
            String ipAddress,
            int port,
            NetworkInterface networkInterface
    ) {
        return CompletableFuture.supplyAsync(() -> {
            var inetAddress = resolveMulticastAddress(ipAddress);
            var datagramChannel = openAndBindChannel(inetAddress, port, networkInterface);
            var membershipKey = joinGroup(datagramChannel, inetAddress, networkInterface);

            return new ConnectionResult(port, datagramChannel, membershipKey);
        });
    }

    private InetAddress resolveMulticastAddress(String ipAddress) {
        try {
            var address = InetAddress.getByName(ipAddress);
            if (!address.isMulticastAddress()) {
                throw new IllegalArgumentException(address + " must be a multicast address");
            }
            return address;
        } catch (UnknownHostException e) {
            throw new CompletionException("Failed to resolve host: " + ipAddress, e);
        }
    }

    private DatagramChannel openAndBindChannel(InetAddress addr, int port, NetworkInterface netIf) {
        try {
            var family = protocolFamilyForAddress(addr);
            var dc = DatagramChannel.open(family);
            dc.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            dc.setOption(StandardSocketOptions.IP_MULTICAST_IF, netIf);
            dc.configureBlocking(false);
            dc.bind(new InetSocketAddress(port));
            return dc;
        } catch (IOException e) {
            String msg = String.format("Failed to open or bind channel for %s on interface %s", addr, netIf);
            LOG.error(msg, e);
            throw new CompletionException(msg, e);
        }
    }

    private MembershipKey joinGroup(DatagramChannel channel, InetAddress groupAddr, NetworkInterface netIf) {
        try {
            return channel.join(groupAddr, netIf);
        } catch (IOException e) {
            String msg = String.format("Failed to join multicast group %s on interface %s", groupAddr, netIf);
            LOG.error(msg, e);

            try {
                channel.close();
            } catch (IOException closeEx) {
                LOG.debug("Error closing channel after join failure", closeEx);
            }

            throw new CompletionException(msg, e);
        }
    }

    private ProtocolFamily protocolFamilyForAddress(InetAddress inetAddress) {
        if (inetAddress instanceof Inet4Address) {
            return StandardProtocolFamily.INET;
        } else if (inetAddress instanceof Inet6Address) {
            return StandardProtocolFamily.INET6;
        } else {
            String msg = "Unrecognized address type: " + inetAddress.getClass();
            LOG.error(msg);
            throw new UnsupportedAddressTypeException();
        }
    }
}