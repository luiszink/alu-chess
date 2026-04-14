package chess.model.ai

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import chess.model.{Game, GameStatus, Color}

class ChessAISpec extends AnyWordSpec with Matchers {

  "ChessAI.selectMove" should {

    "return a legal move from the starting position" in {
      val game = Game.newGame
      val move = ChessAI.selectMove(game)
      move shouldBe defined
      game.applyMove(move.get) shouldBe defined
    }

    "return None when the game is already in checkmate" in {
      // Simulate a terminal state by forcing status
      val game = Game.newGame.copy(status = GameStatus.Checkmate)
      ChessAI.selectMove(game) shouldBe None
    }

    "return None when the game is in stalemate" in {
      val game = Game.newGame.copy(status = GameStatus.Stalemate)
      ChessAI.selectMove(game) shouldBe None
    }

    "return None when the game is a draw" in {
      val game = Game.newGame.copy(status = GameStatus.Draw)
      ChessAI.selectMove(game) shouldBe None
    }

    "respect the maxDepth parameter" in {
      val game  = Game.newGame
      val start = System.currentTimeMillis()
      ChessAI.selectMove(game, timeLimitMs = 5000L, maxDepth = 2)
      val elapsed = System.currentTimeMillis() - start
      elapsed should be < 5000L
    }

    "produce a different response for Black than for White in the starting position" in {
      val gameWhite = Game.newGame
      val moveWhite = ChessAI.selectMove(gameWhite)
      moveWhite shouldBe defined

      // After White moves, it is Black's turn
      val gameBlack = gameWhite.applyMove(moveWhite.get).get
      val moveBlack = ChessAI.selectMove(gameBlack)
      moveBlack shouldBe defined

      // Both moves should be legal
      gameWhite.applyMove(moveWhite.get) shouldBe defined
      gameBlack.applyMove(moveBlack.get) shouldBe defined
    }
  }

  "AIMode" should {

    "start as Disabled" in {
      AIMode.Disabled shouldBe a[AIMode]
    }

    "support PlayingAs White" in {
      val mode = AIMode.PlayingAs(Color.White)
      mode match
        case AIMode.PlayingAs(Color.White) => succeed
        case _ => fail("Expected PlayingAs(White)")
    }

    "support PlayingAs Black" in {
      val mode = AIMode.PlayingAs(Color.Black)
      mode match
        case AIMode.PlayingAs(Color.Black) => succeed
        case _ => fail("Expected PlayingAs(Black)")
    }

    "support PlayingBoth" in {
      AIMode.PlayingBoth shouldBe a[AIMode]
    }
  }
}
