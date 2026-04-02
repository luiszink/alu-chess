package chess.ai

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import chess.model.{Game, Board, Color, Piece, Position, Move, GameStatus, MoveValidator}

class RandomAISpec extends AnyWordSpec with Matchers {

  private val ai = new RandomAI(seed = Some(42L))

  "RandomAI" should {

    "have the correct name" in {
      ai.name shouldBe "RandomAI"
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
      // Fool's mate: quickest checkmate
      val Right(g1) = Game.newGame.applyMoveE(Move(Position(1, 5), Position(2, 5))): @unchecked
      val Right(g2) = g1.applyMoveE(Move(Position(6, 4), Position(4, 4))): @unchecked
      val Right(g3) = g2.applyMoveE(Move(Position(1, 6), Position(3, 6))): @unchecked
      val Right(g4) = g3.applyMoveE(Move(Position(7, 3), Position(3, 7))): @unchecked
      g4.status shouldBe GameStatus.Checkmate
      ai.selectMove(g4) shouldBe None
    }

    "produce deterministic results with a fixed seed" in {
      val ai1 = new RandomAI(seed = Some(99L))
      val ai2 = new RandomAI(seed = Some(99L))
      val game = Game.newGame
      ai1.selectMove(game) shouldBe ai2.selectMove(game)
    }

    "produce different results with different seeds (statistically)" in {
      // Run many games and check the AI actually produces variation
      val game = Game.newGame
      val legalMoves = MoveValidator.legalMoves(game.board, game.currentPlayer)
      // With 20 legal moves, two different seeds are very unlikely to always pick the same move
      val results = (1 to 10).map(s => new RandomAI(seed = Some(s.toLong)).selectMove(game)).toSet
      results.size should be > 1
    }

    "always return a legal move across multiple plies" in {
      val aiWhite = new RandomAI(seed = Some(1L))
      val aiBlack = new RandomAI(seed = Some(2L))

      def playPly(game: Game, pliesLeft: Int): Unit =
        if pliesLeft == 0 || game.status.isTerminal then ()
        else
          val current = if game.currentPlayer == Color.White then aiWhite else aiBlack
          val move = current.selectMove(game)
          move shouldBe defined
          val legalMoves = MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove)
          legalMoves should contain(move.get)
          playPly(game.applyMove(move.get).getOrElse(game), pliesLeft - 1)

      playPly(Game.newGame, 50)
    }
  }
}
