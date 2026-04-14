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
    * Caller must handle terminal positions (checkmate/stalemate) before calling this. */
  def evaluate(board: Board): Int =
    val endgame = isEndgame(board)
    var score = 0
    var r = 0
    while r < 8 do
      var c = 0
      while c < 8 do
        board.cell(r, c) match
          case None => ()
          case Some(piece) =>
            val sign     = if piece.color == Color.White then 1 else -1
            val tableRow = if piece.color == Color.White then r else 7 - r
            val pst      = pstFor(piece, endgame)
            score += sign * (pieceValue(piece) + pst(tableRow)(c))
        c += 1
      r += 1
    score + bonuses(board)

  private def isEndgame(board: Board): Boolean =
    var nonPawnMaterial = 0
    var r = 0
    while r < 8 do
      var c = 0
      while c < 8 do
        board.cell(r, c) match
          case Some(piece) =>
            piece match
              case Piece.King(_) | Piece.Pawn(_) => ()
              case p => nonPawnMaterial += pieceValue(p)
          case None => ()
        c += 1
      r += 1
    nonPawnMaterial < 1300

  private def pstFor(piece: Piece, endgame: Boolean): Array[Array[Int]] = piece match
    case Piece.Pawn(_)   => PawnTable
    case Piece.Knight(_) => KnightTable
    case Piece.Bishop(_) => BishopTable
    case Piece.Rook(_)   => RookTable
    case Piece.Queen(_)  => QueenTable
    case Piece.King(_)   => if endgame then KingEndTable else KingMidTable

  private def bonuses(board: Board): Int =
    var score = 0
    var whiteBishops = 0
    var blackBishops = 0
    val whitePawnCols = Array.fill(8)(0)
    val blackPawnCols = Array.fill(8)(0)

    var r = 0
    while r < 8 do
      var c = 0
      while c < 8 do
        board.cell(r, c) match
          case Some(Piece.Bishop(Color.White)) => whiteBishops += 1
          case Some(Piece.Bishop(Color.Black)) => blackBishops += 1
          case Some(Piece.Pawn(Color.White))   => whitePawnCols(c) += 1
          case Some(Piece.Pawn(Color.Black))   => blackPawnCols(c) += 1
          case _ => ()
        c += 1
      r += 1

    // Bishop pair bonus
    if whiteBishops >= 2 then score += 30
    if blackBishops >= 2 then score -= 30

    // Doubled pawn penalty (-10cp per extra pawn on the same file)
    var c = 0
    while c < 8 do
      if whitePawnCols(c) > 1 then score -= 10 * (whitePawnCols(c) - 1)
      if blackPawnCols(c) > 1 then score += 10 * (blackPawnCols(c) - 1)
      c += 1

    // Isolated pawn penalty (-10cp per pawn with no friendly pawns on adjacent files)
    c = 0
    while c < 8 do
      val wLeft  = if c > 0 then whitePawnCols(c - 1) else 0
      val wRight = if c < 7 then whitePawnCols(c + 1) else 0
      if whitePawnCols(c) > 0 && wLeft == 0 && wRight == 0 then score -= 10
      val bLeft  = if c > 0 then blackPawnCols(c - 1) else 0
      val bRight = if c < 7 then blackPawnCols(c + 1) else 0
      if blackPawnCols(c) > 0 && bLeft == 0 && bRight == 0 then score += 10
      c += 1

    // Passed pawn bonus (approximate: no opposing pawns on same or adjacent files)
    r = 0
    while r < 8 do
      c = 0
      while c < 8 do
        board.cell(r, c) match
          case Some(Piece.Pawn(Color.White)) =>
            val noOpposing =
              blackPawnCols(c) == 0 &&
              (if c > 0 then blackPawnCols(c - 1) == 0 else true) &&
              (if c < 7 then blackPawnCols(c + 1) == 0 else true)
            if noOpposing then
              score += (if r >= 5 then 50 else if r >= 4 then 30 else if r >= 3 then 20 else 0)
          case Some(Piece.Pawn(Color.Black)) =>
            val noOpposing =
              whitePawnCols(c) == 0 &&
              (if c > 0 then whitePawnCols(c - 1) == 0 else true) &&
              (if c < 7 then whitePawnCols(c + 1) == 0 else true)
            if noOpposing then
              score -= (if r <= 2 then 50 else if r <= 3 then 30 else if r <= 4 then 20 else 0)
          case _ => ()
        c += 1
      r += 1

    score
