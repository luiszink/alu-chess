package chess.ai

import chess.model.{Game, Move, MoveValidator}
import scala.util.Random

/** Selects a uniformly random legal move for the current player.
  * Use a fixed seed for reproducible behaviour in tests. */
class RandomAI(seed: Option[Long] = None) extends ChessAI:
  private val rng: Random = seed.fold(new Random())(new Random(_))

  val name: String = "RandomAI"

  def selectMove(game: Game): Option[Move] =
    if game.status.isTerminal then None
    else
      val moves = MoveValidator.legalMoves(
        game.board, game.currentPlayer, game.movedPieces, game.lastMove
      )
      if moves.isEmpty then None
      else Some(moves(rng.nextInt(moves.size)))
