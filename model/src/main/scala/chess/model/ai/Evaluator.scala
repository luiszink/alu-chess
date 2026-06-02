package chess.model.ai

import chess.model.{Board, Color, Piece}

/** Static board evaluation from White's perspective.
  * Positive score = White advantage, negative = Black advantage.
  * Does NOT handle terminal positions (checkmate/stalemate) — caller must check game.status first. */
object Evaluator:

  // Piece base values in centipawns
  def pieceValue(piece: Piece): Int = piece match
    case Piece.Queen(_)  => 900
    case Piece.Rook(_)   => 500
    case Piece.Bishop(_) => 330
    case Piece.Knight(_) => 320
    case Piece.Pawn(_)   => 100
    case Piece.King(_)   => 20000

  // Piece-square tables — row 0 = rank 1 (White's starting side), row 7 = rank 8.
  // For Black pieces, mirror vertically: use table(7 - row)(col).

  private val PawnTable: Array[Array[Int]] = Array(
    Array(  0,  0,  0,  0,  0,  0,  0,  0),
    Array(  5, 10, 10,-20,-20, 10, 10,  5),
    Array(  5, -5,-10,  0,  0,-10, -5,  5),
    Array(  0,  0,  0, 20, 20,  0,  0,  0),
    Array(  5,  5, 10, 25, 25, 10,  5,  5),
    Array( 10, 10, 20, 30, 30, 20, 10, 10),
    Array( 50, 50, 50, 50, 50, 50, 50, 50),
    Array(  0,  0,  0,  0,  0,  0,  0,  0)
  )

  private val KnightTable: Array[Array[Int]] = Array(
    Array(-50,-40,-30,-30,-30,-30,-40,-50),
    Array(-40,-20,  0,  5,  5,  0,-20,-40),
    Array(-30,  5, 10, 15, 15, 10,  5,-30),
    Array(-30,  0, 15, 20, 20, 15,  0,-30),
    Array(-30,  5, 15, 20, 20, 15,  5,-30),
    Array(-30,  0, 10, 15, 15, 10,  0,-30),
    Array(-40,-20,  0,  0,  0,  0,-20,-40),
    Array(-50,-40,-30,-30,-30,-30,-40,-50)
  )

  private val BishopTable: Array[Array[Int]] = Array(
    Array(-20,-10,-10,-10,-10,-10,-10,-20),
    Array(-10,  5,  0,  0,  0,  0,  5,-10),
    Array(-10, 10, 10, 10, 10, 10, 10,-10),
    Array(-10,  0, 10, 10, 10, 10,  0,-10),
    Array(-10,  5,  5, 10, 10,  5,  5,-10),
    Array(-10,  0,  5, 10, 10,  5,  0,-10),
    Array(-10,  0,  0,  0,  0,  0,  0,-10),
    Array(-20,-10,-10,-10,-10,-10,-10,-20)
  )

  private val RookTable: Array[Array[Int]] = Array(
    Array(  0,  0,  0,  5,  5,  0,  0,  0),
    Array( -5,  0,  0,  0,  0,  0,  0, -5),
    Array( -5,  0,  0,  0,  0,  0,  0, -5),
    Array( -5,  0,  0,  0,  0,  0,  0, -5),
    Array( -5,  0,  0,  0,  0,  0,  0, -5),
    Array( -5,  0,  0,  0,  0,  0,  0, -5),
    Array(  5, 10, 10, 10, 10, 10, 10,  5),
    Array(  0,  0,  0,  0,  0,  0,  0,  0)
  )

  private val QueenTable: Array[Array[Int]] = Array(
    Array(-20,-10,-10, -5, -5,-10,-10,-20),
    Array(-10,  0,  5,  0,  0,  0,  0,-10),
    Array(-10,  5,  5,  5,  5,  5,  0,-10),
    Array(  0,  0,  5,  5,  5,  5,  0, -5),
    Array( -5,  0,  5,  5,  5,  5,  0, -5),
    Array(-10,  0,  5,  5,  5,  5,  0,-10),
    Array(-10,  0,  0,  0,  0,  0,  0,-10),
    Array(-20,-10,-10, -5, -5,-10,-10,-20)
  )

  private val KingMidTable: Array[Array[Int]] = Array(
    Array( 20, 30, 10,  0,  0, 10, 30, 20),
    Array( 20, 20,  0,  0,  0,  0, 20, 20),
    Array(-10,-20,-20,-20,-20,-20,-20,-10),
    Array(-20,-30,-30,-40,-40,-30,-30,-20),
    Array(-30,-40,-40,-50,-50,-40,-40,-30),
    Array(-30,-40,-40,-50,-50,-40,-40,-30),
    Array(-30,-40,-40,-50,-50,-40,-40,-30),
    Array(-30,-40,-40,-50,-50,-40,-40,-30)
  )

  private val KingEndTable: Array[Array[Int]] = Array(
    Array(-50,-30,-30,-30,-30,-30,-30,-50),
    Array(-30,-30,  0,  0,  0,  0,-30,-30),
    Array(-30,-10, 20, 30, 30, 20,-10,-30),
    Array(-30,-10, 30, 40, 40, 30,-10,-30),
    Array(-30,-10, 30, 40, 40, 30,-10,-30),
    Array(-30,-10, 20, 30, 30, 20,-10,-30),
    Array(-30,-20,-10,  0,  0,-10,-20,-30),
    Array(-50,-40,-30,-20,-20,-30,-40,-50)
  )

  /** Evaluate board position from White's perspective (centipawns).
    * Caller must handle terminal positions (checkmate/stalemate) before calling this.
    *
    * Implementation: a single 64-cell pass accumulates piece values + non-king PST,
    * non-pawn material (for endgame detection), bishop counts, pawn columns, and
    * pawn positions. After the pass, king PST (which depends on the endgame flag)
    * and pawn-structure bonuses are added in O(8) + O(pawns). */
  def evaluate(board: Board): Int =
    var score = 0
    var nonPawnMaterial = 0
    var whiteBishops = 0
    var blackBishops = 0
    val whitePawnCols = new Array[Int](8)
    val blackPawnCols = new Array[Int](8)
    val whitePawns    = new Array[Int](16) // packed row*8+col
    val blackPawns    = new Array[Int](16)
    var whitePawnCount = 0
    var blackPawnCount = 0
    var whiteKingRow = -1
    var whiteKingCol = -1
    var blackKingRow = -1
    var blackKingCol = -1

    var r = 0
    while r < 8 do
      var c = 0
      while c < 8 do
        board.cell(r, c) match
          case None => ()
          case Some(piece) =>
            val white    = piece.color == Color.White
            val sign     = if white then 1 else -1
            val tableRow = if white then r else 7 - r
            score += sign * pieceValue(piece)
            piece match
              case Piece.Pawn(_) =>
                score += sign * PawnTable(tableRow)(c)
                if white then
                  whitePawnCols(c) += 1
                  whitePawns(whitePawnCount) = r * 8 + c
                  whitePawnCount += 1
                else
                  blackPawnCols(c) += 1
                  blackPawns(blackPawnCount) = r * 8 + c
                  blackPawnCount += 1
              case Piece.Knight(_) =>
                score += sign * KnightTable(tableRow)(c)
                nonPawnMaterial += pieceValue(piece)
              case Piece.Bishop(_) =>
                score += sign * BishopTable(tableRow)(c)
                nonPawnMaterial += pieceValue(piece)
                if white then whiteBishops += 1 else blackBishops += 1
              case Piece.Rook(_) =>
                score += sign * RookTable(tableRow)(c)
                nonPawnMaterial += pieceValue(piece)
              case Piece.Queen(_) =>
                score += sign * QueenTable(tableRow)(c)
                nonPawnMaterial += pieceValue(piece)
              case Piece.King(_) =>
                if white then
                  whiteKingRow = r; whiteKingCol = c
                else
                  blackKingRow = r; blackKingCol = c
        c += 1
      r += 1

    // King PST is endgame-dependent, so applied after non-pawn material is known.
    val kingTable = if nonPawnMaterial < 1300 then KingEndTable else KingMidTable
    if whiteKingRow >= 0 then score += kingTable(whiteKingRow)(whiteKingCol)
    if blackKingRow >= 0 then score -= kingTable(7 - blackKingRow)(blackKingCol)

    // Bishop pair
    if whiteBishops >= 2 then score += 30
    if blackBishops >= 2 then score -= 30

    // Doubled + isolated pawns (per file)
    var c = 0
    while c < 8 do
      val wCol = whitePawnCols(c)
      val bCol = blackPawnCols(c)
      if wCol > 1 then score -= 10 * (wCol - 1)
      if bCol > 1 then score += 10 * (bCol - 1)
      val wLeft  = if c > 0 then whitePawnCols(c - 1) else 0
      val wRight = if c < 7 then whitePawnCols(c + 1) else 0
      if wCol > 0 && wLeft == 0 && wRight == 0 then score -= 10
      val bLeft  = if c > 0 then blackPawnCols(c - 1) else 0
      val bRight = if c < 7 then blackPawnCols(c + 1) else 0
      if bCol > 0 && bLeft == 0 && bRight == 0 then score += 10
      c += 1

    // Passed pawns (no opposing pawn on same or adjacent files)
    var i = 0
    while i < whitePawnCount do
      val pos = whitePawns(i)
      val pr  = pos >>> 3
      val pc  = pos & 7
      val noOpposing =
        blackPawnCols(pc) == 0 &&
          (pc <= 0 || blackPawnCols(pc - 1) == 0) &&
          (pc >= 7 || blackPawnCols(pc + 1) == 0)
      if noOpposing then
        score += (if pr >= 5 then 50 else if pr >= 4 then 30 else if pr >= 3 then 20 else 0)
      i += 1
    i = 0
    while i < blackPawnCount do
      val pos = blackPawns(i)
      val pr  = pos >>> 3
      val pc  = pos & 7
      val noOpposing =
        whitePawnCols(pc) == 0 &&
          (pc <= 0 || whitePawnCols(pc - 1) == 0) &&
          (pc >= 7 || whitePawnCols(pc + 1) == 0)
      if noOpposing then
        score -= (if pr <= 2 then 50 else if pr <= 3 then 30 else if pr <= 4 then 20 else 0)
      i += 1

    score
