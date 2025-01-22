package name.maxdeliso.teflon.data;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.zip.CRC32;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

/**
 * Represents a chat message in the system.
 * Contains the sender's ID, message body, and acknowledgment metadata.
 */
public record Message(
        String senderId,
        String body,
        UUID messageId,
        MessageType type,
        long checksum,
        UUID originalMessageId) {

    /**
     * Maximum RGB color value.
     */
    private static final int MAX_RGB_COLOR = 0xFFFFFF;

    /**
     * Creates a new chat message.
     *
     * @param senderId The unique identifier of the message sender
     * @param body     The content of the message
     */
    public Message(String senderId, String body) {
        this(senderId, body, UUID.randomUUID(), MessageType.CHAT, calculateChecksum(body), null);
    }

    /**
     * Creates an acknowledgment message.
     *
     * @param senderId          The unique identifier of the acknowledging party
     * @param originalMessageId The ID of the message being acknowledged
     * @param isPositive        Whether this is a positive (ACK) or negative (NACK) acknowledgment
     * @return A new acknowledgment message
     */
    public static Message createAcknowledgment(String senderId, UUID originalMessageId, boolean isPositive) {
        MessageType type = isPositive ? MessageType.ACK : MessageType.NACK;
        String body = isPositive ? "Message received" : "Message validation failed";
        return new name.maxdeliso.teflon.data.Message(
                senderId,
                body,
                java.util.UUID.randomUUID(),
                type,
                calculateChecksum(body),
                originalMessageId
        );
    }

    /**
     * Creates a system event message.
     *
     * @param senderId     The unique identifier of the sender
     * @param eventDetails The system event details
     * @return A new system event message
     */
    public static Message createSystemEvent(String senderId, String eventDetails) {
        return new Message(
                senderId,
                eventDetails,
                UUID.randomUUID(),
                MessageType.SYSTEM_EVENT,
                calculateChecksum(eventDetails),
                null
        );
    }

    /**
     * Calculates the checksum for a message body.
     *
     * @param content The content to calculate checksum for
     * @return The CRC32 checksum
     */
    private static long calculateChecksum(String content) {
        CRC32 crc32 = new CRC32();
        crc32.update(content.getBytes(StandardCharsets.UTF_8));
        return crc32.getValue();
    }

    /**
     * Checks if the sender ID is a valid UUID.
     *
     * @return true if the sender ID is a valid UUID, false otherwise
     */
    public boolean isValidSenderId() {
        try {
            UUID.fromString(senderId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Verifies the message checksum.
     *
     * @return true if the checksum is valid, false otherwise
     */
    public boolean isValid() {
        return calculateChecksum(body) == checksum;
    }

    /**
     * Generates a consistent color based on the sender's ID.
     *
     * @return A hex color code in the format "#RRGGBB"
     * @throws IllegalArgumentException if the sender ID is not a valid UUID
     */
    public String generateColor() {
        if (!isValidSenderId()) {
            throw new IllegalArgumentException("Invalid UUID format for senderId: " + senderId);
        }
        return String.format("#%06X", Math.abs(senderId.hashCode()) % MAX_RGB_COLOR);
    }

    /**
     * Returns an HTML-safe version of the message body.
     *
     * @return The message body with HTML special characters escaped
     */
    public String htmlSafeBody() {
        return escapeHtml4(body);
    }

    /**
     * Checks if this message is an acknowledgment.
     *
     * @return true if the message is an ACK or NACK
     */
    public boolean isAcknowledgment() {
        return type == MessageType.ACK || type == MessageType.NACK;
    }

    /**
     * Checks if this message is a system event.
     *
     * @return true if the message is a system event
     */
    public boolean isSystemEvent() {
        return type == MessageType.SYSTEM_EVENT;
    }

    /**
     * Message types supported by the system.
     */
    public enum MessageType {
        CHAT,       // Regular chat message
        ACK,        // Positive acknowledgment
        NACK,       // Negative acknowledgment
        SYSTEM_EVENT // System events (connect/disconnect/etc)
    }
}
