package name.maxdeliso.teflon.net;

import java.nio.ByteBuffer;

/**
 * Interface for sourcing messages with peek and poll operations.
 * This allows for checking message availability without consuming them,
 * and consuming them only when they are successfully sent.
 */
public interface MessageSource {
    /**
     * Peeks at the next message without consuming it.
     *
     * @return The next message as a ByteBuffer, or null if no message is available
     */
    ByteBuffer peek();

    /**
     * Consumes and returns the next message.
     * This should be called only after a successful peek() and send.
     *
     * @return The next message as a ByteBuffer, or null if no message is available
     */
    ByteBuffer poll();
}
