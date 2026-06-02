package chess.model.ai

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import chess.model.{Move, Position}

class TranspositionTableSpec extends AnyWordSpec with Matchers {

  private def move(fromR: Int, fromC: Int, toR: Int, toC: Int): Move =
    Move(Position(fromR, fromC), Position(toR, toC))

  "TranspositionTable" should {

    "roundtrip an entry via store + probe" in {
      val tt = TranspositionTable(sizeMb = 1)
      val m  = move(1, 0, 3, 0)
      tt.store(0xDEADBEEFL, depth = 4, score = 42, TranspositionTable.Bound.Exact, Some(m))
      val e  = tt.probe(0xDEADBEEFL)
      e shouldBe defined
      e.get.depth shouldBe 4
      e.get.score shouldBe 42
      e.get.bound shouldBe TranspositionTable.Bound.Exact
      e.get.move  shouldBe Some(m)
    }

    "return None for an unknown key" in {
      val tt = TranspositionTable(sizeMb = 1)
      tt.probe(0xAAAAL) shouldBe None
    }

    "distinguish entries with different keys that collide in the same slot" in {
      val tt   = TranspositionTable(sizeMb = 1)
      val slot = tt.size
      val k1   = 1L
      val k2   = 1L + slot.toLong      // guaranteed same slot under power-of-two masking
      tt.store(k1, depth = 3, score = 10, TranspositionTable.Bound.Exact, None)
      tt.store(k2, depth = 3, score = 20, TranspositionTable.Bound.Exact, None)
      // Second store replaces the first in that slot; probing k1 must yield None
      tt.probe(k1) shouldBe None
      tt.probe(k2).map(_.score) shouldBe Some(20)
    }

    "clear empties all slots" in {
      val tt = TranspositionTable(sizeMb = 1)
      tt.store(1L, 1, 1, TranspositionTable.Bound.Exact, None)
      tt.clear()
      tt.probe(1L) shouldBe None
    }

    "size is a power of two" in {
      val tt = TranspositionTable(sizeMb = 2)
      val s  = tt.size
      (s & (s - 1)) shouldBe 0
    }
  }
}
