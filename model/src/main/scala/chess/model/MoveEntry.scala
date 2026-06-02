package chess.model

/** A record of a single move in the game, including pre-computed SAN notation. */
case class MoveEntry(
  move: Move,
  piece: Piece,
  captured: Option[Piece],
  san: String,
  resultingStatus: GameStatus
)

object MoveEntry:

  def create(
    move: Move,
    piece: Piece,
    captured: Option[Piece],
    boardBefore: Board,
    resultingStatus: GameStatus,
    movedPieces: Set[Position],
    lastMove: Option[Move]
  ): MoveEntry =
    val san = toSAN(move, piece, captured, boardBefore, resultingStatus, movedPieces, lastMove)
    MoveEntry(move, piece, captured, san, resultingStatus)

  private def toSAN(
    move: Move,
    piece: Piece,
    captured: Option[Piece],
    board: Board,
    resultingStatus: GameStatus,
    movedPieces: Set[Position],
    lastMove: Option[Move]
  ): String =
    val suffix = resultingStatus match
      case GameStatus.Checkmate => "#"
      case GameStatus.Check     => "+"
      case _                    => ""

    piece match
      // Castling
      case Piece.King(_) if math.abs(move.to.col - move.from.col) == 2 =>
        (if move.to.col == 6 then "O-O" else "O-O-O") + suffix

      // Pawn
      case Piece.Pawn(_) =>
        val capturePrefix =
          if captured.isDefined || move.from.col != move.to.col then
            s"${('a' + move.from.col).toChar}x"
          else ""
        val promoSuffix = move.promotion.map(p => s"=$p").getOrElse("")
        capturePrefix + move.to.toString + promoSuffix + suffix

      // Other pieces
      case _ =>
        val pieceChar = pieceToSanChar(piece)
        val disambig = disambiguation(move, piece, board, movedPieces, lastMove)
        val captureStr = if captured.isDefined then "x" else ""
        pieceChar + disambig + captureStr + move.to.toString + suffix

  private def pieceToSanChar(piece: Piece): String = piece match
    case Piece.King(_)   => "K"
    case Piece.Queen(_)  => "Q"
    case Piece.Rook(_)   => "R"
    case Piece.Bishop(_) => "B"
    case Piece.Knight(_) => "N"
    // $COVERAGE-OFF$ Pawns use a separate SAN path; this method is only called for non-Pawn pieces
    case _               => ""
    // $COVERAGE-ON$

  private def disambiguation(
    move: Move,
    piece: Piece,
    board: Board,
    movedPieces: Set[Position],
    lastMove: Option[Move]
  ): String =
    val others = MoveValidator.legalMoves(board, piece.color, movedPieces, lastMove)
      .filter(m =>
        m.to == move.to &&
        m.from != move.from &&
        board.cell(m.from).exists(p => samePieceType(p, piece))
      )

    if others.isEmpty then ""
    else if others.forall(_.from.col != move.from.col) then
      ('a' + move.from.col).toChar.toString
    else if others.forall(_.from.row != move.from.row) then
      (move.from.row + 1).toString
    else
      move.from.toString

  private def samePieceType(a: Piece, b: Piece): Boolean =
    (a, b) match
      // $COVERAGE-OFF$ Kings and Pawns never go through disambiguation (one king per color, pawns use separate SAN path)
      case (Piece.King(_), Piece.King(_))     => true
      // $COVERAGE-ON$
      case (Piece.Queen(_), Piece.Queen(_))   => true
      case (Piece.Rook(_), Piece.Rook(_))     => true
      case (Piece.Bishop(_), Piece.Bishop(_)) => true
      case (Piece.Knight(_), Piece.Knight(_)) => true
      // $COVERAGE-OFF$ Pawns use a separate SAN path in toSAN
      case (Piece.Pawn(_), Piece.Pawn(_))     => true
      // $COVERAGE-ON$
      case _                                  => false
