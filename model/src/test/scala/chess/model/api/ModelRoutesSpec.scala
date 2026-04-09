package chess.model.api

import cats.effect.*
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.circe.*
import io.circe.*
import io.circe.syntax.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class ModelRoutesSpec extends AnyWordSpec with Matchers:

  private val app = ModelRoutes.routes.orNotFound

  private val startFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  private def get(path: String): Response[IO] =
    app.run(Request[IO](Method.GET, Uri.unsafeFromString(path))).unsafeRunSync()

  private def post(path: String, body: Json): Response[IO] =
    app.run(
      Request[IO](Method.POST, Uri.unsafeFromString(path))
        .withEntity(body)
    ).unsafeRunSync()

  private def bodyJson(resp: Response[IO]): Json =
    resp.as[Json].unsafeRunSync()

  "GET /health" should {
    "return ok" in {
      val resp = get("/health")
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[String]("status").toOption shouldBe Some("ok")
      json.hcursor.get[String]("service").toOption shouldBe Some("model")
    }
  }

  "GET /api/model/new-game" should {
    "return a game in starting position" in {
      val resp = get("/api/model/new-game")
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[String]("fen").toOption shouldBe Some(startFen)
      json.hcursor.get[String]("status").toOption shouldBe Some("Playing")
      json.hcursor.get[String]("currentPlayer").toOption shouldBe Some("White")
      json.hcursor.get[Boolean]("isTerminal").toOption shouldBe Some(false)
    }
  }

  "POST /api/model/validate-move" should {
    "accept a valid move (e2→e4)" in {
      val body = Json.obj(
        "fen"       -> Json.fromString(startFen),
        "from"      -> Json.fromString("e2"),
        "to"        -> Json.fromString("e4"),
        "promotion" -> Json.Null,
      )
      val resp = post("/api/model/validate-move", body)
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[String]("currentPlayer").toOption shouldBe Some("Black")
      json.hcursor.get[String]("status").toOption shouldBe Some("Playing")
    }

    "reject an invalid move (e2→e5)" in {
      val body = Json.obj(
        "fen"       -> Json.fromString(startFen),
        "from"      -> Json.fromString("e2"),
        "to"        -> Json.fromString("e5"),
        "promotion" -> Json.Null,
      )
      val resp = post("/api/model/validate-move", body)
      resp.status should not be Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[String]("error").toOption shouldBe defined
    }

    "reject move from empty square" in {
      val body = Json.obj(
        "fen"       -> Json.fromString(startFen),
        "from"      -> Json.fromString("e4"),
        "to"        -> Json.fromString("e5"),
        "promotion" -> Json.Null,
      )
      val resp = post("/api/model/validate-move", body)
      resp.status should not be Status.Ok
    }

    "reject missing fen field" in {
      val body = Json.obj("from" -> Json.fromString("e2"), "to" -> Json.fromString("e4"))
      val resp = post("/api/model/validate-move", body)
      resp.status shouldBe Status.BadRequest
    }
  }

  "POST /api/model/legal-moves" should {
    "return moves for starting position" in {
      val body = Json.obj("fen" -> Json.fromString(startFen))
      val resp = post("/api/model/legal-moves", body)
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      val moves = json.hcursor.get[Vector[Json]]("moves").getOrElse(Vector.empty)
      moves should not be empty
      moves.size shouldBe 20 // 16 Pawn moves + 4 Knight moves
    }

    "reject invalid FEN" in {
      val body = Json.obj("fen" -> Json.fromString("garbage"))
      val resp = post("/api/model/legal-moves", body)
      resp.status shouldBe Status.BadRequest
    }

    "reject missing fen field" in {
      val resp = post("/api/model/legal-moves", Json.obj())
      resp.status shouldBe Status.BadRequest
    }
  }

  "POST /api/model/legal-moves-for-square" should {
    "return moves for e2 pawn" in {
      val body = Json.obj("fen" -> Json.fromString(startFen), "square" -> Json.fromString("e2"))
      val resp = post("/api/model/legal-moves-for-square", body)
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[String]("square").toOption shouldBe Some("e2")
      val piece = json.hcursor.downField("piece")
      piece.get[String]("color").toOption shouldBe Some("White")
      val moves = json.hcursor.get[Vector[Json]]("moves").getOrElse(Vector.empty)
      moves.size shouldBe 2 // e3 and e4
    }

    "return empty moves for empty square" in {
      val body = Json.obj("fen" -> Json.fromString(startFen), "square" -> Json.fromString("e4"))
      val resp = post("/api/model/legal-moves-for-square", body)
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.downField("piece").focus shouldBe Some(Json.Null)
      val moves = json.hcursor.get[Vector[Json]]("moves").getOrElse(Vector.empty)
      moves shouldBe empty
    }
  }

  "POST /api/model/parse-fen" should {
    "parse valid FEN" in {
      val body = Json.obj("fen" -> Json.fromString(startFen))
      val resp = post("/api/model/parse-fen", body)
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[String]("fen").toOption shouldBe Some(startFen)
    }

    "reject invalid FEN" in {
      val body = Json.obj("fen" -> Json.fromString("not-a-fen"))
      val resp = post("/api/model/parse-fen", body)
      resp.status shouldBe Status.BadRequest
    }
  }

  "POST /api/model/to-fen" should {
    "return FEN for game JSON" in {
      val gameJson = Json.obj(
        "fen"            -> Json.fromString(startFen),
        "status"         -> Json.fromString("Playing"),
        "currentPlayer"  -> Json.fromString("White"),
        "halfMoveClock"  -> Json.fromInt(0),
        "fullMoveNumber" -> Json.fromInt(1),
        "isTerminal"     -> Json.fromBoolean(false),
      )
      val resp = post("/api/model/to-fen", gameJson)
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[String]("fen").toOption shouldBe Some(startFen)
    }
  }

  "POST /api/model/parse-pgn" should {
    "parse simple PGN" in {
      val body = Json.obj("pgn" -> Json.fromString("1. e4 e5"))
      val resp = post("/api/model/parse-pgn", body)
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[String]("currentPlayer").toOption shouldBe Some("White")
      json.hcursor.get[Int]("fullMoveNumber").toOption shouldBe Some(2)
    }

    "reject missing pgn field" in {
      val resp = post("/api/model/parse-pgn", Json.obj())
      resp.status shouldBe Status.BadRequest
    }
  }

  "POST /api/model/to-pgn" should {
    "return PGN for a new game" in {
      val gameJson = Json.obj(
        "fen"            -> Json.fromString(startFen),
        "status"         -> Json.fromString("Playing"),
        "currentPlayer"  -> Json.fromString("White"),
        "halfMoveClock"  -> Json.fromInt(0),
        "fullMoveNumber" -> Json.fromInt(1),
        "isTerminal"     -> Json.fromBoolean(false),
      )
      val resp = post("/api/model/to-pgn", gameJson)
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[String]("pgn").toOption shouldBe defined
    }
  }

  "GET /api/model/test-positions" should {
    "return a non-empty list" in {
      val resp = get("/api/model/test-positions")
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      val positions = json.hcursor.get[Vector[Json]]("positions").getOrElse(Vector.empty)
      positions should not be empty
      // Each entry should have name, fen, description
      val first = positions.head.hcursor
      first.get[String]("name").toOption shouldBe defined
      first.get[String]("fen").toOption shouldBe defined
      first.get[String]("description").toOption shouldBe defined
    }
  }
