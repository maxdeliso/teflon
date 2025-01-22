package name.maxdeliso.teflon.net;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MulticastSenderTest {

    @Mock
    private DatagramChannel datagramChannel;

    @Mock
    private InetSocketAddress socketAddress;

    private MulticastSender sender;

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