package ass03

import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors

/** Siren actor with ON/OFF behavior driven only by messages. */
object Siren:

  sealed trait Command
  case object Activate extends Command
  case object Deactivate extends Command
  case object GetState extends Command

  def apply(): Behavior[Command] = off()

  private def off(): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case Activate =>
          ctx.log.info("[SIREN] ON — allarme in corso!")
          on()
        case Deactivate =>
          ctx.log.info("[SIREN] già spenta (nessun effetto)")
          Behaviors.same
        case GetState =>
          ctx.log.info("[SIREN] stato = OFF")
          Behaviors.same
    }

  private def on(): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case Activate =>
          ctx.log.info("[SIREN] già accesa (nessun effetto)")
          Behaviors.same
        case Deactivate =>
          ctx.log.info("[SIREN] OFF — sirena disattivata")
          off()
        case GetState =>
          ctx.log.info("[SIREN] stato = ON")
          Behaviors.same
    }
