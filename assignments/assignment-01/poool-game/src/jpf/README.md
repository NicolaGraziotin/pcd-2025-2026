# Model checking con JPF

I test in `java/poool/` girano con JPF su Java 11. Il container monta il progetto
in `/ass` e questa cartella in `/pcd-jpf`, quindi i comandi vanno eseguiti
**dentro il container**.

## 1. Costruire e avviare il container

```bash
docker compose build
docker compose run --rm -it -v $HOME/IdeaProjects/pcd-2025-2026/assignments/assignment-01:/ass -v $HOME/IdeaProjects/pcd-2025-2026/assignments/assignment-01/src/jpf:/pcd-jpf jpf-dev
```

Tutti i comandi seguenti si eseguono nella shell del container.

## 2. Compilare jpf-core (solo la prima volta)

Genera `RunJPF.jar` e `jpf.jar` in `/home/jpf-core/build`:

```bash
cd /home/jpf-core
./gradlew clean build
```

## 3. Compilare i test

Compila le implementazioni di `BoundedBuffer`/`Barrier` e i test in
`/pcd-jpf/target/jpf-classes` (la stessa cartella indicata da `classpath` nei
file `.jpf`):

```bash
mkdir -p /pcd-jpf/target/jpf-classes

javac --release 11 -d /pcd-jpf/target/jpf-classes -classpath /pcd-jpf/target/jpf-classes:/home/jpf-core/build/jpf.jar /ass/src/main/java/poool/multithread/util/*.java /pcd-jpf/java/poool/*.java
```

Va rilanciato ogni volta che modifichi un test o una delle due implementazioni.

## 4. Eseguire i test

```bash
cd /pcd-jpf

java -jar /home/jpf-core/build/RunJPF.jar java/poool/TestBoundedBufferSafety.jpf
java -jar /home/jpf-core/build/RunJPF.jar java/poool/TestCollisionDeadlock.jpf
java -jar /home/jpf-core/build/RunJPF.jar java/poool/TestCyclicBarrier.jpf
```

Ogni file `.jpf` stampa `no errors found` se la proprietà (assenza di violazioni
di assert e di deadlock) è verificata; in caso contrario stampa la traccia della
violazione.

| Test | Cosa verifica |
|------|---------------|
| `TestBoundedBufferSafety` | 2 produttori + 2 consumatori su buffer da 2: nessun item perso/duplicato, nessuna `get` a vuoto |
| `TestCollisionDeadlock` | risoluzione di collisioni incrociate senza deadlock (lock ordering per `id`) |
| `TestCyclicBarrier` | 3 thread su 2 cicli: le fasi non si sfalsano oltre 1 |
