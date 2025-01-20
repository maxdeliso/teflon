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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;

import static java.util.UUID.randomUUID;

public class Main {
    public static final String MULTICAST_IPV6_BIND_ADDRESS = "FF02::77";
    public static final String MULTICAST_IPV4_BIND_ADDRESS = "224.0.0.122";
    public static final int DEFAULT_UDP_PORT = 1337;
    public static final int BUFFER_LENGTH = 4096;
    public static final TransferQueue<Message> TRANSFER_QUEUE = new LinkedTransferQueue<>();
    private static final Logger LOG = LogManager.getLogger(Main.class);
    private static final Gson GSON = new GsonBuilder().create();
    public static final MessageMarshaller MESSAGE_MARSHALLER = new JsonMessageMarshaller(GSON);
    private static final UUID INSTANCE_ID = randomUUID();
    private static final NetworkInterfaceManager INTERFACE_MANAGER = new NetworkInterfaceManager();
    private static final ConnectionManager CONNECTION_MANAGER = new ConnectionManager();

    public static void main(String[] args) {
        var netExecutor = Executors.newSingleThreadExecutor();
        SwingUtilities.invokeLater(() -> {
            var mainFrame = new MainFrame(
                    INSTANCE_ID,
                    TRANSFER_QUEUE::add,
                    netExecutor,
                    CONNECTION_MANAGER,
                    INTERFACE_MANAGER
            );
            mainFrame.setVisible(true);
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdownExecutor(netExecutor);
        }));
    }

    private static void shutdownExecutor(ExecutorService executorService) {
        try {
            executorService.shutdown();
            LOG.info("Shutting down netExecutor...");
            if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                LOG.warn("Forcing netExecutor shutdownNow() after timeout.");
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            LOG.warn("Shutdown interrupted, forcing netExecutor shutdown.");
            executorService.shutdownNow();
            Thread.currentThread().interrupt(); // Restore interrupt status
        } finally {
            LOG.info("netExecutor shutdown complete.");
        }
    }
}
