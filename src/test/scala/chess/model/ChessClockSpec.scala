package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ChessClockSpec extends AnyWordSpec with Matchers {

  "TimeControl" should {

    "define standard presets" in {
      TimeControl.presets should not be empty
      TimeControl.presets should contain(TimeControl.Bullet1_0)
      TimeControl.presets should contain(TimeControl.Blitz5_0)
      TimeControl.presets should contain(TimeControl.Rapid10_0)
      TimeControl.presets should contain(TimeControl.Classical30_0)
    }

    "have correct values for Bullet1_0" in {
      TimeControl.Bullet1_0.initialTimeMs shouldBe 60_000L
      TimeControl.Bullet1_0.incrementMs shouldBe 0L
      TimeControl.Bullet1_0.name shouldBe "1+0 Bullet"
    }

    "have correct values for Blitz3_2" in {
      TimeControl.Blitz3_2.initialTimeMs shouldBe 180_000L
      TimeControl.Blitz3_2.incrementMs shouldBe 2_000L
    }

    "have correct values for Rapid15_10" in {
      TimeControl.Rapid15_10.initialTimeMs shouldBe 900_000L
      TimeControl.Rapid15_10.incrementMs shouldBe 10_000L
    }
  }

  "ChessClock.fromTimeControl" should {

    "create a clock with correct initial values" in {
      val clock = ChessClock.fromTimeControl(TimeControl.Blitz5_0)
      clock.whiteTimeMs shouldBe 300_000L
      clock.blackTimeMs shouldBe 300_000L
      clock.incrementMs shouldBe 0L
      clock.activeColor shouldBe None
    }

    "create a clock with increment" in {
      val clock = ChessClock.fromTimeControl(TimeControl.Blitz3_2)
      clock.whiteTimeMs shouldBe 180_000L
      clock.blackTimeMs shouldBe 180_000L
      clock.incrementMs shouldBe 2_000L
    }
  }

  "ChessClock.start" should {

    "set activeColor to White and record timestamp" in {
      val clock = ChessClock.fromTimeControl(TimeControl.Blitz5_0)
      val now = 1_000_000_000L
      val started = clock.start(now)
      started.activeColor shouldBe Some(Color.White)
      started.lastTickNanos shouldBe now
    }
  }

  "ChessClock.tick" should {

    "reduce white time when white is active" in {
      val clock = ChessClock(300_000L, 300_000L, 0L, Some(Color.White), 0L)
      val ticked = clock.tick(5_000_000_000L) // 5 seconds later
      ticked.whiteTimeMs shouldBe (300_000L - 5_000L)
      ticked.blackTimeMs shouldBe 300_000L
    }

    "reduce black time when black is active" in {
      val clock = ChessClock(300_000L, 300_000L, 0L, Some(Color.Black), 0L)
      val ticked = clock.tick(3_000_000_000L) // 3 seconds later
      ticked.whiteTimeMs shouldBe 300_000L
      ticked.blackTimeMs shouldBe (300_000L - 3_000L)
    }

    "not change time when no player is active" in {
      val clock = ChessClock(300_000L, 200_000L, 0L, None, 0L)
      val ticked = clock.tick(5_000_000_000L)
      ticked.whiteTimeMs shouldBe 300_000L
      ticked.blackTimeMs shouldBe 200_000L
    }
  }

  "ChessClock.press" should {

    "switch from white to black and add increment" in {
      val clock = ChessClock(300_000L, 300_000L, 2_000L, Some(Color.White), 0L)
      val pressed = clock.press(1_000_000_000L) // 1 second elapsed
      pressed.activeColor shouldBe Some(Color.Black)
      pressed.whiteTimeMs shouldBe (300_000L - 1_000L + 2_000L)
      pressed.blackTimeMs shouldBe 300_000L
    }

    "switch from black to white and add increment" in {
      val clock = ChessClock(300_000L, 300_000L, 2_000L, Some(Color.Black), 0L)
      val pressed = clock.press(2_000_000_000L) // 2 seconds elapsed
      pressed.activeColor shouldBe Some(Color.White)
      pressed.whiteTimeMs shouldBe 300_000L
      pressed.blackTimeMs shouldBe (300_000L - 2_000L + 2_000L)
    }

    "do nothing when no player is active" in {
      val clock = ChessClock(300_000L, 300_000L, 2_000L, None, 0L)
      val pressed = clock.press(1_000_000_000L)
      pressed.activeColor shouldBe None
      pressed.whiteTimeMs shouldBe 300_000L
      pressed.blackTimeMs shouldBe 300_000L
    }
  }

  "ChessClock.stop" should {

    "deactivate the clock" in {
      val clock = ChessClock(300_000L, 300_000L, 0L, Some(Color.White), System.nanoTime())
      val stopped = clock.stop
      stopped.activeColor shouldBe None
    }

    "not fail when already stopped" in {
      val clock = ChessClock(300_000L, 300_000L, 0L, None, 0L)
      val stopped = clock.stop
      stopped.activeColor shouldBe None
    }
  }

  "ChessClock.expired" should {

    "return Some(White) when white time is zero" in {
      val clock = ChessClock(0L, 300_000L, 0L)
      clock.expired shouldBe Some(Color.White)
    }

    "return Some(White) when white time is negative" in {
      val clock = ChessClock(-100L, 300_000L, 0L)
      clock.expired shouldBe Some(Color.White)
    }

    "return Some(Black) when black time is zero" in {
      val clock = ChessClock(300_000L, 0L, 0L)
      clock.expired shouldBe Some(Color.Black)
    }

    "return None when both players have time" in {
      val clock = ChessClock(300_000L, 300_000L, 0L)
      clock.expired shouldBe None
    }
  }

  "ChessClock.remainingMs" should {

    "return white time clamped to 0" in {
      val clock = ChessClock(-100L, 300_000L, 0L)
      clock.remainingMs(Color.White) shouldBe 0L
    }

    "return black time clamped to 0" in {
      val clock = ChessClock(300_000L, -50L, 0L)
      clock.remainingMs(Color.Black) shouldBe 0L
    }

    "return actual time when positive" in {
      val clock = ChessClock(123_456L, 654_321L, 0L)
      clock.remainingMs(Color.White) shouldBe 123_456L
      clock.remainingMs(Color.Black) shouldBe 654_321L
    }
  }

  "ChessClock.formatTime" should {

    "show mm:ss format for time >= 20s" in {
      ChessClock.formatTime(300_000L) shouldBe "05:00"
      ChessClock.formatTime(60_000L) shouldBe "01:00"
      ChessClock.formatTime(20_000L) shouldBe "00:20"
    }

    "show m:ss.t format for time < 20s" in {
      ChessClock.formatTime(19_500L) shouldBe "0:19.5"
      ChessClock.formatTime(9_300L) shouldBe "0:09.3"
      ChessClock.formatTime(1_000L) shouldBe "0:01.0"
    }

    "handle zero" in {
      ChessClock.formatTime(0L) shouldBe "0:00.0"
    }

    "handle negative values by clamping to 0" in {
      ChessClock.formatTime(-500L) shouldBe "0:00.0"
    }
  }
}
