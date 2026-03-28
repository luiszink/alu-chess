package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class MoveEntrySpec extends AnyWordSpec with Matchers {

  def boardWith(pieces: (Position, Piece)*): Board =
    pieces.foldLeft(Board.empty) { case (b, (pos, piece)) => b.put(pos, piece) }

  "MoveEntry.create" should {

    "produce SAN for a simple pawn move" in {
      val board = Board.initial
      val move = Move(Position(1, 4), Position(3, 4)) // e2-e4
      val entry = MoveEntry.create(
        move, Piece.Pawn(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "e4"
      entry.move shouldBe move
      entry.piece shouldBe Piece.Pawn(Color.White)
      entry.captured shouldBe None
      entry.resultingStatus shouldBe GameStatus.Playing
    }

    "produce SAN for a pawn capture" in {
      val board = boardWith(
        Position(3, 4) -> Piece.Pawn(Color.White),
        Position(4, 3) -> Piece.Pawn(Color.Black),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(3, 4), Position(4, 3))
      val entry = MoveEntry.create(
        move, Piece.Pawn(Color.White), Some(Piece.Pawn(Color.Black)), board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "exd5"
    }

    "produce SAN for king-side castling" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(0, 7) -> Piece.Rook(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 4), Position(0, 6))
      val entry = MoveEntry.create(
        move, Piece.King(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "O-O"
    }

    "produce SAN for queen-side castling" in {
      val board = boardWith(
        Position(0, 4) -> Piece.King(Color.White),
        Position(0, 0) -> Piece.Rook(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 4), Position(0, 2))
      val entry = MoveEntry.create(
        move, Piece.King(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "O-O-O"
    }

    "produce SAN for a knight move" in {
      val board = boardWith(
        Position(0, 1) -> Piece.Knight(Color.White),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 1), Position(2, 2))
      val entry = MoveEntry.create(
        move, Piece.Knight(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "Nc3"
    }

    "produce SAN for a rook capture" in {
      val board = boardWith(
        Position(0, 0) -> Piece.Rook(Color.White),
        Position(0, 7) -> Piece.Knight(Color.Black),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 0), Position(0, 7))
      val entry = MoveEntry.create(
        move, Piece.Rook(Color.White), Some(Piece.Knight(Color.Black)), board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "Rxh1"
    }

    "append # for checkmate" in {
      val board = boardWith(
        Position(6, 0) -> Piece.Rook(Color.White),
        Position(0, 0) -> Piece.King(Color.White),
        Position(7, 6) -> Piece.King(Color.Black),
        Position(6, 5) -> Piece.Pawn(Color.Black),
        Position(6, 6) -> Piece.Pawn(Color.Black),
        Position(6, 7) -> Piece.Pawn(Color.Black)
      )
      val move = Move(Position(6, 0), Position(7, 0))
      val entry = MoveEntry.create(
        move, Piece.Rook(Color.White), None, board, GameStatus.Checkmate, Set.empty, None
      )
      entry.san shouldBe "Ra8#"
    }

    "append + for check" in {
      val board = boardWith(
        Position(1, 0) -> Piece.Rook(Color.White),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(1, 0), Position(7, 0))
      val entry = MoveEntry.create(
        move, Piece.Rook(Color.White), None, board, GameStatus.Check, Set.empty, None
      )
      entry.san shouldBe "Ra8+"
    }

    "produce SAN with pawn promotion" in {
      val board = boardWith(
        Position(6, 0) -> Piece.Pawn(Color.White),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(6, 0), Position(7, 0), Some('Q'))
      val entry = MoveEntry.create(
        move, Piece.Pawn(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "a8=Q"
    }

    "disambiguate by file when two knights can reach the same square" in {
      val board = boardWith(
        Position(0, 1) -> Piece.Knight(Color.White),
        Position(0, 5) -> Piece.Knight(Color.White),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      // Both knights on b1 and f1 can go to d2
      val move = Move(Position(0, 1), Position(1, 3)) // Nb1-d2
      val entry = MoveEntry.create(
        move, Piece.Knight(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "Nbd2"
    }

    "disambiguate by rank when two rooks on same file can reach the same square" in {
      val board = boardWith(
        Position(0, 0) -> Piece.Rook(Color.White),
        Position(7, 0) -> Piece.Rook(Color.White),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 0), Position(3, 0)) // Ra1-a4
      val entry = MoveEntry.create(
        move, Piece.Rook(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "R1a4"
    }

    "disambiguate by full square when both file and rank are same" in {
      // Two knights on same file b1 and b3, plus one on f3 -> target d2
      // b1(0,1) and b3(2,1) share file, b1(0,1) row 0 is unique among others
      // Actually b3(2,1) row 2 same as f3(2,5) so row IS ambiguous for b3
      // For b1: others are b3 (same file b) and f3 (different file f) -> file not unique
      //         others are b3 (row 2) and f3 (row 2) -> row IS unique (0 vs 2,2)
      val board = boardWith(
        Position(0, 1) -> Piece.Knight(Color.White), // b1
        Position(2, 1) -> Piece.Knight(Color.White), // b3
        Position(2, 5) -> Piece.Knight(Color.White), // f3
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(0, 1), Position(1, 3)) // Nb1-d2
      val entry = MoveEntry.create(
        move, Piece.Knight(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      // b1 has unique row (0) among the others that can also go to d2
      entry.san shouldBe "N1d2"
    }

    "produce SAN for en passant pawn capture" in {
      val board = boardWith(
        Position(4, 4) -> Piece.Pawn(Color.White),
        Position(4, 3) -> Piece.Pawn(Color.Black),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(4, 4), Position(5, 3))
      val entry = MoveEntry.create(
        move, Piece.Pawn(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      // En passant: diagonal pawn move to empty square, so SAN should be exd6
      entry.san shouldBe "exd6"
    }
  }

  "MoveEntry pieceToSanChar" should {
    "be tested implicitly through create" in {
      // King move (covers pieceToSanChar for King)
      val board = boardWith(
        Position(4, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move = Move(Position(4, 4), Position(5, 4))
      val entry = MoveEntry.create(
        move, Piece.King(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "Ke6"

      // Queen move
      val board2 = boardWith(
        Position(3, 3) -> Piece.Queen(Color.White),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move2 = Move(Position(3, 3), Position(5, 5))
      val entry2 = MoveEntry.create(
        move2, Piece.Queen(Color.White), None, board2, GameStatus.Playing, Set.empty, None
      )
      entry2.san shouldBe "Qf6"

      // Bishop move
      val board3 = boardWith(
        Position(3, 3) -> Piece.Bishop(Color.White),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      val move3 = Move(Position(3, 3), Position(5, 5))
      val entry3 = MoveEntry.create(
        move3, Piece.Bishop(Color.White), None, board3, GameStatus.Playing, Set.empty, None
      )
      entry3.san shouldBe "Bf6"
    }
  }

  "MoveEntry disambiguation with queens" should {
    "disambiguate two queens by file" in {
      val board = boardWith(
        Position(0, 3) -> Piece.Queen(Color.White),
        Position(0, 6) -> Piece.Queen(Color.White),
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      // Both queens can go to d4 (3,3) - queen d1 and queen g1
      // Qd1-d4: from (0,3) to (3,3)
      // Qg1 can also go to d4: (0,6) to (3,3) diagonal - abs match? dr=3,dc=3 yes
      val move = Move(Position(0, 3), Position(3, 3))
      val entry = MoveEntry.create(
        move, Piece.Queen(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "Qdd4"
    }
  }

  "MoveEntry disambiguation with bishops" should {
    "disambiguate two bishops by file" in {
      val board = boardWith(
        Position(0, 2) -> Piece.Bishop(Color.White), // c1
        Position(4, 6) -> Piece.Bishop(Color.White), // g5
        Position(0, 4) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      // Target e3 (2,4): from c1(0,2) dr=2,dc=2 yes. from g5(4,6) dr=2,dc=2 yes
      val move = Move(Position(0, 2), Position(2, 4))
      val entry = MoveEntry.create(
        move, Piece.Bishop(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "Bce3"
    }
  }

  "MoveEntry full square disambiguation" should {
    "use full square when neither file nor rank is unique" in {
      // Need 3+ pieces of same type where the moving piece shares both file AND rank
      // with different other pieces. E.g., queens on a1, a5, e1 -> target c3
      val board = boardWith(
        Position(0, 0) -> Piece.Queen(Color.White), // a1
        Position(4, 0) -> Piece.Queen(Color.White), // a5
        Position(0, 4) -> Piece.Queen(Color.White), // e1 (also acts as "king" area)
        Position(3, 7) -> Piece.King(Color.White),
        Position(7, 4) -> Piece.King(Color.Black)
      )
      // Target c3 (2,2): from a1(0,0): dr=2,dc=2 diagonal ok.
      //   from a5(4,0): dr=2,dc=2 diagonal ok. from e1(0,4): dr=2,dc=2 diagonal ok.
      // a1 shares file 'a' with a5, and row 1 with e1 -> full square needed
      val move = Move(Position(0, 0), Position(2, 2))
      val entry = MoveEntry.create(
        move, Piece.Queen(Color.White), None, board, GameStatus.Playing, Set.empty, None
      )
      entry.san shouldBe "Qa1c3"
    }
  }
}
