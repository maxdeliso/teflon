package name.maxdeliso.teflon.data;

import com.google.gson.Gson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonMessageMarshallerTest {

    private static final String TEST_SENDER_ID = "test-sender-123";
    private static final String TEST_MESSAGE_BODY = "Hello, World!";
    private static final String INVALID_JSON = "{invalid json}";

    private JsonMessageMarshaller marshaller;
    private Message testMessage;

    @BeforeEach
    @SuppressWarnings("unused")
    void setUp() {
        marshaller = new JsonMessageMarshaller(new Gson());
        testMessage = new Message(TEST_SENDER_ID, TEST_MESSAGE_BODY);
    }

    @Test
    void testMessageToBuffer() {
        // Convert message to buffer
        ByteBuffer buffer = marshaller.messageToBuffer(testMessage);
        String json = StandardCharsets.UTF_8.decode(buffer).toString();

        // Verify JSON structure
        assertTrue(json.contains(TEST_SENDER_ID), "JSON should contain sender ID");
        assertTrue(json.contains(TEST_MESSAGE_BODY), "JSON should contain message body");
        assertTrue(json.startsWith("{"), "JSON should start with {");
        assertTrue(json.endsWith("}"), "JSON should end with }");
    }

    @Test
    void testBufferToMessage() {
        // Convert message to buffer and back
        ByteBuffer buffer = marshaller.messageToBuffer(testMessage);
        Optional<Message> result = marshaller.bufferToMessage(buffer);

        // Verify conversion
        assertTrue(result.isPresent(), "Should successfully parse valid JSON");
        assertEquals(TEST_SENDER_ID, result.get().senderId(), "Sender ID should match");
        assertEquals(TEST_MESSAGE_BODY, result.get().body(), "Message body should match");
    }

    @Test
    void testBufferToMessageWithInvalidJson() {
        // Create buffer with invalid JSON
        ByteBuffer buffer = ByteBuffer.wrap(INVALID_JSON.getBytes(StandardCharsets.UTF_8));
        Optional<Message> result = marshaller.bufferToMessage(buffer);

        // Verify error handling
        assertTrue(result.isEmpty(), "Should return empty Optional for invalid JSON");
    }

    @Test
    void testBufferToMessageWithEmptyBuffer() {
        // Create empty buffer
        ByteBuffer buffer = ByteBuffer.allocate(0);
        Optional<Message> result = marshaller.bufferToMessage(buffer);

        // Verify error handling
        assertTrue(result.isEmpty(), "Should return empty Optional for empty buffer");
    }

    @Test
    void testRoundTripConversion() {
        // Convert message to buffer
        ByteBuffer buffer = marshaller.messageToBuffer(testMessage);

        // Convert buffer back to message
        Optional<Message> result = marshaller.bufferToMessage(buffer);

        // Verify round-trip conversion
        assertTrue(result.isPresent(), "Should successfully parse valid JSON");
        assertEquals(testMessage, result.get(), "Round-trip conversion should preserve message");
    }
}