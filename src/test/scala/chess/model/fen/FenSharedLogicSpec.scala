package chess.model.fen

import chess.model.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FenSharedLogicSpec extends AnyWordSpec with Matchers:

  // ── normalizeFenParts ──────────────────────────────────────────────────────

  "FenSharedLogic.normalizeFenParts" should {

    "return defaults for a single-field (board-only) input" in {
      val result = FenSharedLogic.normalizeFenParts(Array("8/8/8/8/8/8/8/8"))
      result shouldBe a[Right[?, ?]]
      val parts = result.toOption.get
      parts(0) shouldBe "8/8/8/8/8/8/8/8"
      parts(1) shouldBe "w"
      parts(2) shouldBe "-"
      parts(3) shouldBe "-"
      parts(4) shouldBe "0"
    }

    "pass through 4-field input unchanged" in {
      val input = Array("board", "w", "KQkq", "-")
      val result = FenSharedLogic.normalizeFenParts(input)
      result shouldBe a[Right[?, ?]]
      result.toOption.get shouldBe input
    }

    "pass through 6-field input unchanged" in {
      val input = Array("board", "w", "KQkq", "-", "0", "1")
      val result = FenSharedLogic.normalizeFenParts(input)
      result shouldBe a[Right[?, ?]]
      result.toOption.get shouldBe input
    }

    "return Left for 2-field input" in {
      val result = FenSharedLogic.normalizeFenParts(Array("board", "w"))
      result shouldBe a[Left[?, ?]]
    }

    "return Left for 3-field input" in {
      val result = FenSharedLogic.normalizeFenParts(Array("board", "w", "KQkq"))
      result shouldBe a[Left[?, ?]]
    }
  }

  // ── parseBoardE ────────────────────────────────────────────────────────────

  "FenSharedLogic.parseBoardE" should {

    "parse the starting position board" in {
      val result = FenSharedLogic.parseBoardE("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
      result shouldBe a[Right[?, ?]]
      result.toOption.get shouldBe Board.initial
    }

    "parse an empty board" in {
      val result = FenSharedLogic.parseBoardE("8/8/8/8/8/8/8/8")
      result shouldBe a[Right[?, ?]]
      result.toOption.get shouldBe Board.empty
    }

    "return Left for wrong number of ranks (7)" in {
      val result = FenSharedLogic.parseBoardE("8/8/8/8/8/8/8")
      result shouldBe Left(ChessError.InvalidFenFormat("Expected 8 ranks"))
    }

    "return Left for wrong number of ranks (9)" in {
      val result = FenSharedLogic.parseBoardE("8/8/8/8/8/8/8/8/8")
      result shouldBe Left(ChessError.InvalidFenFormat("Expected 8 ranks"))
    }

    "return Left for invalid piece character" in {
      val result = FenSharedLogic.parseBoardE("xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
      result shouldBe Left(ChessError.InvalidFenPieceChar('x'))
    }

    "return Left for rank that totals more than 8 squares" in {
      val result = FenSharedLogic.parseBoardE("rnbqkbnrr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
      result shouldBe a[Left[?, ?]]
    }
  }

  // ── parseRankE ─────────────────────────────────────────────────────────────

  "FenSharedLogic.parseRankE" should {

    "parse a full rank of pieces" in {
      val result = FenSharedLogic.parseRankE("RNBQKBNR")
      result shouldBe a[Right[?, ?]]
      result.toOption.get should have size 8
    }

    "parse a rank with only empty squares" in {
      val result = FenSharedLogic.parseRankE("8")
      result shouldBe a[Right[?, ?]]
      result.toOption.get shouldBe Vector.fill(8)(None)
    }

    "parse a mixed rank" in {
      val result = FenSharedLogic.parseRankE("4P3")
      result shouldBe a[Right[?, ?]]
      val row = result.toOption.get
      row should have size 8
      row(4) shouldBe Some(Piece.Pawn(Color.White))
      row(0) shouldBe None
    }

    "return Left for invalid piece character" in {
      val result = FenSharedLogic.parseRankE("xnbqkbnr")
      result shouldBe Left(ChessError.InvalidFenPieceChar('x'))
    }

    "return Left for rank too long (more than 8 squares)" in {
      val result = FenSharedLogic.parseRankE("rnbqkbnrr")
      result shouldBe a[Left[?, ?]]
      result.left.toOption.get shouldBe a[ChessError.InvalidFenBoardRow]
    }

    "return Left for digit 0" in {
      val result = FenSharedLogic.parseRankE("0nrrbbkk")
      result shouldBe a[Left[?, ?]]
      result.left.toOption.get shouldBe a[ChessError.InvalidFenBoardRow]
    }

    "return Left for digit 9" in {
      val result = FenSharedLogic.parseRankE("9")
      result shouldBe a[Left[?, ?]]
    }
  }

  // ── parseColorE ────────────────────────────────────────────────────────────

  "FenSharedLogic.parseColorE" should {

    "parse 'w' as White" in {
      FenSharedLogic.parseColorE("w") shouldBe Right(Color.White)
    }

    "parse 'b' as Black" in {
      FenSharedLogic.parseColorE("b") shouldBe Right(Color.Black)
    }

    "return Left for 'x'" in {
      FenSharedLogic.parseColorE("x") shouldBe Left(ChessError.InvalidFenColor("x"))
    }

    "return Left for uppercase 'W'" in {
      FenSharedLogic.parseColorE("W") shouldBe Left(ChessError.InvalidFenColor("W"))
    }
  }

  // ── charToPiece ────────────────────────────────────────────────────────────

  "FenSharedLogic.charToPiece" should {

    "map all 12 valid piece characters" in {
      FenSharedLogic.charToPiece('K') shouldBe Some(Piece.King(Color.White))
      FenSharedLogic.charToPiece('Q') shouldBe Some(Piece.Queen(Color.White))
      FenSharedLogic.charToPiece('R') shouldBe Some(Piece.Rook(Color.White))
      FenSharedLogic.charToPiece('B') shouldBe Some(Piece.Bishop(Color.White))
      FenSharedLogic.charToPiece('N') shouldBe Some(Piece.Knight(Color.White))
      FenSharedLogic.charToPiece('P') shouldBe Some(Piece.Pawn(Color.White))
      FenSharedLogic.charToPiece('k') shouldBe Some(Piece.King(Color.Black))
      FenSharedLogic.charToPiece('q') shouldBe Some(Piece.Queen(Color.Black))
      FenSharedLogic.charToPiece('r') shouldBe Some(Piece.Rook(Color.Black))
      FenSharedLogic.charToPiece('b') shouldBe Some(Piece.Bishop(Color.Black))
      FenSharedLogic.charToPiece('n') shouldBe Some(Piece.Knight(Color.Black))
      FenSharedLogic.charToPiece('p') shouldBe Some(Piece.Pawn(Color.Black))
    }

    "return None for invalid characters" in {
      FenSharedLogic.charToPiece('x') shouldBe None
      FenSharedLogic.charToPiece('1') shouldBe None
      FenSharedLogic.charToPiece(' ') shouldBe None
    }
  }

  // ── castlingToMovedPieces ──────────────────────────────────────────────────

  "FenSharedLogic.castlingToMovedPieces" should {

    "return empty set for full castling rights (KQkq)" in {
      FenSharedLogic.castlingToMovedPieces("KQkq") shouldBe empty
    }

    "return all relevant positions for no castling rights (-)" in {
      val moved = FenSharedLogic.castlingToMovedPieces("-")
      moved should contain(Position(0, 0)) // a1 rook
      moved should contain(Position(0, 4)) // white king
      moved should contain(Position(0, 7)) // h1 rook
      moved should contain(Position(7, 0)) // a8 rook
      moved should contain(Position(7, 4)) // black king
      moved should contain(Position(7, 7)) // h8 rook
    }

    "handle partial castling rights (Kq)" in {
      val moved = FenSharedLogic.castlingToMovedPieces("Kq")
      // White: K present → h1 rook not moved, king not moved; Q absent → a1 rook moved
      moved should contain(Position(0, 0))     // a1 rook (no Q)
      moved should not contain Position(0, 7)   // h1 rook (K present)
      moved should not contain Position(0, 4)   // white king (K present)
      // Black: q present → a8 rook not moved, king not moved; k absent → h8 rook moved
      moved should contain(Position(7, 7))     // h8 rook (no k)
      moved should not contain Position(7, 0)   // a8 rook (q present)
      moved should not contain Position(7, 4)   // black king (q present)
    }

    "handle only white kingside (K)" in {
      val moved = FenSharedLogic.castlingToMovedPieces("K")
      moved should not contain Position(0, 7) // h1 rook available
      moved should not contain Position(0, 4) // white king available (K present)
      moved should contain(Position(0, 0))    // a1 rook moved (no Q)
    }
  }

  // ── parseEnPassant ─────────────────────────────────────────────────────────

  "FenSharedLogic.parseEnPassant" should {

    "return None for '-'" in {
      FenSharedLogic.parseEnPassant("-") shouldBe None
    }

    "return a white double pawn push for 'e3'" in {
      val result = FenSharedLogic.parseEnPassant("e3")
      result shouldBe defined
      val move = result.get
      move.from shouldBe Position(1, 4) // e2
      move.to shouldBe Position(3, 4)   // e4
    }

    "return a black double pawn push for 'e6'" in {
      val result = FenSharedLogic.parseEnPassant("e6")
      result shouldBe defined
      val move = result.get
      move.from shouldBe Position(6, 4) // e7
      move.to shouldBe Position(4, 4)   // e5
    }

    "return None for an invalid ep row like 'e4'" in {
      FenSharedLogic.parseEnPassant("e4") shouldBe None
    }

    "return None for an invalid ep row like 'a1'" in {
      FenSharedLogic.parseEnPassant("a1") shouldBe None
    }

    "handle all files for rank 3" in {
      for file <- 'a' to 'h' do
        val result = FenSharedLogic.parseEnPassant(s"${file}3")
        result shouldBe defined
        result.get.from.row shouldBe 1
        result.get.to.row shouldBe 3
    }

    "handle all files for rank 6" in {
      for file <- 'a' to 'h' do
        val result = FenSharedLogic.parseEnPassant(s"${file}6")
        result shouldBe defined
        result.get.from.row shouldBe 6
        result.get.to.row shouldBe 4
    }
  }

  // ── computeInitialStatus ───────────────────────────────────────────────────

  "FenSharedLogic.computeInitialStatus" should {

    "return Playing for the starting position" in {
      val game = Game.newGame.copy(status = GameStatus.Playing)
      val result = FenSharedLogic.computeInitialStatus(game)
      result.status shouldBe GameStatus.Playing
    }

    "detect checkmate" in {
      // Back-rank mate: black king on g8, pawns on f7/g7/h7, white rook on a8
      val board = Board.empty
        .put(Position(7, 6), Piece.King(Color.Black))
        .put(Position(6, 5), Piece.Pawn(Color.Black))
        .put(Position(6, 6), Piece.Pawn(Color.Black))
        .put(Position(6, 7), Piece.Pawn(Color.Black))
        .put(Position(7, 0), Piece.Rook(Color.White))
        .put(Position(0, 0), Piece.King(Color.White))
      val game = Game(board, Color.Black, GameStatus.Playing)
      val result = FenSharedLogic.computeInitialStatus(game)
      result.status shouldBe GameStatus.Checkmate
    }

    "detect stalemate" in {
      // Black king on a8, white queen on b6, white king on c8 → stalemate
      val board = Board.empty
        .put(Position(7, 0), Piece.King(Color.Black))
        .put(Position(5, 1), Piece.Queen(Color.White))
        .put(Position(5, 0), Piece.King(Color.White))
      val game = Game(board, Color.Black, GameStatus.Playing)
      val result = FenSharedLogic.computeInitialStatus(game)
      result.status shouldBe GameStatus.Stalemate
    }

    "detect check (not checkmate)" in {
      val board = Board.empty
        .put(Position(7, 4), Piece.King(Color.Black))
        .put(Position(7, 0), Piece.Rook(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
      val game = Game(board, Color.Black, GameStatus.Playing)
      val result = FenSharedLogic.computeInitialStatus(game)
      result.status shouldBe GameStatus.Check
    }

    "detect draw by insufficient material (king vs king)" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(7, 7), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val result = FenSharedLogic.computeInitialStatus(game)
      result.status shouldBe GameStatus.Draw
    }

    "detect draw by 50-move rule (halfMoveClock >= 100)" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(0, 7), Piece.Rook(Color.White))
        .put(Position(7, 7), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing, halfMoveClock = 100)
      val result = FenSharedLogic.computeInitialStatus(game)
      result.status shouldBe GameStatus.Draw
    }
  }
