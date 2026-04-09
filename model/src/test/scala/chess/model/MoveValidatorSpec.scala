package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class MoveValidatorSpec extends AnyWordSpec with Matchers {

  // Helper: empty board with specific pieces
  def boardWith(pieces: (Position, Piece)*): Board =
    pieces.foldLeft(Board.empty) { case (b, (pos, piece)) => b.put(pos, piece) }

  // ---------- Pawn ----------

  "Pawn movement" should {

    "allow white pawn one square forward" in {
      val board = boardWith(Position(1, 4) -> Piece.Pawn(Color.White))
      val move = Move(Position(1, 4), Position(2, 4))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "allow white pawn two squares forward from start row" in {
      val board = boardWith(Position(1, 4) -> Piece.Pawn(Color.White))
      val move = Move(Position(1, 4), Position(3, 4))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "reject white pawn two squares forward from non-start row" in {
      val board = boardWith(Position(2, 4) -> Piece.Pawn(Color.White))
      val move = Move(Position(2, 4), Position(4, 4))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "reject white pawn moving forward into occupied square" in {
      val board = boardWith(
        Position(1, 4) -> Piece.Pawn(Color.White),
        Position(2, 4) -> Piece.Pawn(Color.Black)
      )
      val move = Move(Position(1, 4), Position(2, 4))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "reject white pawn two squares if path blocked" in {
      val board = boardWith(
        Position(1, 4) -> Piece.Pawn(Color.White),
        Position(2, 4) -> Piece.Pawn(Color.Black)
      )
      val move = Move(Position(1, 4), Position(3, 4))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "allow white pawn diagonal capture" in {
      val board = boardWith(
        Position(1, 4) -> Piece.Pawn(Color.White),
        Position(2, 5) -> Piece.Pawn(Color.Black)
      )
      val move = Move(Position(1, 4), Position(2, 5))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "reject white pawn diagonal move without capture" in {
      val board = boardWith(Position(1, 4) -> Piece.Pawn(Color.White))
      val move = Move(Position(1, 4), Position(2, 5))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "reject white pawn moving backward" in {
      val board = boardWith(Position(3, 4) -> Piece.Pawn(Color.White))
      val move = Move(Position(3, 4), Position(2, 4))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "allow black pawn one square forward (downward)" in {
      val board = boardWith(Position(6, 4) -> Piece.Pawn(Color.Black))
      val move = Move(Position(6, 4), Position(5, 4))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "allow black pawn two squares forward from start row" in {
      val board = boardWith(Position(6, 4) -> Piece.Pawn(Color.Black))
      val move = Move(Position(6, 4), Position(4, 4))
      MoveValidator.isValidMove(move, board) shouldBe true
    }
  }

  // ---------- Rook ----------

  "Rook movement" should {

    "allow horizontal move" in {
      val board = boardWith(Position(0, 0) -> Piece.Rook(Color.White))
      val move = Move(Position(0, 0), Position(0, 7))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "allow vertical move" in {
      val board = boardWith(Position(0, 0) -> Piece.Rook(Color.White))
      val move = Move(Position(0, 0), Position(7, 0))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "reject diagonal move" in {
      val board = boardWith(Position(0, 0) -> Piece.Rook(Color.White))
      val move = Move(Position(0, 0), Position(3, 3))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "reject move through occupied square" in {
      val board = boardWith(
        Position(0, 0) -> Piece.Rook(Color.White),
        Position(0, 3) -> Piece.Pawn(Color.Black)
      )
      val move = Move(Position(0, 0), Position(0, 7))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "allow move to square right before blocking piece" in {
      val board = boardWith(
        Position(0, 0) -> Piece.Rook(Color.White),
        Position(0, 3) -> Piece.Pawn(Color.Black)
      )
      val move = Move(Position(0, 0), Position(0, 2))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "allow capturing on the blocking square" in {
      val board = boardWith(
        Position(0, 0) -> Piece.Rook(Color.White),
        Position(0, 3) -> Piece.Pawn(Color.Black)
      )
      val move = Move(Position(0, 0), Position(0, 3))
      MoveValidator.isValidMove(move, board) shouldBe true
    }
  }

  // ---------- Bishop ----------

  "Bishop movement" should {

    "allow diagonal move" in {
      val board = boardWith(Position(0, 0) -> Piece.Bishop(Color.White))
      val move = Move(Position(0, 0), Position(4, 4))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "reject horizontal move" in {
      val board = boardWith(Position(0, 0) -> Piece.Bishop(Color.White))
      val move = Move(Position(0, 0), Position(0, 4))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "reject move through occupied diagonal square" in {
      val board = boardWith(
        Position(0, 0) -> Piece.Bishop(Color.White),
        Position(2, 2) -> Piece.Pawn(Color.Black)
      )
      val move = Move(Position(0, 0), Position(4, 4))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "allow diagonal capture" in {
      val board = boardWith(
        Position(0, 0) -> Piece.Bishop(Color.White),
        Position(3, 3) -> Piece.Pawn(Color.Black)
      )
      val move = Move(Position(0, 0), Position(3, 3))
      MoveValidator.isValidMove(move, board) shouldBe true
    }
  }

  // ---------- Queen ----------

  "Queen movement" should {

    "allow horizontal move" in {
      val board = boardWith(Position(3, 3) -> Piece.Queen(Color.White))
      val move = Move(Position(3, 3), Position(3, 7))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "allow diagonal move" in {
      val board = boardWith(Position(3, 3) -> Piece.Queen(Color.White))
      val move = Move(Position(3, 3), Position(6, 6))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "reject knight-like move" in {
      val board = boardWith(Position(3, 3) -> Piece.Queen(Color.White))
      val move = Move(Position(3, 3), Position(5, 4))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "reject move through occupied square" in {
      val board = boardWith(
        Position(3, 3) -> Piece.Queen(Color.White),
        Position(3, 5) -> Piece.Pawn(Color.Black)
      )
      val move = Move(Position(3, 3), Position(3, 7))
      MoveValidator.isValidMove(move, board) shouldBe false
    }
  }

  // ---------- King ----------

  "King movement" should {

    "allow one square in any direction" in {
      val board = boardWith(Position(4, 4) -> Piece.King(Color.White))
      val directions = List(
        (1, 0), (-1, 0), (0, 1), (0, -1),
        (1, 1), (1, -1), (-1, 1), (-1, -1)
      )
      directions.foreach { (dr, dc) =>
        val move = Move(Position(4, 4), Position(4 + dr, 4 + dc))
        MoveValidator.isValidMove(move, board) shouldBe true
      }
    }

    "reject move of two squares" in {
      val board = boardWith(Position(4, 4) -> Piece.King(Color.White))
      val move = Move(Position(4, 4), Position(6, 4))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "reject staying in place" in {
      val board = boardWith(Position(4, 4) -> Piece.King(Color.White))
      val move = Move(Position(4, 4), Position(4, 4))
      MoveValidator.isValidMove(move, board) shouldBe false
    }
  }

  // ---------- Knight ----------

  "Knight movement" should {

    "allow all eight L-shaped moves" in {
      val board = boardWith(Position(4, 4) -> Piece.Knight(Color.White))
      val targets = List(
        (6, 5), (6, 3), (2, 5), (2, 3),
        (5, 6), (5, 2), (3, 6), (3, 2)
      )
      targets.foreach { (r, c) =>
        val move = Move(Position(4, 4), Position(r, c))
        MoveValidator.isValidMove(move, board) shouldBe true
      }
    }

    "reject non-L-shaped move" in {
      val board = boardWith(Position(4, 4) -> Piece.Knight(Color.White))
      val move = Move(Position(4, 4), Position(5, 5))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "jump over other pieces" in {
      // Surround knight with pieces, should still be able to move
      val board = boardWith(
        Position(4, 4) -> Piece.Knight(Color.White),
        Position(5, 4) -> Piece.Pawn(Color.White),
        Position(3, 4) -> Piece.Pawn(Color.White),
        Position(4, 5) -> Piece.Pawn(Color.White),
        Position(4, 3) -> Piece.Pawn(Color.White)
      )
      val move = Move(Position(4, 4), Position(6, 5))
      MoveValidator.isValidMove(move, board) shouldBe true
    }
  }

  // ---------- Empty square ----------

  "isValidMove" should {

    "return false for empty source square" in {
      val board = Board.empty
      val move = Move(Position(0, 0), Position(1, 0))
      MoveValidator.isValidMove(move, board) shouldBe false
    }
  }

  // ---------- Check detection ----------

  "isInCheck" should {

    "detect check by rook" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(0, 0) -> Piece.Rook(Color.Black)
      )
      MoveValidator.isInCheck(board, Color.White) shouldBe true
    }

    "detect check by bishop" in {
      val board = boardWith(
        Position(0, 0) -> Piece.King(Color.White),
        Position(3, 3) -> Piece.Bishop(Color.Black)
      )
      MoveValidator.isInCheck(board, Color.White) shouldBe true
    }

    "detect check by knight" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(2, 5) -> Piece.Knight(Color.Black)
      )
      MoveValidator.isInCheck(board, Color.White) shouldBe true
    }

    "detect check by pawn" in {
      val board = boardWith(
        Position(4, 4) -> Piece.King(Color.White),
        Position(5, 5) -> Piece.Pawn(Color.Black) // black pawn captures downward-diag
      )
      MoveValidator.isInCheck(board, Color.White) shouldBe true
    }

    "return false when king is safe" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(5, 0) -> Piece.Rook(Color.Black)
      )
      MoveValidator.isInCheck(board, Color.White) shouldBe false
    }

    "return false when no king on board" in {
      val board = boardWith(Position(0, 0) -> Piece.Rook(Color.Black))
      MoveValidator.isInCheck(board, Color.White) shouldBe false
    }
  }

  // ---------- Legal move generation ----------

  "legalMoves" should {

    "not include moves that leave king in check" in {
      // White rook on same row as white king, black rook attacks along that row
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(0, 3) -> Piece.Rook(Color.White),
        Position(0, 0) -> Piece.Rook(Color.Black),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val moves = MoveValidator.legalMoves(board, Color.White)
      // White rook at d1 is pinned to king by black rook at a1
      // So rook can only move along row 0 (staying between king and attacker)
      val rookMoves = moves.filter(_.from == Position(0, 3))
      rookMoves.foreach { m =>
        m.to.row shouldBe 0 // rook must stay on row 0
      }
    }

    "return empty list in checkmate position" in {
      // Back-rank mate: black king at g8 boxed in by own pawns, white rook on rank 8
      val board = boardWith(
        Position(0, 0) -> Piece.King(Color.White),
        Position(7, 0) -> Piece.Rook(Color.White),  // a8: checks king on rank 8
        Position(7, 6) -> Piece.King(Color.Black),   // g8
        Position(6, 5) -> Piece.Pawn(Color.Black),   // f7
        Position(6, 6) -> Piece.Pawn(Color.Black),   // g7
        Position(6, 7) -> Piece.Pawn(Color.Black)    // h7
      )
      val moves = MoveValidator.legalMoves(board, Color.Black)
      moves shouldBe empty
    }
  }

  // ---------- Castling ----------

  "Castling validation" should {

    "allow white king-side castling" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(0, 7) -> Piece.Rook(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 4), Position(0, 6))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "allow white queen-side castling" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(0, 0) -> Piece.Rook(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 4), Position(0, 2))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "allow black king-side castling" in {
      val board = boardWith(
        Position(7, 4) -> Piece.King(Color.Black),
        Position(7, 7) -> Piece.Rook(Color.Black),
        Position(0, 4) -> Piece.King(Color.White)
      )
      val move = Move(Position(7, 4), Position(7, 6))
      MoveValidator.isValidMove(move, board) shouldBe true
    }

    "reject castling when king has moved" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(0, 7) -> Piece.Rook(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 4), Position(0, 6))
      MoveValidator.isValidMove(move, board, movedPieces = Set(Position(0, 4))) shouldBe false
    }

    "reject castling when rook has moved" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(0, 7) -> Piece.Rook(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 4), Position(0, 6))
      MoveValidator.isValidMove(move, board, movedPieces = Set(Position(0, 7))) shouldBe false
    }

    "reject castling when path is blocked" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(0, 5) -> Piece.Bishop(Color.White),
        Position(0, 7) -> Piece.Rook(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 4), Position(0, 6))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "reject castling when king is in check" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(0, 7) -> Piece.Rook(Color.White),
        Position(7, 4) -> Piece.King(Color.Black),
        Position(4, 4) -> Piece.Rook(Color.Black)
      )
      val move = Move(Position(0, 4), Position(0, 6))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "reject castling when king passes through attacked square" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(0, 7) -> Piece.Rook(Color.White),
        Position(7, 4) -> Piece.King(Color.Black),
        Position(4, 5) -> Piece.Rook(Color.Black)
      )
      val move = Move(Position(0, 4), Position(0, 6))
      MoveValidator.isValidMove(move, board) shouldBe false
    }

    "reject castling when no rook present" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 4), Position(0, 6))
      MoveValidator.isValidMove(move, board) shouldBe false
    }
  }

  // ---------- En Passant ----------

  "En passant validation" should {

    "allow white en passant capture" in {
      val board = boardWith(
        Position(4, 4) -> Piece.Pawn(Color.White),
        Position(4, 3) -> Piece.Pawn(Color.Black),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val lastMove = Some(Move(Position(6, 3), Position(4, 3)))
      val move = Move(Position(4, 4), Position(5, 3))
      MoveValidator.isValidMove(move, board, lastMove = lastMove) shouldBe true
    }

    "allow black en passant capture" in {
      val board = boardWith(
        Position(3, 4) -> Piece.Pawn(Color.Black),
        Position(3, 5) -> Piece.Pawn(Color.White),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val lastMove = Some(Move(Position(1, 5), Position(3, 5)))
      val move = Move(Position(3, 4), Position(2, 5))
      MoveValidator.isValidMove(move, board, lastMove = lastMove) shouldBe true
    }

    "reject en passant without double push" in {
      val board = boardWith(
        Position(4, 4) -> Piece.Pawn(Color.White),
        Position(4, 3) -> Piece.Pawn(Color.Black),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val lastMove = Some(Move(Position(5, 3), Position(4, 3)))
      val move = Move(Position(4, 4), Position(5, 3))
      MoveValidator.isValidMove(move, board, lastMove = lastMove) shouldBe false
    }

    "reject en passant without last move" in {
      val board = boardWith(
        Position(4, 4) -> Piece.Pawn(Color.White),
        Position(4, 3) -> Piece.Pawn(Color.Black),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(4, 4), Position(5, 3))
      MoveValidator.isValidMove(move, board) shouldBe false
    }
  }

  // ---------- Insufficient Material ----------

  "isInsufficientMaterial" should {

    "detect K vs K" in {
      val board = boardWith(
        Position(0, 0) -> Piece.King(Color.White),
        Position(7, 7) -> Piece.King(Color.Black)
      )
      MoveValidator.isInsufficientMaterial(board) shouldBe true
    }

    "detect K+B vs K" in {
      val board = boardWith(
        Position(0, 0) -> Piece.King(Color.White),
        Position(2, 2) -> Piece.Bishop(Color.White),
        Position(7, 7) -> Piece.King(Color.Black)
      )
      MoveValidator.isInsufficientMaterial(board) shouldBe true
    }

    "detect K+N vs K" in {
      val board = boardWith(
        Position(0, 0) -> Piece.King(Color.White),
        Position(2, 1) -> Piece.Knight(Color.White),
        Position(7, 7) -> Piece.King(Color.Black)
      )
      MoveValidator.isInsufficientMaterial(board) shouldBe true
    }

    "detect K+B vs K+B on same color squares" in {
      val board = boardWith(
        Position(0, 0) -> Piece.King(Color.White),
        Position(0, 2) -> Piece.Bishop(Color.White),
        Position(7, 7) -> Piece.King(Color.Black),
        Position(7, 5) -> Piece.Bishop(Color.Black)
      )
      MoveValidator.isInsufficientMaterial(board) shouldBe true
    }

    "not detect insufficient material for K+B vs K+B on different color squares" in {
      val board = boardWith(
        Position(0, 0) -> Piece.King(Color.White),
        Position(0, 2) -> Piece.Bishop(Color.White),
        Position(7, 7) -> Piece.King(Color.Black),
        Position(7, 6) -> Piece.Bishop(Color.Black)
      )
      MoveValidator.isInsufficientMaterial(board) shouldBe false
    }

    "not detect insufficient material with queen on board" in {
      val board = boardWith(
        Position(0, 0) -> Piece.King(Color.White),
        Position(3, 3) -> Piece.Queen(Color.White),
        Position(7, 7) -> Piece.King(Color.Black)
      )
      MoveValidator.isInsufficientMaterial(board) shouldBe false
    }

    "not detect insufficient material with pawn on board" in {
      val board = boardWith(
        Position(0, 0) -> Piece.King(Color.White),
        Position(1, 0) -> Piece.Pawn(Color.White),
        Position(7, 7) -> Piece.King(Color.Black)
      )
      MoveValidator.isInsufficientMaterial(board) shouldBe false
    }
  }
}