package chess.model.dao

import slick.jdbc.PostgresProfile.api.*

class GameTable(tag: Tag) extends Table[GameRow](tag, "games"):
  def id              = column[String]("id", O.PrimaryKey)
  def datePlayed      = column[String]("date_played")
  def result          = column[String]("result")
  def pgn             = column[String]("pgn")
  def fen             = column[String]("fen")
  def moveCount       = column[Int]("move_count")
  def timeControlName = column[Option[String]]("time_control_name")
  def initialTimeMs   = column[Option[Long]]("initial_time_ms")
  def incrementMs     = column[Option[Long]]("increment_ms")

  def * = (
    id, datePlayed, result, pgn, fen, moveCount,
    timeControlName, initialTimeMs, incrementMs
  ).mapTo[GameRow]

object GameTable:
  val games: TableQuery[GameTable] = TableQuery[GameTable]
