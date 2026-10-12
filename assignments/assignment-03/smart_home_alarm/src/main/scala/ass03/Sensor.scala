package ass03

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.*
import scala.util.Random

/** Sensor actor that notifies the controller asynchronously on detection. */
object Sensor:

  sealed trait Command
  private case object Tick extends Command
  case object TriggerNow extends Command

  def apply(
      sensorId: String,
      zone: String,
      kind: String,
      controller: ActorRef[AlarmController.Command],
      interval: FiniteDuration = 7.seconds,
      probability: Double = 0.25,
      auto: Boolean = true
  ): Behavior[Command] =
    Behaviors.setup { ctx =>
      if auto then
        ctx.log.info("[SENSOR:{}] avviato ({}/{}) ogni {}", sensorId, kind, zone, interval)
      Behaviors.withTimers { timers =>
        if auto then timers.startTimerAtFixedRate(Tick, interval)
        val rnd = new Random(sensorId.hashCode)
        Behaviors.receiveMessage {
          case Tick =>
            if rnd.nextDouble() < probability then
              ctx.log.info("[SENSOR:{}] movimento rilevato -> invio evento", sensorId)
              controller ! AlarmController.SensorTriggered(sensorId, zone, kind)
            Behaviors.same
          case TriggerNow =>
            ctx.log.info("[SENSOR:{}] trigger manuale -> invio evento", sensorId)
            controller ! AlarmController.SensorTriggered(sensorId, zone, kind)
            Behaviors.same
        }
      }
    }
