package chess.model

/** A position on the chess board (0-indexed internally, displayed as a1–h8). */
case class Position(row: Int, col: Int):

  def isValid: Boolean =
    row >= 0 && row < 8 && col >= 0 && col < 8

  /** Algebraic notation, e.g. "e2" */
  override def toString: String =
    if isValid then s"${('a' + col).toChar}${row + 1}"
    else s"(?$row,$col)"

object Position:
  /** Parse algebraic notation like "e2" into a Position. Returns Left with error detail on failure. */
  def fromStringE(s: String): Either[ChessError, Position] =
    s.trim.toLowerCase match
      case str if str.length == 2 =>
        val col = str(0) - 'a'
        val row = str(1).asDigit - 1
        val pos = Position(row, col)
        if pos.isValid then Right(pos) else Left(ChessError.InvalidPositionString(s))
      case _ => Left(ChessError.InvalidPositionString(s))

  /** Parse algebraic notation like "e2" into a Position. Returns None on invalid input. */
  def fromString(s: String): Option[Position] = fromStringE(s).toOption
