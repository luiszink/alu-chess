package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/** Verifies that all three FEN parser implementations produce identical results
  * for the same inputs.  A shared behaviour block encodes the contract; each
  * parser is then plugged in via `behave like`. */
class FenParserSpec extends AnyWordSpec with Matchers:

  // -------------------------------------------------------------------------
  // Shared behaviour – every FenParser implementation must satisfy this
  // -------------------------------------------------------------------------

  def aCorrectFenParser(makeParser: () => FenParser): Unit =

    val StartFen  = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    val EmptyFen  = "8/8/8/8/8/8/8/8 w - - 0 1"
    val BoardOnly = "8/8/8/2k5/4K3/8/8/8"

    "parse the standard starting position" in {
      val p = makeParser()
      val result = p.parseE(StartFen)
      result shouldBe a[Right[?, ?]]
      result.map(_.board)         shouldBe Right(Board.initial)
      result.map(_.currentPlayer) shouldBe Right(Color.White)
      result.map(_.status)        shouldBe Right(GameStatus.Playing)
    }

    "parse black to move" in {
      val p = makeParser()
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
      p.parseE(fen).map(_.currentPlayer) shouldBe Right(Color.Black)
    }

    "parse an empty board" in {
      val p = makeParser()
      p.parseE(EmptyFen).map(_.board) shouldBe Right(Board.empty)
    }

    "parse a board-only FEN and apply defaults" in {
      val p = makeParser()
      val result = p.parseE(BoardOnly)
      result shouldBe a[Right[?, ?]]
      result.map(_.currentPlayer) shouldBe Right(Color.White)
      result.map(_.status)        shouldBe Right(GameStatus.Draw) // only kings → insufficient material
    }

    "parse halfmove clock" in {
      val p = makeParser()
      p.parseE("8/8/8/8/8/8/8/4K3 w - - 42 21").map(_.halfMoveClock) shouldBe Right(42)
    }

    "parse fullmove number" in {
      val p = makeParser()
      p.parseE("8/8/8/8/8/8/8/4K3 w - - 0 7").map(_.fullMoveNumber) shouldBe Right(7)
    }

    "parse castling rights – all available" in {
      val p = makeParser()
      val result = p.parseE("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1")
      result shouldBe a[Right[?, ?]]
      result.map(_.movedPieces) shouldBe Right(Set.empty)
    }

    "parse castling rights – none available" in {
      val p = makeParser()
      val result = p.parseE("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w - - 0 1")
      result shouldBe a[Right[?, ?]]
      val moved = result.getOrElse(throw new RuntimeException).movedPieces
      moved should contain(Position(0, 4)) // white king
      moved should contain(Position(7, 4)) // black king
    }

    "parse en-passant target" in {
      val p = makeParser()
      val result = p.parseE("rnbqkbnr/pppp1ppp/8/4pP2/8/8/PPPPP1PP/RNBQKBNR w KQkq e6 0 3")
      result shouldBe a[Right[?, ?]]
      result.map(_.lastMove.map(_.from)) shouldBe Right(Some(Position(6, 4)))
      result.map(_.lastMove.map(_.to))   shouldBe Right(Some(Position(4, 4)))
    }

    "parse 4-field FEN (no clocks)" in {
      val p = makeParser()
      val result = p.parseE("8/8/8/8/8/8/8/4K3 w - -")
      result shouldBe a[Right[?, ?]]
      result.map(_.halfMoveClock)  shouldBe Right(0)
      result.map(_.fullMoveNumber) shouldBe Right(1)
    }

    "return Left(InvalidFenFormat) for too-few fields (2-3 tokens)" in {
      val p = makeParser()
      // 3 space-separated tokens → normalizeFenParts rejects (needs 1 or ≥ 4)
      p.parseE("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq") shouldBe
        Left(ChessError.InvalidFenFormat("FEN requires either 1 field (board only) or at least 4 fields"))
    }

    "return Left(InvalidFenFormat) for wrong rank count" in {
      val p = makeParser()
      // 7 ranks instead of 8 → all parsers return a Left
      p.parseE("rnbqkbnr/pppppppp/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe a[Left[?, ?]]
    }

    "return Left(InvalidFenPieceChar) for invalid piece character" in {
      val p = makeParser()
      p.parseE("xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe
        Left(ChessError.InvalidFenPieceChar('x'))
    }

    "return Left(InvalidFenBoardRow) for a rank that is too long" in {
      val p = makeParser()
      p.parseE("rnbqkbnrr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe a[Left[?, ?]]
    }

    "return Left(InvalidFenColor) for an invalid active color" in {
      val p = makeParser()
      p.parseE("8/8/8/8/8/8/8/8 x - - 0 1") shouldBe Left(ChessError.InvalidFenColor("x"))
    }

    "return Left for digit '0' in rank" in {
      val p = makeParser()
      p.parseE("0nrrbbkk/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe a[Left[?, ?]]
    }

    "return Left for digit '9' in rank" in {
      val p = makeParser()
      p.parseE("9/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe a[Left[?, ?]]
    }

    // -- parseT and parse delegate correctly --

    "parseT returns Success for valid FEN" in {
      val p = makeParser()
      p.parseT(StartFen).isSuccess shouldBe true
    }

    "parseT returns Failure for invalid FEN" in {
      val p = makeParser()
      p.parseT("bad fen here z z z z").isFailure shouldBe true
    }

    "parse returns Some for valid FEN" in {
      val p = makeParser()
      p.parse(StartFen) shouldBe defined
    }

    "parse returns None for invalid FEN" in {
      val p = makeParser()
      p.parse("not/valid/at/all z z z z") shouldBe empty
    }

    // -------------------------------------------------------------------------
    // Additional edge cases
    // -------------------------------------------------------------------------

    // -- Empty / whitespace input --

    "return Left for empty string input" in {
      val p = makeParser()
      p.parseE("") shouldBe a[Left[?, ?]]
    }

    "return Left for whitespace-only input" in {
      val p = makeParser()
      p.parseE("   ") shouldBe a[Left[?, ?]]
    }

    // -- Rank too short (fewer than 8 squares) --

    "return Left(InvalidFenBoardRow) for a rank that is too short" in {
      val p = makeParser()
      // rank 1 has only 7 squares (rnbqkbn = 7 pieces, no 8th)
      p.parseE("rnbqkbn/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe a[Left[?, ?]]
    }

    // -- All 12 piece characters are parsed to the correct Piece --

    "parse all 12 piece characters correctly from a FEN rank" in {
      val p = makeParser()
      // Row 0 (rank 1): white K Q R B N P at cols 0-5, empty cols 6-7
      // Row 7 (rank 8): black k q r b n p at cols 0-5, empty cols 6-7
      val fen = "kqrbnp2/8/8/8/8/8/8/KQRBNP2 w - - 0 1"
      val result = p.parseE(fen)
      result shouldBe a[Right[?, ?]]
      val board = result.getOrElse(throw new RuntimeException).board
      // rank 1 = row 0: white pieces
      board.cell(0, 0) shouldBe Some(Piece.King(Color.White))
      board.cell(0, 1) shouldBe Some(Piece.Queen(Color.White))
      board.cell(0, 2) shouldBe Some(Piece.Rook(Color.White))
      board.cell(0, 3) shouldBe Some(Piece.Bishop(Color.White))
      board.cell(0, 4) shouldBe Some(Piece.Knight(Color.White))
      board.cell(0, 5) shouldBe Some(Piece.Pawn(Color.White))
      board.cell(0, 6) shouldBe None
      board.cell(0, 7) shouldBe None
      // rank 8 = row 7: black pieces
      board.cell(7, 0) shouldBe Some(Piece.King(Color.Black))
      board.cell(7, 1) shouldBe Some(Piece.Queen(Color.Black))
      board.cell(7, 2) shouldBe Some(Piece.Rook(Color.Black))
      board.cell(7, 3) shouldBe Some(Piece.Bishop(Color.Black))
      board.cell(7, 4) shouldBe Some(Piece.Knight(Color.Black))
      board.cell(7, 5) shouldBe Some(Piece.Pawn(Color.Black))
      board.cell(7, 6) shouldBe None
      board.cell(7, 7) shouldBe None
    }

    // -- parseEnPassant: rank 3 (white pawn two-step, ep square on rank 3) --

    "parse en-passant target on rank 3 (white pawn double push)" in {
      val p = makeParser()
      // White just played e2-e4 → ep target is e3 (row 2, col 4)
      val result = p.parseE("rnbqkbnr/pppp1ppp/8/8/4Pp2/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1")
      result shouldBe a[Right[?, ?]]
      // ep target e3 → row 2, col 4. Pawn moved from row 1 to row 3
      result.map(_.lastMove.map(_.from)) shouldBe Right(Some(Position(1, 4)))
      result.map(_.lastMove.map(_.to))   shouldBe Right(Some(Position(3, 4)))
    }

    "parse en-passant target on rank 6 (black pawn double push)" in {
      val p = makeParser()
      // Black just played e7-e5 → ep target is e6 (row 5, col 4). Pawn moved from row 6 to row 4.
      val result = p.parseE("rnbqkbnr/pppp1ppp/8/4p3/8/8/PPPPPPPP/RNBQKBNR w KQkq e6 0 1")
      result shouldBe a[Right[?, ?]]
      result.map(_.lastMove.map(_.from)) shouldBe Right(Some(Position(6, 4)))
      result.map(_.lastMove.map(_.to))   shouldBe Right(Some(Position(4, 4)))
    }

    "parse '-' en-passant produces no lastMove" in {
      val p = makeParser()
      val result = p.parseE("8/8/8/8/8/8/8/4K3 w - - 0 1")
      result shouldBe a[Right[?, ?]]
      result.map(_.lastMove) shouldBe Right(None)
    }

    // -- castlingToMovedPieces partial castling rights --

    "parse castling rights – white only (KQ)" in {
      val p = makeParser()
      val result = p.parseE("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQ - 0 1")
      result shouldBe a[Right[?, ?]]
      val moved = result.getOrElse(throw new RuntimeException).movedPieces
      // White rooks and king are unmoved
      moved should not contain Position(0, 4)
      moved should not contain Position(0, 0)
      moved should not contain Position(0, 7)
      // Black king has no castling rights → marked as moved
      moved should contain(Position(7, 4))
    }

    "parse castling rights – black only (kq)" in {
      val p = makeParser()
      val result = p.parseE("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w kq - 0 1")
      result shouldBe a[Right[?, ?]]
      val moved = result.getOrElse(throw new RuntimeException).movedPieces
      // White king has no castling rights → marked as moved
      moved should contain(Position(0, 4))
      // Black rooks and king are unmoved
      moved should not contain Position(7, 4)
      moved should not contain Position(7, 0)
      moved should not contain Position(7, 7)
    }

    "parse castling rights – white kingside only (K)" in {
      val p = makeParser()
      val result = p.parseE("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w K - 0 1")
      result shouldBe a[Right[?, ?]]
      val moved = result.getOrElse(throw new RuntimeException).movedPieces
      // Queenside white rook is marked moved (no Q right)
      moved should contain(Position(0, 0))
      // Kingside white rook is NOT marked moved
      moved should not contain Position(0, 7)
      // White king is NOT marked moved (still has K right)
      moved should not contain Position(0, 4)
      // Black king is marked moved (no k or q rights)
      moved should contain(Position(7, 4))
    }

    "parse castling rights – white queenside only (Q)" in {
      val p = makeParser()
      val result = p.parseE("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w Q - 0 1")
      result shouldBe a[Right[?, ?]]
      val moved = result.getOrElse(throw new RuntimeException).movedPieces
      // Kingside white rook is marked moved (no K right)
      moved should contain(Position(0, 7))
      // Queenside white rook is NOT marked moved
      moved should not contain Position(0, 0)
      // White king is NOT marked moved (still has Q right)
      moved should not contain Position(0, 4)
      // Black king is marked moved (no k or q rights)
      moved should contain(Position(7, 4))
    }

    "parse castling rights – black kingside only (k)" in {
      val p = makeParser()
      val result = p.parseE("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w k - 0 1")
      result shouldBe a[Right[?, ?]]
      val moved = result.getOrElse(throw new RuntimeException).movedPieces
      // White king is marked moved (no K or Q)
      moved should contain(Position(0, 4))
      // Black queenside rook is marked moved (no q right)
      moved should contain(Position(7, 0))
      // Black kingside rook is NOT marked moved
      moved should not contain Position(7, 7)
      // Black king is NOT marked moved (still has k right)
      moved should not contain Position(7, 4)
    }

    "parse castling rights – black queenside only (q)" in {
      val p = makeParser()
      val result = p.parseE("r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w q - 0 1")
      result shouldBe a[Right[?, ?]]
      val moved = result.getOrElse(throw new RuntimeException).movedPieces
      // White king is marked moved (no K or Q)
      moved should contain(Position(0, 4))
      // Black kingside rook is marked moved (no k right)
      moved should contain(Position(7, 7))
      // Black queenside rook is NOT marked moved
      moved should not contain Position(7, 0)
      // Black king is NOT marked moved (still has q right)
      moved should not contain Position(7, 4)
    }

    // -- parseT error message carries ChessError detail --

    "parseT Failure exception message contains ChessError detail" in {
      val p = makeParser()
      val invalidColorFen = "8/8/8/8/8/8/8/8 z - - 0 1"
      val tried = p.parseT(invalidColorFen)
      tried.isFailure shouldBe true
      val msg = tried.failed.getOrElse(throw new RuntimeException).getMessage
      msg should include("z")
    }

    // -- parse Option semantics --

    "parse returns None (not Some(None)) for invalid FEN" in {
      val p = makeParser()
      val result: Option[Game] = p.parse("bad fen 1 2 3 4")
      result shouldBe None
    }

    // -- Leading/trailing whitespace in input is trimmed --

    "parse trims surrounding whitespace" in {
      val p = makeParser()
      val result = p.parseE("  " + StartFen + "  ")
      result shouldBe a[Right[?, ?]]
      result.map(_.board) shouldBe Right(Board.initial)
    }

    // -- halfMoveClock >= 100 triggers Draw --

    "compute Draw status when halfMoveClock reaches 100" in {
      val p = makeParser()
      // King vs king, halfmove clock = 100 → fifty-move rule draw
      val result = p.parseE("4k3/8/8/8/8/8/8/4K3 w - - 100 75")
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Draw)
    }

    "compute Playing status when halfMoveClock is 99" in {
      val p = makeParser()
      // Add a rook so it's not insufficient material
      val result = p.parseE("4k3/8/8/8/8/8/8/R3K3 w Q - 99 50")
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Playing)
    }

  // -------------------------------------------------------------------------
  // Plug in each implementation
  // -------------------------------------------------------------------------

  "FastFenParser" should {
    behave like aCorrectFenParser(() => FastFenParser())
  }

  "RegExFenParser" should {
    behave like aCorrectFenParser(() => RegExFenParser())
  }

  "FenCombinatorParser" should {
    behave like aCorrectFenParser(() => FenCombinatorParser())
  }

  // -------------------------------------------------------------------------
  // Cross-parser roundtrip consistency
  // -------------------------------------------------------------------------

  "All three FenParser implementations" should {

    val parsers: List[(String, FenParser)] = List(
      "FastFenParser"        -> FastFenParser(),
      "RegExFenParser"       -> RegExFenParser(),
      "FenCombinatorParser"  -> FenCombinatorParser()
    )

    def allParseE(fen: String): List[Either[ChessError, Game]] =
      parsers.map { case (_, p) => p.parseE(fen) }

    "produce identical Game for the standard starting position" in {
      val results = allParseE("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      results.foreach(_ shouldBe a[Right[?, ?]])
      results.distinct should have size 1
    }

    "produce identical Game for a midgame position" in {
      val fen = "r1bqk2r/pppp1ppp/2n2n2/2b1p3/2B1P3/3P1N2/PPP2PPP/RNBQK2R w KQkq - 4 5"
      val results = allParseE(fen)
      results.foreach(_ shouldBe a[Right[?, ?]])
      results.distinct should have size 1
    }

    "produce identical Game for the empty board" in {
      val results = allParseE("8/8/8/8/8/8/8/8 w - - 0 1")
      results.foreach(_ shouldBe a[Right[?, ?]])
      results.distinct should have size 1
    }

    "produce identical Game for a position with en-passant on rank 3" in {
      val fen = "rnbqkbnr/pppp1ppp/8/8/4Pp2/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
      val results = allParseE(fen)
      results.foreach(_ shouldBe a[Right[?, ?]])
      results.distinct should have size 1
    }

    "produce identical Game for a position with en-passant on rank 6" in {
      val fen = "rnbqkbnr/pppp1ppp/8/4p3/8/8/PPPPPPPP/RNBQKBNR w KQkq e6 0 1"
      val results = allParseE(fen)
      results.foreach(_ shouldBe a[Right[?, ?]])
      results.distinct should have size 1
    }

    "produce identical Left error for an invalid piece character" in {
      val fen = "xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
      val results = allParseE(fen)
      results.foreach(_ shouldBe a[Left[?, ?]])
      results.distinct should have size 1
    }

    "produce identical Left error for an invalid active color" in {
      val fen = "8/8/8/8/8/8/8/8 z - - 0 1"
      val results = allParseE(fen)
      results.foreach(_ shouldBe a[Left[?, ?]])
      results.distinct should have size 1
    }

    "produce identical Game for partial castling rights (K only)" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w K - 0 1"
      val results = allParseE(fen)
      results.foreach(_ shouldBe a[Right[?, ?]])
      results.distinct should have size 1
    }

    "produce identical Game for partial castling rights (q only)" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w q - 0 1"
      val results = allParseE(fen)
      results.foreach(_ shouldBe a[Right[?, ?]])
      results.distinct should have size 1
    }
  }
