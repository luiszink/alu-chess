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
      val board = Board.empty
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(0, 1), Piece.Knight(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(0, 0), Position(0, 1))
      game.applyMove(move) shouldBe None
    }

    "allow capturing an opponent's piece" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(0, 1), Piece.Knight(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(0, 0), Position(0, 1))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(0, 1)) shouldBe Some(Piece.Rook(Color.White))
    }

    "reject a move that leaves own king in check" in {
      // White rook shields white king from black rook. Moving rook away exposes king.
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 3), Piece.Rook(Color.White))  // shields king on file
        .put(Position(0, 0), Piece.Rook(Color.Black))  // attacks along row 0
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      // move white rook off the row -> exposes king to black rook
      val move = Move(Position(0, 3), Position(3, 3))
      game.applyMove(move) shouldBe None
    }

    "set status to Check when opponent king is in check" in {
      // After white's move, black king will be in check
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(1, 0), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      // Move rook to 7th rank to give check
      val move = Move(Position(1, 0), Position(7, 0))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.status shouldBe GameStatus.Check
    }

    "set status to Checkmate when opponent has no legal moves" in {
      // Back-rank mate: black king at g8 boxed in by own pawns
      // White rook on a7 will move to a8, delivering mate
      val board = Board.empty
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(6, 0), Piece.Rook(Color.White))  // a7 -> moves to a8
        .put(Position(7, 6), Piece.King(Color.Black))   // g8
        .put(Position(6, 5), Piece.Pawn(Color.Black))   // f7
        .put(Position(6, 6), Piece.Pawn(Color.Black))   // g7
        .put(Position(6, 7), Piece.Pawn(Color.Black))   // h7
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(6, 0), Position(7, 0)) // Ra7 -> Ra8#
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.status shouldBe GameStatus.Checkmate
    }

    "set status to Stalemate when opponent has no legal moves but is not in check" in {
      // K vs K+Q, black king cornered but not in check, no legal moves
      val board = Board.empty
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(5, 1), Piece.Queen(Color.White))
        .put(Position(7, 0), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      // Move queen to b6 to create stalemate: black king on a8, queen on b6
      // King on a8, queen on b6 blocks a7,b7,b8 and king covers a1
      // Actually: King at a8 (7,0), Queen at b6 (5,1) -> 
      // a7(6,0) attacked by queen, b8(7,1) attacked by queen, b7(6,1) attacked by queen
      // That's stalemate if black is not in check
      val move = Move(Position(5, 1), Position(5, 1))  // placeholder
      // Let me construct precise stalemate:
      // Black king at a8 (7,0). White queen needs to control a7,b8,b7 without checking.
      // Queen at c6 (5,2): controls a8? No. Let me think differently.
      // Classic stalemate: Black K at h8 (7,7), White K at f7 (6,5), White Q at g5 (4,6)
      // Then Qg6 is stalemate: K can't go to g8(blocked by Q diagonal), h7 (blocked by K+Q)
      val board2 = Board.empty
        .put(Position(6, 5), Piece.King(Color.White))   // f7
        .put(Position(4, 6), Piece.Queen(Color.White))  // g5
        .put(Position(7, 7), Piece.King(Color.Black))   // h8
      val game2 = Game(board2, Color.White, GameStatus.Playing)
      // Qg5 -> Qg6: queen moves to (5,6)
      val move2 = Move(Position(4, 6), Position(5, 6)) // g5 -> g6
      val result2 = game2.applyMove(move2)
      result2 shouldBe defined
      result2.get.status shouldBe GameStatus.Stalemate
    }
  }

  "resign" should {

    "set status to Resigned" in {
      val game = Game.newGame.resign
      game.status shouldBe GameStatus.Resigned
    }
  }
}
