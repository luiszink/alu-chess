package chess.ai

import chess.model.{Game, Move, Piece, MoveValidator}
import scala.util.Random

/** Greedy AI: always captures the highest-value enemy piece available.
  * If no capture is possible, falls back to a random legal move.
  *
  * Piece values: Pawn=1, Knight=3, Bishop=3, Rook=5, Queen=9, King=100.
  * Use a fixed seed for reproducible behaviour in tests. */
class GreedyAI(seed: Option[Long] = None) extends ChessAI:
  private val rng: Random = seed.fold(new Random())(new Random(_))

  val name: String = "GreedyAI"

  private def pieceValue(piece: Piece): Int = piece match
    case Piece.Pawn(_)   => 1
    case Piece.Knight(_) => 3
    case Piece.Bishop(_) => 3
    case Piece.Rook(_)   => 5
    case Piece.Queen(_)  => 9
    case Piece.King(_)   => 100

  def selectMove(game: Game): Option[Move] =
    if game.status.isTerminal then None
    else
      val moves = MoveValidator.legalMoves(
        game.board, game.currentPlayer, game.movedPieces, game.lastMove
      )
      if moves.isEmpty then None
      else
        val captures = moves.flatMap { m =>
          game.board.cell(m.to).map(captured => (m, pieceValue(captured)))
        }
        if captures.nonEmpty then
          val maxVal  = captures.map(_._2).max
          val best    = captures.filter(_._2 == maxVal)
          Some(best(rng.nextInt(best.size))._1)
        else
          Some(moves(rng.nextInt(moves.size)))
