package chess.controller.api

import cats.effect.*
import cats.effect.std.Queue
import cats.effect.unsafe.implicits.global
import org.http4s.*
import org.http4s.implicits.*
import org.http4s.circe.*
import io.circe.*
import io.circe.syntax.*
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import chess.controller.Controller

class ControllerRoutesSpec extends AnyWordSpec with Matchers with BeforeAndAfterEach:

  private var ctrl: Controller = _
  private var sseQueues: Ref[IO, List[Queue[IO, Option[Json]]]] = _
  private var app: HttpApp[IO] = _

  override def beforeEach(): Unit =
    ctrl = Controller()
    sseQueues = Ref.of[IO, List[Queue[IO, Option[Json]]]](Nil).unsafeRunSync()
    app = ControllerRoutes(ctrl, sseQueues).orNotFound

  private def get(path: String): Response[IO] =
    app.run(Request[IO](Method.GET, Uri.unsafeFromString(path))).unsafeRunSync()

  private def post(path: String, body: Json = Json.obj()): Response[IO] =
    app.run(
      Request[IO](Method.POST, Uri.unsafeFromString(path)).withEntity(body)
    ).unsafeRunSync()

  private def bodyJson(resp: Response[IO]): Json =
    resp.as[Json].unsafeRunSync()

  "GET /health" should {
    "return ok" in {
      val resp = get("/health")
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[String]("service").toOption shouldBe Some("controller")
    }
  }

  "GET /api/controller/state" should {
    "return initial game state" in {
      val resp = get("/api/controller/state")
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      val game = json.hcursor.downField("game")
      game.get[String]("status").toOption shouldBe Some("Playing")
      game.get[String]("currentPlayer").toOption shouldBe Some("White")
      json.hcursor.get[Boolean]("isAtLatest").toOption shouldBe Some(true)
      json.hcursor.get[Boolean]("isInReplay").toOption shouldBe Some(false)
      json.hcursor.get[Int]("browseIndex").toOption shouldBe Some(0)
      json.hcursor.get[Int]("totalStates").toOption shouldBe Some(1)
    }
  }

  "POST /api/controller/new-game" should {
    "reset the game" in {
      // Make a move first
      post("/api/controller/move", Json.obj("from" -> Json.fromString("e2"), "to" -> Json.fromString("e4")))
      // Then new game
      val resp = post("/api/controller/new-game")
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.downField("game").get[Int]("fullMoveNumber").toOption shouldBe Some(1)
      json.hcursor.get[Int]("totalStates").toOption shouldBe Some(1)
    }
  }

  "POST /api/controller/move" should {
    "accept a valid move" in {
      val body = Json.obj("from" -> Json.fromString("e2"), "to" -> Json.fromString("e4"))
      val resp = post("/api/controller/move", body)
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.downField("game").get[String]("currentPlayer").toOption shouldBe Some("Black")
      json.hcursor.get[Int]("totalStates").toOption shouldBe Some(2)
    }

    "reject an invalid move" in {
      val body = Json.obj("from" -> Json.fromString("e2"), "to" -> Json.fromString("e5"))
      val resp = post("/api/controller/move", body)
      resp.status should not be Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[String]("error").toOption shouldBe defined
    }

    "reject missing fields" in {
      val resp = post("/api/controller/move", Json.obj("from" -> Json.fromString("e2")))
      resp.status shouldBe Status.BadRequest
    }
  }

  "POST /api/controller/load-fen" should {
    "load a valid FEN" in {
      val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
      val resp = post("/api/controller/load-fen", Json.obj("fen" -> Json.fromString(fen)))
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.downField("game").get[String]("currentPlayer").toOption shouldBe Some("Black")
    }

    "reject invalid FEN" in {
      val resp = post("/api/controller/load-fen", Json.obj("fen" -> Json.fromString("garbage")))
      resp.status shouldBe Status.BadRequest
    }
  }

  "POST /api/controller/resign" should {
    "resign the current game" in {
      val resp = post("/api/controller/resign")
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.downField("game").get[String]("status").toOption shouldBe Some("Resigned")
      json.hcursor.downField("game").get[Boolean]("isTerminal").toOption shouldBe Some(true)
    }
  }

  "browse endpoints" should {
    "navigate back and forward" in {
      // Make two moves
      post("/api/controller/move", Json.obj("from" -> Json.fromString("e2"), "to" -> Json.fromString("e4")))
      post("/api/controller/move", Json.obj("from" -> Json.fromString("e7"), "to" -> Json.fromString("e5")))

      // Browse back
      val backResp = post("/api/controller/browse/back")
      backResp.status shouldBe Status.Ok
      bodyJson(backResp).hcursor.get[Int]("browseIndex").toOption shouldBe Some(1)

      // Browse forward
      val fwdResp = post("/api/controller/browse/forward")
      fwdResp.status shouldBe Status.Ok
      bodyJson(fwdResp).hcursor.get[Int]("browseIndex").toOption shouldBe Some(2)
    }

    "navigate to start and end" in {
      post("/api/controller/move", Json.obj("from" -> Json.fromString("e2"), "to" -> Json.fromString("e4")))

      val startResp = post("/api/controller/browse/to-start")
      bodyJson(startResp).hcursor.get[Int]("browseIndex").toOption shouldBe Some(0)

      val endResp = post("/api/controller/browse/to-end")
      bodyJson(endResp).hcursor.get[Int]("browseIndex").toOption shouldBe Some(1)
    }

    "navigate to specific move" in {
      post("/api/controller/move", Json.obj("from" -> Json.fromString("e2"), "to" -> Json.fromString("e4")))
      post("/api/controller/move", Json.obj("from" -> Json.fromString("e7"), "to" -> Json.fromString("e5")))

      val resp = post("/api/controller/browse/to-move", Json.obj("index" -> Json.fromInt(0)))
      resp.status shouldBe Status.Ok
      bodyJson(resp).hcursor.get[Int]("browseIndex").toOption shouldBe Some(0)
    }
  }

  "GET /api/controller/move-history" should {
    "return moves after playing" in {
      post("/api/controller/move", Json.obj("from" -> Json.fromString("e2"), "to" -> Json.fromString("e4")))
      val resp = get("/api/controller/move-history")
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      val moves = json.hcursor.get[Vector[Json]]("moves").getOrElse(Vector.empty)
      moves.size shouldBe 1
      moves.head.hcursor.get[String]("san").toOption shouldBe Some("e4")
    }
  }

  "GET /api/controller/games" should {
    "return empty list initially" in {
      val resp = get("/api/controller/games")
      resp.status shouldBe Status.Ok
      val json = bodyJson(resp)
      json.hcursor.get[Vector[Json]]("games").getOrElse(Vector.empty) shouldBe empty
    }
  }

  "replay endpoints" should {
    "return 404 for unknown game" in {
      val resp = post("/api/controller/replay/load", Json.obj("id" -> Json.fromString("nonexistent")))
      resp.status shouldBe Status.NotFound
    }

    "exit replay when not in replay mode" in {
      val resp = post("/api/controller/replay/exit")
      resp.status shouldBe Status.Ok
      bodyJson(resp).hcursor.get[Boolean]("isInReplay").toOption shouldBe Some(false)
    }
  }

  "GET /api/controller/export" should {
    "return JSON export" in {
      val resp = get("/api/controller/export")
      resp.status shouldBe Status.Ok
    }
  }

  "POST /api/controller/import" should {
    "roundtrip export/import" in {
      // Make a move
      post("/api/controller/move", Json.obj("from" -> Json.fromString("e2"), "to" -> Json.fromString("e4")))
      // Export
      val exportResp = get("/api/controller/export")
      val exportStr = exportResp.as[String].unsafeRunSync()
      // New game, then import
      post("/api/controller/new-game")
      val importResp = app.run(
        Request[IO](Method.POST, Uri.unsafeFromString("/api/controller/import"))
          .withEntity(exportStr)
      ).unsafeRunSync()
      importResp.status shouldBe Status.Ok
      bodyJson(importResp).hcursor.get[Int]("totalStates").toOption shouldBe Some(2)
    }
  }
