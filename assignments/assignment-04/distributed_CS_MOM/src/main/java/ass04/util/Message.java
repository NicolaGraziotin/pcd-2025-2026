package ass04.util;

/**
 * Message exchanged through RabbitMQ.
 *
 * @param type      the kind of message
 * @param sender    identifier of the process that sent the message
 * @param timestamp Lamport timestamp of the sender. For REQUEST and GRANT this is
 *                  the timestamp of the request being handled, so that a process
 *                  can discard GRANT messages belonging to an older request.
 */
public record Message(MessageType type, String sender, long timestamp) {
}
