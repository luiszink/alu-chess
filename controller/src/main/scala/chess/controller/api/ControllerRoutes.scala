package chess.controller.api

import cats.effect.*
import cats.effect.std.Queue
import fs2.Stream
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.ServerSentEvent
import io.circe.*
import io.circe.syntax.*
import chess.model.*
import chess.controller.{Controller, ControllerInterface}
import chess.util.Observer

import scala.concurrent.duration.*

object ControllerRoutes:

  def apply(ctrl: ControllerInterface, sseQueues: Ref[IO, List[Queue[IO, Option[Json]]]]): HttpRoutes[IO] =

    def gameToJson(game: Game): Json = Json.obj(
      "fen"            -> Json.fromString(Fen.toFen(game)),
      "status"         -> Json.fromString(game.status.toString),
      "currentPlayer"  -> Json.fromString(game.currentPlayer.toString),
      "halfMoveClock"  -> Json.fromInt(game.halfMoveClock),
      "fullMoveNumber" -> Json.fromInt(game.fullMoveNumber),
      "isTerminal"     -> Json.fromBoolean(game.status.isTerminal),
    )

    def stateJson: Json =
      val game = ctrl.game
      Json.obj(
        "game"        -> gameToJson(game),
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

    def moveHistoryJson: Json =
      val history = ctrl.latestMoveHistory
      Json.fromValues(history.map { entry =>
        Json.obj(
          "move"   -> Json.fromString(entry.move.toString),
          "san"    -> Json.fromString(entry.san),
          "status" -> Json.fromString(entry.resultingStatus.toString),
        )
      })

    HttpRoutes.of[IO] {

      // ── Game State ─────────────────────────────────

      // GET /api/controller/state
      case GET -> Root / "api" / "controller" / "state" =>
        Ok(stateJson)

      // POST /api/controller/new-game
      case POST -> Root / "api" / "controller" / "new-game" =>
        IO(ctrl.newGame()) >> Ok(stateJson)

      // POST /api/controller/move  { "from": "e2", "to": "e4", "promotion": null }
      case req @ POST -> Root / "api" / "controller" / "move" =>
        req.as[Json].flatMap { body =>
          val c = body.hcursor
          val result = for
            from  <- c.get[String]("from").left.map(_ => ChessError.InvalidMoveFormat("Missing 'from' field"))
            to    <- c.get[String]("to").left.map(_ => ChessError.InvalidMoveFormat("Missing 'to' field"))
            promo = c.get[String]("promotion").toOption.flatMap(_.headOption)
            fromPos <- Position.fromStringE(from)
            toPos   <- Position.fromStringE(to)
          yield Move(fromPos, toPos, promo)

          result match
            case Left(err) => BadRequest(errorResponse(err))
            case Right(move) =>
              IO(ctrl.doMoveResult(move)).flatMap {
                case Right(_)  => Ok(stateJson)
                case Left(err) =>
                  val status = err match
                    case _: ChessError.GameAlreadyOver => Status.Conflict
                    case _ => Status.UnprocessableEntity
                  IO.pure(Response[IO](status).withEntity(errorResponse(err)))
              }
        }

      // POST /api/controller/load-fen  { "fen": "..." }
      case req @ POST -> Root / "api" / "controller" / "load-fen" =>
        req.as[Json].flatMap { body =>
          body.hcursor.get[String]("fen") match
            case Left(_) => BadRequest(errorResponse(ChessError.InvalidFenFormat("Missing 'fen' field")))
            case Right(fenStr) =>
              IO(ctrl.loadFenResult(fenStr)).flatMap {
                case Right(_)  => Ok(stateJson)
                case Left(err) => BadRequest(errorResponse(err))
              }
        }

      // POST /api/controller/resign
      case POST -> Root / "api" / "controller" / "resign" =>
        IO(ctrl.resign()) >> Ok(stateJson)

      // ── History Navigation ─────────────────────────

      // POST /api/controller/browse/back
      case POST -> Root / "api" / "controller" / "browse" / "back" =>
        IO(ctrl.browseBack()) >> Ok(stateJson)

      // POST /api/controller/browse/forward
      case POST -> Root / "api" / "controller" / "browse" / "forward" =>
        IO(ctrl.browseForward()) >> Ok(stateJson)

      // POST /api/controller/browse/to-start
      case POST -> Root / "api" / "controller" / "browse" / "to-start" =>
        IO(ctrl.browseToStart()) >> Ok(stateJson)

      // POST /api/controller/browse/to-end
      case POST -> Root / "api" / "controller" / "browse" / "to-end" =>
        IO(ctrl.browseToEnd()) >> Ok(stateJson)

      // POST /api/controller/browse/to-move  { "index": 5 }
      case req @ POST -> Root / "api" / "controller" / "browse" / "to-move" =>
        req.as[Json].flatMap { body =>
          body.hcursor.get[Int]("index") match
            case Left(_) => BadRequest(Json.obj("error" -> Json.fromString("Missing 'index' field")))
            case Right(idx) =>
              IO(ctrl.browseToMove(idx)) >> Ok(stateJson)
        }

      // GET /api/controller/move-history
      case GET -> Root / "api" / "controller" / "move-history" =>
        Ok(Json.obj("moves" -> moveHistoryJson))

      // ── Game Records ───────────────────────────────

      // GET /api/controller/games
      case GET -> Root / "api" / "controller" / "games" =>
        val records = ctrl.gameHistory.map { r =>
          Json.obj(
            "id"         -> Json.fromString(r.id),
            "datePlayed" -> Json.fromString(r.datePlayed.toString),
            "result"     -> Json.fromString(r.result),
            "moveCount"  -> Json.fromInt(r.moveCount),
          )
        }
        Ok(Json.obj("games" -> Json.fromValues(records)))

      // POST /api/controller/replay/load  { "id": "..." }
      case req @ POST -> Root / "api" / "controller" / "replay" / "load" =>
        req.as[Json].flatMap { body =>
          body.hcursor.get[String]("id") match
            case Left(_) => BadRequest(Json.obj("error" -> Json.fromString("Missing 'id' field")))
            case Right(id) =>
              IO(ctrl.loadReplay(id)).flatMap {
                case true  => Ok(stateJson)
                case false => NotFound(Json.obj("error" -> Json.fromString("Game not found")))
              }
        }

      // POST /api/controller/replay/exit
      case POST -> Root / "api" / "controller" / "replay" / "exit" =>
        IO(ctrl.exitReplay()) >> Ok(stateJson)

      // ── Import / Export ────────────────────────────

      // GET /api/controller/export
      case GET -> Root / "api" / "controller" / "export" =>
        IO(ctrl.exportCurrentGameAsJson).flatMap { jsonStr =>
          io.circe.parser.parse(jsonStr) match
            case Right(json) => Ok(json)
            case Left(_)     => Ok(jsonStr)
        }

      // POST /api/controller/import  { ...game record JSON... }
      case req @ POST -> Root / "api" / "controller" / "import" =>
        req.as[String](implicitly, EntityDecoder.text[IO]).flatMap { jsonStr =>
          IO(ctrl.importGameFromJson(jsonStr)).flatMap {
            case Right(_)  => Ok(stateJson)
            case Left(err) => BadRequest(errorResponse(err))
          }
        }

      // ── SSE ────────────────────────────────────────

      // GET /api/controller/events  (Server-Sent Events)
      case GET -> Root / "api" / "controller" / "events" =>
        for
          queue <- Queue.unbounded[IO, Option[Json]]
          _     <- sseQueues.update(queue :: _)
          stream = Stream.fromQueueNoneTerminated(queue)
            .map(json => ServerSentEvent(data = Some(json.noSpaces), eventType = Some("state")))
          resp <- Ok(stream)
        yield resp

      // ── Health ─────────────────────────────────────

      case GET -> Root / "health" =>
        Ok(Json.obj("status" -> Json.fromString("ok"), "service" -> Json.fromString("controller")))
    }
