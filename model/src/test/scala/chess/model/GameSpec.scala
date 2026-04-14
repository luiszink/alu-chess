package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class GameSpec extends AnyWordSpec with Matchers {

  "A new Game" should {

    val game = Game.newGame

    "start with white as current player" in {
      game.currentPlayer shouldBe Color.White
    }

    "have an initial board" in {
      game.board shouldBe Board.initial
    }

    "have status Playing" in {
      game.status shouldBe GameStatus.Playing
    }
  }

  "switchPlayer" should {

    "toggle from white to black" in {
      val game = Game.newGame.switchPlayer
      game.currentPlayer shouldBe Color.Black
    }

    "toggle from black back to white" in {
      val game = Game.newGame.switchPlayer.switchPlayer
      game.currentPlayer shouldBe Color.White
    }
  }

  "applyMove" should {

    "move a white pawn from e2 to e4" in {
      val game = Game.newGame
      val move = Move(Position(1, 4), Position(3, 4))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(3, 4)) shouldBe Some(Piece.Pawn(Color.White))
      result.get.board.cell(Position(1, 4)) shouldBe None
      result.get.currentPlayer shouldBe Color.Black
    }

    "reject a move from an empty square" in {
      val game = Game.newGame
      val move = Move(Position(3, 3), Position(4, 3))
      game.applyMove(move) shouldBe None
    }

    "reject a move of the opponent's piece" in {
      val game = Game.newGame
      val move = Move(Position(6, 4), Position(4, 4)) // black pawn, but white's turn
      game.applyMove(move) shouldBe None
    }

    "reject capturing a friendly piece" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(0, 1), Piece.Knight(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(0, 0), Position(0, 1))
      game.applyMove(move) shouldBe None
    }

    "allow capturing an opponent's piece" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(0, 1), Piece.Knight(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(0, 0), Position(0, 1))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(0, 1)) shouldBe Some(Piece.Rook(Color.White))
    }

    "reject a move that leaves own king in check" in {
      // White rook shields white king from black rook. Moving rook away exposes king.
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 3), Piece.Rook(Color.White))  // shields king on file
        .put(Position(0, 0), Piece.Rook(Color.Black))  // attacks along row 0
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      // move white rook off the row -> exposes king to black rook
      val move = Move(Position(0, 3), Position(3, 3))
      game.applyMove(move) shouldBe None
    }

    "set status to Check when opponent king is in check" in {
      // After white's move, black king will be in check
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(1, 0), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      // Move rook to 7th rank to give check
      val move = Move(Position(1, 0), Position(7, 0))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.status shouldBe GameStatus.Check
    }

    "set status to Checkmate when opponent has no legal moves" in {
      // Back-rank mate: black king at g8 boxed in by own pawns
      // White rook on a7 will move to a8, delivering mate
      val board = Board.empty
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(6, 0), Piece.Rook(Color.White))  // a7 -> moves to a8
        .put(Position(7, 6), Piece.King(Color.Black))   // g8
        .put(Position(6, 5), Piece.Pawn(Color.Black))   // f7
        .put(Position(6, 6), Piece.Pawn(Color.Black))   // g7
        .put(Position(6, 7), Piece.Pawn(Color.Black))   // h7
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(6, 0), Position(7, 0)) // Ra7 -> Ra8#
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.status shouldBe GameStatus.Checkmate
    }

    "set status to Stalemate when opponent has no legal moves but is not in check" in {
      // K vs K+Q, black king cornered but not in check, no legal moves
      val board = Board.empty
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(5, 1), Piece.Queen(Color.White))
        .put(Position(7, 0), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      // Move queen to b6 to create stalemate: black king on a8, queen on b6
      // King on a8, queen on b6 blocks a7,b7,b8 and king covers a1
      // Actually: King at a8 (7,0), Queen at b6 (5,1) -> 
      // a7(6,0) attacked by queen, b8(7,1) attacked by queen, b7(6,1) attacked by queen
      // That's stalemate if black is not in check
      val move = Move(Position(5, 1), Position(5, 1))  // placeholder
      // Let me construct precise stalemate:
      // Black king at a8 (7,0). White queen needs to control a7,b8,b7 without checking.
      // Queen at c6 (5,2): controls a8? No. Let me think differently.
      // Classic stalemate: Black K at h8 (7,7), White K at f7 (6,5), White Q at g5 (4,6)
      // Then Qg6 is stalemate: K can't go to g8(blocked by Q diagonal), h7 (blocked by K+Q)
      val board2 = Board.empty
        .put(Position(6, 5), Piece.King(Color.White))   // f7
        .put(Position(4, 6), Piece.Queen(Color.White))  // g5
        .put(Position(7, 7), Piece.King(Color.Black))   // h8
      val game2 = Game(board2, Color.White, GameStatus.Playing)
      // Qg5 -> Qg6: queen moves to (5,6)
      val move2 = Move(Position(4, 6), Position(5, 6)) // g5 -> g6
      val result2 = game2.applyMove(move2)
      result2 shouldBe defined
      result2.get.status shouldBe GameStatus.Stalemate
    }
  }

  "resign" should {

    "set status to Resigned" in {
      val game = Game.newGame.resign
      game.status shouldBe GameStatus.Resigned
    }
  }

  // ---------- Castling ----------

  "Castling" should {

    "move the rook on white king-side castling" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 7), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(0, 4), Position(0, 6))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(0, 6)) shouldBe Some(Piece.King(Color.White))
      result.get.board.cell(Position(0, 5)) shouldBe Some(Piece.Rook(Color.White))
      result.get.board.cell(Position(0, 7)) shouldBe None
      result.get.board.cell(Position(0, 4)) shouldBe None
    }

    "move the rook on white queen-side castling" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(0, 4), Position(0, 2))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(0, 2)) shouldBe Some(Piece.King(Color.White))
      result.get.board.cell(Position(0, 3)) shouldBe Some(Piece.Rook(Color.White))
      result.get.board.cell(Position(0, 0)) shouldBe None
      result.get.board.cell(Position(0, 4)) shouldBe None
    }

    "prevent castling after king has moved" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 7), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing, movedPieces = Set(Position(0, 4)))
      val move = Move(Position(0, 4), Position(0, 6))
      game.applyMove(move) shouldBe None
    }

    "track movedPieces after castling" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 7), Piece.Rook(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(0, 4), Position(0, 6))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.movedPieces should contain(Position(0, 4))
    }
  }

  // ---------- En Passant ----------

  "En Passant" should {

    "capture the opponent's pawn" in {
      val board = Board.empty
        .put(Position(4, 4), Piece.Pawn(Color.White))
        .put(Position(4, 3), Piece.Pawn(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val lastMove = Some(Move(Position(6, 3), Position(4, 3)))
      val game = Game(board, Color.White, GameStatus.Playing, lastMove = lastMove)
      val move = Move(Position(4, 4), Position(5, 3))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(5, 3)) shouldBe Some(Piece.Pawn(Color.White))
      result.get.board.cell(Position(4, 3)) shouldBe None
      result.get.board.cell(Position(4, 4)) shouldBe None
    }

    "reject en passant without opponent double push" in {
      val board = Board.empty
        .put(Position(4, 4), Piece.Pawn(Color.White))
        .put(Position(4, 3), Piece.Pawn(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val lastMove = Some(Move(Position(5, 3), Position(4, 3)))
      val game = Game(board, Color.White, GameStatus.Playing, lastMove = lastMove)
      val move = Move(Position(4, 4), Position(5, 3))
      game.applyMove(move) shouldBe None
    }
  }

  // ---------- Promotion ----------

  "Promotion" should {

    "auto-promote pawn to queen" in {
      val board = Board.empty
        .put(Position(6, 0), Piece.Pawn(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(6, 0), Position(7, 0))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(7, 0)) shouldBe Some(Piece.Queen(Color.White))
    }

    "promote pawn to specified knight" in {
      val board = Board.empty
        .put(Position(6, 0), Piece.Pawn(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(6, 0), Position(7, 0), Some('N'))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(7, 0)) shouldBe Some(Piece.Knight(Color.White))
    }

    "promote pawn to rook on capture" in {
      val board = Board.empty
        .put(Position(6, 0), Piece.Pawn(Color.White))
        .put(Position(7, 1), Piece.Knight(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(6, 0), Position(7, 1), Some('R'))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(7, 1)) shouldBe Some(Piece.Rook(Color.White))
    }

    "promote pawn to bishop" in {
      val board = Board.empty
        .put(Position(6, 0), Piece.Pawn(Color.White))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(6, 0), Position(7, 0), Some('B'))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.board.cell(Position(7, 0)) shouldBe Some(Piece.Bishop(Color.White))
    }
  }

  // ---------- Draw ----------

  "Draw conditions" should {

    "detect draw by insufficient material (K+B captures last piece)" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(3, 3), Piece.Bishop(Color.White))
        .put(Position(7, 7), Piece.King(Color.Black))
        .put(Position(5, 5), Piece.Knight(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(3, 3), Position(5, 5))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.status shouldBe GameStatus.Draw
    }

    "detect draw by 50-move rule" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(3, 3), Piece.Rook(Color.White))
        .put(Position(7, 7), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing, halfMoveClock = 99)
      val move = Move(Position(3, 3), Position(3, 4))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.status shouldBe GameStatus.Draw
    }

    "reset halfMoveClock on pawn move" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(1, 1), Piece.Pawn(Color.White))
        .put(Position(7, 7), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing, halfMoveClock = 50)
      val move = Move(Position(1, 1), Position(2, 1))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.halfMoveClock shouldBe 0
    }

    "reset halfMoveClock on capture" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(3, 3), Piece.Rook(Color.White))
        .put(Position(3, 5), Piece.Knight(Color.Black))
        .put(Position(7, 7), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing, halfMoveClock = 50)
      val move = Move(Position(3, 3), Position(3, 5))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.halfMoveClock shouldBe 0
    }

    "increment halfMoveClock on quiet move" in {
      val board = Board.empty
        .put(Position(0, 0), Piece.King(Color.White))
        .put(Position(3, 3), Piece.Rook(Color.White))
        .put(Position(7, 7), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing, halfMoveClock = 10)
      val move = Move(Position(3, 3), Position(3, 4))
      val result = game.applyMove(move)
      result shouldBe defined
      result.get.halfMoveClock shouldBe 11
    }

    "detect draw by threefold repetition" in {
      val result = for
        g1 <- Game.newGame.applyMove(Move(Position(0, 6), Position(2, 5))) // Ng1-f3
        g2 <- g1.applyMove(Move(Position(7, 6), Position(5, 5)))            // Ng8-f6
        g3 <- g2.applyMove(Move(Position(2, 5), Position(0, 6)))            // Nf3-g1
        g4 <- g3.applyMove(Move(Position(5, 5), Position(7, 6)))            // Nf6-g8 (2nd occurrence)
        g5 <- g4.applyMove(Move(Position(0, 6), Position(2, 5)))            // Ng1-f3
        g6 <- g5.applyMove(Move(Position(7, 6), Position(5, 5)))            // Ng8-f6
        g7 <- g6.applyMove(Move(Position(2, 5), Position(0, 6)))            // Nf3-g1
        g8 <- g7.applyMove(Move(Position(5, 5), Position(7, 6)))            // Nf6-g8 (3rd occurrence)
      yield g8

      result shouldBe defined
      result.get.status shouldBe GameStatus.Draw
    }

    "detect draw by threefold repetition for rook shuffling" in {
      val board = Board.empty
        .put(Position(1, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
        .put(Position(0, 6), Piece.Rook(Color.White))
        .put(Position(0, 1), Piece.Rook(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)

      val result = for
        g1 <- game.applyMove(Move(Position(0, 6), Position(0, 7))) // g1-h1
        g2 <- g1.applyMove(Move(Position(0, 1), Position(0, 0)))   // b1-a1
        g3 <- g2.applyMove(Move(Position(0, 7), Position(0, 6)))   // h1-g1
        g4 <- g3.applyMove(Move(Position(0, 0), Position(0, 1)))   // a1-b1 (2nd occurrence)
        g5 <- g4.applyMove(Move(Position(0, 6), Position(0, 7)))   // g1-h1
        g6 <- g5.applyMove(Move(Position(0, 1), Position(0, 0)))   // b1-a1
        g7 <- g6.applyMove(Move(Position(0, 7), Position(0, 6)))   // h1-g1
        g8 <- g7.applyMove(Move(Position(0, 0), Position(0, 1)))   // a1-b1 (3rd occurrence)
      yield g8

      result shouldBe defined
      result.get.status shouldBe GameStatus.Draw
    }
  }

  // ---------- applyMoveE ----------

  "applyMoveE" should {

    "return Right for a valid move" in {
      val game = Game.newGame
      val move = Move(Position(1, 4), Position(3, 4)) // e2 e4
      game.applyMoveE(move) shouldBe a[Right[?, ?]]
    }

    "return Left(NoPieceAtSource) when source is empty" in {
      val game = Game.newGame
      val move = Move(Position(3, 3), Position(4, 3)) // empty square
      game.applyMoveE(move) shouldBe Left(ChessError.NoPieceAtSource(Position(3, 3)))
    }

    "return Left(WrongColorPiece) when wrong color moves" in {
      val game = Game.newGame
      val move = Move(Position(6, 4), Position(4, 4)) // black pawn, white's turn
      game.applyMoveE(move) shouldBe Left(
        ChessError.WrongColorPiece(Position(6, 4), Color.White, Color.Black)
      )
    }

    "return Left(FriendlyFire) when capturing own piece" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 7), Piece.Rook(Color.White))
        .put(Position(1, 7), Piece.Pawn(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(0, 7), Position(1, 7))
      game.applyMoveE(move) shouldBe Left(ChessError.FriendlyFire(Position(0, 7), Position(1, 7)))
    }

    "return Left(IllegalMovePattern) for an illegal move pattern" in {
      val game = Game.newGame
      val move = Move(Position(1, 4), Position(4, 4)) // pawn moving 3 squares
      game.applyMoveE(move) shouldBe Left(ChessError.IllegalMovePattern(move))
    }

    "return Left(LeavesKingInCheck) when move exposes king" in {
      val board = Board.empty
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(0, 0), Piece.Rook(Color.White))
        .put(Position(0, 7), Piece.Rook(Color.Black))
        .put(Position(7, 4), Piece.King(Color.Black))
      val game = Game(board, Color.White, GameStatus.Playing)
      val move = Move(Position(0, 0), Position(1, 0)) // rook moves off first rank, exposing king
      game.applyMoveE(move) shouldBe Left(ChessError.LeavesKingInCheck(move))
    }

    "be consistent with applyMove (property)" in {
      val game = Game.newGame
      val moves = Seq(
        Move(Position(1, 4), Position(3, 4)),  // valid
        Move(Position(3, 3), Position(4, 3)),  // empty source
        Move(Position(6, 4), Position(4, 4)),  // wrong color
        Move(Position(1, 4), Position(4, 4))   // illegal pattern
      )
      for m <- moves do
        game.applyMove(m) shouldBe game.applyMoveE(m).toOption
    }
  }

  // ---------- GameStatus.isTerminal ----------

  "GameStatus.isTerminal" should {
    "return false for Playing" in {
      GameStatus.Playing.isTerminal shouldBe false
    }
    "return false for Check" in {
      GameStatus.Check.isTerminal shouldBe false
    }
    "return true for Checkmate" in {
      GameStatus.Checkmate.isTerminal shouldBe true
    }
    "return true for Stalemate" in {
      GameStatus.Stalemate.isTerminal shouldBe true
    }
    "return true for Resigned" in {
      GameStatus.Resigned.isTerminal shouldBe true
    }
    "return true for Draw" in {
      GameStatus.Draw.isTerminal shouldBe true
    }
    "return true for TimeOut" in {
      GameStatus.TimeOut.isTerminal shouldBe true
    }
  }

  // ---------- MoveHistory ----------

  "moveHistory" should {

    "be empty for new game" in {
      Game.newGame.moveHistory shouldBe empty
    }

    "record moves as MoveEntry" in {
      val game = Game.newGame
        .applyMove(Move(Position(1, 4), Position(3, 4))).get // e4
      game.moveHistory should have size 1
      game.moveHistory.head.san shouldBe "e4"
    }

    "record multiple moves" in {
      val game = Game.newGame
        .applyMove(Move(Position(1, 4), Position(3, 4))).get // e4
        .applyMove(Move(Position(6, 4), Position(4, 4))).get // e5
      game.moveHistory should have size 2
    }
  }

  // ---------- fullMoveNumber ----------

  "fullMoveNumber" should {

    "start at 1" in {
      Game.newGame.fullMoveNumber shouldBe 1
    }

    "stay 1 after white's move" in {
      val game = Game.newGame
        .applyMove(Move(Position(1, 4), Position(3, 4))).get
      game.fullMoveNumber shouldBe 1
    }

    "increment to 2 after black's move" in {
      val game = Game.newGame
        .applyMove(Move(Position(1, 4), Position(3, 4))).get
        .applyMove(Move(Position(6, 4), Position(4, 4))).get
      game.fullMoveNumber shouldBe 2
    }
  }

  // ---------- captured piece tracking ----------

  "computeUpdatedGame captured piece" should {

    "track en passant capture" in {
      val board = Board.empty
        .put(Position(4, 4), Piece.Pawn(Color.White))
        .put(Position(4, 3), Piece.Pawn(Color.Black))
        .put(Position(0, 4), Piece.King(Color.White))
        .put(Position(7, 4), Piece.King(Color.Black))
      val lastMove = Some(Move(Position(6, 3), Position(4, 3)))
      val game = Game(board, Color.White, GameStatus.Playing, lastMove = lastMove)
      val move = Move(Position(4, 4), Position(5, 3))
      val result = game.applyMove(move).get
      // The en passant capture should be recorded in moveHistory
      result.moveHistory.last.captured shouldBe Some(Piece.Pawn(Color.Black))
    }
  }
}
