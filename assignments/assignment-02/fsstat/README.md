# Startup instructions

## 1. Build the project:

To build the project, run the following command in the root directory of the project:

```bash
mvn clean package
```

## 2. Run the program:

To run the program, use one of the following commands depending on the implementation you want to execute:

### eventloop
```bash
java -jar target/eventloop.jar
```

### rxjava - console
```bash
java -jar target/reactive-console.jar
```

### rxjava - gui
```bash
java -jar target/reactive-gui.jar
```

### vtrheads
```bash
java -jar target/vthreads.jar
```