package ass04;

import ass04.util.Log;
import ass04.util.Message;
import ass04.util.MessageCodec;
import ass04.util.MessageType;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Delivery;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * RabbitMQ implementation of a distributed critical section (Ricart-Agrawala).
 */
public final class MiddlewareImpl implements Middleware {

    private static final String DEFAULT_HOST = "localhost";

    private static final String EXCHANGE_NAME = "ass04.critical-section.exchange";
    private static final String BROADCAST_KEY = "ass04.critical-section.broadcast";
    private static final String NODE_KEY_PREFIX = "ass04.critical-section.node.";

    private static final long DISCOVERY_MS = 500;

    private static final Pattern NODE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final String id;

    /** Guards all protocol state and serializes sends (channels are not thread safe). */
    private final Object monitor = new Object();

    private final Set<String> peers = new HashSet<>();
    private final Set<String> pendingGrants = new HashSet<>();
    private final Map<String, Long> deferredRequests = new HashMap<>();

    private Connection connection;
    private Channel channel;

    private long clock;
    private long requestTimestamp;
    private boolean requesting;
    private boolean insideCriticalSection;
    private boolean closed;

    public MiddlewareImpl(final String id) throws IOException, TimeoutException {
        this(id, DEFAULT_HOST);
    }

    public MiddlewareImpl(final String id, final String host) throws IOException, TimeoutException {
        if (id == null || !NODE_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Invalid node id '" + id + "': use 1-64 letters, digits, '_' or '-'");
        }
        this.id = id;

        try {
            final ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);
            connection = factory.newConnection();
            channel = connection.createChannel();

            channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC, false, true, null);
            final String queue = channel.queueDeclare().getQueue();
            channel.queueBind(queue, EXCHANGE_NAME, BROADCAST_KEY);
            channel.queueBind(queue, EXCHANGE_NAME, nodeKey(id));
            channel.basicConsume(queue, true, this::onMessage, consumerTag -> { });

