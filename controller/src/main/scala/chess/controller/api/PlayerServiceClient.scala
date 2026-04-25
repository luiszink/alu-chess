package chess.controller.api

import cats.effect.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.client.Client
import io.circe.*
import io.circe.syntax.*

class PlayerServiceClient(client: Client[IO]):

  private val baseUrl: String =
    sys.env.getOrElse("PLAYER_SERVICE_URL", "http://localhost:8083")

  def get(path: String): IO[Either[(Status, Json), Json]] =
    Uri.fromString(s"$baseUrl$path") match
      case Left(e)    => IO.pure(Left((Status.BadGateway, Json.obj("error" -> Json.fromString(e.message)))))
      case Right(uri) =>
        client.run(Request[IO](uri = uri)).use { resp =>
          resp.as[Json].map { json =>
            if resp.status.isSuccess then Right(json) else Left((resp.status, json))
          }
        }.handleError(e => Left((Status.BadGateway, Json.obj("error" -> Json.fromString(e.getMessage)))))

  def post(path: String, body: Json): IO[Either[(Status, Json), Json]] =
    Uri.fromString(s"$baseUrl$path") match
      case Left(e)    => IO.pure(Left((Status.BadGateway, Json.obj("error" -> Json.fromString(e.message)))))
      case Right(uri) =>
        val req = Request[IO](method = Method.POST, uri = uri).withEntity(body)
        client.run(req).use { resp =>
          resp.as[Json].map { json =>
            if resp.status.isSuccess then Right(json) else Left((resp.status, json))
          }
        }.handleError(e => Left((Status.BadGateway, Json.obj("error" -> Json.fromString(e.getMessage)))))

  def finishSession(gameId: String): IO[Unit] =
    post(s"/api/player/session/$gameId/finish", Json.obj()).void.handleError(_ => ())
