package chess.controller.api

import cats.effect.*
import cats.effect.unsafe.implicits.global
import chess.controller.{AiMoveRequester, ControllerInterface, GameRegistry}
import chess.model.InMemoryGameRepository
import io.circe.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.client.Client
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.collection.mutable.ListBuffer

class MultiGameRoutesSpec extends AnyWordSpec with Matchers:

  private class RecordingAiMoveRequester extends AiMoveRequester:
    val requestedGames: ListBuffer[String] = ListBuffer.empty

    override def requestMove(gameId: String, controller: ControllerInterface): IO[Unit] =
      IO(requestedGames += gameId).void

  private val dummyHttpApp =
    HttpApp[IO](_ => IO.pure(Response[IO](Status.Ok).withEntity(Json.obj())))

  private val dummyPlayerClient =
    PlayerServiceClient(Client.fromHttpApp(dummyHttpApp))

  private def appWith(requester: RecordingAiMoveRequester): HttpApp[IO] =
    val registry = GameRegistry.make(InMemoryGameRepository()).unsafeRunSync()
    MultiGameRoutes(registry, dummyPlayerClient, requester).orNotFound

  private def post(app: HttpApp[IO], path: String, body: Json = Json.obj()): Response[IO] =
    app.run(Request[IO](Method.POST, Uri.unsafeFromString(path)).withEntity(body)).unsafeRunSync()

  "MultiGameRoutes Kafka AI integration" should {

    "request an AI move after a successful HvAI human move" in {
      val requester = RecordingAiMoveRequester()
      val app = appWith(requester)

      post(app, "/api/controller/game/game-1/activate", Json.obj("mode" -> Json.fromString("HvAI")))
      val moveResponse = post(
        app,
        "/api/controller/game/game-1/move",
        Json.obj("from" -> Json.fromString("e2"), "to" -> Json.fromString("e4")),
      )

      moveResponse.status shouldBe Status.Ok
      requester.requestedGames.toList shouldBe List("game-1")
    }

    "not request an AI move after a Human-vs-Human move" in {
      val requester = RecordingAiMoveRequester()
      val app = appWith(requester)

      post(app, "/api/controller/game/game-2/activate", Json.obj("mode" -> Json.fromString("HvH")))
      post(
        app,
        "/api/controller/game/game-2/move",
        Json.obj("from" -> Json.fromString("e2"), "to" -> Json.fromString("e4")),
      )

      requester.requestedGames.toList shouldBe empty
    }
  }
