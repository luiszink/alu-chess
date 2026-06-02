package chess.model

import java.time.LocalDateTime
import java.util.UUID

case class GameRecord(
  id: String,
  datePlayed: LocalDateTime,
  result: String,
  timeControl: Option[TimeControl],
  moveCount: Int,
  pgn: String,
  gameStates: Vector[Game]
)

object GameRecord:
  def create(
    gameStates: Vector[Game],
    timeControl: Option[TimeControl]
  ): GameRecord =
    val finalGame = gameStates.last
    val result = finalGame.status match
      case GameStatus.Checkmate =>
        if finalGame.currentPlayer == Color.White then "0-1" else "1-0"
      case GameStatus.TimeOut =>
        if finalGame.currentPlayer == Color.White then "0-1" else "1-0"
      case GameStatus.Resigned =>
        if finalGame.currentPlayer == Color.White then "0-1" else "1-0"
      case GameStatus.Stalemate | GameStatus.Draw => "½-½"
      case _ => "*"
    val pgn = Pgn.toPgn(finalGame, timeControl = timeControl)
    GameRecord(
      id = UUID.randomUUID().toString,
      datePlayed = LocalDateTime.now(),
      result = result,
      timeControl = timeControl,
      moveCount = finalGame.moveHistory.size,
      pgn = pgn,
      gameStates = gameStates
    )
