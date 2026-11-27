# Poool Game

## 1. Build the program

To run the project, use the following commands:

```bash
cd poool-game

mvn clean package
```

## 2. Run the program

To run the program, use one of the following commands depending on the implementation you want to execute:

### Sequential

```bash
java -jar target/pool-sequential.jar
```

### Multi-threaded

```bash
java -jar target/pool-multithreaded.jar
```

### Task-based

```bash
java -jar target/pool-taskbased.jar
```