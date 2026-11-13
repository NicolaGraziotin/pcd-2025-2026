# Clustered Smart Home Alarm

Versione distribuita del Smart Home Alarm: lo stesso jar può essere avviato su
più nodi Pekko Cluster con ruoli diversi.

## Architettura

| Ruolo        | Cosa esegue                                                              |
|--------------|--------------------------------------------------------------------------|
| `controller` | Region di cluster sharding per l'entity `AlarmController`                 |
| `sensor`     | Region di cluster sharding per le entity `Sensor`                        |
| `keypad`     | Attore `Keypad` + console interattiva, che invia comandi all'entity      |

L'`AlarmController` è una **entity sharded** con id logico `home-alarm`.
I sensori sono anch'essi entity sharded, una per `sensorId`. Di conseguenza:

* keypad e sensori non conoscono il nodo fisico del controller;
* i sensori possono vivere su nodi diversi dal keypad e dal controller;
* se un nodo cade, lo sharding ricrea l'entity su un altro nodo con ruolo
  `controller` (o `sensor`);
* la comunicazione tra nodi avviene solo tramite messaggi attore, serializzati
  con Jackson CBOR (`ass04.CborSerializable`).

Il `Siren` è un figlio locale dell'entity `AlarmController`: quando l'entity
viene ricreata, la nuova incarnazione parte con una sirena spenta.

## Recovery dopo un restart

Non viene usata persistenza: se l'entity viene fermata e ricreata, il suo stato
precedente è perso. La nuova istanza entra sempre in `RECOVERY`:

* non assume né `ARMED` né `DISARMED`;
* ignora (con log) gli eventi dei sensori;
* rifiuta `Arm`;
* accetta solo il PIN corretto, che la porta in `DISARMED`.

Il comando `restart` della console simula questo scenario senza dover fermare
un container. Per provarlo davvero:

```bash
# ferma il nodo che ospita il controller (seed oppure node1)
docker compose kill seed
docker compose logs -f
```

Dopo il downing del nodo (Split Brain Resolver `keep-majority`), l'entity viene
ricreata sull'altro nodo `controller` (qui `node1`) e riparte in `RECOVERY`.
La console su `node2` resta attiva: `status` mostra `RECOVERY` e `pin 1234`
riporta il sistema in `DISARMED`.

## Avvio con Docker Compose

```bash
cd docker
docker compose up --build -d
docker compose logs -f
```

La console del keypad gira sul servizio `node2`, su un nodo diverso da quello
che ospita il controller. Per interagire:

```bash
docker attach "$(docker compose ps -q node2)"
```

Per staccarsi senza fermare il container usare `Ctrl-p` poi `Ctrl-q`.

Nodi creati:

* `seed`: ruolo `controller` (seed node)
* `node1`: ruoli `controller,sensor` (failover del controller + sensori)
* `node2`: ruoli `sensor,keypad` (sensori + console, mai controller)

Le entity dei 4 sensori vengono distribuite dallo sharding tra `node1` e
`node2`.

### Comandi della console

```
arm <pin> [zone...]   arma (senza zone = full)
pin <codice>          PIN (esce da RECOVERY / disarma / ferma la sirena)
<sole-cifre>          scorciatoia per il PIN
trigger <id>          simula un evento su un sensore remoto
sensors               elenca i sensori
status                stato e zone attive
restart               simula la perdita dell'entity AlarmController
demo                  scenario completo, inclusa la RECOVERY
help                  aiuto
exit                  termina il nodo keypad
```

Sensori: `door-entrance`, `window-living`, `motion-living`,
`motion-bedroom`. PIN di default: `1234`.

All'avvio l'entity è in `RECOVERY`: il primo comando deve essere `pin 1234`
(oppure `1234`).

## Avvio locale senza Docker

Servono tre JVM con porte diverse. Esempio:

```bash
# seed / controller
CLUSTER_HOST=127.0.0.1 CLUSTER_PORT=25520 SEED_HOST=127.0.0.1 SEED_PORT=25520 \
  CLUSTER_ROLES=controller java -jar target/cluster.jar

# sensori + controller (failover)
CLUSTER_HOST=127.0.0.1 CLUSTER_PORT=25521 SEED_HOST=127.0.0.1 SEED_PORT=25520 \
  CLUSTER_ROLES=controller,sensor java -jar target/cluster.jar

# sensori + keypad
CLUSTER_HOST=127.0.0.1 CLUSTER_PORT=25522 SEED_HOST=127.0.0.1 SEED_PORT=25520 \
  CLUSTER_ROLES=sensor,keypad java -jar target/cluster.jar
```

Per costruire il jar:

```bash
sbt assembly
# output: target/cluster.jar
```

## Configurazione per nodo

Variabili d'ambiente lette da `NodeSettings`:

| Variabile        | Default              | Significato                              |
|------------------|----------------------|------------------------------------------|
| `CLUSTER_HOST`   | `127.0.0.1`          | hostname annunciato da Artery             |
| `CLUSTER_PORT`   | `25520`              | porta Artery                              |
| `CLUSTER_ROLES`  | `controller,keypad,sensor` | ruoli del nodo (`controller,sensor,keypad`) |
| `SEED_HOST`      | `CLUSTER_HOST`       | host del seed node                        |
| `SEED_PORT`      | `CLUSTER_PORT`       | porta del seed node                       |
| `ALARM_PIN`      | `1234`               | PIN corretto                              |
| `EXIT_DELAY`     | `5`                  | exit delay in secondi                     |
| `ENTRY_DELAY`    | `5`                  | entry delay in secondi                    |

La configurazione base è in `src/main/resources/application.conf`; host, porta,
ruoli e seed vengono sovrascritti a runtime da `NodeSettings`.
