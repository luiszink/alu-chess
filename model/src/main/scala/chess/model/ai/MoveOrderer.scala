package chess.model.ai

import chess.model.{Game, Board, Move, Piece, Position}

/** Scores and sorts moves to maximise Alpha-Beta cutoff efficiency.
  *
  * Priority order:
  *   1. Winning/equal captures  (MVV-LVA)
  *   2. Promotions
  *   3. Losing captures         (MVV-LVA, still above quiets)
  *   4. Killer moves            (quiet moves that caused cutoffs at this ply)
  *   5. History heuristic       (quiet moves that historically caused cutoffs)
  *   6. All other quiet moves   (history score = 0) */
object MoveOrderer:

  private val TTBase        = 20_000_000
  private val MvvLvaBase    = 10_000_000
  private val PromotionBase =  9_000_000
  private val KillerBonus   =  8_000_000
  private val BacktrackPenalty = 150_000

  /** Sort moves in descending priority (highest-score first).
    * If `ttMove` is supplied it is placed first unconditionally. */
  def orderMoves(
    moves: List[Move],
    game: Game,
    killers: Array[Array[Option[Move]]],
    history: Array[Int],
    ply: Int,
    ttMove: Option[Move] = None
  ): List[Move] =
    moves.sortBy(-moveScore(_, game, killers, history, ply, ttMove))

  private def moveScore(
    move: Move,
    game: Game,
    killers: Array[Array[Option[Move]]],
    history: Array[Int],
    ply: Int,
    ttMove: Option[Move]
  ): Int =
    val board    = game.board
    val captured = capturedPiece(move, game)
    val backtrackPenalty = if isOwnBacktrack(move, game) then BacktrackPenalty else 0

    if ttMove.contains(move) then
      TTBase
    else if captured.isDefined then
      MvvLvaBase + mvvLva(move, board, captured) - backtrackPenalty
    else if move.promotion.isDefined then
      PromotionBase - backtrackPenalty
    else if ply < killers.length && isKiller(move, killers(ply)) then
      KillerBonus - backtrackPenalty
    else
      historyScore(move, board, history) - backtrackPenalty

  /** True if `move` reverses the current player's *own* previous move.
    * `moveHistory` ends with the opponent's most recent move, so the current
    * player's prior move is at index `length - 2`. */
  private def isOwnBacktrack(move: Move, game: Game): Boolean =
    val history = game.moveHistory
    history.length >= 2 && {
      val ownPrev = history(history.length - 2).move
      move.from == ownPrev.to && move.to == ownPrev.from
    }

  /** The piece captured by this move, if any (including en passant). */
  def capturedPiece(move: Move, game: Game): Option[Piece] =
    game.board.cell(move.to).orElse(
      // en passant: pawn moves diagonally to an empty square
      game.board.cell(move.from).flatMap {
        case Piece.Pawn(_) if move.from.col != move.to.col =>
          game.board.cell(Position(move.from.row, move.to.col))
        case _ => None
      }
    )

  /** MVV-LVA: Most Valuable Victim – Least Valuable Attacker.
    * Higher = better capture (e.g. Pawn×Queen >> Queen×Pawn). */
  private def mvvLva(move: Move, board: Board, victim: Option[Piece]): Int =
    val victimVal   = victim.map(victimScore).getOrElse(100)
    val attackerVal = board.cell(move.from).map(attackerScore).getOrElse(0)
    victimVal * 10 - attackerVal

  private def isKiller(move: Move, slots: Array[Option[Move]]): Boolean =
    slots(0).contains(move) || slots(1).contains(move)

  private def historyScore(move: Move, board: Board, history: Array[Int]): Int =
    board.cell(move.from).map { piece =>
      history(pieceTypeIndex(piece) * 64 + move.to.row * 8 + move.to.col)
    }.getOrElse(0)

  // Victim value: higher = more valuable target
  private def victimScore(piece: Piece): Int = piece match
    case Piece.Queen(_)  => 900
    case Piece.Rook(_)   => 500
    case Piece.Bishop(_) => 330
    case Piece.Knight(_) => 320
    case Piece.Pawn(_)   => 100
    case Piece.King(_)   => 10000

  // Attacker penalty: lower = cheaper attacker (prefer pawn captures)
  private def attackerScore(piece: Piece): Int = piece match
    case Piece.Pawn(_)   => 1
    case Piece.Knight(_) => 3
    case Piece.Bishop(_) => 3
    case Piece.Rook(_)   => 5
    case Piece.Queen(_)  => 9
    case Piece.King(_)   => 10

  /** Maps piece type to a 0-5 index for the history table. */
  def pieceTypeIndex(piece: Piece): Int = piece match
    case Piece.King(_)   => 0
    case Piece.Queen(_)  => 1
    case Piece.Rook(_)   => 2
    case Piece.Bishop(_) => 3
    case Piece.Knight(_) => 4
    case Piece.Pawn(_)   => 5
