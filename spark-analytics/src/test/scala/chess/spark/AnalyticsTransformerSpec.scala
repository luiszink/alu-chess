package chess.spark

import chess.spark.AnalyticsCommand.{Clear, Delete, Upsert}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AnalyticsTransformerSpec extends AnyWordSpec with Matchers {

  "AnalyticsTransformer" should {

    "create an upsert command from a game persistence event" in {
      val result = AnalyticsTransformer.commandFromJson(upsertEventJson)

      result shouldBe a[Right[_, _]]
      val Upsert(document) = result.toOption.get: @unchecked
      document.recordId shouldBe "game-1"
      document.result shouldBe "1-0"
      document.moveCount shouldBe 2
      document.timeControlName shouldBe Some("Blitz")
      document.initialTimeMs shouldBe Some(300000L)
      document.incrementMs shouldBe Some(3000L)
      document.finalFen shouldBe "fen-after"
    }

    "create a delete command when recordId is present" in {
      val json =
        """{
          |  "eventId": "event-2",
          |  "eventType": "game-record-delete-requested",
          |  "occurredAt": "2026-06-11T12:00:00Z",
          |  "recordId": "game-1",
          |  "record": null
          |}""".stripMargin

      AnalyticsTransformer.commandFromJson(json) shouldBe Right(Delete("game-1"))
    }

    "create a clear command" in {
      val json =
        """{
          |  "eventId": "event-3",
          |  "eventType": "game-records-clear-requested",
          |  "occurredAt": "2026-06-11T12:00:00Z",
          |  "recordId": null,
          |  "record": null
          |}""".stripMargin

      AnalyticsTransformer.commandFromJson(json) shouldBe Right(Clear)
    }

    "reject invalid JSON without throwing" in {
      AnalyticsTransformer.commandFromJson("{broken").left.toOption.get should include("Invalid event JSON")
    }

    "reject upsert events without record payload" in {
      val json =
        """{
          |  "eventId": "event-4",
          |  "eventType": "game-record-upsert-requested",
          |  "occurredAt": "2026-06-11T12:00:00Z",
          |  "recordId": null,
          |  "record": null
          |}""".stripMargin

      AnalyticsTransformer.commandFromJson(json) shouldBe Left("Upsert event without record payload")
    }
  }

  private val upsertEventJson =
    """{
      |  "eventId": "event-1",
      |  "eventType": "game-record-upsert-requested",
      |  "occurredAt": "2026-06-11T12:00:00Z",
      |  "recordId": null,
      |  "record": {
      |    "id": "game-1",
      |    "datePlayed": "2026-06-11T12:00:00",
      |    "result": "1-0",
      |    "moveCount": 2,
      |    "pgn": "1. e4 e5 1-0",
      |    "timeControl": {
      |      "initialTimeMs": 300000,
      |      "incrementMs": 3000,
      |      "name": "Blitz"
      |    },
      |    "moves": [
      |      { "ply": 0, "fen": "fen-before", "status": "Playing", "currentPlayer": "White" },
      |      { "ply": 1, "fen": "fen-after", "status": "Playing", "currentPlayer": "Black" }
      |    ]
      |  }
      |}""".stripMargin
}
