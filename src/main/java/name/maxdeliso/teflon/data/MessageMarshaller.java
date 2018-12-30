package name.maxdeliso.teflon.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class MessageMarshaller {
    private static final Logger LOG = LoggerFactory.getLogger(MessageMarshaller.class);

    private static final Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;

    private final Gson gson;

    public MessageMarshaller(Gson gson) {
        this.gson = gson;
    }

    public Optional<Message> bufferToMessage(final byte[] buffer) {
        try (final ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer);
             final InputStreamReader isr = new InputStreamReader(inputStream, MESSAGE_CHARSET)) {
            return Optional.ofNullable(gson.fromJson(isr, Message.class));
        } catch (IOException | JsonSyntaxException exc) {
            LOG.warn("failed to deserialize buffer {}", buffer, exc);
            return Optional.empty();
        }
    }

    public ByteBuffer messageToBuffer(final Message message) {
        return ByteBuffer.wrap(gson.toJson(message).getBytes(MESSAGE_CHARSET));
    }
}
