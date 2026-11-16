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
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;

/**
 * RabbitMQ implementation of a distributed critical section.
 *
 * <p>The mutual exclusion algorithm is Ricart-Agrawala. Every process keeps a
 * Lamport clock and, before entering the critical section, broadcasts a REQUEST
 * carrying its timestamp. A process that is not interested in the critical
 * section immediately answers with a GRANT. If it is itself waiting, it compares
 * the two timestamps (ties are broken by node identifier) and either grants the
 * permission or defers it until it leaves the critical section.</p>
 *
 * <p>RabbitMQ is used as a message-oriented middleware: all messages go through
 * a topic exchange. Broadcast messages use a common routing key, while unicast
 * messages use a routing key derived from the destination node. Each node has
 * its own temporary queue, so processes never need to know the queues, addresses
 * or ports of the other participants.</p>
 *
 * <p>Group membership is discovered automatically with HELLO messages. HELLO is
 * also sent periodically as a heartbeat, and nodes that remain silent for too
 * long are removed from the group. This gives best-effort failure detection
 * under a fail-stop assumption (a crashed process stops sending messages).</p>
 */
public final class MiddlewareImpl implements Middleware {

    private static final String DEFAULT_HOST = "localhost";

    private static final String EXCHANGE_NAME = "ass04.critical-section.exchange";
    private static final String BROADCAST_KEY = "ass04.critical-section.broadcast";
    private static final String NODE_KEY_PREFIX = "ass04.critical-section.node.";

    private static final long HEARTBEAT_INTERVAL_MS = 2_000;
    private static final long PEER_TIMEOUT_MS = 6_000;
    private static final long FAILURE_CHECK_INTERVAL_MS = 1_000;
    private static final long DISCOVERY_QUIET_PERIOD_MS = 1_000;
    private static final long WAIT_SLICE_MS = 200;

    private static final Pattern NODE_ID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private final String id;

    /** Guards all mutable protocol state. */
    private final Object monitor = new Object();

    /** RabbitMQ channels are not thread safe, so publications are serialized. */
    private final Object publishLock = new Object();

    /** Known nodes and the local time at which they were last heard from. */
    private final Map<String, Long> peers = new HashMap<>();

    /** Nodes from which we are still waiting for a GRANT for the current request. */
    private final Set<String> pendingGrants = new HashSet<>();

    /** Requests deferred during our critical section: node id -> its timestamp. */
    private final Map<String, Long> deferredRequests = new HashMap<>();

    private Connection connection;
    private Channel channel;
    private ScheduledExecutorService scheduler;

    private long lamportClock;
    private long lastViewChange;
    private long requestTimestamp;
    private boolean requesting;
    private boolean insideCriticalSection;
    private volatile boolean closed;

    public MiddlewareImpl(final String id) throws IOException, TimeoutException {
        this(id, DEFAULT_HOST);
    }

    public MiddlewareImpl(final String id, final String host) throws IOException, TimeoutException {
        if (id == null || !NODE_ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException(
                    "Invalid node id '" + id + "': use 1-64 letters, digits, '_' or '-'");
        }
        this.id = id;
        this.lastViewChange = System.currentTimeMillis();

        try {
            final ConnectionFactory factory = new ConnectionFactory();
            factory.setHost(host);

            this.connection = factory.newConnection();
            this.channel = connection.createChannel();

            declareTopology();

            this.scheduler = Executors.newScheduledThreadPool(2, runnable -> {
                final Thread thread = new Thread(runnable, "ass04-cs-" + this.id);
                thread.setDaemon(true);
                return thread;
            });
            scheduler.scheduleAtFixedRate(
                    this::sendHeartbeat,
                    HEARTBEAT_INTERVAL_MS,
                    HEARTBEAT_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);
            scheduler.scheduleAtFixedRate(
                    this::detectFailedPeers,
                    FAILURE_CHECK_INTERVAL_MS,
                    FAILURE_CHECK_INTERVAL_MS,
                    TimeUnit.MILLISECONDS);

            synchronized (monitor) {
                peers.put(id, System.currentTimeMillis());
            }

            Log.info(id, "joined the group");
            broadcast(MessageType.HELLO, lamportClock);
        } catch (IOException | TimeoutException | RuntimeException e) {
            shutdownResources();
            throw e;
        }
    }

    private void declareTopology() throws IOException {
        channel.exchangeDeclare(EXCHANGE_NAME, BuiltinExchangeType.TOPIC, false, true, null);

        // Server-named, exclusive and auto-delete queue: it disappears together
        // with this middleware instance.
        final String queueName = channel.queueDeclare().getQueue();
        channel.queueBind(queueName, EXCHANGE_NAME, BROADCAST_KEY);
        channel.queueBind(queueName, EXCHANGE_NAME, NODE_KEY_PREFIX + id);

        channel.basicConsume(queueName, true, this::onMessage, consumerTag -> { });
    }

