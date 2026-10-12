package ass03

import org.apache.pekko.actor.typed.{ActorRef, ActorSystem, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.actor.typed.scaladsl.AskPattern.*
import org.apache.pekko.util.Timeout

import scala.concurrent.duration.*
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.StdIn

/** Guardian actor booting siren, controller, keypad and sensors.
  * Console I/O runs on a separate thread, outside actors.
  */
object Main:

  def guardian(config: AlarmConfig): Behavior[Nothing] =
    Behaviors.setup[Nothing] { ctx =>
      val siren = ctx.spawn(Siren(), "siren")
      val controller = ctx.spawn(AlarmController(config, siren), "alarm-controller")
      val keypad = ctx.spawn(Keypad(controller), "keypad")

      val sensors: Map[String, ActorRef[Sensor.Command]] = Map(
        "door-entrance" -> ctx.spawn(Sensor("door-entrance", "perimeter", "door", controller), "sensor-door"),
        "window-living" -> ctx.spawn(Sensor("window-living", "perimeter", "door", controller), "sensor-window"),
        "motion-living" -> ctx.spawn(Sensor("motion-living", "living", "motion", controller), "sensor-living"),
        "motion-bedroom" -> ctx.spawn(Sensor("motion-bedroom", "sleeping", "motion", controller), "sensor-bedroom")
      )

      ctx.log.info("[GUARDIAN] sistema pronto: {} sensori attivi", sensors.size)

      val system = ctx.system
      val thread = new Thread(() => consoleLoop(controller, keypad, sensors, system), "console")
      thread.setDaemon(true)
      thread.start()

      Behaviors.empty
    }

  @main
  def runAlarm(): Unit =
    val config = AlarmConfig.demo()
    println(banner(config))
    val system = ActorSystem(guardian(config), "smart-home-alarm")
    Await.result(system.whenTerminated, Duration.Inf)

  private def consoleLoop(
      controller: ActorRef[AlarmController.Command],
      keypad: ActorRef[Keypad.Command],
      sensors: Map[String, ActorRef[Sensor.Command]],
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
            printStatus(controller, system)
          case s if s.startsWith("trigger") =>
            handleTrigger(s, sensors)
          case s if s.startsWith("demo") =>
            runDemo(controller, sensors, system)
          case other =>
            keypad ! Keypad.RawInput(line.trim)

  private def handleTrigger(line: String, sensors: Map[String, ActorRef[Sensor.Command]]): Unit =
    line.split("\\s+").toList match
      case _ :: id :: Nil =>
        sensors.get(id) match
          case Some(ref) =>
            println(s"[CONSOLE] trigger manuale su '$id'")
            ref ! Sensor.TriggerNow
          case None =>
            println(s"Sensore sconosciuto '$id'. Disponibili: ${sensors.keys.mkString(", ")}")
      case _ =>
        println(s"Uso: trigger <id>. Disponibili: ${sensors.keys.mkString(", ")}")

  private def printStatus(controller: ActorRef[AlarmController.Command], system: ActorSystem[?]): Unit =
    given Timeout = Timeout(3.seconds)
    given ExecutionContext = ExecutionContext.global
    given org.apache.pekko.actor.typed.Scheduler = system.scheduler
    val fut: Future[AlarmController.Status] =
      controller.ask((ref: ActorRef[AlarmController.Status]) => AlarmController.GetStatus(ref))
    try
      val st = Await.result(fut, 3.seconds)
      println(s"[STATUS] stato=${st.state} zoneAttive=${if st.activeZones.isEmpty then "-" else st.activeZones.mkString(",")}")
    catch case e: Exception => println(s"[STATUS] errore: ${e.getMessage}")

  private def runDemo(
      controller: ActorRef[AlarmController.Command],
      sensors: Map[String, ActorRef[Sensor.Command]],
      system: ActorSystem[?]
  ): Unit =
    println("\n--- DEMO guidata ---")
    println("1) arm 1234  -> exit-delay (5s) -> armed")
    controller ! AlarmController.Arm("1234", Set.empty)
    Thread.sleep(6500)
    printStatus(controller, system)
    println("2) trigger motion-living -> entry-delay (5s)")
    sensors("motion-living") ! Sensor.TriggerNow
    Thread.sleep(1500)
    printStatus(controller, system)
    println("3) pin 1234 entro il timeout -> disarmed (nessun allarme)")
    controller ! AlarmController.PinEntered("1234")
    Thread.sleep(500)
    printStatus(controller, system)
    println("4) ri-armo parziale solo 'perimeter' (night-mode): trigger in sleeping ignorato")
    controller ! AlarmController.Arm("1234", Set("perimeter"))
    Thread.sleep(6500)
    sensors("motion-bedroom") ! Sensor.TriggerNow
    Thread.sleep(500)
    printStatus(controller, system)
    println("5) trigger window-living (zona attiva) -> entry-delay -> attendo timeout -> ALARM")
    sensors("window-living") ! Sensor.TriggerNow
    Thread.sleep(6500)
    printStatus(controller, system)
    println("6) pin 1234 -> sirena OFF -> disarmed")
    controller ! AlarmController.PinEntered("1234")
    Thread.sleep(500)
    printStatus(controller, system)
    println("--- fine DEMO ---\n")

  private def printHelp(): Unit =
    println("""
      |Comandi:
      |  arm <pin> [zone...]   arma (senza zone = full). Es: arm 1234 perimeter living
      |  pin <codice>          digita PIN (disarma / ferma sirena / annulla exit-delay)
      |  <solo-cifre>          scorciatoia per pin. Es: 1234
      |  trigger <id>          simula intrusione. Id: door-entrance, window-living,
      |                        motion-living, motion-bedroom
      |  status                mostra stato corrente e zone attive
      |  demo                  esegue uno scenario guidato completo
      |  help                  questo aiuto
      |  exit                  termina
      |PIN di default: 1234
      |""".stripMargin)

  private def banner(c: AlarmConfig): String =
    s"""
      |==============================================
      | Smart Home Alarm — Pekko Typed (actors)
      | PIN=1234 exit=${c.exitDelay.toSeconds}s entry=${c.entryDelay.toSeconds}s
      | zone=${c.allZones.mkString(",")}
      |==============================================""".stripMargin
