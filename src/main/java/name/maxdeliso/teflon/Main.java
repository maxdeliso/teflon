package name.maxdeliso.teflon;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import name.maxdeliso.teflon.ctx.RunContext;
import name.maxdeliso.teflon.data.JsonMessageMarshaller;
import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.data.MessageMarshaller;
import name.maxdeliso.teflon.net.NetSelector;
import name.maxdeliso.teflon.ui.MainFrame;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class Main {
  private static final Logger LOG = LogManager.getLogger(Main.class);

  private static final Gson GSON = new GsonBuilder().create();

  private static final MessageMarshaller MM = new JsonMessageMarshaller(GSON);

  private static final String MCAST_ADDR = "FF02:0:0:0:0:0:0:77";

  private static final int UDP_PORT = 1337;

  private static final int BUFFER_LENGTH = 4096;

  private static final AtomicReference<MainFrame> mainFrameRef = new AtomicReference<>();

  public static void main(String[] args) {
    try {
      var firstViableInterface = Collections
          .list(NetworkInterface.getNetworkInterfaces())
          .stream()
          .filter(ni -> {
            try {
              return ni.isUp() &&
                  ni.supportsMulticast() &&
                  !ni.isLoopback() &&
                  !ni.isPointToPoint() &&
                  !ni.isVirtual();
            } catch (SocketException exc) {
              LOG.error("failed to interrogate prospective network interface", exc);
              return false;
            }
          })
          .findFirst()
          .orElseThrow();

      var transferQueue = new LinkedTransferQueue<Message>();
      var rc = new RunContext();
      var bindAddr = InetAddress.getByName(MCAST_ADDR);

      LOG.info("attempting bind to {} on interface {}", bindAddr, firstViableInterface);

      mainFrameRef.set(new MainFrame(rc, transferQueue::add));

      var netSelector = new NetSelector(UDP_PORT, BUFFER_LENGTH,
          (_addr, rxBytes) -> MM
              .bufferToMessage(rxBytes)
              .ifPresent(msg -> mainFrameRef.get().queueMessageDisplay(msg)),
          bindAddr,
          firstViableInterface,
          () -> Optional
              .ofNullable(transferQueue.poll())
              .map(MM::messageToBuffer)
              .orElse(null)
      );

      mainFrameRef.get().setVisible(true);
      netSelector.selectLoop();
      mainFrameRef.get().dispose();
    } catch (SocketException | UnknownHostException exc) {
      var dialogFrame = new JFrame();

      JOptionPane.showMessageDialog(
          dialogFrame,
          exc.getMessage(),
          "Network Error",
          JOptionPane.ERROR_MESSAGE);

      LOG.error("network related failure", exc);
      dialogFrame.dispose();
    } finally {
      var mainFrame = mainFrameRef.get();

      if(mainFrame != null) {
        mainFrame.dispose();
      }
    }
  }
}
