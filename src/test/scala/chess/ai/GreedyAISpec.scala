package chess.ai

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import chess.model.{Game, Board, Color, Piece, Position, Move, GameStatus, MoveValidator}

class GreedyAISpec extends AnyWordSpec with Matchers {

  private val ai = new GreedyAI(seed = Some(42L))

  "GreedyAI" should {

    "have the correct name" in {
      ai.name shouldBe "GreedyAI"
    }

    "return a legal move from the initial position" in {
      val game = Game.newGame
      val move = ai.selectMove(game)
      move shouldBe defined
      val legalMoves = MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove)
      legalMoves should contain(move.get)
    }

    "return None when the game is already over (Resigned)" in {
      val game = Game.newGame.resign
      ai.selectMove(game) shouldBe None
    }

    "return None when the game is already over (Checkmate)" in {
      // Fool's mate
      val Right(g1) = Game.newGame.applyMoveE(Move(Position(1, 5), Position(2, 5))): @unchecked
      val Right(g2) = g1.applyMoveE(Move(Position(6, 4), Position(4, 4))): @unchecked
      val Right(g3) = g2.applyMoveE(Move(Position(1, 6), Position(3, 6))): @unchecked
      val Right(g4) = g3.applyMoveE(Move(Position(7, 3), Position(3, 7))): @unchecked
      g4.status shouldBe GameStatus.Checkmate
      ai.selectMove(g4) shouldBe None
    }

    "capture the highest-value enemy piece when multiple captures are available" in {
      // White rook on a5 (row=4,col=0) can move along column a.
      // Black pawn on a3 (row=2,col=0) – value 1.
      // Black rook on a8 (row=7,col=0) – value 5.
      // Path in both directions is clear; GreedyAI should take the rook.
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))  // white king e1
        .put(Position(7, 4), Piece.King(Color.Black))  // black king e8 (not on col a)
        .put(Position(4, 0), Piece.Rook(Color.White))  // white rook a5
        .put(Position(2, 0), Piece.Pawn(Color.Black))  // black pawn a3 (value 1)
        .put(Position(6, 0), Piece.Rook(Color.Black))  // black rook a7 (value 5)
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = ai.selectMove(game)
      move shouldBe defined
      move.get.to shouldBe Position(6, 0) // captures the rook, not the pawn
    }

    "capture a piece rather than make a non-capture when a capture is available" in {
      // White rook can capture a black pawn; it should prefer that over passing.
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
        .put(Position(4, 0), Piece.Rook(Color.White))
        .put(Position(4, 5), Piece.Pawn(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = ai.selectMove(game)
      move shouldBe defined
      move.get.to shouldBe Position(4, 5)
    }

    "produce deterministic results with a fixed seed" in {
      val ai1 = new GreedyAI(seed = Some(7L))
      val ai2 = new GreedyAI(seed = Some(7L))
      val game = Game.newGame
      ai1.selectMove(game) shouldBe ai2.selectMove(game)
    }

    "always return a legal move across multiple plies" in {
      var game = Game.newGame
      val aiWhite = new GreedyAI(seed = Some(10L))
      val aiBlack = new GreedyAI(seed = Some(20L))
      var plies = 0
      while !game.status.isTerminal && plies < 50 do
        val current = if game.currentPlayer == Color.White then aiWhite else aiBlack
        val move = current.selectMove(game)
        move shouldBe defined
        val legalMoves = MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove)
        legalMoves should contain(move.get)
        game = game.applyMove(move.get).getOrElse(game)
        plies += 1
    }
  }
}
