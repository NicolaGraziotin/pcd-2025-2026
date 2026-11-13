package ass04

import org.apache.pekko.actor.typed.{Behavior}
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.cluster.ClusterEvent.*
import org.apache.pekko.cluster.typed.{Cluster, Subscribe}

/** Logs cluster membership changes so the distributed behaviour is visible in
  * `docker compose logs`.
  */
object ClusterListener:

  enum Event:
    case ReachabilityChange(event: ReachabilityEvent)
    case MemberChange(event: MemberEvent)

  export Event.*

  def apply(): Behavior[Event] =
    Behaviors.setup { ctx =>
      val memberEventAdapter = ctx.messageAdapter(MemberChange.apply)
      val cluster = Cluster(ctx.system)
      cluster.subscriptions ! Subscribe(memberEventAdapter, classOf[MemberEvent])

      val reachabilityEventAdapter = ctx.messageAdapter(ReachabilityChange.apply)
      cluster.subscriptions ! Subscribe(reachabilityEventAdapter, classOf[ReachabilityEvent])

      ctx.log.info(
        "[CLUSTER] listener attivo su {} con ruoli [{}]",
        cluster.selfMember.address,
        cluster.selfMember.roles.mkString(", ")
      )

      Behaviors.receiveMessage { message =>
        message match
          case ReachabilityChange(event) =>
            event match
              case UnreachableMember(member) =>
                ctx.log.warn("[CLUSTER] membro non raggiungibile: {}", member.address)
              case ReachableMember(member) =>
                ctx.log.info("[CLUSTER] membro nuovamente raggiungibile: {}", member.address)

          case MemberChange(event) =>
            event match
              case MemberJoined(member) =>
                ctx.log.info("[CLUSTER] membro entrato: {}", member.address)
              case MemberUp(member) =>
                ctx.log.info("[CLUSTER] membro UP: {} ruoli=[{}]", member.address, member.roles.mkString(", "))
              case MemberRemoved(member, previousStatus) =>
                ctx.log.warn("[CLUSTER] membro rimosso: {} (era {})", member.address, previousStatus)
              case MemberExited(member) =>
                ctx.log.info("[CLUSTER] membro uscito: {}", member.address)
              case MemberDowned(member) =>
                ctx.log.warn("[CLUSTER] membro down: {}", member.address)
              case other =>
                ctx.log.info("[CLUSTER] member event: {}", other)

        Behaviors.same
      }
    }
