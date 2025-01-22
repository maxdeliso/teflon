package name.maxdeliso.teflon.data;

import java.nio.ByteBuffer;
import java.util.Optional;

/**
 * Interface for converting messages to and from byte buffers.
 * Used for network transmission of messages.
 */
public interface MessageMarshaller {

    /**
     * Converts a byte buffer to a message.
     *
     * @param bb The byte buffer to convert
     * @return The message if conversion was successful, empty otherwise
     */
    Optional<Message> bufferToMessage(ByteBuffer bb);

    /**
     * Converts a message to a byte buffer.
     *
     * @param message The message to convert
     * @return The byte buffer containing the serialized message
     */
    ByteBuffer messageToBuffer(Message message);
}
