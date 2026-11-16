package ass04;

import ass04.util.Log;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Example process that uses the middleware without any knowledge of the other
 * processes. The only parameters are its own identifier and the RabbitMQ host.
 *
 * <p>Usage: {@code java ass04.Main <node-id> [host] [rounds] [work-ms]}</p>
 */
public final class Main {

    private static final int DEFAULT_ROUNDS = 5;
    private static final long DEFAULT_WORK_MS = 400;

    private Main() {
    }

    public static void main(final String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println(
                    "Usage: java ass04.Main <node-id> [host] [rounds] [work-ms]");
            return;
        }

        final String nodeId = args[0];
        final String host = args.length > 1 ? args[1] : defaultHost();
        final int rounds = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_ROUNDS;
        final long workMs = args.length > 3 ? Long.parseLong(args[3]) : DEFAULT_WORK_MS;

        final Middleware middleware = new MiddlewareImpl(nodeId, host);
        Runtime.getRuntime().addShutdownHook(
                new Thread(middleware::close, "shutdown-" + nodeId));

        try {
            Log.info(nodeId, "starting " + rounds + " critical-section round(s)");
            for (int round = 1; round <= rounds; round++) {
                // Random think time makes the processes compete for the CS.
                Thread.sleep(ThreadLocalRandom.current().nextLong(300, 1_200));

                middleware.enterCS();
                try {
                    Log.info(nodeId, "inside CS - round " + round + "/" + rounds);
                    Thread.sleep(workMs);
                } finally {
                    middleware.exitCS();
                }
            }
            Log.info(nodeId, "done");
        } finally {
            middleware.close();
        }
    }

    private static String defaultHost() {
        final String host = System.getenv("RABBITMQ_HOST");
        return host == null || host.isBlank() ? "localhost" : host;
    }
}
