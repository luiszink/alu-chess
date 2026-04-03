package chess.model.pgn

/** Parsed PGN representation: tag pairs, SAN move tokens, and game result. */
case class PgnGame(
  tags: Map[String, String],
  moves: Vector[String],
  result: Option[String]
)
