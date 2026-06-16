package chess.controller.persistence

import chess.kafka.GamePersistenceEvent
import chess.model.{ChessError, GameRecord, GameRepository}

final class KafkaPublishingGameRepository(
  delegate:  GameRepository,
  publisher: GamePersistencePublisher,
) extends GameRepository:

  override def save(record: GameRecord): Unit =
    delegate.save(record)
    publisher.publish(GamePersistenceEvent.upsert(record))

  override def findAll(): Vector[GameRecord] =
    delegate.findAll()

  override def findById(id: String): Option[GameRecord] =
    delegate.findById(id)

  override def delete(id: String): Unit =
    delegate.delete(id)
    publisher.publish(GamePersistenceEvent.delete(id))

  override def clear(): Unit =
    delegate.clear()
    publisher.publish(GamePersistenceEvent.clear())

  override def exportRecordAsJson(id: String): Either[ChessError, String] =
    delegate.exportRecordAsJson(id)

  override def importRecordFromJson(json: String): Either[ChessError, GameRecord] =
    delegate.importRecordFromJson(json).map { record =>
      publisher.publish(GamePersistenceEvent.upsert(record))
      record
    }
