package chess.model.dao

import chess.model.{Fen, GameRecord, Pgn, TimeControl}

import java.time.LocalDateTime

object GameRecordMapper:
  def toRow(record: GameRecord): GameRow =
    GameRow(
      id              = record.id,
      datePlayed      = record.datePlayed.toString,
      result          = record.result,
      pgn             = record.pgn,
      fen             = Fen.toFen(record.gameStates.last),
      moveCount       = record.moveCount,
      timeControlName = record.timeControl.map(_.name),
      initialTimeMs   = record.timeControl.map(_.initialTimeMs),
      incrementMs     = record.timeControl.map(_.incrementMs),
    )

  def fromRow(row: GameRow): Option[GameRecord] =
    Pgn.replayAllStatesE(row.pgn).toOption.map { states =>
      val timeControl = for
        name    <- row.timeControlName
        initial <- row.initialTimeMs
        incr    <- row.incrementMs
      yield TimeControl(initial, incr, name)

      GameRecord(
        id          = row.id,
        datePlayed  = LocalDateTime.parse(row.datePlayed),
        result      = row.result,
        timeControl = timeControl,
        moveCount   = row.moveCount,
        pgn         = row.pgn,
        gameStates  = states,
      )
    }
