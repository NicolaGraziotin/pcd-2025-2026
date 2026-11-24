package ass04.util;

/**
 * Message exchanged through RabbitMQ.
 */
public record Message(MessageType type, String sender, long timestamp) {
}
