package chess.kafka

import chess.model.{GameJson, GameRecord}
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import java.time.Instant
import java.util.UUID

case class GamePersistenceEvent(
  eventId:    String,
  eventType:  String,
  occurredAt: String,
  recordId:   Option[String],
  record:     Option[Json],
)

object GamePersistenceEvent:
  val UpsertRequested: String = "game-record-upsert-requested"
  val DeleteRequested: String = "game-record-delete-requested"
  val ClearRequested:  String = "game-records-clear-requested"

  given Encoder[GamePersistenceEvent] = deriveEncoder
  given Decoder[GamePersistenceEvent] = deriveDecoder

  def upsert(record: GameRecord): GamePersistenceEvent =
    GamePersistenceEvent(
      eventId    = UUID.randomUUID().toString,
      eventType  = UpsertRequested,
      occurredAt = Instant.now().toString,
      recordId   = Some(record.id),
      record     = Some(GameJson.toRecordJson(record)),
    )

  def delete(recordId: String): GamePersistenceEvent =
    GamePersistenceEvent(
      eventId    = UUID.randomUUID().toString,
      eventType  = DeleteRequested,
      occurredAt = Instant.now().toString,
      recordId   = Some(recordId),
      record     = None,
    )

  def clear(): GamePersistenceEvent =
    GamePersistenceEvent(
      eventId    = UUID.randomUUID().toString,
      eventType  = ClearRequested,
      occurredAt = Instant.now().toString,
      recordId   = None,
      record     = None,
    )
