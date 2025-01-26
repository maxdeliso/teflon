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

/**
 * Manages network connections for multicast communication.
 * Handles connection setup, channel configuration, and group membership.
 */
public class ConnectionManager {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(ConnectionManager.class);

    /**
     * Asynchronously connects to a multicast group.
     *
     * @param ipAddress        The multicast IP address to connect to
     * @param port             The port to use
     * @param networkInterface The network interface to use
     * @return A future that completes with the connection result
     */
    public CompletableFuture<ConnectionResult> connectMulticast(
            final String ipAddress,
            final int port,
            final NetworkInterface networkInterface) {
        return CompletableFuture.supplyAsync(() -> {
            var inetAddress = resolveMulticastAddress(ipAddress);
            var datagramChannel = openAndBindChannel(inetAddress, port, networkInterface);
            var membershipKey = joinGroup(datagramChannel, inetAddress, networkInterface);

            return new ConnectionResult(port, datagramChannel, membershipKey);
        });
    }

    /**
     * Resolves a multicast address string to an InetAddress.
     *
     * @param ipAddress The IP address string to resolve
     * @return The resolved InetAddress
     * @throws CompletionException if resolution fails
     */
    private InetAddress resolveMulticastAddress(final String ipAddress) {
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

    /**
     * Opens and configures a datagram channel.
     *
     * @param addr  The address to bind to
     * @param port  The port to bind to
     * @param netIf The network interface to use
     * @return The configured datagram channel
     * @throws CompletionException if channel setup fails
     */
    private DatagramChannel openAndBindChannel(final InetAddress addr,
                                               final int port,
                                               final NetworkInterface netIf) {
        try {
            var family = protocolFamilyForAddress(addr);
            var dc = DatagramChannel.open(family);

            // Common options for both IPv4 and IPv6
            dc.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            dc.setOption(StandardSocketOptions.IP_MULTICAST_IF, netIf);
            dc.setOption(StandardSocketOptions.IP_MULTICAST_TTL, 8);
            dc.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, true);

            if (family == StandardProtocolFamily.INET6) {
                LOG.debug("Configuring IPv6 channel for {}", addr);
                // For IPv6, bind to any address (::) to receive multicast
                dc.bind(new InetSocketAddress("::", port));
            } else {
                LOG.debug("Configuring IPv4 channel for {}", addr);
                // For IPv4, bind to any address (0.0.0.0) to receive multicast
                dc.bind(new InetSocketAddress(port));
            }

            dc.configureBlocking(false);
            LOG.debug("Channel configured and bound successfully");
            return dc;
        } catch (IOException e) {
            String msg = String.format("Failed to open or bind channel for %s on interface %s: %s",
                    addr, netIf, e.getMessage());
            LOG.error(msg, e);
            throw new CompletionException(msg, e);
        }
    }

    /**
     * Joins a multicast group.
     *
     * @param channel   The channel to use
     * @param groupAddr The group address to join
     * @param netIf     The network interface to use
     * @return The membership key
     * @throws CompletionException if joining fails
     */
    private MembershipKey joinGroup(final DatagramChannel channel,
                                    final InetAddress groupAddr,
                                    final NetworkInterface netIf) {
        try {
            // For IPv6, we need to ensure the scope ID is set for link-local addresses
            if (groupAddr instanceof Inet6Address && groupAddr.isLinkLocalAddress()) {
                // Create a new IPv6 address with the correct scope ID
                var ipv6Addr = Inet6Address.getByAddress(
                        null,
                        groupAddr.getAddress(),
                        netIf
                );
                return channel.join(ipv6Addr, netIf);
            }
            return channel.join(groupAddr, netIf);
        } catch (IOException e) {
            String msg = String.format("Failed to join multicast group %s on interface %s",
                    groupAddr, netIf);
            LOG.error(msg, e);

            try {
                channel.close();
            } catch (IOException closeEx) {
                LOG.debug("Error closing channel after join failure", closeEx);
            }

            throw new CompletionException(msg, e);
        }
    }

    /**
     * Determines the protocol family for an address.
     *
     * @param inetAddress The address to check
     * @return The appropriate protocol family
     * @throws UnsupportedAddressTypeException if the address type is not supported
     */
    private ProtocolFamily protocolFamilyForAddress(final InetAddress inetAddress) {
        if (inetAddress instanceof Inet4Address) {
            return StandardProtocolFamily.INET;
        } else if (inetAddress instanceof Inet6Address) {
            return StandardProtocolFamily.INET6;
        } else {
            String msg = "Unrecognized address type: " + (inetAddress != null ? inetAddress.getClass() : "null");
            LOG.error(msg);
            throw new UnsupportedAddressTypeException();
        }
    }
}
