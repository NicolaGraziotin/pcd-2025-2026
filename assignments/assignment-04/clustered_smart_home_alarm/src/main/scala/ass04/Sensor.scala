package ass04

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.sharding.typed.scaladsl.{EntityRef, EntityTypeKey}

import scala.concurrent.duration.*
import scala.util.Random

/** Immutable description of one sensor. */
final case class SensorSpec(sensorId: String, zone: String, kind: String)

/** Sensors known by the application, shared by every node. */
object SensorCatalog:

  val all: Seq[SensorSpec] = Seq(
    SensorSpec("door-entrance", "perimeter", "door"),
    SensorSpec("window-living", "perimeter", "window"),
    SensorSpec("motion-living", "living", "motion"),
    SensorSpec("motion-bedroom", "sleeping", "motion")
  )

  val byId: Map[String, SensorSpec] =
    all.map(spec => spec.sensorId -> spec).toMap

  def ids: String = all.map(_.sensorId).mkString(", ")

/** Sensor actor that notifies the alarm controller asynchronously on detection.
  */
object Sensor:

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Sensor")

  sealed trait Command extends CborSerializable

  final case class Start(sensorId: String) extends Command

  final case class TriggerNow() extends Command

  private case object Tick extends Command

  def apply(
      spec: SensorSpec,
      controller: EntityRef[AlarmController.Command],
      interval: FiniteDuration = 7.seconds,
      probability: Double = 0.25,
      auto: Boolean = true
  ): Behavior[Command] =
    Behaviors.setup { ctx =>
      if auto then
        ctx.log.info(
          "[SENSOR:{}] avviato ({}/{}) ogni {}",
          spec.sensorId,
          spec.kind,
          spec.zone,
          interval
        )
      Behaviors.withTimers { timers =>
        if auto then timers.startTimerAtFixedRate(Tick, interval)
        val rnd = new Random(spec.sensorId.hashCode)
        Behaviors.receiveMessage {
          case Tick =>
            if rnd.nextDouble() < probability then
              ctx.log.info("[SENSOR:{}] movimento rilevato -> invio evento", spec.sensorId)
              controller ! AlarmController.SensorTriggered(spec.sensorId, spec.zone, spec.kind)
            Behaviors.same

          case Start(sensorId) =>
            ctx.log.info("[SENSOR:{}] comando di avvio ricevuto per l'entity {}", spec.sensorId, sensorId)
            Behaviors.same

          case TriggerNow() =>
            ctx.log.info("[SENSOR:{}] trigger manuale -> invio evento", spec.sensorId)
            controller ! AlarmController.SensorTriggered(spec.sensorId, spec.zone, spec.kind)
            Behaviors.same
        }
      }
    }
