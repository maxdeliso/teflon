package name.maxdeliso.teflon.config;

public class Config {
    private int udpPort;

    private int inputBufferLength;

    private String messageSeparator;

    private int backlogLength;

    private String multicastGroup;

    private String interfaceName;

    public int getUdpPort() {
        return udpPort;
    }

    public int getBacklogLength() {
        return backlogLength;
    }

    public String getMulticastGroup() {
        return multicastGroup;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public int getInputBufferLength() {
        return inputBufferLength;
    }
}
