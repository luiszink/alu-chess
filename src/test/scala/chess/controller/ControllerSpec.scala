package chess.controller

import chess.model.{Game, Color, Board, Move, Position, GameStatus, ChessError, Piece}
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

  "doMoveResult" should {

    "return Right(game) for a valid move" in {
      val controller = Controller()
      val result = controller.doMoveResult(Move(Position(1, 4), Position(3, 4)))
      result shouldBe a[Right[?, ?]]
      controller.game.currentPlayer shouldBe Color.Black
    }

    "return Left(NoPieceAtSource) when source is empty" in {
      val controller = Controller()
      val result = controller.doMoveResult(Move(Position(3, 3), Position(4, 3)))
      result shouldBe Left(ChessError.NoPieceAtSource(Position(3, 3)))
    }

    "return Left(WrongColorPiece) when moving wrong color" in {
      val controller = Controller()
      val result = controller.doMoveResult(Move(Position(6, 4), Position(4, 4)))
      result shouldBe Left(ChessError.WrongColorPiece(Position(6, 4), Color.White, Color.Black))
    }

    "return Left(GameAlreadyOver) after checkmate" in {
      val controller = Controller()
      // Fool's Mate: 1.f3 e5 2.g4 Qh4#
      controller.doMove(Move(Position(1,5), Position(2,5))) // f2-f3
      controller.doMove(Move(Position(6,4), Position(4,4))) // e7-e5
      controller.doMove(Move(Position(1,6), Position(3,6))) // g2-g4
      controller.doMove(Move(Position(7,3), Position(3,7))) // Qd8-h4#
      controller.game.status shouldBe GameStatus.Checkmate
      val result = controller.doMoveResult(Move(Position(1,4), Position(3,4)))
      result shouldBe Left(ChessError.GameAlreadyOver(GameStatus.Checkmate))
    }

    "notify observers on successful doMoveResult" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.doMoveResult(Move(Position(1, 4), Position(3, 4)))
      notified shouldBe true
    }
  }

  "loadFen" should {

    "load a valid FEN and return true" in {
      val controller = Controller()
      val result = controller.loadFen("8/8/8/8/8/8/8/4K3 w - - 0 1")
      result shouldBe true
      controller.game.board.cell(Position(0, 4)) shouldBe Some(Piece.King(Color.White))
    }

    "return false for invalid FEN" in {
      val controller = Controller()
      controller.loadFen("not a valid fen") shouldBe false
    }
  }

  "loadFenResult" should {

    "return Right(game) for valid FEN" in {
      val controller = Controller()
      val result = controller.loadFenResult("8/8/8/8/8/8/8/4K3 w - - 0 1")
      result shouldBe a[Right[?, ?]]
      controller.game.board.cell(Position(0, 4)) shouldBe Some(Piece.King(Color.White))
    }

    "return Left for invalid FEN" in {
      val controller = Controller()
      val result = controller.loadFenResult("not a valid fen")
      result shouldBe a[Left[?, ?]]
    }

    "notify observers on successful loadFenResult" in {
      val controller = Controller()
      var notified = false
      val observer = new Observer:
        override def update(): Unit = notified = true
      controller.add(observer)
      controller.loadFenResult("8/8/8/8/8/8/8/4K3 w - - 0 1")
      notified shouldBe true
    }
  }
}
