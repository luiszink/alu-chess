package chess.model.ai

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import chess.model.{Game, GameStatus, Fen}

class AlphaBetaSpec extends AnyWordSpec with Matchers {

  // Helper: load a FEN string or fail the test
  private def loadFen(fen: String): Game =
    Fen.parseE(fen) match
      case Right(game) => game
      case Left(err)   => fail(s"Invalid FEN '$fen': ${err.message}")

  "AlphaBeta.bestMove" should {

    "return None for a terminal (checkmate) position" in {
      // Fool's mate: Black is checkmated
      val fen  = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
      val game = loadFen(fen)
      // White is in check-mate (status = Checkmate), so bestMove returns None
      AlphaBeta.bestMove(game, timeLimitMs = 2000L) shouldBe None
    }

    "find mate in 1 — Scholar's Mate position (White to deliver mate)" in {
      // White has Qf3 and Bc4; can play Qxf7#
      val fen  = "r1bqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4"
      val game = loadFen(fen)
      // Black is already in checkmate after Qxf7#
      game.status shouldBe GameStatus.Checkmate
    }

    "find mate in 1 — back-rank mate" in {
      // Black king h8, White king g6 (covers g7/g8/h7), White rook a1.
      // Ra1-a8# — rook checks on rank 8, all king escapes are covered by Kg6.
      val fen  = "7k/8/6K1/8/8/8/8/R7 w - - 0 1"
      val game = loadFen(fen)
      val move = AlphaBeta.bestMove(game, timeLimitMs = 3000L, maxDepth = 3)
      move shouldBe defined
      val next = game.applyMove(move.get)
      next shouldBe defined
      next.get.status shouldBe GameStatus.Checkmate
    }

    "find mate in 1 — queen delivers checkmate" in {
      // Black king f8, White king e6 (covers e7/f7), White queen h5.
      // Qh5-h8# — queen checks on rank 8, king has no escape (g7/g8 covered by queen, e7/f7 by king).
      val fen  = "5k2/8/4K3/7Q/8/8/8/8 w - - 0 1"
      val game = loadFen(fen)
      val move = AlphaBeta.bestMove(game, timeLimitMs = 3000L, maxDepth = 3)
      move shouldBe defined
      val next = game.applyMove(move.get)
      next shouldBe defined
      next.get.status shouldBe GameStatus.Checkmate
    }

    "capture a hanging queen rather than making a quiet move" in {
      // White knight on c3 can take Black queen on d5 (undefended)
      val fen  = "rnb1kbnr/pppppppp/8/3q4/8/2N5/PPPPPPPP/R1BQKBNR w KQkq - 0 1"
      val game = loadFen(fen)
      val move = AlphaBeta.bestMove(game, timeLimitMs = 3000L, maxDepth = 4)
      move shouldBe defined
      // The best move should capture the queen on d5
      import chess.model.Position
      move.get.to shouldBe Position(4, 3) // d5 = row 4, col 3
    }

    "return a legal move from the starting position" in {
      val game = Game.newGame
      val move = AlphaBeta.bestMove(game, timeLimitMs = 3000L, maxDepth = 4)
      move shouldBe defined
      // Verify the move is legal
      val next = game.applyMove(move.get)
      next shouldBe defined
    }

    "complete within the specified time limit" in {
      val game     = Game.newGame
      val start    = System.currentTimeMillis()
      AlphaBeta.bestMove(game, timeLimitMs = 1000L, maxDepth = 6)
      val elapsed  = System.currentTimeMillis() - start
      elapsed should be < 3000L // allow 2x buffer
    }

    "avoid stalemate when a winning continuation exists" in {
      // White has overwhelming material; must not play stalemate
      // White Q+K vs lone Black king — queen can easily force checkmate
      val fen  = "8/8/8/8/8/3k4/8/3KQ3 w - - 0 1"
      val game = loadFen(fen)
      val move = AlphaBeta.bestMove(game, timeLimitMs = 3000L, maxDepth = 5)
      move shouldBe defined
      val next = game.applyMove(move.get).get
      next.status should not be GameStatus.Stalemate
    }
  }
}
