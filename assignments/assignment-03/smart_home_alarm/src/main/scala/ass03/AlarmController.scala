package ass03

import org.apache.pekko.actor.typed.{ActorRef, Behavior}
import org.apache.pekko.actor.typed.scaladsl.{Behaviors, TimerScheduler}

/** Central FSM actor: Disarmed -> ExitDelay -> Armed -> EntryDelay -> Alarm.
  * Each state is a distinct behavior; timeouts are self-sent messages.
  */
object AlarmController:

  sealed trait Command

  final case class Arm(pin: String, zones: Set[String] = Set.empty) extends Command
  final case class PinEntered(pin: String) extends Command
  final case class SensorTriggered(sensorId: String, zone: String, kind: String) extends Command

  private case object ExitTimeout extends Command
  private case object EntryTimeout extends Command

  final case class GetStatus(replyTo: ActorRef[Status]) extends Command
  final case class Status(state: String, activeZones: Set[String])

  private case object ExitTimerKey
  private case object EntryTimerKey

  def apply(config: AlarmConfig, siren: ActorRef[Siren.Command]): Behavior[Command] =
    Behaviors.setup { ctx =>
      Behaviors.withTimers { timers =>
        ctx.log.info(
          "[CTRL] boot: PIN=**** exit={} entry={} zones={}",
          config.exitDelay, config.entryDelay, config.allZones.mkString(",")
        )
        disarmed(config, siren, timers)
      }
    }

  private def disarmed(
      config: AlarmConfig,
      siren: ActorRef[Siren.Command],
      timers: TimerScheduler[Command]
  ): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case Arm(pin, zones) =>
          if pin != config.correctPin then
            ctx.log.warn("[CTRL][DISARMED] PIN errato, armamento rifiutato")
            Behaviors.same
          else
            val active = if zones.isEmpty then config.allZones else zones.intersect(config.allZones)
            if active.isEmpty then
              ctx.log.warn("[CTRL][DISARMED] nessuna zona valida selezionata, armamento rifiutato")
              Behaviors.same
            else
              ctx.log.info("[CTRL][DISARMED] PIN ok -> EXIT-DELAY (zone attive: {})", active.mkString(","))
              timers.startSingleTimer(ExitTimerKey, ExitTimeout, config.exitDelay)
              exitDelay(config, siren, timers, active)

        case PinEntered(_) =>
          ctx.log.info("[CTRL][DISARMED] PIN digitato a sistema già disinserito: nessun effetto")
          Behaviors.same

        case SensorTriggered(id, zone, kind) =>
          ctx.log.info("[CTRL][DISARMED] evento ignorato da {} ({}/{})", id, kind, zone)
          Behaviors.same

        case GetStatus(replyTo) =>
          replyTo ! Status("DISARMED", Set.empty)
          Behaviors.same

        case _ =>
          Behaviors.same
    }

  private def exitDelay(
      config: AlarmConfig,
      siren: ActorRef[Siren.Command],
      timers: TimerScheduler[Command],
      pendingZones: Set[String]
  ): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case ExitTimeout =>
          ctx.log.info("[CTRL][EXIT-DELAY] timeout -> ARMED (zone: {})", pendingZones.mkString(","))
          armed(config, siren, timers, pendingZones)

        case PinEntered(pin) if pin == config.correctPin =>
          ctx.log.info("[CTRL][EXIT-DELAY] PIN ok: armamento annullato -> DISARMED")
          timers.cancel(ExitTimerKey)
          disarmed(config, siren, timers)

        case PinEntered(_) =>
          ctx.log.warn("[CTRL][EXIT-DELAY] PIN errato: countdown continua")
          Behaviors.same

        case Arm(_, _) =>
          ctx.log.info("[CTRL][EXIT-DELAY] già in uscita: comando ARM ignorato")
          Behaviors.same

        case SensorTriggered(id, zone, kind) =>
          ctx.log.info("[CTRL][EXIT-DELAY] evento {} ({}/{}) loggato, sensori inattivi", id, kind, zone)
          Behaviors.same

        case GetStatus(replyTo) =>
          replyTo ! Status("EXIT_DELAY", pendingZones)
          Behaviors.same

        case _ => Behaviors.same
    }

  private def armed(
      config: AlarmConfig,
      siren: ActorRef[Siren.Command],
      timers: TimerScheduler[Command],
      activeZones: Set[String]
  ): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case SensorTriggered(id, zone, kind) =>
          if activeZones.contains(zone) then
            ctx.log.warn("[CTRL][ARMED] INTRUSIONE da {} ({}/{}) -> ENTRY-DELAY", id, kind, zone)
            timers.startSingleTimer(EntryTimerKey, EntryTimeout, config.entryDelay)
            entryDelay(config, siren, timers, activeZones, id)
          else
            ctx.log.info("[CTRL][ARMED] evento da {} in zona inattiva {}: ignorato", id, zone)
            Behaviors.same

        case PinEntered(pin) if pin == config.correctPin =>
          ctx.log.info("[CTRL][ARMED] PIN ok -> DISARMED")
          disarmed(config, siren, timers)

        case PinEntered(_) =>
          ctx.log.warn("[CTRL][ARMED] PIN errato")
          Behaviors.same

        case Arm(_, _) =>
          ctx.log.info("[CTRL][ARMED] già inserito: comando ARM ignorato")
          Behaviors.same

        case GetStatus(replyTo) =>
          replyTo ! Status("ARMED", activeZones)
          Behaviors.same

        case _ => Behaviors.same
    }

  private def entryDelay(
      config: AlarmConfig,
      siren: ActorRef[Siren.Command],
      timers: TimerScheduler[Command],
      activeZones: Set[String],
      triggeredBy: String
  ): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case PinEntered(pin) if pin == config.correctPin =>
          ctx.log.info("[CTRL][ENTRY-DELAY] PIN ok in tempo -> DISARMED")
          timers.cancel(EntryTimerKey)
          disarmed(config, siren, timers)

        case PinEntered(_) =>
          ctx.log.warn("[CTRL][ENTRY-DELAY] PIN errato: countdown continua")
          Behaviors.same

        case EntryTimeout =>
          ctx.log.warn("[CTRL][ENTRY-DELAY] timeout (trigger: {}) -> ALARM", triggeredBy)
          siren ! Siren.Activate
          alarm(config, siren, timers, activeZones)

        case SensorTriggered(id, zone, kind) =>
          ctx.log.info("[CTRL][ENTRY-DELAY] ulteriore evento {} ({}/{}): nessun effetto", id, kind, zone)
          Behaviors.same

        case Arm(_, _) =>
          ctx.log.info("[CTRL][ENTRY-DELAY] comando ARM ignorato durante entry-delay")
          Behaviors.same

        case GetStatus(replyTo) =>
          replyTo ! Status("ENTRY_DELAY", activeZones)
          Behaviors.same

        case _ => Behaviors.same
    }

  private def alarm(
      config: AlarmConfig,
      siren: ActorRef[Siren.Command],
      timers: TimerScheduler[Command],
      activeZones: Set[String]
  ): Behavior[Command] =
    Behaviors.receive { (ctx, msg) =>
      msg match
        case PinEntered(pin) if pin == config.correctPin =>
          ctx.log.info("[CTRL][ALARM] PIN ok -> sirena OFF -> DISARMED")
          siren ! Siren.Deactivate
          disarmed(config, siren, timers)

        case PinEntered(_) =>
          ctx.log.warn("[CTRL][ALARM] PIN errato: sirena resta ON")
          Behaviors.same

        case SensorTriggered(id, zone, kind) =>
          ctx.log.info("[CTRL][ALARM] evento {} ({}/{}): allarme già attivo", id, kind, zone)
          Behaviors.same

        case Arm(_, _) =>
          ctx.log.info("[CTRL][ALARM] disinserire col PIN prima di ri-armare")
          Behaviors.same

        case GetStatus(replyTo) =>
          replyTo ! Status("ALARM", activeZones)
          Behaviors.same

        case _ => Behaviors.same
    }
