package chess.model.fen

import chess.model.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Runs the same test suite against all three FEN parser implementations to
  * verify that they are mutually consistent. */
class FenParserSpec extends AnyWordSpec with Matchers:

  private val startFen  = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
  private val afterE4   = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
  private val emptyFen  = "8/8/8/8/8/8/8/8 w - - 0 1"
  private val boardOnly = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR"
  private val fen4field = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq -"

  private val parsers: Seq[(String, FenParser)] = Seq(
    "FastFenParser"       -> FastFenParser,
    "RegexFenParser"      -> RegexFenParser,
    "CombinatorFenParser" -> CombinatorFenParser
  )

  for (name, parser) <- parsers do
    s"$name" should {

      "parse the standard starting position" in {
        val result = parser.parseE(startFen)
        result shouldBe a[Right[?, ?]]
        val game = result.toOption.get
        game.board shouldBe Board.initial
        game.currentPlayer shouldBe Color.White
        game.status shouldBe GameStatus.Playing
        game.halfMoveClock shouldBe 0
        game.fullMoveNumber shouldBe 1
      }

      "parse black to move with en-passant" in {
        val result = parser.parseE(afterE4)
        result shouldBe a[Right[?, ?]]
        val game = result.toOption.get
        game.currentPlayer shouldBe Color.Black
        game.lastMove shouldBe defined
      }

      "parse an empty board" in {
        val result = parser.parseE(emptyFen)
        result shouldBe a[Right[?, ?]]
        result.toOption.get.board shouldBe Board.empty
      }

      "parse a board-only FEN with defaults" in {
        val result = parser.parseE(boardOnly)
        result shouldBe a[Right[?, ?]]
        val game = result.toOption.get
        game.board shouldBe Board.initial
        game.currentPlayer shouldBe Color.White
        game.halfMoveClock shouldBe 0
      }

      "parse a 4-field FEN with default clocks" in {
        val result = parser.parseE(fen4field)
        result shouldBe a[Right[?, ?]]
        val game = result.toOption.get
        game.halfMoveClock shouldBe 0
        game.fullMoveNumber shouldBe 1
      }

      "return Left for a completely invalid FEN" in {
        parser.parseE("not a fen at all!!!") shouldBe a[Left[?, ?]]
      }

      "return Left for wrong number of fields (2 or 3)" in {
        parser.parseE("rnbqkbnr w") shouldBe a[Left[?, ?]]
      }

      "return Left for an invalid piece character" in {
        parser.parseE("xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe a[Left[?, ?]]
      }

      "return Left for wrong rank count (7 ranks)" in {
        parser.parseE("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP w KQkq - 0 1") shouldBe a[Left[?, ?]]
      }

      "return Left for an invalid active color" in {
        parser.parseE("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1") shouldBe a[Left[?, ?]]
      }

      "return None for invalid input via parse()" in {
        parser.parse("bad") shouldBe None
      }

      "return a Failure for invalid input via parseT()" in {
        parser.parseT("bad").isFailure shouldBe true
      }

      "return a Success for valid input via parseT()" in {
        parser.parseT(startFen).isSuccess shouldBe true
      }

      "produce results consistent with Fen.parse for the starting position" in {
        val expected = Fen.parse(startFen)
        val actual   = parser.parse(startFen)
        actual shouldBe expected
      }

      "handle castling rights - all available" in {
        val fen    = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
        val result = parser.parseE(fen)
        result shouldBe a[Right[?, ?]]
        result.toOption.get.movedPieces shouldBe empty
      }

      "handle castling rights - none available" in {
        val fen    = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w - - 0 1"
        val result = parser.parseE(fen)
        result shouldBe a[Right[?, ?]]
        val moved = result.toOption.get.movedPieces
        moved should contain(Position(0, 4))
        moved should contain(Position(0, 0))
        moved should contain(Position(0, 7))
      }

    }

  "CombinatorFenParser" should {

    "return Left for an invalid castling token" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkqa - 0 1"
      CombinatorFenParser.parseE(fen) shouldBe a[Left[?, ?]]
    }

    "return Left for an invalid en-passant square token" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - z9 0 1"
      CombinatorFenParser.parseE(fen) shouldBe a[Left[?, ?]]
    }

    "treat a semantically invalid en-passant square as missing" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - e4 0 1"
      val result = CombinatorFenParser.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.toOption.get.lastMove shouldBe empty
    }

    "return Left for non-numeric clocks" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - x 1"
      CombinatorFenParser.parseE(fen) shouldBe a[Left[?, ?]]
    }
  }

  // ── Verify all three parsers agree on a set of representative FENs ────────

  "All three parsers" should {
    "produce identical results for the starting position" in {
      val results = parsers.map { case (_, p) => p.parse(startFen) }
      results.distinct should have size 1
    }

    "produce identical results for after e4" in {
      val results = parsers.map { case (_, p) => p.parse(afterE4) }
      results.distinct should have size 1
    }
  }

  // ── FenParserType enum ────────────────────────────────────────────────────

  "FenParserType" should {
    "return the correct parser instance for each type" in {
      FenParserType.Fast.instance       shouldBe FastFenParser
      FenParserType.Regex.instance      shouldBe RegexFenParser
      FenParserType.Combinator.instance shouldBe CombinatorFenParser
    }

    "each parser instance correctly parses the starting position" in {
      for parserType <- FenParserType.values do
        withClue(s"Parser type: $parserType") {
          parserType.instance.parse(startFen) shouldBe defined
        }
    }

    "switching Fen.activeParser changes the active implementation" in {
      val originalParser = Fen.activeParser
      try
        for parserType <- FenParserType.values do
          Fen.activeParser = parserType
          Fen.activeParser shouldBe parserType
          Fen.activeParser.instance shouldBe parserType.instance
      finally
        Fen.activeParser = originalParser
    }
  }
