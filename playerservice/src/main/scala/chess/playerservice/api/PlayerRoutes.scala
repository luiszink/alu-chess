package chess.playerservice.api

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import io.circe.*
import io.circe.syntax.*
import chess.playerservice.*

object PlayerRoutes:

  def apply(registry: PlayerRegistry): HttpRoutes[IO] =

    def playerJson(p: Player): Json = Json.obj(
      "id"     -> Json.fromString(p.id),
      "name"   -> Json.fromString(p.name),
      "gameId" -> p.gameId.fold(Json.Null)(Json.fromString),
      "color"  -> p.color.fold(Json.Null)(Json.fromString),
    )

    def sessionJson(s: GameSession): Json = Json.obj(
      "id"            -> Json.fromString(s.id),
      "mode"          -> Json.fromString(s.mode.toString),
      "whitePlayerId" -> Json.fromString(s.whitePlayerId),
      "blackPlayerId" -> s.blackPlayerId.fold(Json.Null)(Json.fromString),
      "status"        -> Json.fromString(s.status.toString),
    )

    def err(msg: String): Json = Json.obj("error" -> Json.fromString(msg))

    HttpRoutes.of[IO] {

      // POST /api/player/register  { "name": "Alice" }
      case req @ POST -> Root / "api" / "player" / "register" =>
        req.as[Json].flatMap { body =>
          body.hcursor.get[String]("name") match
            case Left(_)     => BadRequest(err("Missing 'name'"))
            case Right(name) => registry.registerPlayer(name).flatMap(p => Created(playerJson(p)))
        }

      // GET /api/player/{playerId}/status  — Frontend pollt hier bis session.status = Active
      case GET -> Root / "api" / "player" / playerId / "status" =>
        registry.getPlayer(playerId).flatMap {
          case None    => NotFound(err(s"Player $playerId not found"))
          case Some(p) =>
            val sessionIO = p.gameId.fold(IO.pure(Option.empty[GameSession]))(registry.getSession)
            sessionIO.flatMap { sessOpt =>
              Ok(Json.obj(
                "player"  -> playerJson(p),
                "session" -> sessOpt.fold(Json.Null)(sessionJson),
              ))
            }
        }

      // POST /api/player/session/hvai  { "playerId": "..." }
      case req @ POST -> Root / "api" / "player" / "session" / "hvai" =>
        req.as[Json].flatMap { body =>
          body.hcursor.get[String]("playerId") match
            case Left(_)    => BadRequest(err("Missing 'playerId'"))
            case Right(pid) =>
              registry.createHvAISession(pid).flatMap {
                case Left(e)  => Conflict(err(e))
                case Right(s) => Created(sessionJson(s))
              }
        }

      // POST /api/player/session/hvh  { "playerId": "..." }
      case req @ POST -> Root / "api" / "player" / "session" / "hvh" =>
        req.as[Json].flatMap { body =>
          body.hcursor.get[String]("playerId") match
            case Left(_)    => BadRequest(err("Missing 'playerId'"))
            case Right(pid) =>
              registry.createHvHSession(pid).flatMap {
                case Left(e)  => Conflict(err(e))
                case Right(s) => Created(sessionJson(s))
              }
        }

      // POST /api/player/session/{gameId}/join  { "playerId": "..." }
      case req @ POST -> Root / "api" / "player" / "session" / gameId / "join" =>
        req.as[Json].flatMap { body =>
          body.hcursor.get[String]("playerId") match
            case Left(_)    => BadRequest(err("Missing 'playerId'"))
            case Right(pid) =>
              registry.joinHvHSession(gameId, pid).flatMap {
                case Left(e)  => Conflict(err(e))
                case Right(s) => Ok(sessionJson(s))
              }
        }

      // GET /api/player/sessions/waiting  — Lobby-Liste
      case GET -> Root / "api" / "player" / "sessions" / "waiting" =>
        registry.listWaitingSessions().flatMap { sessions =>
          Ok(Json.obj("sessions" -> Json.fromValues(sessions.map(sessionJson))))
        }

      // GET /api/player/session/{gameId}
      case GET -> Root / "api" / "player" / "session" / gameId =>
        registry.getSession(gameId).flatMap {
          case None    => NotFound(err(s"Session $gameId not found"))
          case Some(s) => Ok(sessionJson(s))
        }

      // POST /api/player/session/{gameId}/finish  — vom Controller aufgerufen bei Spielende
      case POST -> Root / "api" / "player" / "session" / gameId / "finish" =>
        registry.markSessionFinished(gameId) >> Ok(Json.obj("ok" -> Json.fromBoolean(true)))

      // GET /health
      case GET -> Root / "health" =>
        Ok(Json.obj("status" -> Json.fromString("ok"), "service" -> Json.fromString("playerservice")))
    }
