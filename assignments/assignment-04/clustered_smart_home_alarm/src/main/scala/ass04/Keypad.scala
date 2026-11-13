package ass04

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors

/** Keypad actor translating console input into controller messages.
  *
  * The keypad only knows how to send commands, not where the controller
  * entity currently lives. In the cluster bootstrap it is wired to the
  * sharded `AlarmController` entity, so it can run on a different node from
  * the controller and the sensors.
  */
object Keypad:

  sealed trait Command
  final case class RawInput(line: String) extends Command

  def apply(send: AlarmController.Command => Unit): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case RawInput(line) =>
          parse(line) match
            case Some(cmd) =>
              ctx.log.info("[KEYPAD] input '{}' -> {}", line, describe(cmd))
              send(cmd)
            case None =>
              ctx.log.warn("[KEYPAD] input non riconosciuto: '{}' (scrivi 'help')", line)
          Behaviors.same
    }

  private def describe(cmd: AlarmController.Command): String = cmd match
    case AlarmController.Arm(_, zones) if zones.isEmpty => s"Arm(full, pin=****)"
    case AlarmController.Arm(_, zones)                 => s"Arm(partial=${zones.mkString(",")})"
    case AlarmController.PinEntered(_)                  => "PinEntered(****)"
    case other                                          => other.toString

  private def parse(line: String): Option[AlarmController.Command] =
    val parts = line.trim.split("\\s+").toList.filter(_.nonEmpty)
    parts match
      case "arm" :: pin :: zones => Some(AlarmController.Arm(pin, zones.toSet))
      case "arm" :: Nil          => Some(AlarmController.Arm("1234", Set.empty))
      case "pin" :: pin :: Nil   => Some(AlarmController.PinEntered(pin))
      case pin :: Nil if pin.matches("\\d+") => Some(AlarmController.PinEntered(pin))
      case _ => None
