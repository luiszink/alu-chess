package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class BoardSpec extends AnyWordSpec with Matchers {

  "An empty Board" should {

    val board = Board.empty

    "have size 8" in {
      board.size shouldBe 8
    }

    "have 8 rows" in {
      board.cells should have size 8
    }

    "have 8 columns per row" in {
      board.cells.foreach { row =>
        row should have size 8
      }
    }

    "have no pieces on any cell" in {
      for
        r <- 0 until 8
        c <- 0 until 8
      do board.cell(r, c) shouldBe None
    }
  }

  "An initial Board" should {

    val board = Board.initial

    "have white rooks on a1 and h1" in {
      board.cell(0, 0) shouldBe Some(Piece.Rook(Color.White))
      board.cell(0, 7) shouldBe Some(Piece.Rook(Color.White))
    }

    "have white king on e1" in {
      board.cell(0, 4) shouldBe Some(Piece.King(Color.White))
    }

    "have black king on e8" in {
      board.cell(7, 4) shouldBe Some(Piece.King(Color.Black))
    }

    "have white pawns on row 2" in {
      for c <- 0 until 8 do
        board.cell(1, c) shouldBe Some(Piece.Pawn(Color.White))
    }

    "have black pawns on row 7" in {
      for c <- 0 until 8 do
        board.cell(6, c) shouldBe Some(Piece.Pawn(Color.Black))
    }

    "have empty cells in the middle" in {
      for
        r <- 2 until 6
        c <- 0 until 8
      do board.cell(r, c) shouldBe None
    }
  }

  "Board.put" should {

    "place a piece on a valid cell" in {
      val board = Board.empty.put(3, 4, Piece.Queen(Color.White))
      board.cell(3, 4) shouldBe Some(Piece.Queen(Color.White))
    }

    "leave the board unchanged for invalid coordinates" in {
      val board = Board.empty.put(-1, 0, Piece.King(Color.White))
      board shouldBe Board.empty
    }
  }

  "Board.clear" should {

    "remove a piece from a valid cell" in {
      val board = Board.initial.clear(0, 0)
      board.cell(0, 0) shouldBe None
    }

    "leave the board unchanged for invalid coordinates" in {
      val board = Board.empty.clear(8, 8)
      board shouldBe Board.empty
    }
  }

  "Board.isValid" should {

    "return true for valid coordinates" in {
      Board.empty.isValid(0, 0) shouldBe true
      Board.empty.isValid(7, 7) shouldBe true
    }

    "return false for out-of-bounds coordinates" in {
      Board.empty.isValid(-1, 0) shouldBe false
      Board.empty.isValid(0, 8) shouldBe false
      Board.empty.isValid(8, 0) shouldBe false
    }
  }

  "Board.toString" should {

    "contain column labels" in {
      val s = Board.empty.toString
      s should include("a")
      s should include("h")
    }

    "contain row numbers" in {
      val s = Board.empty.toString
      s should include("1")
      s should include("8")
    }
  }

  "Board.cell(Position)" should {

    "return the piece at a valid position" in {
      Board.initial.cell(Position(0, 4)) shouldBe Some(Piece.King(Color.White))
    }

    "return None for an empty position" in {
      Board.empty.cell(Position(0, 0)) shouldBe None
    }

    "return None for out-of-bounds coordinates" in {
      Board.empty.cell(-1, 0) shouldBe None
      Board.empty.cell(0, 8) shouldBe None
      Board.empty.cell(8, 0) shouldBe None
    }
  }

  "Board.put(Position)" should {

    "place a piece via Position" in {
      val board = Board.empty.put(Position(4, 4), Piece.Queen(Color.Black))
      board.cell(Position(4, 4)) shouldBe Some(Piece.Queen(Color.Black))
    }
  }

  "Board.clear(Position)" should {

    "remove a piece via Position" in {
      val board = Board.initial.clear(Position(0, 0))
      board.cell(Position(0, 0)) shouldBe None
    }
  }

  "Board.move" should {

    "move a piece from one position to another" in {
      val board = Board.initial
      val m = Move(Position(1, 4), Position(3, 4)) // e2 -> e4
      val result = board.move(m)
      result shouldBe defined
      result.get.cell(Position(3, 4)) shouldBe Some(Piece.Pawn(Color.White))
      result.get.cell(Position(1, 4)) shouldBe None
    }

    "return None when source square is empty" in {
      val board = Board.empty
      val m = Move(Position(0, 0), Position(1, 0))
      board.move(m) shouldBe None
    }
  }
}
