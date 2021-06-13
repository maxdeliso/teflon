package name.maxdeliso.teflon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import name.maxdeliso.teflon.data.JsonMessageMarshaller;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.data.MessageMarshaller;
import name.maxdeliso.teflon.net.InterfaceChooser;
import name.maxdeliso.teflon.net.NetSelector;
import name.maxdeliso.teflon.ui.MainFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class Main {
  private static final Logger LOG = LogManager.getLogger(Main.class);

  private static final Gson GSON = new GsonBuilder().create();

  private static final MessageMarshaller MESSAGE_MARSHALLER = new JsonMessageMarshaller(GSON);

  private static final String MULTICAST_BIND_ADDRESS = "FF02:0:0:0:0:0:0:77";

  private static final int UDP_PORT = 1337;

  private static final int BUFFER_LENGTH = 4096;

  private static final TransferQueue<Message> TRANSFER_QUEUE = new LinkedTransferQueue<>();

  private static final UUID UUID = java.util.UUID.randomUUID();

  private static final CompletableFuture<MainFrame> MAIN_FRAME_F =
      CompletableFuture
          .supplyAsync(() -> new MainFrame(UUID, TRANSFER_QUEUE::add))
          .thenApply(mainFrame -> {
            mainFrame.setVisible(true);
            return mainFrame;
          });

  private static final CompletableFuture<Optional<InetAddress>> BIND_F =
      CompletableFuture
          .supplyAsync(() ->
              {
                try {
                  var bindAddress = Inet6Address.getByName(MULTICAST_BIND_ADDRESS);
                  return Optional.ofNullable(bindAddress);
                } catch (UnknownHostException uke) {
                  return Optional.empty();
                }
              }
          );

  private static final CompletableFuture<Optional<NetworkInterface>> INTERFACE_OPT_F =
      CompletableFuture
          .supplyAsync(InterfaceChooser::new)
          .thenApply(interfaceChooser -> interfaceChooser.queryInterfaces().stream().findFirst());

  private static final CompletableFuture<Void> MAIN =
      BIND_F.thenCompose(inetAddressOpt -> INTERFACE_OPT_F.thenCompose(networkInterfaceOpt -> {
        var inetAddress = inetAddressOpt
            .orElseThrow(
                () -> new IllegalStateException("failed to bind"));

        var networkInterface = networkInterfaceOpt
            .orElseThrow(
                () -> new IllegalStateException("failed to locate viable network interface"));

        return MAIN_FRAME_F
            .thenCompose(mainFrame -> networkLoop(mainFrame, inetAddress, networkInterface));
      }));

  private static CompletableFuture<Void> networkLoop(
      MainFrame mainFrame,
      InetAddress bindAddress,
      NetworkInterface networkInterface) {
    return CompletableFuture.supplyAsync(() -> new NetSelector(
            UDP_PORT,
            BUFFER_LENGTH,
            (_address, rxBytes) -> MESSAGE_MARSHALLER
                .bufferToMessage(rxBytes)
                .ifPresent(mainFrame::queueMessageDisplay),
            bindAddress,
            networkInterface,
            () -> Optional
                .ofNullable(TRANSFER_QUEUE.poll())
                .map(MESSAGE_MARSHALLER::messageToBuffer)
                .orElse(null)
        )
    ).thenCompose(NetSelector::selectLoop);
  }

  public static void main(String[] args) {
    try {
      LOG.info("starting up with UUID {}", UUID);
      MAIN.get();
    } catch (InterruptedException ie) {
      LOG.error("synchronization error", ie);
      System.exit(1);
    } catch (ExecutionException ee) {
      LOG.error("error", ee.getCause());

      var dialogFrame = new JFrame();
      JOptionPane.showMessageDialog(
          dialogFrame,
          ee.getCause().getMessage()
              + ". Please consult the log files for more information.",
          "Error",
          JOptionPane.ERROR_MESSAGE);
      dialogFrame.dispose();
      System.exit(2);
    }
  }
}
