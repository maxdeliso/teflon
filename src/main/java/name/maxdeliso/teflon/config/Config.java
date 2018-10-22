package name.maxdeliso.teflon.config;

/**
 * A class to hold configuration values.
 */
public final class Config {
    public static final int TEFLON_PORT = 1337;
    public static final int IO_TIMEOUT_MS = 5;

    public static final int INPUT_BUFFER_LEN = 4096;
    public static final int TEFLON_WIDTH = 512;
    public static final int TEFLON_HEIGHT = 316;
    public static final String TEFLON_TITLE = "Teflon";

    public static final String MESSAGE_SEPARATOR = " >> ";

    public static final int BACKLOG_LENGTH = 1024;

    public static final String MULTICAST_GROUP = "FF02:0:0:0:0:0:0:77";
}
