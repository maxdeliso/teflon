package name.maxdeliso.teflon.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks message delivery status and acknowledgments.
 * Handles message timeouts and maintains delivery statistics.
 */
public class MessageTracker {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(MessageTracker.class);

    /**
     * Default timeout for message acknowledgment in seconds.
     */
    private static final int MESSAGE_TIMEOUT_SECONDS = 5;

    /**
     * Map of message IDs to their tracking information.
     */
    private final Map<UUID, MessageInfo> messageMap = new ConcurrentHashMap<>();

    /**
     * Executor for cleanup tasks.
     */
    private final ScheduledExecutorService cleanupExecutor;

    /**
     * The ID of this instance.
     */
    private final String instanceId;

    /**
     * The clock used for timing.
     */
    private final Clock clock;

    /**
     * Total messages sent.
     */
    private long totalMessagesSent = 0;

    /**
     * Total acknowledgments received.
     */
    private long totalAcksReceived = 0;

    /**
     * Total negative acknowledgments received.
     */
    private long totalNacksReceived = 0;

    /**
     * Total messages timed out.
     */
    private long totalMessagesTimedOut = 0;

    /**
     * Creates a new message tracker.
     */
    public MessageTracker() {
        this(UUID.randomUUID().toString());
    }

    /**
     * Creates a new message tracker with a specific instance ID.
     *
     * @param instanceId The ID to use for this instance
     */
    public MessageTracker(String instanceId) {
        this(instanceId, new RealClock());
    }

    /**
     * Creates a new message tracker with a specific instance ID and clock.
     *
     * @param instanceId The ID to use for this instance
     * @param clock      The clock to use for timing
     */
    public MessageTracker(String instanceId, Clock clock) {
        this.instanceId = instanceId;
        this.clock = clock;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MessageTracker-Cleanup");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic cleanup of timed-out messages, starting immediately
        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupTimedOutMessages,
                0, // Start immediately
                MESSAGE_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * Tracks a new outgoing message.
     *
     * @param message The message to track
     */
    public void trackMessage(Message message) {
        if (message.type() != Message.MessageType.CHAT) {
            return; // Only track chat messages
        }

        messageMap.put(message.messageId(), new MessageInfo(
                message,
                clock.now(),
                new ConcurrentHashMap<>()
        ));
        totalMessagesSent++;
        LOG.debug("Tracking new message: {}", message.messageId());
    }

    /**
     * Processes an acknowledgment message.
     *
     * @param ack The acknowledgment message
     */
    public void processAcknowledgment(Message ack) {
        if (!ack.isAcknowledgment() || ack.originalMessageId() == null) {
            return;
        }

        MessageInfo info = messageMap.get(ack.originalMessageId());
        if (info == null) {
            LOG.debug("Ignoring acknowledgment for unknown or timed out message: {}", ack.originalMessageId());
            return; // Message not found or already timed out
        }

        // Don't process acknowledgments for messages we sent ourselves
        if (info.message.senderId().equals(instanceId)) {
            return;
        }

        info.acknowledgments.put(ack.senderId(), ack);
        if (ack.type() == Message.MessageType.ACK) {
            totalAcksReceived++;
            LOG.debug("Received ACK for message: {} from: {}",
                    ack.originalMessageId(), ack.senderId());
        } else {
            totalNacksReceived++;
            LOG.debug("Received NACK for message: {} from: {}",
                    ack.originalMessageId(), ack.senderId());
        }
    }

    /**
     * Gets the acknowledgment status for a message.
     *
     * @param messageId The ID of the message to check
     * @return The set of sender IDs that have acknowledged the message
     */
    public Set<String> getAcknowledgingParties(UUID messageId) {
        MessageInfo info = messageMap.get(messageId);
        return info != null ? info.acknowledgments.keySet() : Set.of();
    }

    /**
     * Gets delivery statistics.
     *
     * @return A map of statistic names to their values
     */
    public Map<String, Long> getDeliveryStats() {
        return Map.of(
                "messagesSent", totalMessagesSent,
                "acksReceived", totalAcksReceived,
                "nacksReceived", totalNacksReceived,
                "messagesTimedOut", totalMessagesTimedOut,
                "pendingMessages", (long) messageMap.size()
        );
    }

    /**
     * Gets the ID of this tracker instance.
     *
     * @return The instance ID
     */
    public String getInstanceId() {
        return instanceId;
    }

    /**
     * Cleans up messages that have timed out.
     */
    void cleanupTimedOutMessages() {
        Instant cutoff = clock.now().minusSeconds(MESSAGE_TIMEOUT_SECONDS);

        messageMap.entrySet().removeIf(entry -> {
            if (entry.getValue().timestamp.isBefore(cutoff)) {
                totalMessagesTimedOut++;
                LOG.debug("Message timed out: {}", entry.getKey());
                return true;
            }
            return false;
        });
    }

    /**
     * Shuts down the tracker's cleanup executor.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Record class to hold message tracking information.
     */
    private record MessageInfo(
            Message message,
            Instant timestamp,
            Map<String, Message> acknowledgments
    ) {
    }
}
