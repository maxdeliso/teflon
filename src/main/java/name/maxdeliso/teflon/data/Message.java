package name.maxdeliso.teflon.data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.UUID;

import static name.maxdeliso.teflon.config.Config.MESSAGE_SEPARATOR;

/**
 * A simple message class with a sender id and a string body.
 */
public final class Message implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(Message.class);

    private static final long serialVersionUID = 2L;

    private final UUID senderId;
    private final String body;

    public Message(UUID senderId, String body) {
        this.senderId = senderId;
        this.body = body;
    }

    public UUID senderId() {
        return this.senderId;
    }

    private String body() {
        return this.body;
    }

    @Override
    public String toString() {
        return String.join(MESSAGE_SEPARATOR, senderId().toString(), body());
    }

    public static Optional<Message> bufferToMessage(final ByteBuffer buffer) {
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer.array());
             final ObjectInput datagramInput = new ObjectInputStream(inputStream)) {
            return Optional.of((Message) datagramInput.readObject());
        } catch (final IOException | ClassNotFoundException exc) {
            LOG.warn("failed to deserialize buffer {}", buffer, exc);
            buffer.clear();
            return Optional.empty();
        }
    }

    public static Optional<ByteBuffer> messageToBuffer(final Message message) {
        try(final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream)) {
            objectOutputStream.writeObject(message);
            return Optional.of(ByteBuffer.wrap(byteArrayOutputStream.toByteArray()));
        } catch (final IOException exc) {
            LOG.error("failed to convert message {} to byte buffer", message, exc);
            return Optional.empty();
        }
    }
}
