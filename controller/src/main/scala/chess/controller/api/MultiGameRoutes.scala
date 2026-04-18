package chess.controller.api

import cats.effect.*
import cats.effect.std.Queue
import cats.syntax.foldable.*
import fs2.Stream
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.ServerSentEvent
import io.circe.*
import io.circe.syntax.*
import chess.model.*
import chess.model.ai.AIMode
import chess.controller.{Controller, ControllerInterface, GameEntry, GameRegistry}
import chess.util.Observer

import cats.effect.unsafe.implicits.global

object MultiGameRoutes:

  def apply(
    registry:     GameRegistry,
    playerClient: PlayerServiceClient,
  ): HttpRoutes[IO] =

    // ── JSON helpers (identical to ControllerRoutes) ────────────

    def gameToJson(game: Game): Json = Json.obj(
      "fen"            -> Json.fromString(Fen.toFen(game)),
      "status"         -> Json.fromString(game.status.toString),
      "currentPlayer"  -> Json.fromString(game.currentPlayer.toString),
      "halfMoveClock"  -> Json.fromInt(game.halfMoveClock),
      "fullMoveNumber" -> Json.fromInt(game.fullMoveNumber),
      "isTerminal"     -> Json.fromBoolean(game.status.isTerminal),
    )

    def stateJson(ctrl: ControllerInterface): Json = Json.obj(
      "game"        -> gameToJson(ctrl.game),
      "browseIndex" -> Json.fromInt(ctrl.browseIndex),
      "totalStates" -> Json.fromInt(ctrl.gameStatesCount),
      "isAtLatest"  -> Json.fromBoolean(ctrl.isAtLatest),
      "isInReplay"  -> Json.fromBoolean(ctrl.isInReplay),
      "statusText"  -> Json.fromString(ctrl.statusText),
      "clock"       -> ctrl.clock.map { c =>
        Json.obj(
          "whiteTimeMs" -> Json.fromLong(c.whiteTimeMs),
          "blackTimeMs" -> Json.fromLong(c.blackTimeMs),
        )
      }.getOrElse(Json.Null),
    )

    def errorResponse(err: ChessError): Json = Json.obj(
      "error"   -> Json.fromString(err.getClass.getSimpleName.stripSuffix("$")),
      "message" -> Json.fromString(err.message),
    )

    def moveHistoryJson(ctrl: ControllerInterface): Json =
      Json.fromValues(ctrl.latestMoveHistory.map { entry =>
        Json.obj(
          "move"   -> Json.fromString(entry.move.toString),
          "san"    -> Json.fromString(entry.san),
          "status" -> Json.fromString(entry.resultingStatus.toString),
        )
      })

    // ── Observer factory ────────────────────────────────────────

    def makeObserver(ctrl: ControllerInterface, sseQueues: Ref[IO, List[Queue[IO, Option[Json]]]]): Observer =
      new Observer:
        override def update(): Unit =
          val state = stateJson(ctrl)
          val push  = sseQueues.get.flatMap(_.traverse_(q => q.tryOffer(Some(state)).void))
          push.unsafeRunAndForget()

    // ── Route helper: 404 if game not found ─────────────────────

    def withEntry(gameId: String)(f: GameEntry => IO[Response[IO]]): IO[Response[IO]] =
      registry.get(gameId).flatMap {
        case None        => NotFound(Json.obj("error" -> Json.fromString(s"Game '$gameId' not found")))
        case Some(entry) => f(entry)
      }

    // ── Routes ──────────────────────────────────────────────────

    HttpRoutes.of[IO] {

      // ── Player proxy ─────────────────────────────────────────

      // POST /api/player/register
      case req @ POST -> Root / "api" / "player" / "register" =>
        req.as[Json].flatMap { body =>
          playerClient.post("/api/player/register", body).flatMap {
            case Left(e)  => InternalServerError(Json.obj("error" -> Json.fromString(e)))
            case Right(j) => Created(j)
          }
        }

      // POST /api/player/session/hvai
      case req @ POST -> Root / "api" / "player" / "session" / "hvai" =>
        req.as[Json].flatMap { body =>
          playerClient.post("/api/player/session/hvai", body).flatMap {
            case Left(e)  => InternalServerError(Json.obj("error" -> Json.fromString(e)))
            case Right(j) => Created(j)
          }
        }

      // POST /api/player/session/hvh
      case req @ POST -> Root / "api" / "player" / "session" / "hvh" =>
        req.as[Json].flatMap { body =>
          playerClient.post("/api/player/session/hvh", body).flatMap {
            case Left(e)  => InternalServerError(Json.obj("error" -> Json.fromString(e)))
            case Right(j) => Created(j)
          }
        }

      // POST /api/player/session/{gameId}/join
      case req @ POST -> Root / "api" / "player" / "session" / gameId / "join" =>
        req.as[Json].flatMap { body =>
          playerClient.post(s"/api/player/session/$gameId/join", body).flatMap {
            case Left(e)  => InternalServerError(Json.obj("error" -> Json.fromString(e)))
            case Right(j) => Ok(j)
          }
        }

      // GET /api/player/{playerId}/status
      case GET -> Root / "api" / "player" / playerId / "status" =>
        playerClient.get(s"/api/player/$playerId/status").flatMap {
          case Left(e)  => BadGateway(Json.obj("error" -> Json.fromString(e)))
          case Right(j) => Ok(j)
        }

      // GET /api/player/sessions/waiting
      case GET -> Root / "api" / "player" / "sessions" / "waiting" =>
        playerClient.get("/api/player/sessions/waiting").flatMap {
          case Left(e)  => BadGateway(Json.obj("error" -> Json.fromString(e)))
          case Right(j) => Ok(j)
        }

      // GET /api/player/session/{gameId}
      case GET -> Root / "api" / "player" / "session" / gameId =>
        playerClient.get(s"/api/player/session/$gameId").flatMap {
          case Left(e)  => BadGateway(Json.obj("error" -> Json.fromString(e)))
          case Right(j) => Ok(j)
        }

      // ── Game activation ──────────────────────────────────────

      // POST /api/controller/game/{gameId}/activate  { "mode": "HvAI"|"HvH" }
      // Idempotent: creates a new GameEntry only if none exists for gameId
      case req @ POST -> Root / "api" / "controller" / "game" / gameId / "activate" =>
        req.as[Json].flatMap { body =>
          val mode = body.hcursor.get[String]("mode").getOrElse("HvAI")
          registry.get(gameId).flatMap {
            case Some(_) =>
              Ok(Json.obj("gameId" -> Json.fromString(gameId), "status" -> Json.fromString("existing")))
            case None =>
              registry.create(gameId).flatMap { entry =>
                val observer = makeObserver(entry.controller, entry.sseQueues)
                IO(entry.controller.add(observer)) >>
                  (if mode == "HvAI" then
                    IO(entry.controller.setAIMode(AIMode.PlayingAs(chess.model.Color.Black)))
                  else IO.unit) >>
                  Ok(Json.obj("gameId" -> Json.fromString(gameId), "status" -> Json.fromString("created")))
              }
          }
        }

      // ── Per-game state ────────────────────────────────────────

      // GET /api/controller/game/{gameId}/state
      case GET -> Root / "api" / "controller" / "game" / gameId / "state" =>
        withEntry(gameId)(entry => Ok(stateJson(entry.controller)))

      // POST /api/controller/game/{gameId}/move  { "from": "e2", "to": "e4", "promotion": null }
      case req @ POST -> Root / "api" / "controller" / "game" / gameId / "move" =>
        withEntry(gameId) { entry =>
          req.as[Json].flatMap { body =>
            val c = body.hcursor
            val result = for
              from  <- c.get[String]("from").left.map(_ => ChessError.InvalidMoveFormat("Missing 'from'"))
              to    <- c.get[String]("to").left.map(_ => ChessError.InvalidMoveFormat("Missing 'to'"))
              promo  = c.get[String]("promotion").toOption.flatMap(_.headOption)
              fromP <- Position.fromStringE(from)
              toP   <- Position.fromStringE(to)
            yield Move(fromP, toP, promo)

            result match
              case Left(err) => BadRequest(errorResponse(err))
              case Right(move) =>
                IO(entry.controller.doMoveResult(move)).flatMap {
                  case Right(updated) =>
                    val finishIO =
                      if updated.status.isTerminal then playerClient.finishSession(gameId)
                      else IO.unit
                    finishIO >> Ok(stateJson(entry.controller))
                  case Left(err) =>
                    val status = err match
                      case _: ChessError.GameAlreadyOver => Status.Conflict
                      case _ => Status.UnprocessableEntity
                    IO.pure(Response[IO](status).withEntity(errorResponse(err)))
                }
          }
        }

      // POST /api/controller/game/{gameId}/resign
      case POST -> Root / "api" / "controller" / "game" / gameId / "resign" =>
        withEntry(gameId) { entry =>
          IO(entry.controller.resign()) >>
            playerClient.finishSession(gameId) >>
            Ok(stateJson(entry.controller))
        }

      // POST /api/controller/game/{gameId}/new-game
      case POST -> Root / "api" / "controller" / "game" / gameId / "new-game" =>
        withEntry(gameId)(entry => IO(entry.controller.newGame()) >> Ok(stateJson(entry.controller)))

      // ── Browse ────────────────────────────────────────────────

      case POST -> Root / "api" / "controller" / "game" / gameId / "browse" / "back" =>
        withEntry(gameId)(entry => IO(entry.controller.browseBack()) >> Ok(stateJson(entry.controller)))

      case POST -> Root / "api" / "controller" / "game" / gameId / "browse" / "forward" =>
        withEntry(gameId)(entry => IO(entry.controller.browseForward()) >> Ok(stateJson(entry.controller)))

      case POST -> Root / "api" / "controller" / "game" / gameId / "browse" / "to-start" =>
        withEntry(gameId)(entry => IO(entry.controller.browseToStart()) >> Ok(stateJson(entry.controller)))

      case POST -> Root / "api" / "controller" / "game" / gameId / "browse" / "to-end" =>
        withEntry(gameId)(entry => IO(entry.controller.browseToEnd()) >> Ok(stateJson(entry.controller)))

      case req @ POST -> Root / "api" / "controller" / "game" / gameId / "browse" / "to-move" =>
        withEntry(gameId) { entry =>
          req.as[Json].flatMap { body =>
            body.hcursor.get[Int]("index") match
              case Left(_)    => BadRequest(Json.obj("error" -> Json.fromString("Missing 'index'")))
              case Right(idx) => IO(entry.controller.browseToMove(idx)) >> Ok(stateJson(entry.controller))
          }
        }

      // GET /api/controller/game/{gameId}/move-history
      case GET -> Root / "api" / "controller" / "game" / gameId / "move-history" =>
        withEntry(gameId)(entry => Ok(Json.obj("moves" -> moveHistoryJson(entry.controller))))

      // ── SSE per Spiel ─────────────────────────────────────────

      // GET /api/controller/game/{gameId}/events
      case GET -> Root / "api" / "controller" / "game" / gameId / "events" =>
        withEntry(gameId) { entry =>
          for
            queue  <- Queue.unbounded[IO, Option[Json]]
            _      <- entry.sseQueues.update(queue :: _)
            stream  = Stream.fromQueueNoneTerminated(queue)
                        .map(json => ServerSentEvent(data = Some(json.noSpaces), eventType = Some("state")))
            resp   <- Ok(stream)
          yield resp
        }

      // ── Health ────────────────────────────────────────────────

      case GET -> Root / "api" / "controller" / "games" / "active" =>
        registry.list.flatMap { ids =>
          Ok(Json.obj("activeGames" -> Json.fromValues(ids.map(Json.fromString))))
        }
    }
