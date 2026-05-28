package chess.lichess.api

import cats.data.NonEmptyList
import cats.effect.IO
import chess.lichess.config.LichessConfig
import chess.lichess.state.LichessBotSession
import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.headers.{`Cache-Control`, `Content-Type`}
import org.http4s.CacheDirective
import org.http4s.ServerSentEvent

/** Thin REST + SSE facade for the React frontend.
  *
  * All routes are nested under `/api/lichess/` via nginx and never expose the
  * raw Lichess token; the UI only sees pre-digested JSON. */
object LichessRoutes:

  def apply(cfg: LichessConfig, session: Option[LichessBotSession]): HttpRoutes[IO] =
    HttpRoutes.of[IO] {

      case GET -> Root / "health" =>
        Ok(Json.obj("status" -> Json.fromString("ok")))

      case GET -> Root / "api" / "lichess" / "status" =>
        session match
          case Some(s) => s.snapshot.flatMap(Ok(_))
          case None =>
            Ok(Json.obj(
              "configured" -> Json.False,
              "message"    -> Json.fromString("LICHESS_BOT_TOKEN not set"),
              "policy"     -> Json.obj(
                "autoAccept"        -> Json.fromBoolean(cfg.autoAccept),
                "acceptRated"       -> Json.fromBoolean(cfg.acceptRated),
                "variants"          -> Json.arr(cfg.acceptVariants.toList.sorted.map(Json.fromString) *),
                "minInitialSeconds" -> Json.fromInt(cfg.minInitialSeconds),
                "maxInitialSeconds" -> Json.fromInt(cfg.maxInitialSeconds),
                "maxGames"          -> Json.fromInt(cfg.maxGamesConcurrent),
              ),
            ))

      case GET -> Root / "api" / "lichess" / "events-sse" =>
        session match
          case None =>
            ServiceUnavailable(Json.obj("error" -> Json.fromString("bot not configured")))
          case Some(s) =>
            val sse: fs2.Stream[IO, ServerSentEvent] =
              s.events.map(j => ServerSentEvent(data = Some(j.noSpaces), eventType = Some("lichess")))
            Ok(sse).map(_.putHeaders(
              `Cache-Control`(NonEmptyList.one(CacheDirective.`no-cache`())),
            ))
    }
