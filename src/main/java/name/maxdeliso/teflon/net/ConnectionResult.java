package name.maxdeliso.teflon.net;

import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.Objects;

/**
 * Represents the result of establishing a network connection.
 * Contains the port, datagram channel, and multicast membership key.
 */
public final class ConnectionResult {
    /**
     * The port number used for the connection.
     */
    private final int port;

    /**
     * The datagram channel for network communication.
     */
    private final DatagramChannel dc;

    /**
     * The multicast group membership key.
     */
    private final MembershipKey membershipKey;

    /**
     * Creates a new connection result.
     *
     * @param portNum The port number used
     * @param channel The datagram channel
     * @param key     The multicast membership key
     */
    public ConnectionResult(final int portNum,
                            final DatagramChannel channel,
                            final MembershipKey key) {
        this.port = portNum;
        this.dc = channel;
        this.membershipKey = key;
    }

    /**
     * Gets the datagram channel.
     *
     * @return The datagram channel
     */
    public DatagramChannel getDc() {
        return dc;
    }

    /**
     * Gets the port number.
     *
     * @return The port number
     */
    public int getPort() {
        return port;
    }

    /**
     * Gets the multicast membership key.
     *
     * @return The membership key
     */
    public MembershipKey getMembershipKey() {
        return membershipKey;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        var that = (ConnectionResult) o;
        return port == that.port
                && Objects.equals(dc, that.dc)
                && Objects.equals(membershipKey, that.membershipKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(port, dc, membershipKey);
    }

    @Override
    public String toString() {
        return "{" + membershipKey + " " + port + '}';
    }
}
