package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

/** Tests for the behaviour introduced by `Fen.parseE` calling `computeInitialStatus`.
  * Every test in this file is concerned with the GameStatus that the FEN parser assigns
  * to the returned Game, as well as the correctness of en passant / castling state
  * that affects whether moves are immediately legal after load. */
class FenStatusSpec extends AnyWordSpec with Matchers {

  // -------------------------------------------------------------------------
  // 1. Normal FEN → GameStatus.Playing
  // -------------------------------------------------------------------------

  "Fen.parseE on a normal position" should {

    "assign GameStatus.Playing for the standard starting position" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Playing)
    }

    "assign GameStatus.Playing after 1.e4 (black to move, not in check)" in {
      // After 1.e4 the position is normal; black is not in check and has legal moves.
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Playing)
    }

    "assign GameStatus.Playing for a mid-game position with legal moves available" in {
      // Ruy López tabiya — both sides have many legal moves, no check.
      val fen = "r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3"
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Playing)
    }
  }

  // -------------------------------------------------------------------------
  // 2. FEN with current player in check → GameStatus.Check
  // -------------------------------------------------------------------------

  "Fen.parseE on a position where the current player is in check" should {

    "assign GameStatus.Check when a rook attacks the black king on the e-file (black to move)" in {
      // Black king on e8 (row 7, col 4).  White rook on e1 (row 0, col 4) gives check
      // along the e-file (path rows 1-6 on col 4 is clear).
      // Black king can escape to d8 or f8, so the status is Check, not Checkmate.
      val fen = "r3k2r/pppp1ppp/8/8/8/8/8/K3R3 b kq - 0 1"
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Check)
    }

    "assign GameStatus.Check for white king attacked by a rook (white to move)" in {
      // White king on e1 (0,4) is directly attacked by black rook on e8 (7,4).
      // White has escape moves (king can step sideways), so this is Check not Checkmate.
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 0), Piece.Rook(Color.White))   // gives white an escape-blocker
        .put(Position(7, 4), Piece.King(Color.Black))
        .put(Position(5, 4), Piece.Rook(Color.Black))   // attacks white king on e-file
      val game = Game(board, Color.White, GameStatus.Playing)
      val fen = Fen.toFen(game)
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Check)
    }
  }

  // -------------------------------------------------------------------------
  // 3. FEN with checkmate → GameStatus.Checkmate
  // -------------------------------------------------------------------------

  "Fen.parseE on a checkmate position" should {

    "assign GameStatus.Checkmate for Fool's Mate (white king mated)" in {
      // After 1.f3 e5 2.g4 Qh4#.  White king on e1 is in check from the black queen on h4
      // (diagonal e1-h4), the f3-pawn blocks one escape, and g4 cuts the other.
      // White has no legal moves.
      val fen = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Checkmate)
    }

    "assign GameStatus.Checkmate for a back-rank mate (black king mated)" in {
      // Black king on g8 (7,6), boxed in by own pawns on f7, g7, h7.
      // White rook on a8 (7,0) delivers checkmate — black is to move and has no legal moves.
      val fen = "R5k1/5ppp/8/8/8/8/6PP/6K1 b - - 0 1"
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Checkmate)
    }

    "assign GameStatus.Checkmate for Scholar's Mate position (black king mated)" in {
      // Black king on e8 mated by queen on f7, bishop on c4.
      val fen = "rnbqkb1r/pppp1Qpp/2n2n2/4p3/2B1P3/8/PPPP1PPP/RNB1K1NR b KQkq - 0 4"
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Checkmate)
    }
  }

  // -------------------------------------------------------------------------
  // 4. FEN with stalemate → GameStatus.Stalemate
  // -------------------------------------------------------------------------

  "Fen.parseE on a stalemate position" should {

    "assign GameStatus.Stalemate when the current player has no legal moves and is not in check" in {
      // Classic stalemate trap: black king on a8 (7,0), white queen on c7 (6,2),
      // white king on b6 (5,1).
      // Black king on a8 cannot move: b8 attacked by queen (diagonal c7-b8),
      // a7 attacked by queen (same rank), b7 attacked by queen and king.
      // The king itself is not in check (a8 is not on the queen's rank, file, or diagonal
      // from c7: dr=1, dc=2 — not a sliding attack line).
      val fen = "k7/2Q5/1K6/8/8/8/8/8 b - - 0 1"
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Stalemate)
    }

    "assign GameStatus.Stalemate for the queen + king vs lone king stalemate trap (white to move)" in {
      // White king on f7 (6,5), white queen on g5 (4,6) about to create stalemate on
      // the *next* white move.  Parsed as-is, white has many legal moves → Playing.
      // But if white's queen is already on g6 (5,6) the black king on h8 is stalemated.
      val fen = "7k/8/6QK/8/8/8/8/8 b - - 0 1"
      val result = Fen.parseE(fen)
      result shouldBe a[Right[?, ?]]
      result.map(_.status) shouldBe Right(GameStatus.Stalemate)
    }
  }

  // -------------------------------------------------------------------------
  // 5. FEN parse → game can continue with applyMoveE (legal move succeeds)
  // -------------------------------------------------------------------------

  "A game loaded via Fen.parseE" should {

    "allow a legal pawn move to be applied immediately" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))
      val move = Move(Position(1, 4), Position(3, 4)) // e2-e4
      val result = game.applyMoveE(move)
      result shouldBe a[Right[?, ?]]
      result.map(_.board.cell(Position(3, 4))) shouldBe Right(Some(Piece.Pawn(Color.White)))
      result.map(_.currentPlayer) shouldBe Right(Color.Black)
    }

    "allow a legal knight move to be applied from a mid-game FEN" in {
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))
      val move = Move(Position(7, 6), Position(5, 5)) // Ng8-f6
      val result = game.applyMoveE(move)
      result shouldBe a[Right[?, ?]]
      result.map(_.board.cell(Position(5, 5))) shouldBe Right(Some(Piece.Knight(Color.Black)))
    }

    "reject an illegal move from a parsed FEN" in {
      val fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))
      val illegalMove = Move(Position(0, 0), Position(5, 0)) // rook blocked by own pawn
      game.applyMoveE(illegalMove) shouldBe a[Left[?, ?]]
    }
  }

  // -------------------------------------------------------------------------
  // 6. toFen roundtrip: toFen(parseE(fen)) produces equivalent FEN
  // -------------------------------------------------------------------------

  "Fen.toFen / Fen.parseE roundtrip" should {

    "preserve board, active color, and castling for the starting position" in {
      val originalFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
      val game = Fen.parseE(originalFen).getOrElse(fail("initial parse failed"))
      val roundtrippedFen = Fen.toFen(game)
      val reparsed = Fen.parseE(roundtrippedFen).getOrElse(fail("roundtrip reparse failed"))
      reparsed.board shouldBe game.board
      reparsed.currentPlayer shouldBe game.currentPlayer
      reparsed.movedPieces shouldBe game.movedPieces
      reparsed.status shouldBe game.status
    }

    "preserve board and active color after 1.e4 (en passant target included)" in {
      val originalFen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
      val game = Fen.parseE(originalFen).getOrElse(fail("initial parse failed"))
      val roundtrippedFen = Fen.toFen(game)
      val reparsed = Fen.parseE(roundtrippedFen).getOrElse(fail("roundtrip reparse failed"))
      reparsed.board shouldBe game.board
      reparsed.currentPlayer shouldBe game.currentPlayer
      reparsed.lastMove shouldBe game.lastMove
    }

    "preserve board and castling rights for a castling test position" in {
      val originalFen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
      val game = Fen.parseE(originalFen).getOrElse(fail("initial parse failed"))
      val roundtrippedFen = Fen.toFen(game)
      val reparsed = Fen.parseE(roundtrippedFen).getOrElse(fail("roundtrip reparse failed"))
      reparsed.board shouldBe game.board
      reparsed.movedPieces shouldBe game.movedPieces
    }

    "produce a FEN that parses to the same status" in {
      val originalFen = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
      val game = Fen.parseE(originalFen).getOrElse(fail("initial parse failed"))
      val roundtrippedFen = Fen.toFen(game)
      val reparsed = Fen.parseE(roundtrippedFen).getOrElse(fail("roundtrip reparse failed"))
      reparsed.status shouldBe game.status // both Checkmate
    }
  }

  // -------------------------------------------------------------------------
  // 7. Invalid FEN inputs return Left
  // -------------------------------------------------------------------------

  "Fen.parseE on invalid input" should {

    "return Left(InvalidFenFormat) for an empty string" in {
      val result = Fen.parseE("")
      result shouldBe a[Left[?, ?]]
    }

    "return Left(InvalidFenFormat) for a FEN with only 2 or 3 fields" in {
      Fen.parseE("rnbqkbnr/pppppppp/8/8 w") shouldBe a[Left[?, ?]]
      Fen.parseE("rnbqkbnr/pppppppp/8/8 w K") shouldBe a[Left[?, ?]]
    }

    "return Left(InvalidFenFormat) for wrong number of ranks (7 instead of 8)" in {
      val result = Fen.parseE("rnbqkbnr/pppppppp/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      result shouldBe Left(ChessError.InvalidFenFormat("Expected 8 ranks"))
    }

    "return Left(InvalidFenFormat) for wrong number of ranks (9 instead of 8)" in {
      val result = Fen.parseE("rnbqkbnr/pppppppp/8/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      result shouldBe Left(ChessError.InvalidFenFormat("Expected 8 ranks"))
    }

    "return Left(InvalidFenPieceChar) for an unrecognised piece character" in {
      val result = Fen.parseE("xnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      result shouldBe Left(ChessError.InvalidFenPieceChar('x'))
    }

    "return Left(InvalidFenBoardRow) for a rank that totals more than 8 squares" in {
      val result = Fen.parseE("rnbqkbnrr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1")
      result shouldBe a[Left[?, ?]]
      result.left.map(_.isInstanceOf[ChessError.InvalidFenBoardRow]) shouldBe Left(true)
    }

    "return Left(InvalidFenColor) for an unrecognised active-color token" in {
      val result = Fen.parseE("8/8/8/8/8/8/8/8 x - - 0 1")
      result shouldBe Left(ChessError.InvalidFenColor("x"))
    }

    "return Left(InvalidFenColor) for an upper-case color token" in {
      val result = Fen.parseE("8/8/8/8/8/8/8/8 W - - 0 1")
      result shouldBe Left(ChessError.InvalidFenColor("W"))
    }

    "return Left(InvalidFenFormat) for a board-only input with an impossible rank count" in {
      // Single field that has only 6 slashes (7 ranks)
      val result = Fen.parseE("8/8/8/8/8/8/8")
      result shouldBe Left(ChessError.InvalidFenFormat("Expected 8 ranks"))
    }
  }

  // -------------------------------------------------------------------------
  // 8. En passant: FEN with ep target square → lastMove set → capture is legal
  // -------------------------------------------------------------------------

  "Fen.parseE en passant handling" should {

    "set lastMove so a white pawn can immediately capture en passant" in {
      // White pawn on f5 (4,5), black pawn on e5 (4,4) after e7-e5.
      // FEN ep target is e6 (row 5, col 4).
      val fen = "rnbqkbnr/pppp1ppp/8/4pP2/8/8/PPPPP1PP/RNBQKBNR w KQkq e6 0 3"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))

      // The en passant last move should encode the black pawn's double push e7→e5.
      game.lastMove shouldBe defined
      val lm = game.lastMove.get
      lm.from shouldBe Position(6, 4) // e7
      lm.to   shouldBe Position(4, 4) // e5

      // White pawn on f5 should be able to capture on e6 (the ep square).
      val epCapture = Move(Position(4, 5), Position(5, 4)) // f5 × e6
      val result = game.applyMoveE(epCapture)
      result shouldBe a[Right[?, ?]]
      result.map(_.board.cell(Position(5, 4))) shouldBe Right(Some(Piece.Pawn(Color.White)))
      // The captured pawn must have been removed from e5.
      result.map(_.board.cell(Position(4, 4))) shouldBe Right(None)
    }

    "set lastMove so a black pawn can immediately capture en passant" in {
      // Black pawn on d4 (3,3), white pawn on e4 (3,4) after e2-e4.
      // FEN ep target is e3 (row 2, col 4).
      val fen = "rnbqkbnr/ppp1pppp/8/8/3pP3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 2"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))

      game.lastMove shouldBe defined
      val lm = game.lastMove.get
      lm.from shouldBe Position(1, 4) // e2
      lm.to   shouldBe Position(3, 4) // e4

      // Black pawn on d4 captures en passant on e3.
      val epCapture = Move(Position(3, 3), Position(2, 4)) // d4 × e3
      val result = game.applyMoveE(epCapture)
      result shouldBe a[Right[?, ?]]
      result.map(_.board.cell(Position(2, 4))) shouldBe Right(Some(Piece.Pawn(Color.Black)))
      // The captured white pawn must have been removed from e4.
      result.map(_.board.cell(Position(3, 4))) shouldBe Right(None)
    }

    "set lastMove to None when the ep field is a dash" in {
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))
      game.lastMove shouldBe None
    }

    "not allow an en passant capture when no ep target is present in the FEN" in {
      // Same board layout as the ep test but without the ep target square.
      val fen = "rnbqkbnr/pppp1ppp/8/4pP2/8/8/PPPPP1PP/RNBQKBNR w KQkq - 0 3"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))
      val epCapture = Move(Position(4, 5), Position(5, 4)) // f5 tries e6 ep
      game.applyMoveE(epCapture) shouldBe a[Left[?, ?]]
    }
  }

  // -------------------------------------------------------------------------
  // 9. Castling rights: FEN rights → castling legal; "-" → castling illegal
  // -------------------------------------------------------------------------

  "Fen.parseE castling rights" should {

    "allow white kingside castling when FEN has K right" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))
      val castleKingside = Move(Position(0, 4), Position(0, 6)) // O-O
      val result = game.applyMoveE(castleKingside)
      result shouldBe a[Right[?, ?]]
      result.map(_.board.cell(Position(0, 6))) shouldBe Right(Some(Piece.King(Color.White)))
      result.map(_.board.cell(Position(0, 5))) shouldBe Right(Some(Piece.Rook(Color.White)))
    }

    "allow white queenside castling when FEN has Q right" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))
      val castleQueenside = Move(Position(0, 4), Position(0, 2)) // O-O-O
      val result = game.applyMoveE(castleQueenside)
      result shouldBe a[Right[?, ?]]
      result.map(_.board.cell(Position(0, 2))) shouldBe Right(Some(Piece.King(Color.White)))
      result.map(_.board.cell(Position(0, 3))) shouldBe Right(Some(Piece.Rook(Color.White)))
    }

    "allow black kingside castling when FEN has k right" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R b KQkq - 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))
      val castleKingside = Move(Position(7, 4), Position(7, 6)) // black O-O
      val result = game.applyMoveE(castleKingside)
      result shouldBe a[Right[?, ?]]
      result.map(_.board.cell(Position(7, 6))) shouldBe Right(Some(Piece.King(Color.Black)))
      result.map(_.board.cell(Position(7, 5))) shouldBe Right(Some(Piece.Rook(Color.Black)))
    }

    "prevent castling for both sides when FEN castling field is '-'" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w - - 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))

      // White king-side castling must be rejected.
      val whiteKingside = Move(Position(0, 4), Position(0, 6))
      game.applyMoveE(whiteKingside) shouldBe a[Left[?, ?]]

      // White queen-side castling must also be rejected.
      val whiteQueenside = Move(Position(0, 4), Position(0, 2))
      game.applyMoveE(whiteQueenside) shouldBe a[Left[?, ?]]
    }

    "prevent white queenside castling when only 'K' right is present" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w K - 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))

      // Queenside should be blocked (a1 rook flagged as moved).
      val whiteQueenside = Move(Position(0, 4), Position(0, 2))
      game.applyMoveE(whiteQueenside) shouldBe a[Left[?, ?]]

      // Kingside should still be allowed.
      val whiteKingside = Move(Position(0, 4), Position(0, 6))
      game.applyMoveE(whiteKingside) shouldBe a[Right[?, ?]]
    }

    "prevent black castling when only white rights are present ('KQ')" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R b KQ - 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))

      // Black king should not be able to castle in either direction.
      val blackKingside  = Move(Position(7, 4), Position(7, 6))
      val blackQueenside = Move(Position(7, 4), Position(7, 2))
      game.applyMoveE(blackKingside)  shouldBe a[Left[?, ?]]
      game.applyMoveE(blackQueenside) shouldBe a[Left[?, ?]]
    }

    "reflect castling rights in movedPieces after parsing '-' castling field" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w - - 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))
      // All four rooks and both kings should be in movedPieces.
      game.movedPieces should contain(Position(0, 4)) // white king e1
      game.movedPieces should contain(Position(0, 0)) // white rook a1
      game.movedPieces should contain(Position(0, 7)) // white rook h1
      game.movedPieces should contain(Position(7, 4)) // black king e8
      game.movedPieces should contain(Position(7, 0)) // black rook a8
      game.movedPieces should contain(Position(7, 7)) // black rook h8
    }

    "have empty movedPieces after parsing 'KQkq' castling field" in {
      val fen = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"
      val game = Fen.parseE(fen).getOrElse(fail("parseE returned Left"))
      game.movedPieces shouldBe empty
    }
  }
}
