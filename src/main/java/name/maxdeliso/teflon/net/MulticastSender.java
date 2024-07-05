package name.maxdeliso.teflon.net;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public class MulticastSender {
    private static final Logger LOG = LogManager.getLogger(MulticastSender.class);
    private final DatagramChannel dc;
    private final InetSocketAddress isa;

    public MulticastSender(DatagramChannel dc, InetSocketAddress isa) {
        this.dc = dc;
        this.isa = isa;
    }

    public void send(ByteBuffer bb) {
        try {
            final var bufferLength = bb.array().length;
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
