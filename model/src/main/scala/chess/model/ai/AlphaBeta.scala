package chess.model.ai

import chess.model.{Game, Move, Board, Color, Piece, GameStatus, MoveValidator}

/** Minimax search with Alpha-Beta pruning, quiescence search, and iterative deepening.
  *
  * Search enhancements:
  *   - Transposition Table (Zobrist-keyed) with bound-aware cutoffs and TT-move ordering
  *   - Null-Move Pruning (R=2, with non-pawn-material and in-check guards)
  *   - Quiescence Search extended with check evasions and checking moves
  *
  * Public API is pure: `bestMove(game, timeLimitMs, maxDepth): Option[Move]`.
  * Locally-scoped mutable state (killer/history tables, TT) is confined to a single call. */
object AlphaBeta:

  val CheckmateScore: Int = 100_000
  private val MaxKillerPly: Int = 64
  private val NullMoveR: Int    = 2
  private val SoftDeadlineMarginMs: Long = 10L
  private val MaxQuiescenceDepth: Int = 10

  /** Find the best move for the current player using iterative deepening alpha-beta. */
  def bestMove(game: Game, timeLimitMs: Long = 3000L, maxDepth: Int = 6): Option[Move] =
    if game.status.isTerminal then return None
    val rootMoves = MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove)
    if rootMoves.isEmpty then return None

    // Always take a forced mate in one immediately.
    rootMoves.find { move =>
      game.applyMove(move).exists(_.status == GameStatus.Checkmate)
    } match
      case Some(m) => return Some(m)
      case None    => ()

    val now = System.nanoTime()
    val hardDeadline = now + timeLimitMs * 1_000_000L
    val softMarginNanos = math.min(SoftDeadlineMarginMs * 1_000_000L, (timeLimitMs.max(1L) * 1_000_000L) / 20L)
    val deadline =
      val candidate = hardDeadline - softMarginNanos
      if candidate > now then candidate else hardDeadline

    val killers  = Array.fill(MaxKillerPly)(Array.fill[Option[Move]](2)(None))
    val history  = Array.fill(6 * 64)(0)
    val tt       = TranspositionTable()

    var bestSoFar: Option[Move] = rootMoves.headOption
    var depth = 1
    while depth <= maxDepth && hasTimeLeft(deadline) do
      val (score, candidate, completed) = rootSearch(game, depth, killers, history, tt, deadline)
      if completed && candidate.isDefined then
        bestSoFar = candidate
      if completed && score >= CheckmateScore - MaxKillerPly then
        return bestSoFar
      depth += 1

    bestSoFar

  // --- Root search ---

  private def rootSearch(
    game: Game,
    depth: Int,
    killers: Array[Array[Option[Move]]],
    history: Array[Int],
    tt: TranspositionTable,
    deadline: Long
  ): (Int, Option[Move], Boolean) =
    val key    = Zobrist.hash(game)
    val ttMove = tt.probe(key).flatMap(_.move)
    val moves  = MoveOrderer.orderMoves(
      MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove),
      game, killers, history, 0, ttMove
    )

    var alpha    = Int.MinValue / 2
    val beta     = Int.MaxValue / 2
    var bestMove: Option[Move] = None
    val iter     = moves.iterator

    while iter.hasNext && hasTimeLeft(deadline) do
      val move = iter.next()
      game.applyMove(move) match
        case None => ()
        case Some(next) =>
          val score = -negamax(next, depth - 1, -beta, -alpha, 1, deadline, killers, history, tt)
          if score > alpha then
            alpha    = score
            bestMove = Some(move)

    val completed = !iter.hasNext
    if completed then
      bestMove.foreach { m =>
        tt.store(key, depth, alpha, TranspositionTable.Bound.Exact, Some(m))
      }
    (alpha, bestMove, completed)

  // --- Negamax with alpha-beta pruning ---

  private def negamax(
    game: Game,
    depth: Int,
    alpha: Int,
    beta: Int,
    ply: Int,
    deadline: Long,
    killers: Array[Array[Option[Move]]],
    history: Array[Int],
    tt: TranspositionTable
  ): Int =
    if !hasTimeLeft(deadline) then return 0
    if game.status.isTerminal then return terminalScore(game, ply)
    if ply > 0 && isRepetition(game) then return 0
    if depth <= 0 then return quiescence(game, alpha, beta, ply, deadline, 0)

    // --- TT probe ---
    val key      = Zobrist.hash(game)
    val ttEntry  = tt.probe(key)
    ttEntry match
      case Some(e) if e.depth >= depth && !isRepetition(game) =>
        e.bound match
          case TranspositionTable.Bound.Exact                       => return e.score
          case TranspositionTable.Bound.Lower if e.score >= beta    => return e.score
          case TranspositionTable.Bound.Upper if e.score <= alpha   => return e.score
          case _                                                    => ()
      case _ => ()
    val ttMove = ttEntry.flatMap(_.move)

    val inCheck = MoveValidator.isInCheck(game.board, game.currentPlayer)

    // --- Null-Move Pruning ---
    if depth >= 3
       && !inCheck
       && beta < CheckmateScore - MaxKillerPly
       && beta > -(CheckmateScore - MaxKillerPly)
       && hasNonPawnMaterial(game.board, game.currentPlayer)
    then
      val nullGame = game.copy(
        currentPlayer = game.currentPlayer.opposite,
        lastMove      = None
      )
      val nullScore = -negamax(nullGame, depth - 1 - NullMoveR, -beta, -beta + 1, ply + 1, deadline, killers, history, tt)
      if nullScore >= beta then return nullScore

    val moves = MoveOrderer.orderMoves(
      MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove),
      game, killers, history, ply, ttMove
    )
    if moves.isEmpty then return terminalScore(game, ply)

    var currentAlpha   = alpha
    var bestScore      = Int.MinValue / 2
    var bestMoveLocal: Option[Move] = None
    var cutoff         = false
    val iter           = moves.iterator

    while iter.hasNext && !cutoff && hasTimeLeft(deadline) do
      val move = iter.next()
      game.applyMove(move) match
        case None => ()
        case Some(next) =>
          val score = -negamax(next, depth - 1, -beta, -currentAlpha, ply + 1, deadline, killers, history, tt)
          if score > bestScore then
            bestScore     = score
            bestMoveLocal = Some(move)
          if score > currentAlpha then currentAlpha = score
          if currentAlpha >= beta then
            if !isCapture(move, game) then
              storeKiller(killers, ply, move)
              updateHistory(history, game.board, move, depth)
            cutoff = true

    if bestScore == Int.MinValue / 2 then return 0

    val bound =
      if bestScore <= alpha then TranspositionTable.Bound.Upper
      else if bestScore >= beta then TranspositionTable.Bound.Lower
      else TranspositionTable.Bound.Exact
    tt.store(key, depth, bestScore, bound, bestMoveLocal)

    bestScore

  // --- Quiescence search (captures + promotions + checks + check evasions) ---

  private def quiescence(
    game: Game,
    alpha: Int,
    beta: Int,
    ply: Int,
    deadline: Long,
    qDepth: Int
  ): Int =
    if !hasTimeLeft(deadline) then return 0
    if game.status.isTerminal then return terminalScore(game, ply)
    if qDepth >= MaxQuiescenceDepth then
      return perspectiveScore(Evaluator.evaluate(game.board), game.currentPlayer)

    val inCheck = MoveValidator.isInCheck(game.board, game.currentPlayer)

    var currentAlpha = alpha
    var best         = Int.MinValue / 2

    if !inCheck then
      val standPat = perspectiveScore(Evaluator.evaluate(game.board), game.currentPlayer)
      if standPat >= beta then return beta
      best = standPat
      if standPat > currentAlpha then currentAlpha = standPat

    val legal = MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove)

    // When in check: all evasions. Otherwise: captures, promotions, checking moves.
    val tactical =
      if inCheck then legal
      else legal.filter(m =>
        isCapture(m, game) || m.promotion.isDefined || givesCheck(m, game)
      )

    if tactical.isEmpty && inCheck then
      // No legal move while in check → mate
      return terminalScore(game, ply)

    var cutoff = false
    val iter   = tactical.iterator
    while iter.hasNext && !cutoff && hasTimeLeft(deadline) do
      val move = iter.next()
      game.applyMove(move) match
        case None => ()
        case Some(next) =>
          val score = -quiescence(next, -beta, -currentAlpha, ply + 1, deadline, qDepth + 1)
          if score > best then best = score
          if score > currentAlpha then currentAlpha = score
          if currentAlpha >= beta then cutoff = true

    if best == Int.MinValue / 2 then
      // No tactical moves searched → return stand-pat (alpha) when quiet
      if inCheck then terminalScore(game, ply) else alpha
    else best

  // --- Helpers ---

  private def terminalScore(game: Game, ply: Int): Int =
    game.status match
      case GameStatus.Checkmate                              => -(CheckmateScore - ply)
      case GameStatus.Stalemate | GameStatus.Draw           =>  0
      case GameStatus.Resigned | GameStatus.TimeOut         => -(CheckmateScore - ply)
      case _                                                 =>
        perspectiveScore(Evaluator.evaluate(game.board), game.currentPlayer)

  private def perspectiveScore(whiteScore: Int, currentPlayer: Color): Int =
    if currentPlayer == Color.White then whiteScore else -whiteScore

  private def isCapture(move: Move, game: Game): Boolean =
    game.board.cell(move.to).isDefined ||
      (game.board.cell(move.from).exists { case Piece.Pawn(_) => true; case _ => false }
        && move.from.col != move.to.col)

  private def givesCheck(move: Move, game: Game): Boolean =
    game.applyMove(move).exists(g =>
      MoveValidator.isInCheck(g.board, g.currentPlayer)
    )

  /** True if `color` has any piece other than king or pawn (null-move safety). */
  private def hasNonPawnMaterial(board: Board, color: Color): Boolean =
    var r = 0
    while r < 8 do
      var c = 0
      while c < 8 do
        board.cell(r, c) match
          case Some(p) if p.color == color =>
            p match
              case Piece.King(_) | Piece.Pawn(_) => ()
              case _ => return true
          case _ => ()
        c += 1
      r += 1
    false

  private def storeKiller(killers: Array[Array[Option[Move]]], ply: Int, move: Move): Unit =
    if ply < killers.length then
      killers(ply)(1) = killers(ply)(0)
      killers(ply)(0) = Some(move)

  private def updateHistory(history: Array[Int], board: Board, move: Move, depth: Int): Unit =
    board.cell(move.from) match
      case Some(piece) =>
        val idx = MoveOrderer.pieceTypeIndex(piece) * 64 + move.to.row * 8 + move.to.col
        history(idx) = (history(idx) + depth * depth).min(1_000_000)
      case None => ()

  private def hasTimeLeft(deadline: Long): Boolean =
    System.nanoTime() < deadline

  private def isRepetition(game: Game): Boolean =
    game.repetitionKeys.nonEmpty && {
      val current = game.repetitionKeys.last
      game.repetitionKeys.init.contains(current)
    }
