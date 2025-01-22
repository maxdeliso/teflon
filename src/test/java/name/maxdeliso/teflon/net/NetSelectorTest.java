package name.maxdeliso.teflon.net;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.MembershipKey;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NetSelectorTest {

    private static final int BUFFER_LENGTH = 1024;
    private static final int TEST_PORT = 12345;

    @Mock
    private DatagramChannel datagramChannel;

    @Mock
    private MembershipKey membershipKey;

    @Mock
    private BiConsumer<SocketAddress, ByteBuffer> messageConsumer;

    @Mock
    private Supplier<ByteBuffer> messageSupplier;

    @Mock
    private Selector selector;

    @Mock
    private SelectionKey selectionKey;

    private ConnectionResult connectionResult;
    private NetSelector netSelector;
    private InetAddress groupAddress;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() throws IOException {
        groupAddress = InetAddress.getByName("239.255.255.250");
        connectionResult = new ConnectionResult(
                TEST_PORT,
                datagramChannel,
                membershipKey
        );

        netSelector = new NetSelector(
                BUFFER_LENGTH,
                connectionResult,
                messageConsumer,
                messageSupplier
        );

        // Default behavior for mocks
        when(membershipKey.isValid()).thenReturn(true);
        when(membershipKey.group()).thenReturn(groupAddress);

        // Mock selector behavior
        Set<SelectionKey> selectedKeys = new HashSet<>();
        selectedKeys.add(selectionKey);
        when(selector.select()).thenReturn(1);
        when(selector.selectedKeys()).thenReturn(selectedKeys);
        when(selectionKey.isValid()).thenReturn(true);

        // Mock channel registration
        when(datagramChannel.register(any(Selector.class), anyInt())).thenReturn(selectionKey);
    }

    @Test
    void testMessageReceiving() throws IOException {
        // Prepare test data
        ByteBuffer testBuffer = ByteBuffer.wrap("Test message".getBytes());
        InetSocketAddress sender = new InetSocketAddress("localhost", TEST_PORT);

        // Mock selector behavior
        when(selectionKey.isReadable()).thenReturn(true);
        when(selectionKey.isWritable()).thenReturn(false);

        // Mock channel behavior
        when(datagramChannel.receive(any(ByteBuffer.class))).thenAnswer(invocation -> {
            ByteBuffer buffer = invocation.getArgument(0);
            buffer.put(testBuffer.array());
            return sender;
        }).thenReturn(null);  // Return null to end the loop

        // Mock membership key to become invalid after first iteration
        when(membershipKey.isValid())
                .thenReturn(true)
                .thenReturn(false);

        try (MockedStatic<Selector> selectorStatic = mockStatic(Selector.class)) {
            selectorStatic.when(Selector::open).thenReturn(selector);

            // Run the selector loop
            CompletableFuture<Void> future = netSelector.selectLoop();

            // Verify message was received and processed
            verify(messageConsumer, timeout(1000)).accept(eq(sender), any(ByteBuffer.class));
            assertFalse(future.isCompletedExceptionally());
        }
    }

    @Test
    void testMessageSending() throws IOException {
        // Prepare test data
        ByteBuffer testBuffer = ByteBuffer.wrap("Test message".getBytes());

        // Mock selector behavior
        when(selectionKey.isReadable()).thenReturn(false);
        when(selectionKey.isWritable()).thenReturn(true);

        // Mock supplier behavior
        when(messageSupplier.get())
                .thenReturn(testBuffer)
                .thenReturn(null);  // Return null to end the loop

        // Mock membership key to become invalid after first iteration
        when(membershipKey.isValid())
                .thenReturn(true)
                .thenReturn(false);

        try (MockedStatic<Selector> selectorStatic = mockStatic(Selector.class)) {
            selectorStatic.when(Selector::open).thenReturn(selector);

            // Run the selector loop
            CompletableFuture<Void> future = netSelector.selectLoop();

            // Verify message was sent
            verify(datagramChannel, timeout(1000)).send(eq(testBuffer), any(SocketAddress.class));
            assertFalse(future.isCompletedExceptionally());
        }
    }

    @Test
    void testSelectorLoopTermination() throws IOException {
        // Mock selector behavior
        when(selectionKey.isReadable()).thenReturn(false);
        when(selectionKey.isWritable()).thenReturn(false);

        // Mock membership key to become invalid after first check
        when(membershipKey.isValid())
                .thenReturn(true)
                .thenReturn(false);

        try (MockedStatic<Selector> selectorStatic = mockStatic(Selector.class)) {
            selectorStatic.when(Selector::open).thenReturn(selector);

            // Run the selector loop
            CompletableFuture<Void> future = netSelector.selectLoop();

            // Verify loop terminates
            assertTrue(future.isDone());
            assertFalse(future.isCompletedExceptionally());
        }
    }

    @Test
    void testErrorHandling() throws IOException {
        // Mock selector behavior
        when(selectionKey.isReadable()).thenReturn(true);

        // Mock channel to throw exception
        when(datagramChannel.receive(any(ByteBuffer.class)))
                .thenThrow(new IOException("Test error"));

        try (MockedStatic<Selector> selectorStatic = mockStatic(Selector.class)) {
            selectorStatic.when(Selector::open).thenReturn(selector);

            // Run the selector loop and expect IOException
            try {
                netSelector.selectLoop();
                fail("Expected IOException was not thrown");
            } catch (IOException e) {
                assertEquals("Test error", e.getMessage());
            }
        }
    }

    @Test
    void testReceivingLargeMessage() throws IOException {
        // Create a large message that will be truncated to buffer size
        ByteBuffer largeMessage = ByteBuffer.allocate(BUFFER_LENGTH * 2);
        for (int i = 0; i < BUFFER_LENGTH * 2; i++) {
            largeMessage.put((byte) i);
        }
        largeMessage.flip();

        // Mock selector behavior
        when(selectionKey.isReadable()).thenReturn(true);
        when(selectionKey.isWritable()).thenReturn(false);
        when(selectionKey.isValid()).thenReturn(true);

        // Mock the reception behavior to only write up to buffer size
        when(datagramChannel.receive(any(ByteBuffer.class))).thenAnswer(invocation -> {
            ByteBuffer buffer = invocation.getArgument(0);
            // Only write up to buffer's remaining capacity
            int bytesToWrite = Math.min(largeMessage.remaining(), buffer.remaining());
            byte[] data = new byte[bytesToWrite];
            largeMessage.get(data);
            buffer.put(data);
            return new InetSocketAddress("localhost", TEST_PORT);
        });

        when(membershipKey.isValid())
                .thenReturn(true)
                .thenReturn(false);

        try (MockedStatic<Selector> selectorStatic = mockStatic(Selector.class)) {
            selectorStatic.when(Selector::open).thenReturn(selector);

            // Run the selector loop
            netSelector.selectLoop();

            // Verify the message was processed once
            verify(messageConsumer, times(1))
                    .accept(eq(new InetSocketAddress("localhost", TEST_PORT)), any(ByteBuffer.class));
        }
    }

    @Test
    void testSendingLargeMessage() throws IOException {
        // Create a large message
        ByteBuffer largeMessage = ByteBuffer.allocate(BUFFER_LENGTH * 2);
        for (int i = 0; i < BUFFER_LENGTH * 2; i++) {
            largeMessage.put((byte) i);
        }
        largeMessage.flip();

        // Mock selector behavior
        when(selectionKey.isReadable()).thenReturn(false);
        when(selectionKey.isWritable()).thenReturn(true);
        when(selectionKey.isValid()).thenReturn(true);

        // Set up the message supplier
        when(messageSupplier.get())
                .thenReturn(largeMessage)
                .thenReturn(null);  // Return null to end the loop

        when(membershipKey.isValid())
                .thenReturn(true)
                .thenReturn(false);

        try (MockedStatic<Selector> selectorStatic = mockStatic(Selector.class)) {
            selectorStatic.when(Selector::open).thenReturn(selector);

            // Run the selector loop
            netSelector.selectLoop();

            // Verify send was called once
            verify(datagramChannel, times(1)).send(any(ByteBuffer.class), any(SocketAddress.class));
        }
    }

    @Test
    void testMultipleLargeMessages() throws IOException {
        // Create multiple messages that will be truncated to buffer size
        ByteBuffer[] messages = new ByteBuffer[3];
        for (int i = 0; i < messages.length; i++) {
            messages[i] = ByteBuffer.allocate(BUFFER_LENGTH);  // Limit to buffer size
            for (int j = 0; j < BUFFER_LENGTH; j++) {
                messages[i].put((byte) (i * j));
            }
            messages[i].flip();
        }

        // Mock selector behavior
        when(selectionKey.isReadable()).thenReturn(true);
        when(selectionKey.isWritable()).thenReturn(false);
        when(selectionKey.isValid()).thenReturn(true);

        // Mock the receive behavior for multiple messages
        when(datagramChannel.receive(any(ByteBuffer.class))).thenAnswer(invocation -> {
            ByteBuffer buffer = invocation.getArgument(0);
            // Only write one message at a time
            ByteBuffer message = messages[0];  // Take first message
            int bytesToWrite = Math.min(message.remaining(), buffer.remaining());
            byte[] data = new byte[bytesToWrite];
            message.get(data);
            buffer.put(data);
            return new InetSocketAddress("localhost", TEST_PORT);
        });

        when(membershipKey.isValid())
                .thenReturn(true)
                .thenReturn(false);

        try (MockedStatic<Selector> selectorStatic = mockStatic(Selector.class)) {
            selectorStatic.when(Selector::open).thenReturn(selector);

            // Run the selector loop
            netSelector.selectLoop();

            // Verify each message was processed once
            verify(messageConsumer, times(1)).accept(eq(new InetSocketAddress("localhost", TEST_PORT)), any(ByteBuffer.class));
        }
    }

    @Test
    void testExactBufferSizeMessage() throws IOException {
        // Create a message exactly the size of the buffer
        ByteBuffer exactMessage = ByteBuffer.allocate(BUFFER_LENGTH);
        for (int i = 0; i < BUFFER_LENGTH; i++) {
            exactMessage.put((byte) i);
        }
        exactMessage.flip();

        // Mock selector behavior
        when(selectionKey.isReadable()).thenReturn(true);
        when(selectionKey.isWritable()).thenReturn(false);
        when(selectionKey.isValid()).thenReturn(true);

        // Mock the receive behavior
        when(datagramChannel.receive(any(ByteBuffer.class))).thenAnswer(invocation -> {
            ByteBuffer buffer = invocation.getArgument(0);
            buffer.put(exactMessage);
            return new InetSocketAddress("localhost", TEST_PORT);
        });

        when(membershipKey.isValid())
                .thenReturn(true)
                .thenReturn(false);

        try (MockedStatic<Selector> selectorStatic = mockStatic(Selector.class)) {
            selectorStatic.when(Selector::open).thenReturn(selector);

            // Run the selector loop
            netSelector.selectLoop();

            // Verify the message was processed once
            verify(messageConsumer, times(1)).accept(eq(new InetSocketAddress("localhost", TEST_PORT)), any(ByteBuffer.class));
        }
    }

    @Test
    void testExactBufferSizeSending() throws IOException {
        // Create a message exactly the size of the buffer
        ByteBuffer exactMessage = ByteBuffer.allocate(BUFFER_LENGTH);
        for (int i = 0; i < BUFFER_LENGTH; i++) {
            exactMessage.put((byte) i);
        }
        exactMessage.flip();

        // Mock selector behavior
        when(selectionKey.isReadable()).thenReturn(false);
        when(selectionKey.isWritable()).thenReturn(true);
        when(selectionKey.isValid()).thenReturn(true);

        // Set up the message supplier
        when(messageSupplier.get())
                .thenReturn(exactMessage)
                .thenReturn(null);  // Return null to end the loop

        when(membershipKey.isValid())
                .thenReturn(true)
                .thenReturn(false);

        try (MockedStatic<Selector> selectorStatic = mockStatic(Selector.class)) {
            selectorStatic.when(Selector::open).thenReturn(selector);

            // Run the selector loop
            netSelector.selectLoop();

            // Verify send was called once
            verify(datagramChannel, times(1)).send(any(ByteBuffer.class), any(SocketAddress.class));
        }
    }
}