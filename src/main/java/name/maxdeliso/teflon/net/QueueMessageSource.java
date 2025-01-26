package name.maxdeliso.teflon.net;

import name.maxdeliso.teflon.data.Message;
import name.maxdeliso.teflon.data.MessageMarshaller;

import java.nio.ByteBuffer;
import java.util.concurrent.TransferQueue;

/**
 * Implementation of MessageSource that wraps a TransferQueue of Messages.
 * Handles the conversion of Messages to ByteBuffers using a MessageMarshaller.
 */
public final class QueueMessageSource implements MessageSource {
    private final TransferQueue<Message> queue;
    private final MessageMarshaller marshaller;
    private volatile Message currentMessage;
    private volatile ByteBuffer currentBuffer;

    /**
     * Creates a new QueueMessageSource.
     *
     * @param queue      The queue to source messages from
     * @param marshaller The marshaller to convert messages to bytes
     */
    public QueueMessageSource(final TransferQueue<Message> queue, final MessageMarshaller marshaller) {
        this.queue = queue;
        this.marshaller = marshaller;
    }

    @Override
    public ByteBuffer peek() {
        if (currentBuffer != null && currentBuffer.hasRemaining()) {
            return currentBuffer;
        }

        if (currentMessage == null) {
            currentMessage = queue.peek();
            if (currentMessage == null) {
                return null;
            }
            currentBuffer = marshaller.messageToBuffer(currentMessage);
        }
        return currentBuffer;
    }

    @Override
    public ByteBuffer poll() {
        if (currentMessage != null) {
            queue.poll(); // Remove the message we peeked
            ByteBuffer buffer = currentBuffer;
            currentMessage = null;
            currentBuffer = null;
            return buffer;
        }
        return null;
    }
}
