# Smart Home Alarm

## 1. Run the program:

To run the project, use the following commands:

```bash
cd smart_home_alarm

sbt clean run
```

### Commands:

```
arm <pin> [zone...]   arm (without zones = full). E.g.: arm 1234 perimeter living
pin <code>            enter PIN (disarm / stop siren / cancel exit-delay)
<digits-only>         shortcut for pin. E.g.: 1234
trigger <id>          simulate intrusion. Id: door-entrance,
                      window-living, motion-living, motion-bedroom
status                show current status and active zones
demo                  run a complete guided scenario
help                  this help
exit                  exit
Default PIN: 1234
```