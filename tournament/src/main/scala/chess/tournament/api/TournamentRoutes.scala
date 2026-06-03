package chess.tournament.api

import cats.effect.{IO, Ref}
import cats.effect.std.Queue
import chess.tournament.client.TournamentApiClient
import chess.tournament.config.TournamentConfig
import chess.tournament.model.*
import chess.tournament.bot.TournamentBot
import io.circe.{Decoder, Json}
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.ServerSentEvent
import org.http4s.headers.`Content-Type`
import org.http4s.MediaType
import fs2.Stream

import scala.concurrent.duration.*

private final case class CreateRequest(
    name: String,
    nbRounds: Int,
    clockLimit: Int,
    clockIncrement: Int,
    format: String,
) derives Decoder

/** REST + SSE facade for the tournament UI.
  *
  * Routes:
  *   GET  /health                          → health check
  *   GET  /api/tournament/status           → current bot status
  *   GET  /api/tournament/list             → list all tournaments on server
  *   POST /api/tournament/create           → create a new tournament (JSON body)
  *   POST /api/tournament/join/{id}        → join an existing tournament
  *   POST /api/tournament/start/{id}       → start a tournament (director only)
  *   POST /api/tournament/connect/{id}     → register + stream + play
  *   GET  /api/tournament/logs             → SSE log stream
  */
object TournamentRoutes:

  def apply(
      cfg: TournamentConfig,
      apiClient: TournamentApiClient,
      statusRef: Ref[IO, BotStatus],
      logQueue: Queue[IO, String],
  ): HttpRoutes[IO] =

    def statusJson: IO[Json] =
      statusRef.get.map {
        case BotStatus.Idle =>
          Json.obj("status" -> Json.fromString("idle"))
        case BotStatus.InTournament(id, round, games) =>
          Json.obj(
            "status"       -> Json.fromString("playing"),
            "tournamentId" -> Json.fromString(id),
            "round"        -> Json.fromInt(round),
            "gamesActive"  -> Json.fromInt(games),
          )
        case BotStatus.Error(msg) =>
          Json.obj("status" -> Json.fromString("error"), "message" -> Json.fromString(msg))
      }

    HttpRoutes.of[IO] {

      // ── UI ──────────────────────────────────────────────────────────────────
      case GET -> Root =>
        val html = getClass.getClassLoader.getResourceAsStream("tournament-ui.html")
        val body = new String(html.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
        Ok(body).map(_.withContentType(`Content-Type`(MediaType.text.html)))

      // ── Health ─────────────────────────────────────────────────────────────
      case GET -> Root / "health" =>
        Ok(Json.obj("status" -> Json.fromString("ok"), "service" -> Json.fromString("tournament")))

      // ── Status ─────────────────────────────────────────────────────────────
      case GET -> Root / "api" / "tournament" / "status" =>
        statusJson.flatMap(Ok(_))

      // ── List tournaments ───────────────────────────────────────────────────
      case GET -> Root / "api" / "tournament" / "list" =>
        apiClient.listTournaments
          .flatMap { resp =>
            val toArr = (ts: List[TournamentInfo]) => Json.arr(ts.map { t =>
              Json.obj(
                "id"        -> Json.fromString(t.id),
                "name"      -> Json.fromString(t.fullName),
                "status"    -> Json.fromString(t.status.getOrElse("unknown")),
                "players"   -> Json.fromInt(t.nbPlayers.getOrElse(0)),
                "rounds"    -> Json.fromInt(t.nbRounds.getOrElse(0)),
                "format"    -> Json.fromString(t.format.getOrElse("swiss")),
              )
            }*)
            Ok(Json.obj(
              "created"  -> toArr(resp.created),
              "started"  -> toArr(resp.started),
              "finished" -> toArr(resp.finished),
            ))
          }
          .handleErrorWith(e => InternalServerError(Json.obj("error" -> Json.fromString(e.getMessage))))

      // ── Create tournament ──────────────────────────────────────────────────
      case req @ POST -> Root / "api" / "tournament" / "create" =>
        req.as[CreateRequest].flatMap { body =>
          apiClient
            .createTournament(body.name, body.nbRounds, body.clockLimit, body.clockIncrement, body.format)
            .flatMap(Ok(_))
            .handleErrorWith(e => InternalServerError(Json.obj("error" -> Json.fromString(e.getMessage))))
        }

      // ── Join tournament ────────────────────────────────────────────────────
      case POST -> Root / "api" / "tournament" / "join" / id =>
        apiClient
          .joinTournament(id)
          .flatMap(_ => Ok(Json.obj("ok" -> Json.fromBoolean(true))))
          .handleErrorWith(e => InternalServerError(Json.obj("error" -> Json.fromString(e.getMessage))))

      // ── Start tournament ───────────────────────────────────────────────────
      case POST -> Root / "api" / "tournament" / "start" / id =>
        apiClient
          .startTournament(id)
          .flatMap(Ok(_))
          .handleErrorWith(e => InternalServerError(Json.obj("error" -> Json.fromString(e.getMessage))))

      // ── Connect bot to tournament (register + join + stream + play) ────────
      case POST -> Root / "api" / "tournament" / "connect" / id =>
        statusRef.get.flatMap {
          case BotStatus.InTournament(running, _, _) if running == id =>
            Conflict(Json.obj("error" -> Json.fromString(s"Already connected to tournament $id")))
          case _ =>
            TournamentBot
              .run(apiClient, cfg, id, logQueue, statusRef)
              .start
              .flatMap(_ => Ok(Json.obj("ok" -> Json.fromBoolean(true), "tournamentId" -> Json.fromString(id))))
        }

      // ── SSE log stream ─────────────────────────────────────────────────────
      case GET -> Root / "api" / "tournament" / "logs" =>
        val events: Stream[IO, ServerSentEvent] =
          Stream
            .fromQueueUnterminated(logQueue)
            .map(msg => ServerSentEvent(data = Some(msg)))
            .merge(
              Stream.awakeEvery[IO](15.seconds).map(_ => ServerSentEvent(comment = Some("keep-alive")))
            )
        Ok(events)

      // ── Tournament detail ──────────────────────────────────────────────────
      case GET -> Root / "api" / "tournament" / "info" / id =>
        apiClient
          .getTournament(id)
          .flatMap(Ok(_))
          .handleErrorWith(e => NotFound(Json.obj("error" -> Json.fromString(e.getMessage))))
    }
