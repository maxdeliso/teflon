package name.maxdeliso.teflon.net.test;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import name.maxdeliso.teflon.net.MulticastSender;

@ExtendWith(MockitoExtension.class)
public class MulticastSenderTest {

    @Mock
    private DatagramChannel datagramChannel;

    @Mock
    private InetSocketAddress socketAddress;

    private MulticastSender sender;

    @Mock
    private DatagramSocket mockSocket;

    @BeforeEach
    void setUp() {
        sender = new MulticastSender(datagramChannel, socketAddress);
    }

    @Test
    void testSuccessfulSend() throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap("test message".getBytes());
        when(datagramChannel.send(any(ByteBuffer.class), eq(socketAddress))).thenReturn(12);

        sender.send(buffer);
        verify(datagramChannel).send(any(ByteBuffer.class), eq(socketAddress));
    }

    @Test
    void testPartialSend() throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap("test message".getBytes());
        when(datagramChannel.send(any(ByteBuffer.class), eq(socketAddress))).thenReturn(6);

        sender.send(buffer);
        verify(datagramChannel).send(any(ByteBuffer.class), eq(socketAddress));
    }

    @Test
    void testSendWithIOException() throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap("test message".getBytes());
        when(datagramChannel.send(any(ByteBuffer.class), eq(socketAddress)))
                .thenThrow(new IOException("Test exception"));

        sender.send(buffer);
        verify(datagramChannel).send(any(ByteBuffer.class), eq(socketAddress));
    }

    @Test
    void testSendWithNullBuffer() {
        MulticastSender sender = new MulticastSender(datagramChannel, socketAddress);
        assertThrows(NullPointerException.class, () -> sender.send(null));
        verifyNoInteractions(datagramChannel);
    }

    @Test
    void testSendWithEmptyBuffer() {
        ByteBuffer emptyBuffer = ByteBuffer.allocate(0);
        sender.send(emptyBuffer);
        verifyNoInteractions(datagramChannel);
    }
}