    private void onMessage(final String consumerTag, final Delivery delivery) {
        final Message message;
        try {
            message = MessageCodec.decode(delivery.getBody());
        } catch (RuntimeException e) {
            Log.warn(id, "discarding malformed message: " + e.getMessage());
            return;
        }

        // Every node receives its own broadcasts too.
        if (id.equals(message.sender())) {
            return;
        }

        try {
            synchronized (monitor) {
                if (closed) {
                    return;
                }
                lamportClock = Math.max(lamportClock, message.timestamp());

                switch (message.type()) {
                    case HELLO -> onHello(message);
                    case BYE -> onBye(message);
                    case REQUEST -> onRequest(message);
                    case GRANT -> onGrant(message);
                }
            }
        } catch (RuntimeException e) {
            Log.error(id, "error while handling " + message.type()
                    + " from " + message.sender() + ": " + e.getMessage());
        }
    }

    private void onHello(final Message message) {
        final long now = System.currentTimeMillis();
        final boolean discovered = learn(message.sender(), now);
        if (!discovered) {
            return;
        }

        Log.info(id, "discovered node " + message.sender());

        // Answer the newcomer so that it can discover us as well.
        sendQuietly(unicastKey(message.sender()), MessageType.HELLO, lamportClock);

        // If we are already collecting permissions, the newcomer must grant us
        // too before we can enter the critical section.
        if (requesting && !insideCriticalSection) {
            pendingGrants.add(message.sender());
            sendQuietly(unicastKey(message.sender()), MessageType.REQUEST, requestTimestamp);
            Log.info(id, "added new node " + message.sender() + " to the current request");
        }
    }

    private void onBye(final Message message) {
        final boolean removed = peers.remove(message.sender()) != null;
        pendingGrants.remove(message.sender());
        deferredRequests.remove(message.sender());

        if (removed) {
            lastViewChange = System.currentTimeMillis();
            Log.info(id, "node " + message.sender() + " left the group");
            monitor.notifyAll();
        }
    }

    private void onRequest(final Message message) {
        final long now = System.currentTimeMillis();
        final boolean discovered = learn(message.sender(), now);
        if (discovered) {
            Log.info(id, "discovered node " + message.sender() + " (via REQUEST)");
            sendQuietly(unicastKey(message.sender()), MessageType.HELLO, lamportClock);
        }

        // While we are inside the critical section, every later request must
        // wait, even if its timestamp is smaller (we simply received it late).
        final boolean mustWait = requesting
                && (insideCriticalSection
                    || requestTimestamp < message.timestamp()
                    || (requestTimestamp == message.timestamp()
                        && id.compareTo(message.sender()) < 0));

        if (mustWait) {
            deferredRequests.put(message.sender(), message.timestamp());
            Log.info(id, "deferred request from " + message.sender()
                    + " (t=" + message.timestamp() + ")");
        } else {
            sendQuietly(unicastKey(message.sender()), MessageType.GRANT, message.timestamp());
        }
    }

    private void onGrant(final Message message) {
        if (!peers.containsKey(message.sender())) {
            // A late GRANT from a node that has already been removed or that we
            // no longer know: it cannot be part of the current request.
            return;
        }
        peers.put(message.sender(), System.currentTimeMillis());

        if (!requesting || insideCriticalSection || message.timestamp() != requestTimestamp) {
            return;
        }

        if (pendingGrants.remove(message.sender())) {
            Log.info(id, "received GRANT from " + message.sender()
                    + " (" + pendingGrants.size() + " still pending)");
            if (pendingGrants.isEmpty()) {
                monitor.notifyAll();
            }
        }
    }

    /**
     * Adds a node to the local view, or refreshes its heartbeat. Must be called
     * while holding {@link #monitor}.
     *
     * @return {@code true} if the node was not known before
     */
    private boolean learn(final String nodeId, final long now) {
        final Long previous = peers.put(nodeId, now);
        if (previous == null) {
            lastViewChange = now;
            return true;
        }
        return false;
    }

    private void sendHeartbeat() {
        synchronized (monitor) {
            if (closed) {
                return;
            }
            sendQuietly(BROADCAST_KEY, MessageType.HELLO, lamportClock);
        }
    }

    private void detectFailedPeers() {
        synchronized (monitor) {
            if (closed) {
                return;
            }

            final long cutoff = System.currentTimeMillis() - PEER_TIMEOUT_MS;
            boolean changed = false;
            final Iterator<Map.Entry<String, Long>> iterator = peers.entrySet().iterator();
            while (iterator.hasNext()) {
                final Map.Entry<String, Long> entry = iterator.next();
                final String nodeId = entry.getKey();
                if (!nodeId.equals(id) && entry.getValue() < cutoff) {
                    iterator.remove();
                    pendingGrants.remove(nodeId);
                    deferredRequests.remove(nodeId);
                    changed = true;
                    Log.warn(id, "node " + nodeId + " timed out");
                }
            }

            if (changed) {
                lastViewChange = System.currentTimeMillis();
                monitor.notifyAll();
            }
        }
    }

