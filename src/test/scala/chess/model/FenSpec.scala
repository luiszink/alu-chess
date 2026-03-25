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
  }
