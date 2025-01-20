package name.maxdeliso.teflon.data;

import java.util.UUID;

import static org.apache.commons.text.StringEscapeUtils.escapeHtml4;

public record Message(String senderId, String body) {
    public boolean isValidSenderId() {
        try {
            UUID.fromString(senderId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String generateColor() {
        if (!isValidSenderId()) {
            throw new IllegalArgumentException("Invalid UUID format for senderId: " + senderId);
        }
        int colorInt = senderId.hashCode() & 0xFFFFFF;
        return String.format("#%06X", colorInt);
    }

    public String htmlSafeBody() {
        return escapeHtml4(body);
    }
}
