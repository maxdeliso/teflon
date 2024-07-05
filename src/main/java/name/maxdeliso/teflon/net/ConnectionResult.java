package name.maxdeliso.teflon.net;

import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.Objects;

public class ConnectionResult {
    private final int port;
    private final DatagramChannel dc;

    public DatagramChannel getDc() {
        return dc;
    }

    public int getPort() {
        return port;
    }

    public MembershipKey getMembershipKey() {
        return membershipKey;
    }

    private final MembershipKey membershipKey;

    public ConnectionResult(int port,
                            DatagramChannel dc,
                            MembershipKey mk) {
        this.port = port;
        this.dc = dc;
        this.membershipKey = mk;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConnectionResult that = (ConnectionResult) o;
        return port == that.port && Objects.equals(dc, that.dc) && Objects.equals(membershipKey, that.membershipKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(port, dc, membershipKey);
    }

    @Override
    public String toString() {
        return "{" +
                membershipKey + " " + port +
                '}';
    }
}
