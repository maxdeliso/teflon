package name.maxdeliso.teflon.data;

import java.nio.ByteBuffer;
import java.util.Optional;

public interface MessageMarshaller {
    Optional<Message> bufferToMessage(final ByteBuffer bb);

    ByteBuffer messageToBuffer(final Message message);
}
