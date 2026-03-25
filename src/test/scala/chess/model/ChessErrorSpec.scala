package chess.model

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ChessErrorSpec extends AnyWordSpec with Matchers:

  val pos = Position(1, 4)
  val from = Position(1, 0)
  val to = Position(3, 0)
  val move = Move(from, to)

  "ChessError.message" should {
    "return non-empty message for InvalidMoveFormat" in {
      ChessError.InvalidMoveFormat("xyz").message should include("xyz")
    }
    "return non-empty message for InvalidPositionString" in {
      ChessError.InvalidPositionString("zz").message should include("zz")
    }
    "return non-empty message for InvalidPromotionPiece" in {
      ChessError.InvalidPromotionPiece('X').message should include("X")
    }
    "return non-empty message for NoPieceAtSource" in {
      ChessError.NoPieceAtSource(pos).message should include(pos.toString)
    }
    "return non-empty message for WrongColorPiece" in {
      val err = ChessError.WrongColorPiece(pos, Color.White, Color.Black)
      err.message should include(pos.toString)
      err.message should include("White")
      err.message should include("Black")
    }
    "return non-empty message for FriendlyFire" in {
      val err = ChessError.FriendlyFire(from, to)
      err.message should include(from.toString)
      err.message should include(to.toString)
    }
    "return non-empty message for IllegalMovePattern" in {
      ChessError.IllegalMovePattern(move).message should not be empty
    }
    "return non-empty message for LeavesKingInCheck" in {
      ChessError.LeavesKingInCheck(move).message should not be empty
    }
    "return non-empty message for InvalidFenFormat" in {
      ChessError.InvalidFenFormat("bad fen").message should include("bad fen")
    }
    "return non-empty message for InvalidFenBoardRow" in {
      ChessError.InvalidFenBoardRow("rank8").message should include("rank8")
    }
    "return non-empty message for InvalidFenPieceChar" in {
      ChessError.InvalidFenPieceChar('X').message should include("X")
    }
    "return non-empty message for InvalidFenColor" in {
      ChessError.InvalidFenColor("x").message should include("x")
    }
    "return non-empty message for GameAlreadyOver" in {
      ChessError.GameAlreadyOver(GameStatus.Checkmate).message should include("Checkmate")
    }
  }