    @Override
    public void enterCS() throws InterruptedException, IOException {
        synchronized (monitor) {
            ensureOpen();
            awaitStableView();
            ensureOpen();

            if (requesting) {
                throw new IllegalStateException(
                        "Node " + id + " already owns or is requesting the critical section");
            }

            requesting = true;
            insideCriticalSection = false;
            requestTimestamp = ++lamportClock;
            pendingGrants.clear();
            pendingGrants.addAll(peers.keySet());
            pendingGrants.remove(id);
            deferredRequests.clear();

            Log.info(id, "requesting CS at t=" + requestTimestamp
                    + " (waiting for " + pendingGrants.size() + " GRANT(s))");

            try {
                broadcast(MessageType.REQUEST, requestTimestamp);

                while (!pendingGrants.isEmpty()) {
                    ensureOpen();
                    monitor.wait(WAIT_SLICE_MS);
                }
            } catch (InterruptedException e) {
                cancelRequest();
                throw e;
            } catch (IOException e) {
                cancelRequest();
                throw e;
            } catch (RuntimeException e) {
                cancelRequest();
                throw e;
            }

            insideCriticalSection = true;
            Log.info(id, "entered CS");
        }
    }

    @Override
    public void exitCS() throws InterruptedException, IOException {
        synchronized (monitor) {
            ensureOpen();
            if (!insideCriticalSection) {
                throw new IllegalStateException(
                        "Node " + id + " is not inside the critical section");
            }

            insideCriticalSection = false;
            requesting = false;
            releaseDeferredRequests();
            monitor.notifyAll();
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
            releaseDeferredRequests();
            monitor.notifyAll();
        }

        sendQuietly(BROADCAST_KEY, MessageType.BYE, lamportClock);
        Log.info(id, "left the group");
        shutdownResources();
    }

    private void awaitStableView() throws InterruptedException, IOException {
        // Give HELLO replies and heartbeats a chance to arrive, so that the
        // request is sent to the whole group and not only to a partial view.
        while (!closed) {
            final long elapsed = System.currentTimeMillis() - lastViewChange;
            if (elapsed >= DISCOVERY_QUIET_PERIOD_MS) {
                return;
            }
            monitor.wait(DISCOVERY_QUIET_PERIOD_MS - elapsed);
        }
        throw new IOException("Middleware for node " + id + " is closed");
    }

    private void cancelRequest() {
        if (!requesting) {
            return;
        }
        requesting = false;
        insideCriticalSection = false;
        pendingGrants.clear();
        releaseDeferredRequests();
        monitor.notifyAll();
    }

    private void releaseDeferredRequests() {
        if (deferredRequests.isEmpty()) {
            return;
        }

        for (Map.Entry<String, Long> deferred : deferredRequests.entrySet()) {
            try {
                sendUnicast(deferred.getKey(), MessageType.GRANT, deferred.getValue());
                Log.info(id, "granted deferred request of " + deferred.getKey());
            } catch (IOException | RuntimeException e) {
                Log.error(id, "could not grant node " + deferred.getKey()
                        + ": " + e.getMessage());
            }
        }
        deferredRequests.clear();
    }

    private void ensureOpen() throws IOException {
        if (closed) {
            throw new IOException("Middleware for node " + id + " is closed");
        }
    }

    private void broadcast(final MessageType type, final long timestamp) throws IOException {
        send(BROADCAST_KEY, type, timestamp);
    }

    private void sendUnicast(final String target, final MessageType type, final long timestamp)
            throws IOException {
        send(NODE_KEY_PREFIX + target, type, timestamp);
    }

    private void send(final String routingKey, final MessageType type, final long timestamp)
            throws IOException {
        final byte[] body = MessageCodec.encode(new Message(type, id, timestamp));
        synchronized (publishLock) {
            channel.basicPublish(EXCHANGE_NAME, routingKey, null, body);
        }
    }

    private void sendQuietly(final String routingKey, final MessageType type,
                             final long timestamp) {
        try {
            send(routingKey, type, timestamp);
        } catch (IOException | RuntimeException e) {
            Log.warn(id, "could not send " + type + ": " + e.getMessage());
        }
    }

    private static String unicastKey(final String nodeId) {
        return NODE_KEY_PREFIX + nodeId;
    }

    private void shutdownResources() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        if (channel != null && channel.isOpen()) {
            try {
                channel.close();
            } catch (Exception ignored) {
                // Best effort during shutdown.
            }
        }
        if (connection != null && connection.isOpen()) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // Best effort during shutdown.
            }
        }
    }
}
