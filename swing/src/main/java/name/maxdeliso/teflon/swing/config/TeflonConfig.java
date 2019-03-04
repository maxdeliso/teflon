package name.maxdeliso.teflon.swing.config;

public class TeflonConfig {
    private int udpPort;

    private int bufferLength;

    private int backlogLength;

    private String hostAddress;

    private String interfaceName;

    public int getUdpPort() {
        return udpPort;
    }

    public int getBufferLength() {
        return bufferLength;
    }

    public int getBacklogLength() {
        return backlogLength;
    }

    public String getHostAddress() {
        return hostAddress;
    }

    public String getInterfaceName() {
        return interfaceName;
    }
}
