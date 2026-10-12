package ass03

import scala.concurrent.duration.*

/** Alarm configuration: PIN, exit/entry delays and known zones. */
final case class AlarmConfig(
    correctPin: String = "1234",
    exitDelay: FiniteDuration = 10.seconds,
    entryDelay: FiniteDuration = 10.seconds,
    allZones: Set[String] = Set("perimeter", "living", "sleeping")
)

object AlarmConfig:
  def demo(): AlarmConfig =
    AlarmConfig(correctPin = "1234", exitDelay = 5.seconds, entryDelay = 5.seconds)

  def realistic(): AlarmConfig =
    AlarmConfig(correctPin = "1234", exitDelay = 30.seconds, entryDelay = 20.seconds)
