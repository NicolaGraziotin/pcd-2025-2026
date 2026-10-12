package ass03

import org.apache.pekko.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.apache.pekko.actor.typed.ActorRef
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.*

/** ActorTestKit checks for every FSM transition, including the zone bonus. */
class AlarmControllerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike:

  private def spawnController(exit: FiniteDuration = 300.millis, entry: FiniteDuration = 300.millis): ActorRef[AlarmController.Command] =
    val sirenStub: ActorRef[Siren.Command] = spawn(Siren())
    val config = AlarmConfig("1234", exit, entry, Set("perimeter", "living", "sleeping"))
    spawn(AlarmController(config, sirenStub))

  private def statusOf(ctrl: ActorRef[AlarmController.Command]): AlarmController.Status =
    val p = createTestProbe[AlarmController.Status]()
    ctrl ! AlarmController.GetStatus(p.ref)
    p.receiveMessage()

  "AlarmController" should {

    "ignorare i sensori quando disarmed" in {
      val ctrl = spawnController()
      ctrl ! AlarmController.SensorTriggered("motion-living", "living", "motion")
      statusOf(ctrl).state shouldBe "DISARMED"
    }

    "rifiutare ARM con PIN errato" in {
      val ctrl = spawnController()
      ctrl ! AlarmController.Arm("0000", Set.empty)
      statusOf(ctrl).state shouldBe "DISARMED"
    }

    "andare ARMED dopo exit-delay" in {
      val ctrl = spawnController()
      ctrl ! AlarmController.Arm("1234", Set.empty)
      statusOf(ctrl).state shouldBe "EXIT_DELAY"
      Thread.sleep(600)
      statusOf(ctrl).state shouldBe "ARMED"
    }

    "annullare exit-delay con PIN corretto" in {
      val ctrl = spawnController(exit = 2.seconds)
      ctrl ! AlarmController.Arm("1234", Set.empty)
      statusOf(ctrl).state shouldBe "EXIT_DELAY"
      ctrl ! AlarmController.PinEntered("1234")
      statusOf(ctrl).state shouldBe "DISARMED"
    }

    "andare in ENTRY_DELAY su intrusione a sistema armed" in {
      val ctrl = spawnController()
      ctrl ! AlarmController.Arm("1234", Set.empty)
      Thread.sleep(600)
      ctrl ! AlarmController.SensorTriggered("door-entrance", "perimeter", "door")
      statusOf(ctrl).state shouldBe "ENTRY_DELAY"
    }

    "tornare DISARMED con PIN in tempo durante entry-delay" in {
      val ctrl = spawnController(entry = 2.seconds)
      ctrl ! AlarmController.Arm("1234", Set.empty)
      Thread.sleep(600)
      ctrl ! AlarmController.SensorTriggered("door-entrance", "perimeter", "door")
      ctrl ! AlarmController.PinEntered("1234")
      statusOf(ctrl).state shouldBe "DISARMED"
    }

    "andare in ALARM allo scadere dell'entry-delay" in {
      val ctrl = spawnController()
      ctrl ! AlarmController.Arm("1234", Set.empty)
      Thread.sleep(600)
      ctrl ! AlarmController.SensorTriggered("door-entrance", "perimeter", "door")
      Thread.sleep(600)
      statusOf(ctrl).state shouldBe "ALARM"
    }

    "spegnere ALARM solo con PIN corretto" in {
      val ctrl = spawnController()
      ctrl ! AlarmController.Arm("1234", Set.empty)
      Thread.sleep(600)
      ctrl ! AlarmController.SensorTriggered("door-entrance", "perimeter", "door")
      Thread.sleep(600)
      statusOf(ctrl).state shouldBe "ALARM"
      ctrl ! AlarmController.PinEntered("0000")
      statusOf(ctrl).state shouldBe "ALARM"
      ctrl ! AlarmController.PinEntered("1234")
      statusOf(ctrl).state shouldBe "DISARMED"
    }

    "ignorare zone inattive in arming parziale (bonus)" in {
      val ctrl = spawnController()
      ctrl ! AlarmController.Arm("1234", Set("perimeter"))
      Thread.sleep(600)
      val st = statusOf(ctrl)
      st.state shouldBe "ARMED"
      st.activeZones shouldBe Set("perimeter")
      ctrl ! AlarmController.SensorTriggered("motion-bedroom", "sleeping", "motion")
      Thread.sleep(200)
      statusOf(ctrl).state shouldBe "ARMED"
      ctrl ! AlarmController.SensorTriggered("door-entrance", "perimeter", "door")
      statusOf(ctrl).state shouldBe "ENTRY_DELAY"
    }
  }
