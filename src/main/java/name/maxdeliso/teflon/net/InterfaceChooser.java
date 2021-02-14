package name.maxdeliso.teflon.net;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class InterfaceChooser {
  private List<NetworkInterface> queryAvailableInterfaces() {
    try {
      return Collections.list(NetworkInterface.getNetworkInterfaces());
    } catch (SocketException se) {
      return new ArrayList<>();
    }
  }

  public List<NetworkInterface> queryInterfaces() {
    return queryAvailableInterfaces()
        .stream()
        .filter(ni -> {
          try {
            return ni.isUp() &&
                ni.supportsMulticast() &&
                !ni.isLoopback() &&
                !ni.isPointToPoint() &&
                !ni.isVirtual();
          } catch (SocketException exc) {
            return false;
          }
        })
        .collect(Collectors.toList());
  }
}
