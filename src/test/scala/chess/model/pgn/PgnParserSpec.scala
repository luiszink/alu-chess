package chess.model.pgn

import chess.model.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

/** Runs the same test suite against all three PGN parser implementations to
  * verify that they are mutually consistent. */
class PgnParserSpec extends AnyWordSpec with Matchers:

  private val simplePgn =
    """[Event "Test"]
      |[Site "Local"]
      |[White "Alice"]
      |[Black "Bob"]
      |[Result "*"]
      |
      |1. e4 e5 2. Nf3 *""".stripMargin

  private val foolsMate =
    """[Event "Fool's Mate"]
      |[Result "0-1"]
      |
      |1. f3 e5 2. g4 Qh4# 0-1""".stripMargin

  private val withComments =
    """1. e4 {best move} e5 (1...c5) 2. Nf3 *""".stripMargin

  private val headersOnly =
    """[Event "Test"]
      |[Result "*"]""".stripMargin

  private val movetextOnly = "1. e4 e5 2. Nf3 Nc6 *"

  private val parsers: Seq[(String, PgnParser)] = Seq(
    "FastPgnParser"       -> FastPgnParser,
    "RegexPgnParser"      -> RegexPgnParser,
    "CombinatorPgnParser" -> CombinatorPgnParser
  )

  for (name, parser) <- parsers do
    s"$name" should {

      "parse a simple PGN with tags and moves" in {
        val result = parser.parseE(simplePgn)
        result shouldBe a[Right[?, ?]]
        val pgn = result.toOption.get
        pgn.tags("Event") shouldBe "Test"
        pgn.tags("White") shouldBe "Alice"
        pgn.tags("Black") shouldBe "Bob"
        pgn.moves should contain allOf ("e4", "e5", "Nf3")
        pgn.result shouldBe Some("*")
      }

      "parse Fool's Mate" in {
        val result = parser.parseE(foolsMate)
        result shouldBe a[Right[?, ?]]
        val pgn = result.toOption.get
        pgn.tags("Event") shouldBe "Fool's Mate"
        pgn.moves should have size 4
        pgn.result shouldBe Some("0-1")
      }

      "parse PGN with comments and variations" in {
        val result = parser.parseE(withComments)
        result shouldBe a[Right[?, ?]]
        val pgn = result.toOption.get
        pgn.moves should contain allOf ("e4", "e5", "Nf3")
        pgn.moves should not contain "best"
        pgn.moves should not contain "c5"
      }

      "parse PGN with tags only" in {
        val result = parser.parseE(headersOnly)
        result shouldBe a[Right[?, ?]]
        val pgn = result.toOption.get
        pgn.tags("Event") shouldBe "Test"
        pgn.moves shouldBe empty
        pgn.result shouldBe Some("*")
      }

      "parse movetext-only PGN" in {
        val result = parser.parseE(movetextOnly)
        result shouldBe a[Right[?, ?]]
        val pgn = result.toOption.get
        pgn.tags shouldBe empty
        pgn.moves should contain allOf ("e4", "e5", "Nf3", "Nc6")
        pgn.result shouldBe Some("*")
      }

      "return Left for empty input" in {
        parser.parseE("") shouldBe a[Left[?, ?]]
        parser.parseE("   ") shouldBe a[Left[?, ?]]
      }

      "return None for empty input via parse()" in {
        parser.parse("") shouldBe None
      }

      "parse all result types" in {
        for resultStr <- Seq("1-0", "0-1", "1/2-1/2", "*") do
          val pgn = s"1. e4 e5 $resultStr"
          val result = parser.parseE(pgn)
          result shouldBe a[Right[?, ?]]
          result.toOption.get.result shouldBe Some(resultStr)
      }

      "replay a simple game via replayE" in {
        val result = parser.replayE(simplePgn)
        result shouldBe a[Right[?, ?]]
        result.toOption.get.moveHistory should have size 3
      }

      "replay Fool's Mate via replayE" in {
        val result = parser.replayE(foolsMate)
        result shouldBe a[Right[?, ?]]
        result.toOption.get.status shouldBe GameStatus.Checkmate
      }

      "return Left for invalid move via replayE" in {
        val pgn = "1. e4 Zz9 *"
        val result = parser.replayE(pgn)
        result shouldBe a[Left[?, ?]]
      }

      "stop replay after terminal status" in {
        val pgn = "1. f3 e5 2. g4 Qh4# 3. e4 0-1"
        val result = parser.replayE(pgn)
        result shouldBe a[Right[?, ?]]
        result.toOption.get.status shouldBe GameStatus.Checkmate
      }

      "parse PGN with result 1-0 in tags" in {
        val pgn =
          """[Result "1-0"]
            |
            |1. e4 e5 1-0""".stripMargin
        val result = parser.parseE(pgn)
        result shouldBe a[Right[?, ?]]
        result.toOption.get.result shouldBe Some("1-0")
      }

      "parse PGN with result 1/2-1/2" in {
        val pgn = "1. e4 e5 1/2-1/2"
        val result = parser.parseE(pgn)
        result shouldBe a[Right[?, ?]]
        result.toOption.get.result shouldBe Some("1/2-1/2")
      }
    }

  // ── Verify all three parsers agree ────────────────────────────────────────

  "All three PGN parsers" should {

    "produce identical PgnGame for a simple PGN" in {
      val results = parsers.map { case (_, p) => p.parseE(simplePgn).toOption.get }
      results.map(_.tags).distinct should have size 1
      results.map(_.moves).distinct should have size 1
      results.map(_.result).distinct should have size 1
    }

    "produce identical PgnGame for Fool's Mate" in {
      val results = parsers.map { case (_, p) => p.parseE(foolsMate).toOption.get }
      results.map(_.moves).distinct should have size 1
      results.map(_.result).distinct should have size 1
    }

    "produce identical replay results" in {
      val results = parsers.map { case (_, p) => p.replayE(simplePgn).toOption.get }
      results.map(_.board).distinct should have size 1
      results.map(_.currentPlayer).distinct should have size 1
      results.map(_.moveHistory.size).distinct should have size 1
    }
  }

  // ── PgnParserType enum ────────────────────────────────────────────────────

  "PgnParserType" should {

    "return the correct parser instance for each type" in {
      PgnParserType.Fast.instance       shouldBe FastPgnParser
      PgnParserType.Regex.instance      shouldBe RegexPgnParser
      PgnParserType.Combinator.instance shouldBe CombinatorPgnParser
    }

    "each parser instance correctly parses a simple PGN" in {
      for parserType <- PgnParserType.values do
        withClue(s"Parser type: $parserType") {
          parserType.instance.parse(simplePgn) shouldBe defined
        }
    }

    "switching Pgn.activeParser changes the active implementation" in {
      val originalParser = Pgn.activeParser
      try
        for parserType <- PgnParserType.values do
          Pgn.activeParser = parserType
          Pgn.activeParser shouldBe parserType
          Pgn.activeParser.instance shouldBe parserType.instance
      finally
        Pgn.activeParser = originalParser
    }
  }

  // ── PgnSharedLogic ────────────────────────────────────────────────────────

  "PgnSharedLogic" should {

    "extract SAN tokens from movetext" in {
      val tokens = PgnSharedLogic.extractSanTokens("1. e4 e5 2. Nf3 Nc6 *")
      tokens shouldBe Vector("e4", "e5", "Nf3", "Nc6")
    }

    "strip comments and variations from tokens" in {
      val tokens = PgnSharedLogic.extractSanTokens("1. e4 {great} e5 (1...c5) 2. Nf3 *")
      tokens shouldBe Vector("e4", "e5", "Nf3")
    }

    "extract result from movetext" in {
      PgnSharedLogic.extractResult("1. e4 e5 1-0") shouldBe Some("1-0")
      PgnSharedLogic.extractResult("1. e4 e5 0-1") shouldBe Some("0-1")
      PgnSharedLogic.extractResult("1. e4 e5 1/2-1/2") shouldBe Some("1/2-1/2")
      PgnSharedLogic.extractResult("1. e4 e5 *") shouldBe Some("*")
    }

    "return None when no result in movetext" in {
      PgnSharedLogic.extractResult("1. e4 e5") shouldBe None
    }

    "parseSAN handles castling for white" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 7), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      PgnSharedLogic.parseSAN("O-O", g) shouldBe defined
      PgnSharedLogic.parseSAN("0-0", g) shouldBe defined
    }

    "parseSAN handles queenside castling" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      PgnSharedLogic.parseSAN("O-O-O", g) shouldBe defined
      PgnSharedLogic.parseSAN("0-0-0", g) shouldBe defined
    }

    "parseSAN handles black castling" in {
      val board = Board.empty
        .put(Position(7, 4), Piece.King(Color.Black))
        .put(Position(7, 7), Piece.Rook(Color.Black))
        .put(Position(7, 0), Piece.Rook(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
      val g = Game(board, Color.Black, GameStatus.Playing)
      PgnSharedLogic.parseSAN("O-O", g) shouldBe defined
      PgnSharedLogic.parseSAN("O-O-O", g) shouldBe defined
    }

    "parseSAN handles piece moves" in {
      val game = Game.newGame
      PgnSharedLogic.parseSAN("Nf3", game) shouldBe defined
    }

    "parseSAN handles pawn moves" in {
      val game = Game.newGame
      PgnSharedLogic.parseSAN("e4", game) shouldBe defined
    }

    "parseSAN handles promotion" in {
      val board = Board.empty
        .put(Position(6, 0), Piece.Pawn(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = PgnSharedLogic.parseSAN("a8=Q", g)
      move shouldBe defined
      move.get.promotion shouldBe Some('Q')
    }

    "parseSAN handles captures" in {
      val board = Board.empty
        .put(Position(3, 4), Piece.Pawn(Color.White))
        .put(Position(4, 3), Piece.Pawn(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      PgnSharedLogic.parseSAN("exd5", g) shouldBe defined
    }

    "parseSAN handles piece capture" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(7, 0), Piece.Knight(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      PgnSharedLogic.parseSAN("Rxa8", g) shouldBe defined
    }

    "parseSAN handles disambiguation by file" in {
      val board = Board.empty
        .put(Position(0, 1), Piece.Knight(Color.White))
        .put(Position(0, 5), Piece.Knight(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = PgnSharedLogic.parseSAN("Nbd2", g)
      move shouldBe defined
      move.get.from shouldBe Position(0, 1)
    }

    "parseSAN handles disambiguation by rank" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(7, 0), Piece.Rook(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = PgnSharedLogic.parseSAN("R1a4", g)
      move shouldBe defined
      move.get.from shouldBe Position(0, 0)
    }

    "parseSAN handles disambiguation by full square" in {
      val board = Board.empty
        .put(Position(0, 1), Piece.Knight(Color.White))
        .put(Position(2, 1), Piece.Knight(Color.White))
        .put(Position(2, 5), Piece.Knight(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val g = Game(board, Color.White, GameStatus.Playing)
      val move = PgnSharedLogic.parseSAN("Nb1d2", g)
      move shouldBe defined
      move.get.from shouldBe Position(0, 1)
    }

    "parseSAN strips check and checkmate suffixes" in {
      val game = Game.newGame
      PgnSharedLogic.parseSAN("e4+", game) shouldBe defined
      PgnSharedLogic.parseSAN("e4#", game) shouldBe defined
    }

    "parseSAN returns None for invalid SAN" in {
      PgnSharedLogic.parseSAN("Z9", Game.newGame) shouldBe None
    }

    "parseSAN returns None for too-short piece move" in {
      PgnSharedLogic.parseSAN("N", Game.newGame) shouldBe None
    }

    "parseSAN returns None for too-short pawn move" in {
      PgnSharedLogic.parseSAN("e", Game.newGame) shouldBe None
    }

    "replayMoves replays a simple game" in {
      val pgn = PgnGame(Map.empty, Vector("e4", "e5", "Nf3"), Some("*"))
      val result = PgnSharedLogic.replayMoves(pgn)
      result shouldBe a[Right[?, ?]]
      result.toOption.get.moveHistory should have size 3
    }

    "replayMoves returns Left for invalid move" in {
      val pgn = PgnGame(Map.empty, Vector("e4", "Zz9"), None)
      val result = PgnSharedLogic.replayMoves(pgn)
      result shouldBe a[Left[?, ?]]
    }

    "replayMoves stops after terminal status" in {
      val pgn = PgnGame(Map.empty, Vector("f3", "e5", "g4", "Qh4#", "e4"), Some("0-1"))
      val result = PgnSharedLogic.replayMoves(pgn)
      result shouldBe a[Right[?, ?]]
      result.toOption.get.status shouldBe GameStatus.Checkmate
    }

    "replayMoves handles empty moves" in {
      val pgn = PgnGame(Map.empty, Vector.empty, Some("*"))
      val result = PgnSharedLogic.replayMoves(pgn)
      result shouldBe a[Right[?, ?]]
      result.toOption.get.moveHistory shouldBe empty
    }
  }

  // ── PgnGame data class ────────────────────────────────────────────────────

  "PgnGame" should {

    "store tags, moves, and result" in {
      val pgn = PgnGame(Map("Event" -> "Test"), Vector("e4", "e5"), Some("*"))
      pgn.tags shouldBe Map("Event" -> "Test")
      pgn.moves shouldBe Vector("e4", "e5")
      pgn.result shouldBe Some("*")
    }

    "support empty tags and moves" in {
      val pgn = PgnGame(Map.empty, Vector.empty, None)
      pgn.tags shouldBe empty
      pgn.moves shouldBe empty
      pgn.result shouldBe None
    }
  }
