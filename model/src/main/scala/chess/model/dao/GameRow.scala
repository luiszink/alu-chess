package chess.model.dao

case class GameRow(
  id:              String,
  datePlayed:      String,
  result:          String,
  pgn:             String,
  fen:             String,
  moveCount:       Int,
  timeControlName: Option[String],
  initialTimeMs:   Option[Long],
  incrementMs:     Option[Long]
)
