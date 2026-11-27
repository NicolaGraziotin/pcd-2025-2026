# Distributed Critical Section over a MOM

## 1. Build the program

To run the project, use the following commands:

```bash
cd distributed_CS_MOM

mvn clean package
```

### Start a RabbitMQ broker on Docker:

```bash
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:4-management
```

## 2. Run the program

Run at least two processes in separate terminals:

```bash
java -jar target/client.jar n1
java -jar target/client.jar n2
java -jar target/client.jar n3
```

### Flags

|Flag|Default|Description|
|-|-|-|
|`<node-id>`|`n1`|Specify the node ID (required).|
|`[host]`|`localhost`|Specify the RabbitMQ host (optional).|
|`[rounds]`|`5`|Specify the number of rounds (optional).|
|`[work-ms]`|`1000`|Specify the work time in milliseconds (optional).|

### Example:

```bash
java -jar target/client.jar n1 localhost 10 500
```

The host can also be set with the `RABBITMQ_HOST` environment variable.
