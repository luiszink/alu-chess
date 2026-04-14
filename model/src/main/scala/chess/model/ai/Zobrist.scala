package chess.model.ai

import chess.model.{Board, Color, Game, Move, Piece, Position}

import java.util.Random

/** Zobrist hashing for chess positions.
  *
  * Produces a 64-bit key that identifies a position uniquely enough to be used
  * as a transposition-table key. Hashing is recomputed from the Game on each
  * call (O(64) board scan) — fast enough at the search depths we reach. */
object Zobrist:

  private val rnd = Random(0xC0FFEEL)

  // 12 piece kinds (6 types × 2 colors) × 64 squares
  private val pieceSquare: Array[Array[Long]] =
    Array.fill(12, 64)(rnd.nextLong())
  private val blackToMove: Long = rnd.nextLong()
  // Castling rights: 0=WK, 1=WQ, 2=BK, 3=BQ
  private val castling: Array[Long] = Array.fill(4)(rnd.nextLong())
  // En-passant file 0..7
  private val epFile: Array[Long] = Array.fill(8)(rnd.nextLong())

  /** 0..11 piece index: White [K,Q,R,B,N,P] = 0..5, Black = 6..11. */
  def pieceIndex(piece: Piece): Int =
    val kindIdx = piece match
      case Piece.King(_)   => 0
      case Piece.Queen(_)  => 1
      case Piece.Rook(_)   => 2
      case Piece.Bishop(_) => 3
      case Piece.Knight(_) => 4
      case Piece.Pawn(_)   => 5
    if piece.color == Color.White then kindIdx else kindIdx + 6

  /** Compute the Zobrist key for a position. */
  def hash(game: Game): Long =
    var key = 0L

    // Pieces
    val board = game.board
    var r = 0
    while r < 8 do
      var c = 0
      while c < 8 do
        board.cell(r, c) match
          case Some(p) => key ^= pieceSquare(pieceIndex(p))(r * 8 + c)
          case None    => ()
        c += 1
      r += 1

    // Side to move
    if game.currentPlayer == Color.Black then key ^= blackToMove

    // Castling rights (derived from movedPieces and actual occupancy)
    val mp = game.movedPieces
    // White king on e1, rook h1 → WK; rook a1 → WQ
    if !mp.contains(Position(0, 4)) && board.cell(0, 4).contains(Piece.King(Color.White)) then
      if !mp.contains(Position(0, 7)) && board.cell(0, 7).contains(Piece.Rook(Color.White)) then
        key ^= castling(0)
      if !mp.contains(Position(0, 0)) && board.cell(0, 0).contains(Piece.Rook(Color.White)) then
        key ^= castling(1)
    if !mp.contains(Position(7, 4)) && board.cell(7, 4).contains(Piece.King(Color.Black)) then
      if !mp.contains(Position(7, 7)) && board.cell(7, 7).contains(Piece.Rook(Color.Black)) then
        key ^= castling(2)
      if !mp.contains(Position(7, 0)) && board.cell(7, 0).contains(Piece.Rook(Color.Black)) then
        key ^= castling(3)

    // En-passant file only if the right is position-relevant (a capture is possible)
    game.lastMove match
      case Some(m) if isDoublePawnPush(m, board) && hasRelevantEnPassant(game, m) =>
        key ^= epFile(m.to.col)
      case _                                     => ()

    key

  private def isDoublePawnPush(m: Move, board: Board): Boolean =
    board.cell(m.to) match
      case Some(Piece.Pawn(_)) => math.abs(m.to.row - m.from.row) == 2
      case _                   => false

  private def hasRelevantEnPassant(game: Game, m: Move): Boolean =
    val board = game.board
    val sideToMove = game.currentPlayer
    val pawnRow = m.to.row
    val file = m.to.col

    def isCapturingPawnAt(col: Int): Boolean =
      col >= 0 && col < 8 && board.cell(pawnRow, col).contains(Piece.Pawn(sideToMove))

    isCapturingPawnAt(file - 1) || isCapturingPawnAt(file + 1)
