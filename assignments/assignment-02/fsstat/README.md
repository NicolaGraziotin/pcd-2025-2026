# FSStat library

## 1. Build the project:

To build the project, run the following command in the root directory of the project:

```bash
mvn clean package
```

## 2. Run the program:

To run the program, use one of the following commands depending on the implementation you want to execute:

### Eventloop (Vert.x)

```bash
java -jar target/eventloop.jar
```

### Reactive (RXJava) - console
```bash
java -jar target/reactive-console.jar
```

### Virtual threads
```bash
java -jar target/vthreads.jar
```

Every console version of the program can be run with the following flags:

#### Flags

|Flag|Default|Description|
|-|-|-|
|`<dir>`||Specify the root directory to count files in (required).|
|`<maxFS>`||Specify the max file size to consider (required).|
|`<NB>`||Specify the number of bands to divide the file sizes into (required).|

#### Example

```bash
java -jar target/eventloop.jar ../ 1000 10
```

### Reactive (RXJava) - gui
```bash
java -jar target/reactive-gui.jar
```

In this version of the program, the user can specify the parameters through a GUI interface. The GUI allows the user to start and stop the report generation and visualize the statistics dynamically.