            synchronized (monitor) {
                peers.add(id);
                send(BROADCAST_KEY, MessageType.HELLO, clock);
                // Give the HELLOs of concurrent starters time to arrive.
                final long deadline = System.currentTimeMillis() + DISCOVERY_MS;
                for (long remaining; (remaining = deadline - System.currentTimeMillis()) > 0; ) {
                    monitor.wait(remaining);
                }
            }
            Log.info(id, "joined the group");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            shutdownResources();
            throw new IOException("Interrupted while discovering peers", e);
        } catch (IOException | TimeoutException | RuntimeException e) {
            shutdownResources();
            throw e;
        }
    }

    private void onMessage(final String consumerTag, final Delivery delivery) {
        final Message message;
        try {
            message = MessageCodec.decode(delivery.getBody());
        } catch (RuntimeException e) {
            Log.warn(id, "discarding malformed message: " + e.getMessage());
            return;
        }

        if (id.equals(message.sender())) {
            return;
        }

        synchronized (monitor) {
            if (closed) {
                return;
            }
            clock = Math.max(clock, message.timestamp());

            switch (message.type()) {
                case HELLO -> onHello(message);
                case BYE -> onBye(message);
                case REQUEST -> onRequest(message);
                case GRANT -> onGrant(message);
            }
        }
    }

    private void onHello(final Message message) {
        if (!peers.add(message.sender())) {
            return;
        }
        Log.info(id, "discovered node " + message.sender());

        // Answer the newcomer so that it discovers us too.
        sendQuietly(nodeKey(message.sender()), MessageType.HELLO, clock);

        // A newcomer must grant our in-progress request before we can enter.
        if (requesting && !insideCriticalSection) {
            pendingGrants.add(message.sender());
            sendQuietly(nodeKey(message.sender()), MessageType.REQUEST, requestTimestamp);
        }
    }

    private void onBye(final Message message) {
        if (!peers.remove(message.sender())) {
            return;
        }
        pendingGrants.remove(message.sender());
        deferredRequests.remove(message.sender());
        monitor.notifyAll();
        Log.info(id, "node " + message.sender() + " left the group");
    }

    private void onRequest(final Message message) {
        // Learn the sender if its HELLO has not arrived yet.
        if (peers.add(message.sender())) {
            Log.info(id, "discovered node " + message.sender() + " (via REQUEST)");
            sendQuietly(nodeKey(message.sender()), MessageType.HELLO, clock);
        }

        final boolean defer = requesting
                && (insideCriticalSection
                    || requestTimestamp < message.timestamp()
                    || (requestTimestamp == message.timestamp()
                        && id.compareTo(message.sender()) < 0));

        if (defer) {
            deferredRequests.put(message.sender(), message.timestamp());
            Log.info(id, "deferred request from " + message.sender()
                    + " (t=" + message.timestamp() + ")");
        } else {
            sendQuietly(nodeKey(message.sender()), MessageType.GRANT, message.timestamp());
        }
    }

    private void onGrant(final Message message) {
        if (!requesting || insideCriticalSection || message.timestamp() != requestTimestamp) {
            return;
        }
        if (pendingGrants.remove(message.sender()) && pendingGrants.isEmpty()) {
            monitor.notifyAll();
        }
    }

    @Override
    public void enterCS() throws InterruptedException, IOException {
        synchronized (monitor) {
            if (closed) {
                throw new IOException("Middleware for node " + id + " is closed");
            }
            if (requesting) {
                throw new IllegalStateException(
                        "Node " + id + " already owns or is requesting the critical section");
            }

            requesting = true;
            insideCriticalSection = false;
            requestTimestamp = ++clock;
            pendingGrants.clear();
            pendingGrants.addAll(peers);
            pendingGrants.remove(id);
            deferredRequests.clear();

            Log.info(id, "requesting CS at t=" + requestTimestamp
                    + " (waiting for " + pendingGrants.size() + " GRANT(s))");

            try {
                send(BROADCAST_KEY, MessageType.REQUEST, requestTimestamp);
                while (!closed && !pendingGrants.isEmpty()) {
                    monitor.wait();
                }
            } catch (InterruptedException | IOException | RuntimeException e) {
                resetRequest();
                throw e;
            }

            if (closed) {
                throw new IOException("Middleware for node " + id + " is closed");
            }
            insideCriticalSection = true;
            Log.info(id, "entered CS");
        }
    }

    @Override
    public void exitCS() throws InterruptedException, IOException {
        synchronized (monitor) {
            if (!insideCriticalSection) {
                throw new IllegalStateException(
                        "Node " + id + " is not inside the critical section");
            }
            resetRequest();
            Log.info(id, "exited CS");
        }
    }

    @Override
    public void close() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            closed = true;
            requesting = false;
            insideCriticalSection = false;
            pendingGrants.clear();
            sendQuietly(BROADCAST_KEY, MessageType.BYE, clock);
            monitor.notifyAll();
        }
        Log.info(id, "left the group");
        shutdownResources();
    }

    private void resetRequest() {
        requesting = false;
        insideCriticalSection = false;
        pendingGrants.clear();
        for (Map.Entry<String, Long> request : deferredRequests.entrySet()) {
            sendQuietly(nodeKey(request.getKey()), MessageType.GRANT, request.getValue());
        }
        deferredRequests.clear();
        monitor.notifyAll();
    }

    /** Only called while holding {@link #monitor}. */
    private void send(final String routingKey, final MessageType type, final long timestamp)
            throws IOException {
        final byte[] body = MessageCodec.encode(new Message(type, id, timestamp));
        channel.basicPublish(EXCHANGE_NAME, routingKey, null, body);
    }

    private void sendQuietly(final String routingKey, final MessageType type,
                             final long timestamp) {
        try {
            send(routingKey, type, timestamp);
        } catch (IOException | RuntimeException e) {
            Log.warn(id, "could not send " + type + ": " + e.getMessage());
        }
    }

    private static String nodeKey(final String nodeId) {
        return NODE_KEY_PREFIX + nodeId;
    }

    private void shutdownResources() {
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (Exception ignored) {
            }
        }
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (Exception ignored) {
            }
        }
    }
}
