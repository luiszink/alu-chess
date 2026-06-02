package chess.model.ai

import chess.model.{Game, Move}

/** Public facade for the chess AI.
  *
  * Usage:
  * {{{
  *   ChessAI.selectMove(game)                         // 3-second budget, depth 6
  *   ChessAI.selectMove(game, timeLimitMs = 1000L)    // 1-second budget
  *   ChessAI.selectMove(game, maxDepth = 4)           // fixed depth (no time limit)
  * }}}
  *
  * Returns None when the position is already terminal (checkmate/stalemate/draw). */
object ChessAI:

  /** Default time budget per move in milliseconds. */
  val DefaultTimeLimitMs: Long = 3000L

  /** Default maximum search depth (iterative deepening cap).
    * At depth 6 with alpha-beta + quiescence this typically reaches 1700-2000 Elo. */
  val DefaultMaxDepth: Int = 6

  /** Select the best move for the current player.
    *
    * @param game        Position to search (terminal positions return None immediately)
    * @param timeLimitMs Time budget in milliseconds (default 3 s)
    * @param maxDepth    Hard cap on search depth (default 6)
    * @return Best legal move found, or None if the game is already over */
  def selectMove(
    game: Game,
    timeLimitMs: Long = DefaultTimeLimitMs,
    maxDepth: Int     = DefaultMaxDepth
  ): Option[Move] =
    if game.status.isTerminal then None
    else AlphaBeta.bestMove(game, timeLimitMs, maxDepth)
