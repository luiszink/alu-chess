package chess.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class GameJsonSpec extends AnyWordSpec with Matchers:

  private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  "GameJson.toJson" should {
    "include the FEN field" in {
      val game = Fen.parse(startFen).get
      val json = GameJson.toJson(game)
      (json \ "fen").asOpt[String] shouldBe Some(startFen)
    }

    "include status, currentPlayer, halfMoveClock, fullMoveNumber, isTerminal" in {
      val game = Fen.parse(startFen).get
      val json = GameJson.toJson(game)
      (json \ "status").asOpt[String]          shouldBe Some("Playing")
      (json \ "currentPlayer").asOpt[String]   shouldBe Some("White")
      (json \ "halfMoveClock").asOpt[Int]       shouldBe Some(0)
      (json \ "fullMoveNumber").asOpt[Int]      shouldBe Some(1)
      (json \ "isTerminal").asOpt[Boolean]      shouldBe Some(false)
    }

    "encode a terminal (checkmate) game correctly" in {
      // Fool's Mate
      val foolsMate = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
      val game = Fen.parse(foolsMate).get
      val json = GameJson.toJson(game)
      (json \ "status").asOpt[String]      shouldBe Some("Checkmate")
      (json \ "isTerminal").asOpt[Boolean] shouldBe Some(true)
    }
  }

  "GameJson.toJsonString" should {
    "produce a non-empty JSON string" in {
      val game   = Fen.parse(startFen).get
      val result = GameJson.toJsonString(game)
      result should not be empty
      result should include("fen")
    }
  }

  "GameJson.fromJson" should {
    "round-trip a game through JSON" in {
      val game  = Fen.parse(startFen).get
      val json  = GameJson.toJson(game)
      val back  = GameJson.fromJson(json)
      back shouldBe a[Right[?, ?]]
      back.toOption.get.board         shouldBe game.board
      back.toOption.get.currentPlayer shouldBe game.currentPlayer
    }

    "return Left when the 'fen' field is missing" in {
      import play.api.libs.json.*
      val badJson = Json.obj("status" -> "Playing")
      GameJson.fromJson(badJson) shouldBe a[Left[?, ?]]
    }

    "return Left when the FEN value is invalid" in {
      import play.api.libs.json.*
      val badJson = Json.obj("fen" -> "not-a-fen")
      GameJson.fromJson(badJson) shouldBe a[Left[?, ?]]
    }
  }

  "GameJson.fromJsonString" should {
    "round-trip a game through a JSON string" in {
      val game   = Fen.parse(startFen).get
      val json   = GameJson.toJsonString(game)
      val back   = GameJson.fromJsonString(json)
      back shouldBe a[Right[?, ?]]
      back.toOption.get.board shouldBe game.board
    }

    "return Left for completely invalid JSON" in {
      GameJson.fromJsonString("{ not json }") shouldBe a[Left[?, ?]]
    }

    "return Left for JSON without a fen field" in {
      GameJson.fromJsonString("""{"status":"Playing"}""") shouldBe a[Left[?, ?]]
    }
  }
