package name.maxdeliso.teflon.net;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages network interface discovery and filtering.
 * Provides methods to query available network interfaces suitable for multicast.
 */
public class NetworkInterfaceManager {

    /**
     * Queries and filters network interfaces suitable for multicast communication.
     *
     * @return List of suitable network interfaces
     */
    public List<NetworkInterface> queryInterfaces() {
        return queryAvailableInterfaces().stream().filter(ni -> {
            try {
                var hwAddr = ni.getHardwareAddress();
                var ifaceAddrs = ni.getInterfaceAddresses();
                var inetAddrs = ni.getInetAddresses();

                return ni.isUp()
                        && ni.supportsMulticast()
                        && !ni.isLoopback()
                        && !ni.isPointToPoint()
                        && !ni.isVirtual()
                        && hwAddr != null
                        && !ifaceAddrs.isEmpty()
                        && inetAddrs.hasMoreElements();
            } catch (SocketException exc) {
                return false;
            }
        }).collect(Collectors.toList());
    }

    /**
     * Queries all available network interfaces.
     *
     * @return List of all network interfaces, or empty list if query fails
     */
    private List<NetworkInterface> queryAvailableInterfaces() {
        try {
            return Collections.list(NetworkInterface.getNetworkInterfaces());
        } catch (SocketException se) {
            return new ArrayList<>();
        }
    }
}
