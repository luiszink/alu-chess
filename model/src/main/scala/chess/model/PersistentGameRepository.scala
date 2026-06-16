package chess.model

import cats.effect.unsafe.implicits.global
import chess.model.dao.{GameDao, GameRecordMapper}

class PersistentGameRepository private (dao: GameDao, initial: Vector[GameRecord]) extends GameRepository:
  private var records: Vector[GameRecord] = initial

  override def save(record: GameRecord): Unit =
    records = record +: records
    dao.insert(GameRecordMapper.toRow(record))
      .handleError(ex => System.err.println(s"[DB] save failed for ${record.id}: ${ex.getMessage}"))
      .unsafeRunAndForget()

  override def findAll(): Vector[GameRecord] = records

  override def findById(id: String): Option[GameRecord] =
    records.find(_.id == id)

  override def delete(id: String): Unit =
    records = records.filterNot(_.id == id)
    dao.delete(id)
      .handleError(ex => System.err.println(s"[DB] delete failed for $id: ${ex.getMessage}"))
      .unsafeRunAndForget()

  override def clear(): Unit =
    records = Vector.empty
    dao.clear()
      .handleError(ex => System.err.println(s"[DB] clear failed: ${ex.getMessage}"))
      .unsafeRunAndForget()

  override def exportRecordAsJson(id: String): Either[ChessError, String] =
    findById(id)
      .map(record => Right(GameJson.toRecordJsonString(record)))
      .getOrElse(Left(ChessError.InvalidFenFormat(s"No game record with id '$id'")))

  override def importRecordFromJson(json: String): Either[ChessError, GameRecord] =
    GameJson.fromRecordJsonString(json).map { record =>
      records = record +: records.filterNot(_.id == record.id)
      dao.insert(GameRecordMapper.toRow(record))
        .handleError(ex => System.err.println(s"[DB] import failed for ${record.id}: ${ex.getMessage}"))
        .unsafeRunAndForget()
      record
    }

object PersistentGameRepository:
  def apply(dao: GameDao): PersistentGameRepository =
    val rows = dao.findAll().unsafeRunSync()
    val records = rows.flatMap(GameRecordMapper.fromRow)
    new PersistentGameRepository(dao, records)
