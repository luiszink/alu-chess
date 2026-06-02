package chess.aview

import chess.controller.{Controller, ControllerInterface}
import chess.model.{Board, Color, GameStatus, Piece, Position}
import chess.model.ai.AIMode
import chess.util.Observer
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import java.io.ByteArrayOutputStream

class TUISpec extends AnyWordSpec with Matchers {

  def captureOutput(block: => Unit): String =
    val baos = new ByteArrayOutputStream()
    scala.Console.withOut(baos)(block)
    baos.toString

  "A TUI" should {

    "process 'q' and return false (stop)" in {
      val controller = Controller()
      val tui = TUI(controller)
      tui.processInput("q") shouldBe false
    }

    "process 'n' and return true (continue)" in {
      val controller = Controller()
      val tui = TUI(controller)
      tui.processInput("n") shouldBe true
    }

    "process empty input and return true (continue)" in {
      val controller = Controller()
      val tui = TUI(controller)
      tui.processInput("") shouldBe true
    }

    "process 'ai both' and enable AI-vs-AI mode" in {
      val controller = Controller()
      val tui = TUI(controller)
      tui.processInput("ai both") shouldBe true
      controller.aiMode shouldBe AIMode.PlayingBoth
    }

    "process 'ai off' after ai both and disable AI mode" in {
      val controller = Controller()
      val tui = TUI(controller)
      tui.processInput("ai both") shouldBe true
      tui.processInput("ai off") shouldBe true
      controller.aiMode shouldBe AIMode.Disabled
    }

    "process a valid move like 'e2 e4'" in {
      val controller = Controller()
      val tui = TUI(controller)
      tui.processInput("e2 e4") shouldBe true
      controller.game.board.cell(Position(3, 4)) shouldBe Some(Piece.Pawn(Color.White))
    }

    "process an invalid move and return true (continue)" in {
      val controller = Controller()
      val tui = TUI(controller)
      tui.processInput("e5 e6") shouldBe true // empty square, move rejected
    }

    "process unparseable input and return true (continue)" in {
      val controller = Controller()
      val tui = TUI(controller)
      tui.processInput("xyz") shouldBe true
    }

    "print error message for unparseable move format" in {
      val controller = Controller()
      val tui = TUI(controller)
      val output = captureOutput { tui.processInput("xyz") }
      output should include("nicht erkannt")
    }

    "print error message for invalid chess move" in {
      val controller = Controller()
      val tui = TUI(controller)
      val output = captureOutput { tui.processInput("e5 e6") }
      output should include("Ungültiger Zug")
    }

    "print 'Spiel ist beendet' when game is over (checkmate) and move is attempted" in {
      val controller = Controller()
      // Fool's Mate: 1.f3 e5 2.g4 Qh4# (fastest checkmate in chess)
      controller.doMove(chess.model.Move(chess.model.Position(1,5), chess.model.Position(2,5))) // f2-f3
      controller.doMove(chess.model.Move(chess.model.Position(6,4), chess.model.Position(4,4))) // e7-e5
      controller.doMove(chess.model.Move(chess.model.Position(1,6), chess.model.Position(3,6))) // g2-g4
      controller.doMove(chess.model.Move(chess.model.Position(7,3), chess.model.Position(3,7))) // Qd8-h4#
      controller.game.status shouldBe chess.model.GameStatus.Checkmate
      val tui = TUI(controller)
      val output = captureOutput { tui.processInput("e2 e4") }
      output should include("beendet")
    }

    "run inputLoop until q is entered" in {
      val controller = Controller()
      val tui = TUI(controller)
      val input = new java.io.ByteArrayInputStream("q\n".getBytes)
      val output = new java.io.ByteArrayOutputStream()
      Console.withIn(input) {
        Console.withOut(output) {
          tui.inputLoop()
        }
      }
      output.toString should include("Auf Wiedersehen!")
    }

    "exit inputLoop when stdin is closed (Failure path)" in {
      val controller = Controller()
      val tui = TUI(controller)
      val brokenInput = new java.io.InputStream:
        override def read(): Int = throw new java.io.IOException("stream closed")
        override def read(b: Array[Byte], off: Int, len: Int): Int =
          throw new java.io.IOException("stream closed")
      Console.withIn(new java.io.BufferedReader(new java.io.InputStreamReader(brokenInput))) {
        Console.withOut(new java.io.ByteArrayOutputStream()) {
          tui.inputLoop() // must exit via Failure branch, not throw
        }
      }
      succeed // reached here means inputLoop handled the failure gracefully
    }
  }
}
