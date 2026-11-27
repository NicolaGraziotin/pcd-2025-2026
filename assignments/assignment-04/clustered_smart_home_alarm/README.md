# Clustered Smart Home Alarm

## 1. Start the program

To run the project, use the following commands:

```bash
cd clustered_smart_home_alarm
```

### Docker Compose

```bash
cd docker
docker compose up --build -d
docker compose logs -f
```

## 2. Interact with the keypad

The keypad console runs on the `node2` service, on a different node from the one
hosting the controller. To interact with it:

```bash
docker attach "$(docker compose ps -q node2)"
```

To detach without stopping the container, press `Ctrl-p` followed by `Ctrl-q`.

### Commands

```
arm <pin> [zone...]   arm (without zones = full)
pin <code>            PIN (exits RECOVERY / disarms / stops the siren)
<digits-only>         shortcut for PIN
trigger <id>          simulate an event on a remote sensor
sensors               list sensors
status                status and active zones
restart               simulate the loss of the AlarmController entity
demo                  complete scenario, including RECOVERY
help                  help
exit                  terminate the keypad node
```

## 3. Recovery after a restart

The console `restart` command simulates this scenario without stopping a container. To test it for real:

```bash
# stop the node hosting the controller (seed or node1)
docker compose kill seed
docker compose logs -f
```

After the node is downed (Split Brain Resolver `keep-majority`), the entity is
recreated on the other `controller` node (here `node1`) and restarts in `RECOVERY`.
The console on `node2` remains active: `status` shows `RECOVERY` and `pin 1234`
brings the system back to `DISARMED`.
