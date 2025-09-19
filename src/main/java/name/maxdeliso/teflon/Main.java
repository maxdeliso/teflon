package name.maxdeliso.teflon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import name.maxdeliso.teflon.data.JsonMessageMarshaller;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.data.MessageMarshaller;
import name.maxdeliso.teflon.net.ConnectionManager;
import name.maxdeliso.teflon.net.NetworkInterfaceManager;
import name.maxdeliso.teflon.ui.MainFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.SwingUtilities;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;

import static java.util.UUID.randomUUID;

/**
 * Main entry point for the Teflon chat application.
 * Sets up the application environment and launches the UI.
 */
public final class Main {
    /**
     * IPv6 multicast address for chat communication.
     */
    public static final String MULTICAST_IPV6_BIND_ADDRESS = "FF02::77";

    /**
     * IPv4 multicast address for chat communication.
     */
    public static final String MULTICAST_IPV4_BIND_ADDRESS = "224.0.0.122";

    /**
     * Default UDP port for network communication.
     */
    public static final int DEFAULT_UDP_PORT = 1337;

    /**
     * Buffer size for network operations.
     */
    public static final int BUFFER_LENGTH = 4096;

    /**
     * Queue for transferring messages between UI and network threads.
     */
    public static final TransferQueue<Message> TRANSFER_QUEUE = new LinkedTransferQueue<>();

    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(Main.class);

    /**
     * JSON serializer/deserializer.
     */
    private static final Gson GSON = new GsonBuilder().create();

    /**
     * Message marshaller for network communication.
     */
    public static final MessageMarshaller MESSAGE_MARSHALLER = new JsonMessageMarshaller(GSON);

    /**
     * Unique identifier for this application instance.
     */
    private static final UUID INSTANCE_ID = randomUUID();

    /**
     * Manager for network interface operations.
     */
    private static final NetworkInterfaceManager INTERFACE_MANAGER = new NetworkInterfaceManager();

    /**
     * Manager for network connections.
     */
    private static final ConnectionManager CONNECTION_MANAGER = new ConnectionManager();

    /**
     * Private constructor to prevent instantiation.
     */
    private Main() {
        // Utility class should not be instantiated
    }

    /**
     * Application entry point.
     *
     */
    static void main() {
        try (var netExecutor = Executors.newSingleThreadExecutor()) {
            SwingUtilities.invokeLater(() -> {
                var mainFrame = new MainFrame(
                        INSTANCE_ID,
                        TRANSFER_QUEUE::add,
                        netExecutor,
                        CONNECTION_MANAGER,
                        INTERFACE_MANAGER
                );
                mainFrame.setVisible(true);
                mainFrame.getInputTextField().requestFocusInWindow();
            });

            Thread.currentThread().join();
        } catch (InterruptedException e) {
            LOG.warn("Main thread interrupted, shutting down.");
            Thread.currentThread().interrupt();
        }
    }
}
