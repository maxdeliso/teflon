package name.maxdeliso.teflon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import name.maxdeliso.teflon.data.JsonMessageMarshaller;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.data.MessageMarshaller;
import name.maxdeliso.teflon.net.InterfaceChooser;
import name.maxdeliso.teflon.net.NetSelector;
import name.maxdeliso.teflon.ui.MainFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;

import static java.util.UUID.randomUUID;

class Main {
  private static final Logger LOG = LogManager.getLogger(Main.class);

  private static final Gson GSON = new GsonBuilder().create();

  private static final MessageMarshaller MESSAGE_MARSHALLER = new JsonMessageMarshaller(GSON);

  private static final String MULTICAST_IPV6_BIND_ADDRESS = "FF02::77";

  private static final String MULTICAST_IPV4_BIND_ADDRESS = "224.0.0.122";

  private static final int UDP_PORT = 1337;

  private static final int BUFFER_LENGTH = 4096;

  private static final TransferQueue<Message> TRANSFER_QUEUE = new LinkedTransferQueue<>();

  private static final UUID UUID = randomUUID();

  private static final CompletableFuture<MainFrame> MAIN_FRAME_F =
      CompletableFuture
          .supplyAsync(() -> new MainFrame(UUID, TRANSFER_QUEUE::add))
          .thenApply(mainFrame -> {
            mainFrame.setVisible(true);
            return mainFrame;
          });

  private static CompletableFuture<List<InetAddress>> lookupAddressesAsync(String... addressLiterals) {
    return CompletableFuture
        .supplyAsync(() -> {
          List<InetAddress> results = new ArrayList<>();

          for (String address : addressLiterals) {
            try {
              results.add(InetAddress.getByName(address));
            } catch (UnknownHostException uhe) {
              LOG.warn("failed to get configured address by name: {}", address, uhe);
            }
          }

          if (results.isEmpty()) {
            throw new RuntimeException("failed to lookup any of the supplied addresses: " +
                String.join(", ", addressLiterals));
          }

          return results;
        });
  }

  public static void main(String[] args) {
    try {
      LOG.info("starting up with UUID {}", UUID);

      lookupAddressesAsync(MULTICAST_IPV6_BIND_ADDRESS, MULTICAST_IPV4_BIND_ADDRESS)
          .thenCompose(addresses -> MAIN_FRAME_F
              .thenCompose(mainFrame ->
                  CompletableFuture.supplyAsync(() -> new NetSelector(
                          UDP_PORT,
                          BUFFER_LENGTH,
                          addresses,
                          new InterfaceChooser().queryInterfaces(),
                          (_address, bb) -> MESSAGE_MARSHALLER
                              .bufferToMessage(bb)
                              .ifPresent(mainFrame::queueMessageDisplay),
                          () -> Optional
                              .ofNullable(TRANSFER_QUEUE.poll())
                              .map(MESSAGE_MARSHALLER::messageToBuffer)
                              .orElse(null)
                      )
                  ).thenCompose(NetSelector::selectLoop)))
          .get();
    } catch (InterruptedException ie) {
      LOG.error("synchronization error", ie);
      System.exit(1);
    } catch (ExecutionException ee) {
      LOG.error("error", ee.getCause());

      var dialogFrame = new JFrame();
      JOptionPane.showMessageDialog(
          dialogFrame,
          "A fatal error has occurred and logs have been written to "
              + Paths.get(".").toAbsolutePath().normalize().toString()
              + " : "
              + ee.getCause().getMessage()
              + ".",
          "Error",
          JOptionPane.ERROR_MESSAGE);
      dialogFrame.dispose();
      System.exit(2);
    }
  }
}
