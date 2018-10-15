package name.maxdeliso.teflon.data;

import java.io.Serializable;

import static name.maxdeliso.teflon.config.Config.MESSAGE_SEPARATOR;

/**
 * A simple message class with a numeric sender id and a string body.
 */
public final class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    private final int senderId;
    private final String body;

    public Message(int senderId, String body) {
        this.senderId = senderId;
        this.body = body;
    }

    public int senderId() {
        return this.senderId;
    }

    private String body() {
        return this.body;
    }

    @Override
    public String toString() {
        return String.join(MESSAGE_SEPARATOR, Integer.toHexString(senderId()), body());
    }
}
