# Distributed Tic-Tac-Toe with Java RMI

Implementation of Assignment #04 - Exercise #2.

## Overview

The system is a distributed object application built on Java RMI:

- A **server** exports a `GameLobby` remote object. Players use it to create a
  new game or join an existing one by name.
- When a game starts, each player receives a **remote `GameSession`** bound to
  that player's symbol (`X` or `O`). The client uses the session to play moves
  and to leave the game.
- Each client exports a **`PlayerListener`** remote object. The server uses this
  callback to push events (game started, move made, game over, opponent left)
  back to the client.

## Package structure

```
pcd.ttt.common   Remote interfaces and serializable data shared by client/server
pcd.ttt.server   LobbyImpl, GameImpl, GameSessionImpl, RunServer
pcd.ttt.client   PlayerListenerImpl, Console, RunClient
```

### Remote interfaces

| Interface | Implemented by | Purpose |
|---|---|---|
| `GameLobby` | server | create/join games, list joinable games |
| `GameSession` | server | per-player view: play a move, read state, leave |
| `PlayerListener` | client | server-to-client callbacks |

### Serializable data

`BoardState` is an **immutable snapshot** of the board, turn, and status.
`Symbol`, `GameStatus`, and the `GameException` hierarchy are also serializable,
so they can cross the RMI boundary.

## Concurrency strategy

- The lobby uses a `ConcurrentHashMap`; `putIfAbsent` makes game creation
  atomic even if two clients use the same name concurrently.
- Each `GameImpl` has its own monitor (`lock`) protecting board, turn, players,
  and status. Different games do not contend with each other.
- Every operation validates and updates state under the lock, creates an
  immutable `BoardState` snapshot, and enqueues callbacks. **No remote call is
  performed while holding the lock**, avoiding slow-client blocking and
  lock-ordering deadlocks.
- Each player has a dedicated single-thread notifier executor. Events for a
  player are delivered in order, and a slow client cannot delay the opponent.
- At the end of a game the server removes the game from the lobby, shuts down
  the notifiers, and unexports the player sessions to avoid leaks.

## Build

With Maven:

```bash
mvn clean package
```

Or directly with `javac`:

```bash
mkdir -p target/classes
find src/main/java -name '*.java' > sources.txt
javac -d target/classes @sources.txt
```

## Run

Start the server (it creates an RMI registry on the given port, or reuses one):

```bash
java -cp target/classes pcd.ttt.server.RunServer 1099
```

When the server and the clients run on different machines, set the address that
clients must use to reach the exported objects:

```bash
java -Djava.rmi.server.hostname=<server-ip> -cp target/classes pcd.ttt.server.RunServer 1099
```

Start two clients in two terminals:

```bash
java -cp target/classes pcd.ttt.client.RunClient localhost 1099 Alice
java -cp target/classes pcd.ttt.client.RunClient localhost 1099 Bob
```

Client commands:

```
list                 list the games waiting for an opponent
create <gameName>    create a new game and wait for an opponent
join <gameName>      join an existing game
move <row> <col>     play a move (rows and columns are 0..2)
board                show the current board
leave                leave the current game
help                 show help
quit                 leave and exit
```

Example: Alice types `create demo`, then Bob types `join demo`. Alice is `X`
and moves first. Both clients receive the board after every move.

## Error handling

The server rejects duplicate game names, unknown games, full games, moves made
out of turn, out-of-range coordinates, and occupied cells. Errors are returned
as serializable `GameException` subclasses. If a player leaves an active game,
the opponent is notified and the game is aborted and removed from the lobby.
