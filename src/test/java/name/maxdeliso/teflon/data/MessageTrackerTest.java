package name.maxdeliso.teflon.data;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the MessageTracker class.
 */
class MessageTrackerTest {

    private static final String TEST_INSTANCE_ID = "550e8400-e29b-41d4-a716-446655440000";
    private static final String TEST_RECEIVER_ID = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
    private static final Instant START_TIME = Instant.parse("2024-01-01T00:00:00Z");
    private MessageTracker tracker;
    private Message testMessage;
    private UUID testMessageId;
    private LogicalClock clock;

    @BeforeEach
    void setUp() {
        clock = new LogicalClock(START_TIME);
        tracker = new MessageTracker(TEST_INSTANCE_ID, clock);
        testMessageId = UUID.randomUUID();
        testMessage = new Message(
                TEST_INSTANCE_ID,
                "Test message",
                testMessageId,
                Message.MessageType.CHAT,
                123456L, // dummy checksum
                null
        );
    }

    @AfterEach
    void tearDown() {
        tracker.shutdown();
    }

    @Test
    void testTrackMessage() {
        tracker.trackMessage(testMessage);
        Map<String, Long> stats = tracker.getDeliveryStats();

        assertEquals(1L, stats.get("messagesSent"), "Should track one sent message");
        assertEquals(0L, stats.get("acksReceived"), "Should have no acks initially");
        assertEquals(0L, stats.get("nacksReceived"), "Should have no nacks initially");
        assertEquals(1L, stats.get("pendingMessages"), "Should have one pending message");
    }

    @Test
    void testProcessPositiveAcknowledgment() {
        // Create a message from a different sender
        Message otherMessage = new Message(
                TEST_RECEIVER_ID,
                "Message from other",
                testMessageId,
                Message.MessageType.CHAT,
                123456L,
                null
        );
        tracker.trackMessage(otherMessage);

        Message ack = new Message(
                TEST_INSTANCE_ID,
                "Message received",
                UUID.randomUUID(),
                Message.MessageType.ACK,
                123456L,
                testMessageId
        );
        tracker.processAcknowledgment(ack);

        Map<String, Long> stats = tracker.getDeliveryStats();
        assertEquals(1L, stats.get("acksReceived"), "Should record one ACK");
        assertEquals(0L, stats.get("nacksReceived"), "Should have no NACKs");

        Set<String> ackParties = tracker.getAcknowledgingParties(testMessageId);
        assertTrue(ackParties.contains(TEST_INSTANCE_ID), "Instance should be in acknowledging parties");
    }

    @Test
    void testSuppressSelfAcknowledgment() {
        // Track a message sent by this instance
        tracker.trackMessage(testMessage);

        // Try to acknowledge our own message
        Message selfAck = new Message(
                TEST_RECEIVER_ID,
                "Message received",
                UUID.randomUUID(),
                Message.MessageType.ACK,
                123456L,
                testMessageId
        );
        tracker.processAcknowledgment(selfAck);

        Map<String, Long> stats = tracker.getDeliveryStats();
        assertEquals(0L, stats.get("acksReceived"), "Should not record ACK for own message");
        assertEquals(0L, stats.get("nacksReceived"), "Should not record NACK for own message");

        Set<String> ackParties = tracker.getAcknowledgingParties(testMessageId);
        assertTrue(ackParties.isEmpty(), "Should have no acknowledging parties for self-sent message");
    }

    @Test
    void testProcessNegativeAcknowledgment() {
        // Create a message from a different sender
        Message otherMessage = new Message(
                TEST_RECEIVER_ID,
                "Message from other",
                testMessageId,
                Message.MessageType.CHAT,
                123456L,
                null
        );
        tracker.trackMessage(otherMessage);

        Message nack = new Message(
                TEST_INSTANCE_ID,
                "Message validation failed",
                UUID.randomUUID(),
                Message.MessageType.NACK,
                123456L,
                testMessageId
        );
        tracker.processAcknowledgment(nack);

        Map<String, Long> stats = tracker.getDeliveryStats();
        assertEquals(0L, stats.get("acksReceived"), "Should have no ACKs");
        assertEquals(1L, stats.get("nacksReceived"), "Should record one NACK");
    }

    @Test
    void testMultipleAcknowledgments() {
        // Create a message from a different sender
        Message otherMessage = new Message(
                TEST_RECEIVER_ID,
                "Message from other",
                testMessageId,
                Message.MessageType.CHAT,
                123456L,
                null
        );
        tracker.trackMessage(otherMessage);

        // Create acknowledgments from different receivers
        String receiver2Id = "7ba7b810-9dad-11d1-80b4-00c04fd430c8";
        Message ack1 = new Message(
                TEST_INSTANCE_ID,
                "Message received",
                UUID.randomUUID(),
                Message.MessageType.ACK,
                123456L,
                testMessageId
        );
        Message ack2 = new Message(
                receiver2Id,
                "Message received",
                UUID.randomUUID(),
                Message.MessageType.ACK,
                123456L,
                testMessageId
        );

        tracker.processAcknowledgment(ack1);
        tracker.processAcknowledgment(ack2);

        Set<String> ackParties = tracker.getAcknowledgingParties(testMessageId);
        assertEquals(2, ackParties.size(), "Should have two acknowledging parties");
        assertTrue(ackParties.contains(TEST_INSTANCE_ID), "First receiver should be in acknowledging parties");
        assertTrue(ackParties.contains(receiver2Id), "Second receiver should be in acknowledging parties");
    }

    @Test
    void testIgnoreNonChatMessages() {
        Message systemEvent = new Message(
                TEST_INSTANCE_ID,
                "System event",
                UUID.randomUUID(),
                Message.MessageType.SYSTEM_EVENT,
                123456L,
                null
        );
        tracker.trackMessage(systemEvent);

        Map<String, Long> stats = tracker.getDeliveryStats();
        assertEquals(0L, stats.get("messagesSent"), "Should not track system events");
    }

    @Test
    void testMessageTimeout() {
        // Create a message from a different sender
        Message otherMessage = new Message(
                TEST_RECEIVER_ID,
                "Message from other",
                testMessageId,
                Message.MessageType.CHAT,
                123456L,
                null
        );
        tracker.trackMessage(otherMessage);

        // Advance clock past timeout
        clock.advanceSeconds(6); // MESSAGE_TIMEOUT_SECONDS + 1

        // Force cleanup
        tracker.cleanupTimedOutMessages();

        Map<String, Long> stats = tracker.getDeliveryStats();
        assertEquals(1L, stats.get("messagesTimedOut"), "Message should be timed out");
        assertEquals(0L, stats.get("pendingMessages"), "Should have no pending messages");

        Set<String> ackParties = tracker.getAcknowledgingParties(testMessageId);
        assertTrue(ackParties.isEmpty(), "Should have no acknowledging parties after timeout");
    }

    @Test
    void testInvalidAcknowledgments() {
        // Create a message from a different sender
        Message otherMessage = new Message(
                TEST_RECEIVER_ID,
                "Message from other",
                testMessageId,
                Message.MessageType.CHAT,
                123456L,
                null
        );
        tracker.trackMessage(otherMessage);

        // Try to process acknowledgment for non-existent message
        Message invalidAck = new Message(
                TEST_INSTANCE_ID,
                "Message received",
                UUID.randomUUID(),
                Message.MessageType.ACK,
                123456L,
                UUID.randomUUID() // Different message ID
        );
        tracker.processAcknowledgment(invalidAck);

        Map<String, Long> stats = tracker.getDeliveryStats();
        assertEquals(0L, stats.get("acksReceived"), "Should not record ACK for invalid message");
    }
}