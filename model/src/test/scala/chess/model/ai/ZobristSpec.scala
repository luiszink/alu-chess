package chess.model.ai

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import chess.model.{Fen, Game, Move, Position}

class ZobristSpec extends AnyWordSpec with Matchers {

  private def loadFen(fen: String): Game =
    Fen.parseE(fen) match
      case Right(g)  => g
      case Left(err) => fail(s"Invalid FEN '$fen': ${err.message}")

  "Zobrist.hash" should {

    "be deterministic for the same position" in {
      val g = Game.newGame
      Zobrist.hash(g) shouldBe Zobrist.hash(g)
    }

    "differ between a position and the position after a move" in {
      val g       = Game.newGame
      val after   = g.applyMove(Move(Position(1, 4), Position(3, 4))).get // e2-e4
      Zobrist.hash(g) should not equal Zobrist.hash(after)
    }

    "flip on a null-move (side-to-move change only)" in {
      val g      = Game.newGame
      val nulled = g.copy(currentPlayer = g.currentPlayer.opposite, lastMove = None)
      Zobrist.hash(g) should not equal Zobrist.hash(nulled)
    }

    "reach equal hashes via move transposition" in {
      // Same final position reached via two different move orders
      val g       = Game.newGame
      val pathA   = for
        g1 <- g.applyMove(Move(Position(1, 4), Position(3, 4)))   // e4
        g2 <- g1.applyMove(Move(Position(6, 4), Position(4, 4)))  // e5
        g3 <- g2.applyMove(Move(Position(0, 6), Position(2, 5)))  // Nf3
        g4 <- g3.applyMove(Move(Position(7, 1), Position(5, 2)))  // Nc6
      yield g4
      val pathB   = for
        g1 <- g.applyMove(Move(Position(0, 6), Position(2, 5)))   // Nf3
        g2 <- g1.applyMove(Move(Position(7, 1), Position(5, 2)))  // Nc6
        g3 <- g2.applyMove(Move(Position(1, 4), Position(3, 4)))  // e4
        g4 <- g3.applyMove(Move(Position(6, 4), Position(4, 4)))  // e5
      yield g4
      pathA shouldBe defined
      pathB shouldBe defined
      Zobrist.hash(pathA.get) shouldBe Zobrist.hash(pathB.get)
    }

    "differ when castling rights differ" in {
      val base      = Game.newGame
      // Simulate white king having moved (loses both castling rights) by putting it into movedPieces
      val noCastle  = base.copy(movedPieces = Set(Position(0, 4)))
      Zobrist.hash(base) should not equal Zobrist.hash(noCastle)
    }
  }
}
