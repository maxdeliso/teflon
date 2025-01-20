package name.maxdeliso.teflon.net;

import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.util.Objects;

public class ConnectionResult {
    private final int port;
    private final DatagramChannel dc;
    private final MembershipKey membershipKey;

    public ConnectionResult(int port,
                            DatagramChannel dc,
                            MembershipKey mk) {
        this.port = port;
        this.dc = dc;
        this.membershipKey = mk;
    }

    public DatagramChannel getDc() {
        return dc;
    }

    public int getPort() {
        return port;
    }

    public MembershipKey getMembershipKey() {
        return membershipKey;
    }

    @Override
    public boolean equals(Object o) {
        boolean result;
        if (this == o) {
            result = true;
        } else if (o == null || getClass() != o.getClass()) {
            result = false;
        } else {
            var that = (ConnectionResult) o;
            result = (port == that.port) &&
                    java.util.Objects.equals(dc, that.dc) &&
                    java.util.Objects.equals(membershipKey, that.membershipKey);
        }
        return result;
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
