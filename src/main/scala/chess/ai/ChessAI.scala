package chess.ai

import chess.model.{Game, Move}

/** Trait for a chess AI that selects a move for the current player in a given game state. */
trait ChessAI:
  /** Display name of this AI strategy. */
  def name: String

  /** Select a move for the current player.
    * Returns None if the game is already over or no legal moves exist. */
  def selectMove(game: Game): Option[Move]
