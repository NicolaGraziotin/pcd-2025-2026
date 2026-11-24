package ass04.util;

import java.nio.charset.StandardCharsets;

/**
 * Small text codec for {@link Message}.
 */
public final class MessageCodec {

    private static final String SEPARATOR = "|";
    private static final int FIELD_COUNT = 3;

    private MessageCodec() {
    }

    public static byte[] encode(final Message message) {
        final String payload = message.type().name()
                + SEPARATOR + message.sender()
                + SEPARATOR + message.timestamp();
        return payload.getBytes(StandardCharsets.UTF_8);
    }

    public static Message decode(final byte[] body) {
        final String payload = new String(body, StandardCharsets.UTF_8);
        final String[] fields = payload.split("\\|", -1);
        if (fields.length != FIELD_COUNT) {
            throw new IllegalArgumentException("Malformed message: " + payload);
        }
        try {
            return new Message(
                    MessageType.valueOf(fields[0]),
                    fields[1],
                    Long.parseLong(fields[2])
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Malformed message: " + payload, e);
        }
    }
}
