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
  *
  * Sensors are cluster-sharded entities: their logical identity is the
  * `sensorId`, while the actual actor may live on any node with the `sensor`
  * role. This lets the keypad trigger a sensor on another node and lets the
  * cluster move a sensor entity if its current node leaves.
  */
object Sensor:

  val TypeKey: EntityTypeKey[Command] = EntityTypeKey[Command]("Sensor")

  sealed trait Command extends CborSerializable

  /** Starts the periodic detection loop for an entity. It is idempotent in the
    * sense that repeated `Start` messages do not create duplicate sensors:
    * cluster sharding guarantees one entity per `sensorId`.
    */
  final case class Start(sensorId: String) extends Command

  /** Manual trigger, sent by the keypad to the sharded sensor entity. */
  final case class TriggerNow() extends Command

  /** Local timer message; never serialized across nodes. */
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
