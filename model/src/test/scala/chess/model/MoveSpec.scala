package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class MoveSpec extends AnyWordSpec with Matchers {

  "A Move" should {

    "display as algebraic notation" in {
      Move(Position(1, 4), Position(3, 4)).toString shouldBe "e2→e4"
    }

    "display with promotion suffix" in {
      Move(Position(6, 4), Position(7, 4), Some('Q')).toString shouldBe "e7→e8=Q"
    }
  }

  "Move.fromString" should {

    "parse 'e2 e4' format" in {
      Move.fromString("e2 e4") shouldBe Some(Move(Position(1, 4), Position(3, 4)))
    }

    "parse 'e2e4' format (no space)" in {
      Move.fromString("e2e4") shouldBe Some(Move(Position(1, 4), Position(3, 4)))
    }

    "be case insensitive" in {
      Move.fromString("E2 E4") shouldBe Some(Move(Position(1, 4), Position(3, 4)))
    }

    "handle extra whitespace" in {
      Move.fromString("  e2   e4  ") shouldBe Some(Move(Position(1, 4), Position(3, 4)))
    }

    "parse promotion format 'e7 e8 Q'" in {
      Move.fromString("e7 e8 Q") shouldBe Some(Move(Position(6, 4), Position(7, 4), Some('Q')))
    }

    "parse promotion case-insensitively" in {
      Move.fromString("e7 e8 n") shouldBe Some(Move(Position(6, 4), Position(7, 4), Some('N')))
    }

    "parse all promotion types" in {
      Move.fromString("a7 a8 R") shouldBe Some(Move(Position(6, 0), Position(7, 0), Some('R')))
      Move.fromString("a7 a8 B") shouldBe Some(Move(Position(6, 0), Position(7, 0), Some('B')))
      Move.fromString("a7 a8 N") shouldBe Some(Move(Position(6, 0), Position(7, 0), Some('N')))
    }

    "reject invalid promotion character" in {
      Move.fromString("e7 e8 X") shouldBe None
    }

    "return None for invalid input" in {
      Move.fromString("") shouldBe None
      Move.fromString("e2") shouldBe None
      Move.fromString("e2 z9") shouldBe None
      Move.fromString("hello world foo") shouldBe None
    }

    "be consistent with fromStringE (property)" in {
      val inputs = Seq("e2 e4", "e2e4", "e7 e8 Q", "e7 e8 X", "", "e2", "z9 a1")
      for s <- inputs do
        Move.fromString(s) shouldBe Move.fromStringE(s).toOption
    }
  }

  "Move.fromStringE" should {

    "return Right for valid 'e2 e4' format" in {
      Move.fromStringE("e2 e4") shouldBe Right(Move(Position(1, 4), Position(3, 4)))
    }

    "return Right for valid 'e2e4' compact format" in {
      Move.fromStringE("e2e4") shouldBe Right(Move(Position(1, 4), Position(3, 4)))
    }

    "return Right for valid promotion 'e7 e8 Q'" in {
      Move.fromStringE("e7 e8 Q") shouldBe Right(Move(Position(6, 4), Position(7, 4), Some('Q')))
    }

    "return Right for promotion case-insensitively" in {
      Move.fromStringE("e7 e8 n") shouldBe Right(Move(Position(6, 4), Position(7, 4), Some('N')))
    }

    "return Left(InvalidMoveFormat) for empty string" in {
      Move.fromStringE("") shouldBe Left(ChessError.InvalidMoveFormat(""))
    }

    "return Left(InvalidMoveFormat) for too many tokens" in {
      Move.fromStringE("hello world foo") shouldBe a[Left[?, ?]]
    }

    "return Left(InvalidPositionString) for invalid from-position" in {
      Move.fromStringE("z9 e4") shouldBe Left(ChessError.InvalidPositionString("z9"))
    }

    "return Left(InvalidPositionString) for invalid to-position" in {
      Move.fromStringE("e2 z9") shouldBe Left(ChessError.InvalidPositionString("z9"))
    }

    "return Left(InvalidPromotionPiece) for invalid promotion character" in {
      Move.fromStringE("e7 e8 X") shouldBe Left(ChessError.InvalidPromotionPiece('X'))
    }
  }

  "Move.fromStringT" should {

    "return Success for valid move" in {
      Move.fromStringT("e2 e4").isSuccess shouldBe true
      Move.fromStringT("e2 e4").get shouldBe Move(Position(1, 4), Position(3, 4))
    }

    "return Failure for invalid input" in {
      Move.fromStringT("").isFailure shouldBe true
      Move.fromStringT("z9 a1").isFailure shouldBe true
    }

    "Failure contains the error message" in {
      val result = Move.fromStringT("z9 a1")
      result.isFailure shouldBe true
      result.failed.get.getMessage should include("z9")
    }
  }
}
