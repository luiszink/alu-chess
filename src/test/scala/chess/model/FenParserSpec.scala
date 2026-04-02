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

    "return Left(InvalidFenFormat) for too-few fields" in {
      val p = makeParser()
      p.parseE("rnbqkbnr/pppppppp/8/8") shouldBe
        Left(ChessError.InvalidFenFormat("FEN requires either 1 field (board only) or at least 4 fields"))
    }

    "return Left(InvalidFenFormat) for wrong rank count" in {
      val p = makeParser()
      p.parseE("rnbqkbnr/pppppppp/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe
        Left(ChessError.InvalidFenFormat("Expected 8 ranks"))
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
