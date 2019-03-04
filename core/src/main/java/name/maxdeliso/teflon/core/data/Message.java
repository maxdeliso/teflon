package name.maxdeliso.teflon.core.data;

/**
 * A simple message class with a sender id and a string body.
 */
public final class Message {
    private static final String MESSAGE_SEPARATOR = " >> ";

    private final String senderId;
    private final String body;

    public Message(String senderId, String body) {
        this.senderId = senderId;
        this.body = body;
    }

    public String senderId() {
        return this.senderId;
    }

    private String body() {
        return this.body;
    }

    @Override
    public String toString() {
        return String.join(MESSAGE_SEPARATOR, senderId(), body());
    }
}
