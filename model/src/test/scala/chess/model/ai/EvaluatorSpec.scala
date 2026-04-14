package chess.model.ai

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import chess.model.{Game, Board, Color, Piece, Position, GameStatus}

class EvaluatorSpec extends AnyWordSpec with Matchers {

  "Evaluator.evaluate" should {

    "return approximately 0 for the symmetric starting position" in {
      val score = Evaluator.evaluate(Board.initial)
      score should be >= -50
      score should be <= 50
    }

    "return a large positive score when Black is missing a queen" in {
      val board = Board.initial.clear(Position(7, 3)) // remove Black queen at d8
      val score = Evaluator.evaluate(board)
      score should be > 800
    }

    "return a large negative score when White is missing a queen" in {
      val board = Board.initial.clear(Position(0, 3)) // remove White queen at d1
      val score = Evaluator.evaluate(board)
      score should be < -800
    }

    "score White rook advantage as positive" in {
      val board = Board.initial.clear(Position(7, 0)) // remove Black rook at a8
      val score = Evaluator.evaluate(board)
      score should be > 400
    }

    "give bishop pair bonus to the side with two bishops" in {
      // Remove one black bishop from the initial position
      val board = Board.initial.clear(Position(7, 2)) // remove Black bishop at c8
      val scoreOneBishop = Evaluator.evaluate(board)
      val boardBothBishops = Board.initial.clear(Position(0, 2)) // remove White bishop
      val scoreBothSides = Evaluator.evaluate(boardBothBishops)
      // White still has both bishops, so bishop pair bonus should be applied
      scoreOneBishop should be > scoreBothSides
    }

    "return consistent scores for symmetric positions" in {
      // A position where both sides have equal material
      val score = Evaluator.evaluate(Board.initial)
      // Score should be nearly symmetric (within PST rounding)
      math.abs(score) should be < 50
    }
  }

  "Evaluator.pieceValue" should {

    "return correct values for each piece type" in {
      Evaluator.pieceValue(Piece.Queen(Color.White))  shouldBe 900
      Evaluator.pieceValue(Piece.Rook(Color.White))   shouldBe 500
      Evaluator.pieceValue(Piece.Bishop(Color.White)) shouldBe 330
      Evaluator.pieceValue(Piece.Knight(Color.White)) shouldBe 320
      Evaluator.pieceValue(Piece.Pawn(Color.White))   shouldBe 100
      Evaluator.pieceValue(Piece.King(Color.White))   shouldBe 20000
    }

    "return the same values for Black pieces" in {
      Evaluator.pieceValue(Piece.Queen(Color.Black))  shouldBe 900
      Evaluator.pieceValue(Piece.Rook(Color.Black))   shouldBe 500
      Evaluator.pieceValue(Piece.Bishop(Color.Black)) shouldBe 330
    }
  }
}
