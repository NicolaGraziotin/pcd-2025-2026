package ass04.util;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Minimal logger that prefixes every line with the node identifier.
 */
public final class Log {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private Log() {
    }

    public static void info(final String nodeId, final String message) {
        print(nodeId, message);
    }

    public static void warn(final String nodeId, final String message) {
        print(nodeId, "WARN  " + message);
    }

    public static void error(final String nodeId, final String message) {
        print(nodeId, "ERROR " + message);
    }

    private static void print(final String nodeId, final String message) {
        System.out.printf("%s [%s] %s%n",
                LocalTime.now().format(TIME_FORMAT),
                nodeId,
                message);
    }
}
