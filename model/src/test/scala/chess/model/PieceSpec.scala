package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class PieceSpec extends AnyWordSpec with Matchers {

  "A Piece" should {

    "have all six types" in {
      val whitePieces = List(
        Piece.King(Color.White),
        Piece.Queen(Color.White),
        Piece.Rook(Color.White),
        Piece.Bishop(Color.White),
        Piece.Knight(Color.White),
        Piece.Pawn(Color.White)
      )
      whitePieces should have size 6
    }

    "display uppercase symbols for white pieces" in {
      Piece.King(Color.White).toString shouldBe "K"
      Piece.Queen(Color.White).toString shouldBe "Q"
      Piece.Rook(Color.White).toString shouldBe "R"
      Piece.Bishop(Color.White).toString shouldBe "B"
      Piece.Knight(Color.White).toString shouldBe "N"
      Piece.Pawn(Color.White).toString shouldBe "P"
    }

    "display lowercase symbols for black pieces" in {
      Piece.King(Color.Black).toString shouldBe "k"
      Piece.Queen(Color.Black).toString shouldBe "q"
      Piece.Rook(Color.Black).toString shouldBe "r"
      Piece.Bishop(Color.Black).toString shouldBe "b"
      Piece.Knight(Color.Black).toString shouldBe "n"
      Piece.Pawn(Color.Black).toString shouldBe "p"
    }

    "have the correct color" in {
      Piece.King(Color.White).color shouldBe Color.White
      Piece.King(Color.Black).color shouldBe Color.Black
    }
  }

  "A Color" should {

    "have an opposite" in {
      Color.White.opposite shouldBe Color.Black
      Color.Black.opposite shouldBe Color.White
    }
  }
}
