package name.maxdeliso.teflon.net;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

/**
 * Handles sending multicast messages over a datagram channel.
 * Provides reliable message sending with logging of success and failures.
 */
public class MulticastSender {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = LogManager.getLogger(MulticastSender.class);

    /**
     * The datagram channel for sending messages.
     */
    private final DatagramChannel dc;

    /**
     * The target socket address for multicast messages.
     */
    private final InetSocketAddress isa;

    /**
     * Creates a new multicast sender.
     *
     * @param dc  The datagram channel to use
     * @param isa The target socket address
     */
    public MulticastSender(final DatagramChannel dc, final InetSocketAddress isa) {
        this.dc = dc;
        this.isa = isa;
    }

    /**
     * Sends a message via the datagram channel.
     * Logs success or failure of the send operation.
     *
     * @param bb The byte buffer containing the message to send
     */
    public void send(final ByteBuffer bb) {
        if (bb == null) {
            throw new NullPointerException("ByteBuffer cannot be null");
        }

        final var bufferLength = bb.remaining();
        if (bufferLength == 0) {
            LOG.debug("skipping send of empty buffer");
            return;
        }

        try {
            final var sentBytes = dc.send(bb, isa);

            if (bufferLength != sentBytes) {
                LOG.warn("only successfully sent {} of {} bytes", sentBytes, bufferLength);
            } else {
                LOG.debug("sent {} bytes", sentBytes);
            }
        } catch (IOException exc) {
            LOG.error("i/o exception while attempting to send", exc);
        }
    }
}
