package chess.model.ai

import chess.model.{Game, Move, Board, Color, Piece, GameStatus, MoveValidator}

/** Minimax search with Alpha-Beta pruning, quiescence search, and iterative deepening.
  *
  * Public API is pure: `bestMove(game, timeLimitMs, maxDepth): Option[Move]`
  * Internally uses locally-scoped mutable state (killer/history tables) for performance. */
object AlphaBeta:

  val CheckmateScore: Int = 100_000
  private val MaxKillerPly: Int = 64

  /** Find the best move for the current player using iterative deepening alpha-beta.
    *
    * @param game        Current game state (must not be terminal)
    * @param timeLimitMs Time budget in milliseconds (default 3 seconds)
    * @param maxDepth    Hard depth cap (default 6; increase for stronger play)
    * @return The best move found within the time limit, or None if the position is terminal */
  def bestMove(game: Game, timeLimitMs: Long = 3000L, maxDepth: Int = 6): Option[Move] =
    if game.status.isTerminal then return None
    val rootMoves = MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove)
    if rootMoves.isEmpty then return None

    val deadline = System.nanoTime() + timeLimitMs * 1_000_000L
    // killers(ply)(0|1) — up to 2 killer moves per ply
    val killers  = Array.fill(MaxKillerPly)(Array.fill[Option[Move]](2)(None))
    // history(pieceTypeIndex * 64 + row * 8 + col) — quiet move bonuses
    val history  = Array.fill(6 * 64)(0)

    var bestSoFar: Option[Move] = rootMoves.headOption
    var depth = 1
    while depth <= maxDepth && System.nanoTime() < deadline do
      val (score, candidate) = rootSearch(game, depth, killers, history, deadline)
      // Only update if we completed the depth within time (candidate is defined means at least one move was searched)
      if candidate.isDefined then
        bestSoFar = candidate
      // Stop early on forced mate
      if score >= CheckmateScore - MaxKillerPly then
        return bestSoFar
      depth += 1

    bestSoFar

  // --- Root search: one full depth, returns (bestScore, bestMove) ---

  private def rootSearch(
    game: Game,
    depth: Int,
    killers: Array[Array[Option[Move]]],
    history: Array[Int],
    deadline: Long
  ): (Int, Option[Move]) =
    val moves = MoveOrderer.orderMoves(
      MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove),
      game, killers, history, 0
    )

    var alpha    = Int.MinValue / 2
    val beta     = Int.MaxValue / 2
    var bestMove: Option[Move] = None
    val iter     = moves.iterator

    while iter.hasNext && System.nanoTime() < deadline do
      val move = iter.next()
      game.applyMove(move) match
        case None => ()
        case Some(next) =>
          val score = -negamax(next, depth - 1, -beta, -alpha, 1, deadline, killers, history)
          if score > alpha then
            alpha    = score
            bestMove = Some(move)

    (alpha, bestMove)

  // --- Negamax with alpha-beta pruning ---

  private def negamax(
    game: Game,
    depth: Int,
    alpha: Int,
    beta: Int,
    ply: Int,
    deadline: Long,
    killers: Array[Array[Option[Move]]],
    history: Array[Int]
  ): Int =
    // Time check
    if System.nanoTime() > deadline then return 0

    // Terminal position
    if game.status.isTerminal then return terminalScore(game, ply)

    // Leaf node: drop into quiescence search
    if depth <= 0 then return quiescence(game, alpha, beta, ply, deadline)

    val moves = MoveOrderer.orderMoves(
      MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove),
      game, killers, history, ply
    )

    // No legal moves — terminal (should be caught by status, but guard anyway)
    if moves.isEmpty then return terminalScore(game, ply)

    var currentAlpha = alpha
    var bestScore    = Int.MinValue / 2
    var cutoff       = false
    val iter         = moves.iterator

    while iter.hasNext && !cutoff && System.nanoTime() < deadline do
      val move = iter.next()
      game.applyMove(move) match
        case None => ()
        case Some(next) =>
          val score = -negamax(next, depth - 1, -beta, -currentAlpha, ply + 1, deadline, killers, history)
          if score > bestScore then bestScore = score
          if score > currentAlpha then currentAlpha = score
          if currentAlpha >= beta then
            // Beta cutoff: record killer and update history for quiet moves
            if !isCapture(move, game) then
              storeKiller(killers, ply, move)
              updateHistory(history, game.board, move, depth)
            cutoff = true

    if bestScore == Int.MinValue / 2 then 0 else bestScore

  // --- Quiescence search: only captures until quiet position ---

  private def quiescence(
    game: Game,
    alpha: Int,
    beta: Int,
    ply: Int,
    deadline: Long
  ): Int =
    if game.status.isTerminal then return terminalScore(game, ply)

    // Stand-pat: current position score as lower bound
    val standPat = perspectiveScore(Evaluator.evaluate(game.board), game.currentPlayer)
    if standPat >= beta then return beta

    var currentAlpha = if standPat > alpha then standPat else alpha
    var best         = standPat

    val captures = MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove)
      .filter(m => isCapture(m, game))

    var cutoff = false
    val iter   = captures.iterator
    while iter.hasNext && !cutoff && System.nanoTime() < deadline do
      val move = iter.next()
      game.applyMove(move) match
        case None => ()
        case Some(next) =>
          val score = -quiescence(next, -beta, -currentAlpha, ply + 1, deadline)
          if score > best then best = score
          if score > currentAlpha then currentAlpha = score
          if currentAlpha >= beta then
            cutoff = true

    best

  // --- Helpers ---

  /** Score for the current player in a terminal position.
    * Prefers shorter mates by subtracting ply (faster mate = higher score). */
  private def terminalScore(game: Game, ply: Int): Int =
    game.status match
      case GameStatus.Checkmate                              => -(CheckmateScore - ply)
      case GameStatus.Stalemate | GameStatus.Draw           =>  0
      case GameStatus.Resigned | GameStatus.TimeOut         => -(CheckmateScore - ply)
      case _                                                 =>
        perspectiveScore(Evaluator.evaluate(game.board), game.currentPlayer)

  /** Convert White-perspective score to current player's perspective. */
  private def perspectiveScore(whiteScore: Int, currentPlayer: Color): Int =
    if currentPlayer == Color.White then whiteScore else -whiteScore

  /** True if the move is a capture (including en passant). */
  private def isCapture(move: Move, game: Game): Boolean =
    game.board.cell(move.to).isDefined ||
      (game.board.cell(move.from).exists { case Piece.Pawn(_) => true; case _ => false }
        && move.from.col != move.to.col)

  private def storeKiller(killers: Array[Array[Option[Move]]], ply: Int, move: Move): Unit =
    if ply < killers.length then
      killers(ply)(1) = killers(ply)(0)
      killers(ply)(0) = Some(move)

  private def updateHistory(history: Array[Int], board: Board, move: Move, depth: Int): Unit =
    board.cell(move.from) match
      case Some(piece) =>
        val idx = MoveOrderer.pieceTypeIndex(piece) * 64 + move.to.row * 8 + move.to.col
        history(idx) = (history(idx) + depth * depth).min(1_000_000) // cap to prevent overflow
      case None => ()
