package name.maxdeliso.teflon.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Implementation of MessageMarshaller that uses JSON for serialization.
 * Uses GSON for JSON conversion and UTF-8 for character encoding.
 */
public final class JsonMessageMarshaller implements MessageMarshaller {

    /**
     * Character set used for message encoding/decoding.
     */
    private static final Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;

    /**
     * GSON instance for JSON serialization/deserialization.
     */
    private final Gson gson;

    /**
     * Creates a new JSON message marshaller.
     *
     * @param gsonInstance The GSON instance to use for JSON conversion
     */
    public JsonMessageMarshaller(final Gson gsonInstance) {
        this.gson = gsonInstance;
    }

    @Override
    public Optional<Message> bufferToMessage(final ByteBuffer bb) {
        try {
            var buffer = MESSAGE_CHARSET.decode(bb).toString();
            return Optional.ofNullable(gson.fromJson(buffer, Message.class));
        } catch (JsonSyntaxException exc) {
            return Optional.empty();
        }
    }

    @Override
    public ByteBuffer messageToBuffer(final Message message) {
        return ByteBuffer.wrap(gson.toJson(message).getBytes(MESSAGE_CHARSET));
    }
}
