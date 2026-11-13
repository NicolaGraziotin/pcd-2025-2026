package ass04

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.scaladsl.{ClusterSharding, Entity, EntityRef}
import org.apache.pekko.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration.*
import scala.io.StdIn

/** Entry point for every cluster node.
  *
  * The same image can run with one or more roles (`controller`, `sensor`,
  * `keypad`). Cluster sharding owns the alarm controller and sensor entities,
  * so actors can be addressed by logical id without knowing their node.
  */
object Main:

  def main(args: Array[String]): Unit =
    val settings = NodeSettings.fromEnv()
    println(banner(settings))
    val system = ActorSystem[Nothing](RootBehavior(settings), settings.systemName, settings.config)
    Await.result(system.whenTerminated, Duration.Inf)

  object RootBehavior:

    def apply(settings: NodeSettings): Behavior[Nothing] =
      Behaviors.setup[Nothing] { ctx =>
        val _ = ctx.spawn(ClusterListener(), "cluster-listener")
        val sharding = ClusterSharding(ctx.system)

        // The alarm controller entity is hosted only by nodes with the
        // `controller` role. On every other node `init` creates a proxy, so
        // sensors and keypads can still send messages to it.
        val _ = sharding.init(
          Entity(AlarmController.TypeKey) { _ =>
            AlarmController(settings.alarmConfig)
          }.withRole("controller")
        )

        val alarm: EntityRef[AlarmController.Command] =
          sharding.entityRefFor(AlarmController.TypeKey, AlarmController.EntityId)

        // Sensor entities are hosted by nodes with the `sensor` role.
        val _ = sharding.init(
          Entity(Sensor.TypeKey) { entityContext =>
            SensorCatalog.byId.get(entityContext.entityId) match
              case Some(spec) =>
                Sensor(spec, controller = alarm)
              case None =>
                ctx.log.warn("[BOOT] sensore sconosciuto richiesto: {}", entityContext.entityId)
                Behaviors.empty[Sensor.Command]
          }.withRole("sensor")
        )

        // Every sensor-role node asks for all sensors to be started. Sharding
        // guarantees exactly one entity per sensorId, so this is idempotent
        // and also lets the cluster re-create a sensor after a node failure.
        if settings.roles.contains("sensor") then
          SensorCatalog.all.foreach { spec =>
            sharding.entityRefFor(Sensor.TypeKey, spec.sensorId) ! Sensor.Start(spec.sensorId)
          }
          ctx.log.info(
            "[BOOT] avvio richiesto per {} sensori: {}",
            SensorCatalog.all.size,
            SensorCatalog.ids
          )

        // Only keypad nodes run the console. Commands are sent to the sharded
        // controller entity, so this node can be different from the one that
        // currently hosts the alarm controller.
        if settings.roles.contains("keypad") then
          val keypad = ctx.spawn(Keypad(cmd => alarm ! cmd), "keypad")
          val thread = new Thread(
            () => consoleLoop(alarm, keypad, sharding, settings.alarmConfig, ctx.system),
            "console"
          )
          thread.setDaemon(true)
          thread.start()

        ctx.log.info(
          "[BOOT] nodo pronto host={} port={} ruoli=[{}] seed=[{}]",
          settings.host,
          settings.port,
          settings.roles.toSeq.sorted.mkString(", "),
          settings.seeds.map((h, p) => s"$h:$p").mkString(", ")
        )

        Behaviors.empty
      }

  // -------------------------------------------------------------------------
  // Console (runs outside the actor system, on its own thread)
  // -------------------------------------------------------------------------

  private def consoleLoop(
      alarm: EntityRef[AlarmController.Command],
      keypad: ActorRef[Keypad.Command],
      sharding: ClusterSharding,
      config: AlarmConfig,
      system: ActorSystem[?]
  ): Unit =
    var running = true
    printHelp()
    while running do
      print("> ")
      val line = StdIn.readLine()
      if line == null then running = false
      else
        line.trim.toLowerCase match
          case "" => ()
          case "exit" | "quit" =>
            println("Arresto del sistema...")
            running = false
            system.terminate()
          case "help" => printHelp()
          case "status" =>
            printStatus(alarm)
          case "sensors" =>
            println(
              SensorCatalog.all
                .map(spec => s"${spec.sensorId} (${spec.kind}, zona=${spec.zone})")
                .mkString("Sensori: ", ", ", "")
            )
          case "restart" | "crash" =>
            println("[CONSOLE] simulo la perdita dell'entity AlarmController (stato in memoria perso)")
            alarm ! AlarmController.RestartEntity()
            Thread.sleep(700)
            printStatus(alarm)
          case s if s == "trigger" || s.startsWith("trigger ") =>
            handleTrigger(s, sharding)
          case "demo" =>
            runDemo(alarm, sharding, config)
          case other =>
            // The keypad parses `arm`, `pin` and numeric shortcuts and sends
            // the resulting command to the sharded controller entity.
            keypad ! Keypad.RawInput(other)

  private def handleTrigger(line: String, sharding: ClusterSharding): Unit =
    line.split("\\s+").toList match
      case _ :: id :: Nil =>
        SensorCatalog.byId.get(id) match
          case Some(spec) =>
            println(s"[CONSOLE] trigger manuale su '${spec.sensorId}'")
            sharding.entityRefFor(Sensor.TypeKey, spec.sensorId) ! Sensor.TriggerNow()
          case None =>
            println(s"Sensore sconosciuto '$id'. Disponibili: ${SensorCatalog.ids}")
      case _ =>
        println(s"Uso: trigger <id>. Disponibili: ${SensorCatalog.ids}")

  private def printStatus(alarm: EntityRef[AlarmController.Command]): Unit =
    given Timeout = Timeout(5.seconds)
    val fut =
      alarm.ask((replyTo: ActorRef[AlarmController.Status]) => AlarmController.GetStatus(replyTo))
    try
      val st = Await.result(fut, 5.seconds)
      val zones = if st.activeZones.isEmpty then "-" else st.activeZones.toSeq.sorted.mkString(",")
      println(s"[STATUS] stato=${st.state} zoneAttive=$zones")
    catch
      case e: Exception =>
        println(s"[STATUS] errore: ${e.getMessage} (il cluster è pronto?)")

  private def runDemo(
      alarm: EntityRef[AlarmController.Command],
      sharding: ClusterSharding,
      config: AlarmConfig
  ): Unit =
    val afterExit = (config.exitDelay + 1500.millis).toMillis
    val afterEntry = (config.entryDelay + 1500.millis).toMillis
    val trigger = (sensorId: String) =>
      sharding.entityRefFor(Sensor.TypeKey, sensorId) ! Sensor.TriggerNow()

    val pin = config.correctPin

    println("\n--- DEMO guidata su cluster ---")
    println(s"0) pin $pin -> esce dalla RECOVERY -> disarmed")
    alarm ! AlarmController.PinEntered(pin)
    Thread.sleep(700)
    printStatus(alarm)

    println(s"1) arm $pin -> exit-delay -> armed")
    alarm ! AlarmController.Arm(pin, Set.empty)
    Thread.sleep(afterExit)
    printStatus(alarm)

    println("2) trigger motion-living -> entry-delay")
    trigger("motion-living")
    Thread.sleep(1500)
    printStatus(alarm)

    println(s"3) pin $pin entro il timeout -> disarmed (nessun allarme)")
    alarm ! AlarmController.PinEntered(pin)
    Thread.sleep(500)
    printStatus(alarm)

    println("4) ri-armo parziale solo 'perimeter' (night-mode): trigger in sleeping ignorato")
    alarm ! AlarmController.Arm(pin, Set("perimeter"))
    Thread.sleep(afterExit)
    trigger("motion-bedroom")
    Thread.sleep(500)
    printStatus(alarm)

    println("5) trigger window-living (zona attiva) -> entry-delay -> attendo timeout -> ALARM")
    trigger("window-living")
    Thread.sleep(afterEntry)
    printStatus(alarm)

    println(s"6) pin $pin -> sirena OFF -> disarmed")
    alarm ! AlarmController.PinEntered(pin)
    Thread.sleep(500)
    printStatus(alarm)

    println("7) restart entity -> stato perso -> la nuova istanza è in RECOVERY")
    alarm ! AlarmController.RestartEntity()
    Thread.sleep(1000)
    printStatus(alarm)

    println(s"8) pin $pin -> RECOVERY -> disarmed")
    alarm ! AlarmController.PinEntered(pin)
    Thread.sleep(500)
    printStatus(alarm)

    println("--- fine DEMO ---\n")

  private def printHelp(): Unit =
    println("""
      |Comandi:
      |  arm <pin> [zone...]   arma (senza zone = full). Es: arm 1234 perimeter living
      |  pin <codice>          digita PIN (esce da RECOVERY / disarma / ferma sirena)
      |  <solo-cifre>          scorciatoia per pin. Es: 1234
      |  trigger <id>          simula intrusione su un sensore remoto
      |  sensors               elenca i sensori noti
      |  status                mostra stato corrente e zone attive
      |  restart               simula la perdita dell'entity AlarmController
      |  demo                  esegue uno scenario guidato completo
      |  help                  questo aiuto
      |  exit                  termina
      |
      |Sensori: door-entrance, window-living, motion-living, motion-bedroom
      |PIN di default: 1234
      |All'avvio l'entity è in RECOVERY: il primo comando deve essere il PIN corretto.
      |""".stripMargin)

  private def banner(s: NodeSettings): String =
    s"""
       |==============================================
       | Smart Home Alarm — Pekko Typed Cluster
       | nodo=${s.host}:${s.port} ruoli=[${s.roles.toSeq.sorted.mkString(",")}]
       | seed=${s.seeds.map((h, p) => s"$h:$p").mkString(",")}
       | exit=${s.alarmConfig.exitDelay.toSeconds}s entry=${s.alarmConfig.entryDelay.toSeconds}s
       | zone=${s.alarmConfig.allZones.toSeq.sorted.mkString(",")}
       |==============================================""".stripMargin
