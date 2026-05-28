package chess.lichess.api

import cats.data.NonEmptyList
import cats.effect.{IO, Ref}
import chess.lichess.config.LichessConfig
import chess.lichess.state.{BotState, LichessBotSession}
import io.circe.Json
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.headers.`Cache-Control`
import org.http4s.CacheDirective
import org.http4s.ServerSentEvent

/** Thin REST + SSE facade for the React frontend.
  *
  * All routes are nested under `/api/lichess/` via nginx and never expose the
  * raw Lichess token; the UI only sees pre-digested JSON. */
object LichessRoutes:

  def apply(cfg: LichessConfig, stateRef: Ref[IO, BotState]): HttpRoutes[IO] =

    val policyJson: Json = Json.obj(
      "autoAccept"        -> Json.fromBoolean(cfg.autoAccept),
      "acceptRated"       -> Json.fromBoolean(cfg.acceptRated),
      "variants"          -> Json.arr(cfg.acceptVariants.toList.sorted.map(Json.fromString) *),
      "minInitialSeconds" -> Json.fromInt(cfg.minInitialSeconds),
      "maxInitialSeconds" -> Json.fromInt(cfg.maxInitialSeconds),
      "maxGames"          -> Json.fromInt(cfg.maxGamesConcurrent),
    )

    HttpRoutes.of[IO] {

      case GET -> Root / "health" =>
        Ok(Json.obj("status" -> Json.fromString("ok")))

      case GET -> Root / "api" / "lichess" / "status" =>
        stateRef.get.flatMap {
          case BotState.Running(s) =>
            s.snapshot.flatMap(Ok(_))
          case BotState.NotConfigured =>
            Ok(Json.obj(
              "configured" -> Json.False,
              "state"      -> Json.fromString("notConfigured"),
              "message"    -> Json.fromString("LICHESS_BOT_TOKEN not set"),
              "policy"     -> policyJson,
            ))
          case BotState.Connecting =>
            Ok(Json.obj(
              "configured" -> Json.False,
              "state"      -> Json.fromString("connecting"),
              "message"    -> Json.fromString("Verbindung zu lichess.org wird aufgebaut…"),
              "policy"     -> policyJson,
            ))
          case BotState.Failed(msg) =>
            Ok(Json.obj(
              "configured" -> Json.False,
              "state"      -> Json.fromString("failed"),
              "message"    -> Json.fromString(s"Verbindung fehlgeschlagen: $msg"),
              "policy"     -> policyJson,
            ))
        }

      case GET -> Root / "api" / "lichess" / "events-sse" =>
        stateRef.get.flatMap {
          case BotState.Running(s) =>
            val sse: fs2.Stream[IO, ServerSentEvent] =
              s.events.map(j => ServerSentEvent(data = Some(j.noSpaces), eventType = Some("lichess")))
            Ok(sse).map(_.putHeaders(
              `Cache-Control`(NonEmptyList.one(CacheDirective.`no-cache`())),
            ))
          case _ =>
            ServiceUnavailable(Json.obj("error" -> Json.fromString("bot not connected")))
        }
    }
