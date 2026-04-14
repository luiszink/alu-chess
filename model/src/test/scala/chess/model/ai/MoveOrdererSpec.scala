package chess.model.ai

import chess.model.{Board, Color, Game, GameStatus, Move, Piece, Position}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class MoveOrdererSpec extends AnyWordSpec with Matchers {

  private def baseGame(lastMove: Option[Move] = None): Game =
    val board = Board.empty
      .put(Position(0, 4), Piece.King(Color.White))
      .put(Position(7, 4), Piece.King(Color.Black))
      .put(Position(0, 6), Piece.Rook(Color.White))
    Game(board, Color.White, GameStatus.Playing, lastMove = lastMove)

  "MoveOrderer.orderMoves" should {

    "de-prioritize immediate backtracking moves" in {
      val game = baseGame(lastMove = Some(Move(Position(0, 7), Position(0, 6))))
      val backtrack = Move(Position(0, 6), Position(0, 7))
      val alternative = Move(Position(0, 6), Position(1, 6))

      val ordered = MoveOrderer.orderMoves(
        List(backtrack, alternative),
        game,
        killers = Array.fill(64)(Array.fill[Option[Move]](2)(None)),
        history = Array.fill(6 * 64)(0),
        ply = 0,
        ttMove = None
      )

      ordered.head shouldBe alternative
    }

    "still place ttMove first even if it is a backtrack" in {
      val game = baseGame(lastMove = Some(Move(Position(0, 7), Position(0, 6))))
      val backtrack = Move(Position(0, 6), Position(0, 7))
      val alternative = Move(Position(0, 6), Position(1, 6))

      val ordered = MoveOrderer.orderMoves(
        List(backtrack, alternative),
        game,
        killers = Array.fill(64)(Array.fill[Option[Move]](2)(None)),
        history = Array.fill(6 * 64)(0),
        ply = 0,
        ttMove = Some(backtrack)
      )

      ordered.head shouldBe backtrack
    }
  }
}
