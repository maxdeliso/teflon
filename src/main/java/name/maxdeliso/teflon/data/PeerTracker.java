package name.maxdeliso.teflon.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.SocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tracks known peers in the network.
 * Maintains a list of peers with their UUIDs and IP addresses.
 */
public class PeerTracker {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(PeerTracker.class);

    /**
     * Default timeout for peer activity in seconds.
     */
    private static final int PEER_TIMEOUT_SECONDS = 30;

    /**
     * Map of peer UUIDs to their information.
     */
    private final Map<String, PeerInfo> peers = new ConcurrentHashMap<>();

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
     * Creates a new peer tracker.
     */
    public PeerTracker() {
        this(UUID.randomUUID().toString());
    }

    /**
     * Creates a new peer tracker with a specific instance ID.
     *
     * @param instanceId The ID to use for this instance
     */
    public PeerTracker(String instanceId) {
        this(instanceId, new RealClock());
    }

    /**
     * Creates a new peer tracker with a specific instance ID and clock.
     *
     * @param instanceId The ID to use for this instance
     * @param clock      The clock to use for timing
     */
    public PeerTracker(String instanceId, Clock clock) {
        this.instanceId = instanceId;
        this.clock = clock;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PeerTracker-Cleanup");
            t.setDaemon(true);
            return t;
        });

        // Schedule periodic cleanup of inactive peers, starting immediately
        cleanupExecutor.scheduleAtFixedRate(
                this::cleanupInactivePeers,
                0, // Start immediately
                PEER_TIMEOUT_SECONDS,
                TimeUnit.SECONDS
        );
    }

    /**
     * Updates peer information when a message is received.
     *
     * @param senderId     The sender's UUID
     * @param senderAddress The sender's network address
     */
    public void updatePeer(String senderId, SocketAddress senderAddress) {
        if (senderId.equals(instanceId)) {
            return; // Don't track ourselves
        }

        String ipAddress = extractIpAddress(senderAddress);
        if (ipAddress == null) {
            LOG.debug("Could not extract IP address from: {}", senderAddress);
            return;
        }

        peers.put(senderId, new PeerInfo(senderId, ipAddress, clock.now()));
        LOG.debug("Updated peer: {} at {}", senderId, ipAddress);
    }

    /**
     * Gets all known peers.
     *
     * @return A map of peer UUIDs to their information
     */
    public Map<String, PeerInfo> getPeers() {
        return Map.copyOf(peers);
    }

    /**
     * Gets the number of known peers.
     *
     * @return The number of peers
     */
    public int getPeerCount() {
        return peers.size();
    }

    /**
     * Cleans up peers that haven't been seen recently.
     */
    public void cleanupInactivePeers() {
        Instant cutoff = clock.now().minusSeconds(PEER_TIMEOUT_SECONDS);
        peers.entrySet().removeIf(entry -> {
            boolean shouldRemove = entry.getValue().lastSeen().isBefore(cutoff);
            if (shouldRemove) {
                LOG.debug("Removing inactive peer: {}", entry.getKey());
            }
            return shouldRemove;
        });
    }

    /**
     * Extracts IP address from a socket address.
     *
     * @param address The socket address
     * @return The IP address string, or null if extraction fails
     */
    private String extractIpAddress(SocketAddress address) {
        if (address instanceof java.net.InetSocketAddress inetAddress) {
            return inetAddress.getAddress().getHostAddress();
        }
        return null;
    }

    /**
     * Resets the peer tracker by clearing all peers.
     */
    public void reset() {
        peers.clear();
        LOG.debug("Peer tracker reset - cleared all peers");
    }

    /**
     * Shuts down the peer tracker.
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
     * Information about a peer.
     */
    public record PeerInfo(
            String uuid,
            String ipAddress,
            Instant lastSeen
    ) {}
}
