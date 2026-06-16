package chess.kafka

import chess.model.{Game, GameRecord}
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import java.time.LocalDateTime

class GamePersistenceEventSpec extends AnyWordSpec with Matchers:

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

  "GamePersistenceEvent.upsert" should {
    "carry the game record JSON and survive JSON roundtrip" in {
      val record = makeRecord("record-1")
      val event = GamePersistenceEvent.upsert(record)

      event.eventType shouldBe GamePersistenceEvent.UpsertRequested
      event.recordId shouldBe Some("record-1")
      event.record shouldBe defined
      event.record.get.hcursor.get[String]("id") shouldBe Right("record-1")

      decode[GamePersistenceEvent](event.asJson.noSpaces) shouldBe Right(event)
    }
  }

  "GamePersistenceEvent.delete" should {
    "use the record id as event payload metadata" in {
      val event = GamePersistenceEvent.delete("record-2")

      event.eventType shouldBe GamePersistenceEvent.DeleteRequested
      event.recordId shouldBe Some("record-2")
      event.record shouldBe None
    }
  }
