package name.maxdeliso.teflon.data;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class JsonMessageMarshaller implements MessageMarshaller {

  private static final Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;

  private final Gson gson;

  public JsonMessageMarshaller(Gson gson) {
    this.gson = gson;
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
