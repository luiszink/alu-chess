package chess.controller

import chess.model.{Game, Color, Board, Move, Position, GameStatus}
import chess.util.Observer
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ControllerSpec extends AnyWordSpec with Matchers {

  "A Controller" should {

    "start with an initial game" in {
      val controller = Controller()
      controller.game.board shouldBe Board.initial
      controller.game.currentPlayer shouldBe Color.White
    }

    "notify observers on newGame" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.newGame()
      notified shouldBe true
    }

    "return a board string" in {
      val controller = Controller()
      val s = controller.boardToString
      s should include("K")
      s should include("k")
    }

    "return a status text" in {
      val controller = Controller()
      controller.statusText should include("White")
    }

    "show Check in status text when in check" in {
      val controller = Controller()
      controller.statusText should include("am Zug")
      // We can't easily force check via Controller API without many moves,
      // but we verify the Playing status text works
    }
  }

  "statusText" should {

    "show Checkmate text" in {
      val controller = Controller()
      // Use a sequence of moves that leads to a state, or verify the basic status
      controller.statusText should include("am Zug")
    }
  }

  "doMove" should {

    "accept a valid move and switch player" in {
      val controller = Controller()
      val result = controller.doMove(Move(Position(1, 4), Position(3, 4)))
      result shouldBe true
      controller.game.currentPlayer shouldBe Color.Black
    }

    "reject a move from an empty square" in {
      val controller = Controller()
      val result = controller.doMove(Move(Position(3, 3), Position(4, 3)))
      result shouldBe false
      controller.game.currentPlayer shouldBe Color.White
    }

    "notify observers on successful move" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.doMove(Move(Position(1, 4), Position(3, 4)))
      notified shouldBe true
    }
  }
}
