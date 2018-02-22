package name.maxdeliso.teflon.data;

import java.io.Serializable;

/**
 * A simple message class with a numeric sender id and a string body.
 */
public final class TeflonMessage implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int senderId;
    private final String body;

    public TeflonMessage(int senderId, String body) {
        this.senderId = senderId;
        this.body = body;
    }

    public int senderId() {
        return this.senderId;
    }

    public String body() {
        return this.body;
    }

    @Override
    public String toString() {
        return Integer.toHexString(senderId()) + " >> " + body();
    }
}
