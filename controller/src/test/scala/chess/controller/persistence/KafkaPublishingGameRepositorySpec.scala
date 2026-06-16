package chess.controller.persistence

import chess.kafka.GamePersistenceEvent
import chess.model.{Game, GameJson, GameRecord, InMemoryGameRepository}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDateTime

class KafkaPublishingGameRepositorySpec extends AnyWordSpec with Matchers:

  private final class RecordingPublisher extends GamePersistencePublisher:
    var events: Vector[GamePersistenceEvent] = Vector.empty

    override def publish(event: GamePersistenceEvent): Unit =
      events = events :+ event

  private def makeRecord(id: String): GameRecord =
    GameRecord(
      id          = id,
      datePlayed  = LocalDateTime.parse("2026-06-07T09:00:00"),
      result      = "*",
      timeControl = None,
      moveCount   = 0,
      pgn         = "",
      gameStates  = Vector(Game.newGame),
    )

  "KafkaPublishingGameRepository" should {
    "save locally and publish an upsert event" in {
      val publisher = RecordingPublisher()
      val repository = KafkaPublishingGameRepository(InMemoryGameRepository(), publisher)
      val record = makeRecord("record-1")

      repository.save(record)

      repository.findById("record-1") shouldBe Some(record)
      publisher.events.map(_.eventType) shouldBe Vector(GamePersistenceEvent.UpsertRequested)
      publisher.events.head.recordId shouldBe Some("record-1")
    }

    "publish imported, deleted, and clear events" in {
      val publisher = RecordingPublisher()
      val repository = KafkaPublishingGameRepository(InMemoryGameRepository(), publisher)
      val record = makeRecord("record-2")

      repository.importRecordFromJson(GameJson.toRecordJsonString(record)) shouldBe a[Right[?, ?]]
      repository.delete("record-2")
      repository.clear()

      publisher.events.map(_.eventType) shouldBe Vector(
        GamePersistenceEvent.UpsertRequested,
        GamePersistenceEvent.DeleteRequested,
        GamePersistenceEvent.ClearRequested,
      )
    }
  }
