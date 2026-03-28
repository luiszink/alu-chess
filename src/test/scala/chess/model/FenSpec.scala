package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class FenSpec extends AnyWordSpec with Matchers:

  "Fen.parse" should {

    "parse the standard starting position" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
      val game = Fen.parse(fen)
      game shouldBe defined
      game.get.board shouldBe Board.initial
      game.get.currentPlayer shouldBe Color.White
      game.get.status shouldBe GameStatus.Playing
      game.get.halfMoveClock shouldBe 0
    }

    "parse black to move" in {
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
      val game = Fen.parse(fen)
      game shouldBe defined
      game.get.currentPlayer shouldBe Color.Black
    }

    "parse an empty board" in {
      val fen = "8/8/8/8/8/8/8/8 w - - 0 1"
      val game = Fen.parse(fen)
      game shouldBe defined
      game.get.board shouldBe Board.empty
    }

    "parse piece placement correctly" in {
      val fen = "8/8/8/8/8/8/8/R3K2R w KQ - 0 1"
      val game = Fen.parse(fen)
      game shouldBe defined
      val b = game.get.board
      b.cell(Position(0, 0)) shouldBe Some(Piece.Rook(Color.White))
      b.cell(Position(0, 4)) shouldBe Some(Piece.King(Color.White))
      b.cell(Position(0, 7)) shouldBe Some(Piece.Rook(Color.White))
      b.cell(Position(0, 1)) shouldBe None
    }

    "handle castling rights - all available" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
      val game = Fen.parse(fen)
      game shouldBe defined
      game.get.movedPieces shouldBe empty
    }

    "handle castling rights - none available" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w - - 0 1"
      val game = Fen.parse(fen)
      game shouldBe defined
      val moved = game.get.movedPieces
      moved should contain(Position(0, 4)) // white king
      moved should contain(Position(0, 0)) // a1 rook
      moved should contain(Position(0, 7)) // h1 rook
      moved should contain(Position(7, 4)) // black king
      moved should contain(Position(7, 0)) // a8 rook
      moved should contain(Position(7, 7)) // h8 rook
    }

    "handle castling rights - only white kingside" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w K - 0 1"
      val game = Fen.parse(fen)
      game shouldBe defined
      val moved = game.get.movedPieces
      moved should not contain Position(0, 7) // h1 rook still available
      moved should not contain Position(0, 4) // white king still available (K present)
      moved should contain(Position(0, 0))    // a1 rook moved (no Q)
      moved should contain(Position(7, 4))    // black king
    }

    "parse en passant target" in {
      val fen = "rnbqkbnr/pppp1ppp/8/4pP2/8/8/PPPPP1PP/RNBQKBNR w KQkq e6 0 3"
      val game = Fen.parse(fen)
      game shouldBe defined
      game.get.lastMove shouldBe defined
      val lm = game.get.lastMove.get
      lm.from shouldBe Position(6, 4) // e7
      lm.to shouldBe Position(4, 4)   // e5
    }

    "parse halfmove clock" in {
      val fen = "8/8/8/8/8/8/8/4K3 w - - 42 21"
      val game = Fen.parse(fen)
      game shouldBe defined
      game.get.halfMoveClock shouldBe 42
    }

    "return None for invalid FEN - too few fields" in {
      Fen.parse("rnbqkbnr/pppppppp/8/8") shouldBe None
    }

    "return None for invalid FEN - wrong rank count" in {
      Fen.parse("rnbqkbnr/pppppppp/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe None
    }

    "return None for invalid FEN - invalid piece character" in {
      Fen.parse("xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe None
    }

    "return None for invalid FEN - rank too long" in {
      Fen.parse("rnbqkbnrr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe None
    }

    "return None for invalid active color" in {
      Fen.parse("8/8/8/8/8/8/8/8 x - - 0 1") shouldBe None
    }

    "parse all test positions without error" in {
      TestPositions.positions.foreach { tp =>
        if tp.fen.nonEmpty then
          withClue(s"Failed on '${tp.name}': ") {
            Fen.parse(tp.fen) shouldBe defined
          }
      }
    }

    "parse 4-field FEN (no halfmove clock or fullmove number)" in {
      val fen = "8/8/8/8/8/8/8/4K3 w - -"
      val game = Fen.parse(fen)
      game shouldBe defined
      game.get.halfMoveClock shouldBe 0
      game.get.fullMoveNumber shouldBe 1
    }

    "parse draw by 50-move rule from FEN" in {
      val fen = "4k3/8/8/8/8/8/8/4K2R w - - 100 51"
      val game = Fen.parse(fen)
      game shouldBe defined
      game.get.status shouldBe GameStatus.Draw
    }
  }

  "Fen.toFen" should {

    "produce valid FEN for the starting position" in {
      val game = Game.newGame
      val fen = Fen.toFen(game)
      fen shouldBe "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
    }

    "roundtrip: parse then toFen produces equivalent game" in {
      val originalFen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
      val game = Fen.parse(originalFen)
      game shouldBe defined
      val resultFen = Fen.toFen(game.get)
      val reparsed = Fen.parse(resultFen)
      reparsed shouldBe defined
      reparsed.get.board shouldBe game.get.board
      reparsed.get.currentPlayer shouldBe game.get.currentPlayer
    }

    "include castling availability" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w Kq - 0 1"
      val game = Fen.parse(fen)
      game shouldBe defined
      val result = Fen.toFen(game.get)
      result should include("Kq")
    }

    "produce '-' castling when no castling rights available" in {
      // Parse FEN with no castling rights; toFen should round-trip to '-'
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w - - 0 1"
      val game = Fen.parse(fen)
      game shouldBe defined
      val result = Fen.toFen(game.get)
      result.split(" ")(2) shouldBe "-"
    }

    "encode en passant square after double pawn push" in {
      val game = Game.newGame.applyMove(Move(Position(1, 4), Position(3, 4))).get // e2-e4
      val fen = Fen.toFen(game)
      fen should include("e3") // en passant target square
    }

    "produce '-' en passant when last move is not a double pawn push" in {
      val game = Game.newGame.applyMove(Move(Position(1, 4), Position(2, 4))).get // e2-e3
      val fen = Fen.toFen(game)
      fen.split(" ")(3) shouldBe "-"
    }

    "produce '-' en passant when last move is a non-pawn moving two rows" in {
      // Construct a game state directly where lastMove is a rook moving 2 rows
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
        .put(Position(2, 0), Piece.Rook(Color.White))
      val lastMove = Some(Move(Position(0, 0), Position(2, 0))) // rook moved 2 rows
      val game = Game(board, Color.Black, GameStatus.Playing, lastMove = lastMove)
      val fen = Fen.toFen(game)
      fen.split(" ")(3) shouldBe "-"
    }
  }

  "Fen.parseE" should {

    "accept board-only FEN and apply defaults" in {
      val fen = "8/8/8/2k5/4K3/8/8/8"
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.currentPlayer) shouldBe Right(Color.White)
      result.map(_.status) shouldBe Right(GameStatus.Draw)
      result.map(_.board.cell(Position(4, 2))) shouldBe Right(Some(Piece.King(Color.Black)))
      result.map(_.board.cell(Position(3, 4))) shouldBe Right(Some(Piece.King(Color.White)))
    }

    "return Right for valid FEN starting position" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.board) shouldBe Right(Board.initial)
      result.map(_.currentPlayer) shouldBe Right(Color.White)
    }

    "return Right for black to move" in {
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
      Fen.parseE(fen).map(_.currentPlayer) shouldBe Right(Color.Black)
    }

    "return Left(InvalidFenFormat) for invalid board-only FEN" in {
      Fen.parseE("rnbqkbnr/pppppppp/8/8") shouldBe Left(ChessError.InvalidFenFormat("Expected 8 ranks"))
    }

    "return Left(InvalidFenFormat) for wrong rank count" in {
      val result = Fen.parseE("rnbqkbnr/pppppppp/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      result shouldBe Left(ChessError.InvalidFenFormat("Expected 8 ranks"))
    }

    "return Left(InvalidFenPieceChar) for invalid piece character" in {
      val result = Fen.parseE("xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      result shouldBe Left(ChessError.InvalidFenPieceChar('x'))
    }

    "return Left(InvalidFenBoardRow) for rank too long" in {
      val result = Fen.parseE("rnbqkbnrr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      result shouldBe a[Left[?, ?]]
      result.left.map(_.isInstanceOf[ChessError.InvalidFenBoardRow]) shouldBe Left(true)
    }

    "return Left(InvalidFenColor) for invalid active color" in {
      val result = Fen.parseE("8/8/8/8/8/8/8/8 x - - 0 1")
      result shouldBe Left(ChessError.InvalidFenColor("x"))
    }

    "be consistent with parse (property)" in {
      val fens = Seq(
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "8/8/8/8/8/8/8/8 w - - 0 1",
        "rnbqkbnr/pppppppp/8/8",
        "xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
        "8/8/8/8/8/8/8/8 x - - 0 1"
      )
      for fen <- fens do
        Fen.parse(fen) shouldBe Fen.parseE(fen).toOption
    }

    "return Left(InvalidFenBoardRow) for rank containing digit '0'" in {
      // '0' as digit represents 0 squares which is invalid (must be 1-8)
      Fen.parseE("0nrrbbkk/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe a[Left[?, ?]]
    }

    "return Left(InvalidFenBoardRow) for rank containing digit '9'" in {
      // '9' would represent 9 empty squares which is invalid for an 8-file board
      Fen.parseE("9/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") shouldBe a[Left[?, ?]]
    }
  }

  "Fen.parseE en passant with invalid target row" should {

    "treat an ep target on an unexpected row as no en passant" in {
      // 'e4' is row 3 (0-indexed) which is neither rank 3 (row 2) nor rank 6 (row 5)
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e4 0 1"
      val game = Fen.parseE(fen)
      game shouldBe a[Right[?, ?]]
      game.map(_.lastMove) shouldBe Right(None)
    }
  }

  "Fen.parseT" should {

    "return Success for valid FEN" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
      Fen.parseT(fen).isSuccess shouldBe true
      Fen.parseT(fen).get.board shouldBe Board.initial
    }

    "return Failure for invalid FEN - too few fields" in {
      Fen.parseT("rnbqkbnr/pppppppp/8/8").isFailure shouldBe true
    }

    "return Failure for invalid piece character" in {
      val result = Fen.parseT("xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      result.isFailure shouldBe true
      result.failed.get.getMessage should not be empty
    }
  }
