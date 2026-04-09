package chess.model

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import io.circe.*
import io.circe.syntax.*

class GameJsonSpec extends AnyWordSpec with Matchers:

  private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  "GameJson.toJson" should {
    "include the FEN field" in {
      val game = Fen.parse(startFen).get
      val json = GameJson.toJson(game)
      json.hcursor.get[String]("fen").toOption shouldBe Some(startFen)
    }

    "include status, currentPlayer, halfMoveClock, fullMoveNumber, isTerminal" in {
      val game = Fen.parse(startFen).get
      val json = GameJson.toJson(game)
      json.hcursor.get[String]("status").toOption          shouldBe Some("Playing")
      json.hcursor.get[String]("currentPlayer").toOption   shouldBe Some("White")
      json.hcursor.get[Int]("halfMoveClock").toOption       shouldBe Some(0)
      json.hcursor.get[Int]("fullMoveNumber").toOption      shouldBe Some(1)
      json.hcursor.get[Boolean]("isTerminal").toOption      shouldBe Some(false)
    }

    "encode a terminal (checkmate) game correctly" in {
      // Fool's Mate
      val foolsMate = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
      val game = Fen.parse(foolsMate).get
      val json = GameJson.toJson(game)
      json.hcursor.get[String]("status").toOption      shouldBe Some("Checkmate")
      json.hcursor.get[Boolean]("isTerminal").toOption shouldBe Some(true)
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
      val badJson = Json.obj("status" -> Json.fromString("Playing"))
      GameJson.fromJson(badJson) shouldBe a[Left[?, ?]]
    }

    "return Left when the FEN value is invalid" in {
      val badJson = Json.obj("fen" -> Json.fromString("not-a-fen"))
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

  "GameJson.toRecordJson" should {
    "include per-ply FEN entries" in {
      val game0 = Game.newGame
      val game1 = game0.applyMove(Move(Position(1, 4), Position(3, 4))).get
      val game2 = game1.applyMove(Move(Position(6, 4), Position(4, 4))).get
      val record = GameRecord.create(Vector(game0, game1, game2), None)

      val json = GameJson.toRecordJson(record)
      val moves = json.hcursor.downField("moves").focus.flatMap(_.asArray).get

      moves should have size 3
      moves.head.hcursor.get[String]("fen").toOption shouldBe Some(Fen.toFen(game0))
      moves(1).hcursor.get[String]("fen").toOption shouldBe Some(Fen.toFen(game1))
      moves(2).hcursor.get[String]("fen").toOption shouldBe Some(Fen.toFen(game2))
    }
  }

  "GameJson.fromRecordJsonString" should {
    "round-trip a full game record" in {
      val game0 = Game.newGame
      val game1 = game0.applyMove(Move(Position(1, 4), Position(3, 4))).get
      val game2 = game1.applyMove(Move(Position(6, 4), Position(4, 4))).get
      val states = Vector(game0, game1, game2)
      val record = GameRecord.create(states, Some(TimeControl.Blitz3_0))

      val json = GameJson.toRecordJsonString(record)
      val back = GameJson.fromRecordJsonString(json)

      back shouldBe a[Right[?, ?]]
      back.toOption.get.gameStates.map(Fen.toFen) shouldBe states.map(Fen.toFen)
      back.toOption.get.timeControl shouldBe Some(TimeControl.Blitz3_0)
    }

    "accept legacy JSON with only a fen field" in {
      val legacy = s"""{"fen":"$startFen"}"""

      val back = GameJson.fromRecordJsonString(legacy)

      back shouldBe a[Right[?, ?]]
      back.toOption.get.gameStates should have size 1
      Fen.toFen(back.toOption.get.gameStates.head) shouldBe startFen
      back.toOption.get.moveCount shouldBe 0
    }

    "return Left for an empty moves array" in {
      val json = """{"moves": []}"""
      GameJson.fromRecordJsonString(json) shouldBe a[Left[?, ?]]
    }

    "derive defaults when optional record fields are missing" in {
      val game0 = Game.newGame
      val game1 = game0.applyMove(Move(Position(1, 4), Position(3, 4))).get

      val minimal =
        s"""{
           |  "moves": [
           |    {"fen": "${Fen.toFen(game0)}"},
           |    {"fen": "${Fen.toFen(game1)}"}
           |  ]
           |}""".stripMargin

      val back = GameJson.fromRecordJsonString(minimal)

      back shouldBe a[Right[?, ?]]
      back.toOption.get.moveCount shouldBe 1
      back.toOption.get.pgn should not be empty
      back.toOption.get.result shouldBe "*"
      back.toOption.get.timeControl shouldBe None
    }
  }
