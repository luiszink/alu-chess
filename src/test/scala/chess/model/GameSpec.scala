package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class GameSpec extends AnyWordSpec with Matchers {

  "A new Game" should {

    val game = Game.newGame

    "start with white as current player" in {
      game.currentPlayer shouldBe Color.White
    }

    "have an initial board" in {
      game.board shouldBe Board.initial
    }

    "have status Playing" in {
      game.status shouldBe GameStatus.Playing
    }
  }

  "switchPlayer" should {

    "toggle from white to black" in {
      val game = Game.newGame.switchPlayer
      game.currentPlayer shouldBe Color.Black
    }

    "toggle from black back to white" in {
      val game = Game.newGame.switchPlayer.switchPlayer
      game.currentPlayer shouldBe Color.White
    }
  }

  "applyMove" should {

    "move a white pawn from e2 to e4" in {
      val game = Game.newGame
      val move = Move(Position(1, 4), Position(3, 4))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(3, 4)) shouldBe Some(Piece.Pawn(Color.White))
      result.get.board.cell(Position(1, 4)) shouldBe None
      result.get.currentPlayer shouldBe Color.Black
    }

    "reject a move from an empty square" in {
      val game = Game.newGame
      val move = Move(Position(3, 3), Position(4, 3))
      game.applyMove(move) shouldBe None
    }

    "reject a move of the opponent's piece" in {
      val game = Game.newGame
      val move = Move(Position(6, 4), Position(4, 4)) // black pawn, but white's turn
      game.applyMove(move) shouldBe None
    }

    "reject capturing a friendly piece" in {
      // place two white pieces next to each other
      val board = Board.empty
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(0, 1), Piece.Knight(Color.White))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(0, 0), Position(0, 1))
      game.applyMove(move) shouldBe None
    }

    "allow capturing an opponent's piece" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(0, 1), Piece.Knight(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(0, 0), Position(0, 1))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(0, 1)) shouldBe Some(Piece.Rook(Color.White))
    }
  }

  "resign" should {

    "set status to Resigned" in {
      val game = Game.newGame.resign
      game.status shouldBe GameStatus.Resigned
    }
  }
}
