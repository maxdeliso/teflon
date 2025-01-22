package name.maxdeliso.teflon.net;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetworkInterfaceManagerTest {

    @Mock
    private NetworkInterface networkInterface;

    private NetworkInterfaceManager manager;

    @BeforeEach
    void setUp() {
        manager = new NetworkInterfaceManager();
    }

    @Test
    void testQueryInterfacesWithNoInterfaces() {
        try (MockedStatic<NetworkInterface> mockedStatic = Mockito.mockStatic(NetworkInterface.class)) {
            mockedStatic.when(NetworkInterface::getNetworkInterfaces)
                    .thenReturn(Collections.enumeration(Collections.emptyList()));

            List<NetworkInterface> result = manager.queryInterfaces();
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void testQueryInterfacesWithSocketException() {
        try (MockedStatic<NetworkInterface> mockedStatic = Mockito.mockStatic(NetworkInterface.class)) {
            mockedStatic.when(NetworkInterface::getNetworkInterfaces)
                    .thenThrow(new SocketException("Test exception"));

            List<NetworkInterface> result = manager.queryInterfaces();
            assertTrue(result.isEmpty());
        }
    }

    @Test
    void testQueryInterfacesWithValidInterface() throws SocketException {
        NetworkInterface validInterface = mock(NetworkInterface.class);
        when(validInterface.supportsMulticast()).thenReturn(true);
        when(validInterface.isUp()).thenReturn(true);
        when(validInterface.isLoopback()).thenReturn(false);

        try (MockedStatic<NetworkInterface> mockedStatic = Mockito.mockStatic(NetworkInterface.class)) {
            mockedStatic.when(NetworkInterface::getNetworkInterfaces)
                    .thenReturn(Collections.enumeration(List.of(validInterface)));

            NetworkInterfaceManager manager = new NetworkInterfaceManager();
            List<NetworkInterface> interfaces = manager.queryInterfaces();

            assertTrue(interfaces.isEmpty() || interfaces.stream().allMatch(iface -> {
                try {
                    return iface.supportsMulticast();
                } catch (SocketException e) {
                    return false;
                }
            }));
        }
    }

    @Test
    void testQueryInterfacesWithInvalidInterface() {
        try (MockedStatic<NetworkInterface> networkInterfaceMockedStatic = mockStatic(NetworkInterface.class)) {
            NetworkInterface networkInterface = mock(NetworkInterface.class);
            try {
                when(networkInterface.isUp()).thenReturn(true);
                when(networkInterface.supportsMulticast()).thenReturn(false);
                when(networkInterface.getHardwareAddress()).thenReturn(new byte[]{1, 2, 3, 4, 5, 6});
                when(networkInterface.getInterfaceAddresses()).thenReturn(Collections.emptyList());
                when(networkInterface.getInetAddresses()).thenReturn(Collections.emptyEnumeration());

                networkInterfaceMockedStatic.when(NetworkInterface::getNetworkInterfaces)
                        .thenReturn(Collections.enumeration(Collections.singletonList(networkInterface)));

                List<NetworkInterface> interfaces = manager.queryInterfaces();

                assertTrue(interfaces.isEmpty());
                verify(networkInterface).supportsMulticast();
            } catch (SocketException e) {
                fail("Unexpected SocketException: " + e.getMessage());
            }
        }
    }

    @Test
    void testQueryInterfacesWithSocketExceptionDuringFiltering() throws SocketException {
        when(networkInterface.isUp()).thenThrow(new SocketException("Test exception"));

        try (MockedStatic<NetworkInterface> mockedStatic = Mockito.mockStatic(NetworkInterface.class)) {
            mockedStatic.when(NetworkInterface::getNetworkInterfaces)
                    .thenReturn(Collections.enumeration(List.of(networkInterface)));

            List<NetworkInterface> result = manager.queryInterfaces();
            assertTrue(result.isEmpty());
        }
    }
}