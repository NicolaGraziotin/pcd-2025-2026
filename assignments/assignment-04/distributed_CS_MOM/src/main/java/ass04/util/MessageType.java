package ass04.util;

/**
 * Kind of message exchanged by the middleware.
 */
public enum MessageType {

    /** Announces that a process has joined the group or is still alive. */
    HELLO,

    /** Announces that a process is leaving the group. */
    BYE,

    /** Requests permission to enter the critical section. */
    REQUEST,

    /** Grants permission for one specific request. */
    GRANT
}
