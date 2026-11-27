# Distributed Tic-Tac-Toe with Java RMI

## 1. Build the program

To run the project, use the following commands:

```bash
cd distributed_TTT

mvn clean package
```

## 2. Run the program

### Start the server:

It creates an RMI registry on the given port, or reuses one.

```bash
java -jar target/server.jar
```

#### Flags

|Flag|Default|Description|
|-|-|-|
|`<port>`|`1099`|Specify the RMI registry port.|

#### Example:

When the server and the clients run on different machines, set the address that
clients must use to reach the exported objects:

```bash
java -Djava.rmi.server.hostname=<server-ip> -jar target/server.jar 1099
```

### Start two clients in two terminals:

```bash
java -jar target/client.jar
```

#### Flags
|Flag|Default|Description|
|-|-|-|
|`<server-ip>`|`localhost`|Specify the server IP address (required).|
|`<port>`|`1099`|Specify the RMI registry port (required).|
|`<player-name>`|`Player`|Specify the player name (optional).|

#### Example:

```bash
java -jar target/client.jar localhost 1099 Alice
java -jar target/client.jar localhost 1099 Bob
```

**Client commands:**

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
