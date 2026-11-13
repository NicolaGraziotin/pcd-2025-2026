package ass04

import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.*

/** Per-node settings, read from environment variables so the same image can
  * be started as a seed/controller, sensor or keypad node.
  *
  * Supported variables:
  *   - CLUSTER_HOST    hostname advertised by Artery (Docker service name)
  *   - CLUSTER_PORT    Artery port (default 25520)
  *   - CLUSTER_ROLES   comma-separated roles: controller,sensor,keypad
  *   - SEED_HOST       hostname of the seed node (default: CLUSTER_HOST)
  *   - SEED_PORT       port of the seed node (default: CLUSTER_PORT)
  *   - ALARM_PIN       PIN (default 1234)
  *   - EXIT_DELAY      exit delay in seconds (default 5)
  *   - ENTRY_DELAY     entry delay in seconds (default 5)
  */
final case class NodeSettings(
    systemName: String,
    host: String,
    port: Int,
    seeds: Seq[(String, Int)],
    roles: Set[String],
    alarmConfig: AlarmConfig
):

  def config: Config =
    val rolesConfig = roles.toSeq.sorted.map(role => s""""$role"""").mkString("[", ",", "]")
    val seedNodes = seeds
      .map { case (seedHost, seedPort) => s""""pekko://$systemName@$seedHost:$seedPort"""" }
      .mkString("[", ",", "]")

    ConfigFactory
      .parseString(s"""
        pekko.remote.artery.canonical.hostname = "$host"
        pekko.remote.artery.canonical.port = $port
        pekko.cluster.roles = $rolesConfig
        pekko.cluster.seed-nodes = $seedNodes
      """)
      .withFallback(ConfigFactory.load())

object NodeSettings:

  val AllRoles: Set[String] = Set("controller", "sensor", "keypad")

  def fromEnv(env: Map[String, String] = sys.env): NodeSettings =
    val host = env.getOrElse("CLUSTER_HOST", "127.0.0.1")
    val port = env.get("CLUSTER_PORT").flatMap(_.toIntOption).getOrElse(25520)
    val seedHost = env.getOrElse("SEED_HOST", host)
    val seedPort = env.get("SEED_PORT").flatMap(_.toIntOption).getOrElse(port)

    val roles = env
      .get("CLUSTER_ROLES")
      .map(parseRoles)
      .filter(_.nonEmpty)
      .getOrElse(AllRoles)

    val unknownRoles = roles.diff(AllRoles)
    require(
      unknownRoles.isEmpty,
      s"Ruoli sconosciuti in CLUSTER_ROLES: ${unknownRoles.mkString(", ")}. Validi: ${AllRoles.mkString(", ")}"
    )

    val alarmConfig = AlarmConfig(
      correctPin = env.getOrElse("ALARM_PIN", "1234"),
      exitDelay = env.get("EXIT_DELAY").flatMap(_.toIntOption).map(_.seconds).getOrElse(5.seconds),
      entryDelay = env.get("ENTRY_DELAY").flatMap(_.toIntOption).map(_.seconds).getOrElse(5.seconds)
    )

    NodeSettings(
      systemName = "smart-home-alarm",
      host = host,
      port = port,
      seeds = Seq(seedHost -> seedPort),
      roles = roles,
      alarmConfig = alarmConfig
    )

  private def parseRoles(value: String): Set[String] =
    value.split(",").iterator.map(_.trim.toLowerCase).filter(_.nonEmpty).toSet
