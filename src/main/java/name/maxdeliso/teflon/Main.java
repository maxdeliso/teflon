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

import java.util.UUID;
import java.util.concurrent.*;

import static java.util.UUID.randomUUID;

public class Main {
  private static final Logger LOG = LogManager.getLogger(Main.class);
  private static final Gson GSON = new GsonBuilder().create();

  public static final String MULTICAST_IPV6_BIND_ADDRESS = "FF02::77";
  public static final String MULTICAST_IPV4_BIND_ADDRESS = "224.0.0.122";
  public static final int DEFAULT_UDP_PORT = 1337;
  public static final int BUFFER_LENGTH = 4096;
  private static final UUID UUID = randomUUID();

  public static final MessageMarshaller MESSAGE_MARSHALLER = new JsonMessageMarshaller(GSON);
  public static final TransferQueue<Message> TRANSFER_QUEUE = new LinkedTransferQueue<>();
  private static final NetworkInterfaceManager nim = new NetworkInterfaceManager();
  private static final ConnectionManager cm = new ConnectionManager();

  public static void main(String[] args) {
    final ExecutorService netExecutor = Executors.newSingleThreadExecutor();
    var mainFrame = new MainFrame(UUID, TRANSFER_QUEUE::add, netExecutor, cm, nim);
    mainFrame.setVisible(true);
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
        netExecutor.shutdown();
        LOG.info("net executor shutdown initiated...");
        if (!netExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
          netExecutor.shutdownNow();
        }
      } catch (InterruptedException e) {
        netExecutor.shutdownNow();
      } finally {
        LOG.info("net executor shutdown complete");
      }
    }));

    LOG.info("main thread joining");
  }
}
