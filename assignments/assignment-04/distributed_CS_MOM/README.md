# Assignment 04 — Distributed Critical Section over a MOM

A small high-level middleware that provides distributed mutual exclusion to
processes connected through RabbitMQ.

A process only supplies its own identifier and calls `enterCS()` / `exitCS()`.
It does not know which other processes exist, where they run, or which queues
they use.

## How it works

The implementation uses the **Ricart–Agrawala** algorithm:

1. Every process maintains a Lamport logical clock.
2. To enter the critical section, a process broadcasts a `REQUEST` with its
   timestamp and waits for a `GRANT` from every other known process.
3. A process that is not interested in the critical section immediately replies
   with `GRANT`.
4. A process that is also waiting compares timestamps. The request with the
   smaller timestamp wins; equal timestamps are broken by node identifier. The
   losing request is deferred.
5. When a process leaves the critical section, it sends `GRANT` to all deferred
   requests.

While a process is inside the critical section it defers every new request until
it exits. This is slightly stricter than the original algorithm and prevents an
older, late request from being granted while the section is already occupied.

### RabbitMQ topology

* One topic exchange (`ass04.critical-section.exchange`).
* Each process creates its own exclusive, auto-delete queue and binds it to:
  * the broadcast routing key (`ass04.critical-section.broadcast`);
  * its own unicast routing key (`ass04.critical-section.node.<id>`).
* Broadcast messages (`HELLO`, `BYE`, `REQUEST`) go to all nodes; `GRANT` messages
  are sent directly to one node.

The broker therefore decouples the processes: they only need the broker address,
not the identity or location of the other participants.

### Membership

* A starting process broadcasts `HELLO`; known processes answer with a unicast
  `HELLO`, so both sides discover each other.
* After the initial `HELLO` a process waits briefly (500 ms) so that HELLOs from
  processes started at the same time can arrive before it begins.
* If a new process joins while a request is in progress, the requester adds it to
  the set of nodes from which a `GRANT` is required.
* A process sends `BYE` when it closes, so the others remove it from the group.
* There is no failure detection: a process that crashes without sending `BYE`
  remains in the others' view. The exercise assumes clean exits.

## Project structure

```
src/main/java/ass04/
├── Main.java                  # example process
├── Middleware.java            # public middleware interface
├── MiddlewareImpl.java        # Ricart–Agrawala + RabbitMQ implementation
└── util/
    ├── Log.java
    ├── Message.java
    ├── MessageCodec.java
    └── MessageType.java
```

## Build

```bash
mvn clean package
```

The shade plugin produces a self-contained executable jar in `target/`.

## Run

Start a RabbitMQ broker on `localhost` (or pass a different host)

```bash
docker run -d --name rabbitmq \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management
```

then run at
least two processes in separate terminals:

```bash
java -jar target/pcd-ass-04-cs-mom-1.0-SNAPSHOT.jar n1
java -jar target/pcd-ass-04-cs-mom-1.0-SNAPSHOT.jar n2
java -jar target/pcd-ass-04-cs-mom-1.0-SNAPSHOT.jar n3
```

Each process performs 5 rounds by default. The arguments are:

```
java ass04.Main <node-id> [host] [rounds] [work-ms]
```

For example:

```bash
java -jar target/pcd-ass-04-cs-mom-1.0-SNAPSHOT.jar n1 localhost 10 500
```

The host can also be set with the `RABBITMQ_HOST` environment variable.

The log shows the interesting events (requests, deferred grants, CS entry and
exit). In a correct execution, the `inside CS` lines of different processes never
overlap in time.

## Assumptions and limitations

* Nodes leave cleanly with `BYE`; there is no timeout-based failure detection, so
  a crashed node would leave the survivors waiting for its `GRANT`.
* Message delivery is provided by RabbitMQ; the middleware assumes no message
  loss while the broker is reachable.
* Network partitions are not handled. Stronger guarantees would require a
  consensus service or a quorum-based membership protocol.
