package chess.model

/** A move from one position to another, with optional promotion type. No validation — just data. */
case class Move(from: Position, to: Position, promotion: Option[Char] = None):

  override def toString: String =
    val promoStr = promotion.map(p => s"=$p").getOrElse("")
    s"$from→$to$promoStr"

object Move:
  /** Parse a move string like "e2 e4", "e7 e8 Q" (promotion), or "e2e4". Returns Left with error detail on failure. */
  def fromStringE(s: String): Either[ChessError, Move] =
    val parts = s.trim.toLowerCase.split("\\s+")
    parts match
      case Array(f, t, p) if p.length == 1 =>
        val promoChar = p.charAt(0).toUpper
        if "QRBN".contains(promoChar) then
          for
            from <- Position.fromStringE(f)
            to   <- Position.fromStringE(t)
          yield Move(from, to, Some(promoChar))
        else Left(ChessError.InvalidPromotionPiece(p.charAt(0).toUpper))
      case Array(f, t) =>
        for
          from <- Position.fromStringE(f)
          to   <- Position.fromStringE(t)
        yield Move(from, to)
      case Array(ft) if ft.length == 4 =>
        for
          from <- Position.fromStringE(ft.substring(0, 2))
          to   <- Position.fromStringE(ft.substring(2, 4))
        yield Move(from, to)
      case _ => Left(ChessError.InvalidMoveFormat(s))

  /** Parse a move string. Returns Failure with exception message on failure. */
  def fromStringT(s: String): scala.util.Try[Move] =
    fromStringE(s).left.map(e => new IllegalArgumentException(e.message)).toTry

  /** Parse a move string like "e2 e4", "e7 e8 Q" (promotion), or "e2e4". Returns None on invalid input. */
  def fromString(s: String): Option[Move] = fromStringE(s).toOption
