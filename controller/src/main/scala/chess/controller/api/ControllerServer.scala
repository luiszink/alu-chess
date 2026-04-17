package chess.controller.api

import cats.effect.*
import cats.effect.std.Queue
import cats.syntax.foldable.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.CORS
import io.circe.*
import io.circe.syntax.*
import chess.model.*
import chess.controller.Controller
import chess.util.Observer

object ControllerServer extends IOApp:

  private def jsonAppWithNotFound(routes: HttpRoutes[IO]): HttpApp[IO] =
    HttpApp[IO] { req =>
      routes.run(req).getOrElseF {
        NotFound(Json.obj(
          "error"   -> Json.fromString("NotFound"),
          "message" -> Json.fromString("Route not found"),
          "method"  -> Json.fromString(req.method.name),
          "path"    -> Json.fromString(req.uri.path.renderString),
        ))
      }
    }

  private def gameToJson(game: Game): Json = Json.obj(
    "fen"            -> Json.fromString(Fen.toFen(game)),
    "status"         -> Json.fromString(game.status.toString),
    "currentPlayer"  -> Json.fromString(game.currentPlayer.toString),
    "halfMoveClock"  -> Json.fromInt(game.halfMoveClock),
    "fullMoveNumber" -> Json.fromInt(game.fullMoveNumber),
    "isTerminal"     -> Json.fromBoolean(game.status.isTerminal),
  )

  override def run(args: List[String]): IO[ExitCode] =
    val port = sys.env.getOrElse("PORT", "8081").toInt

    for
      ctrl      <- IO(Controller())
      sseQueues <- Ref.of[IO, List[Queue[IO, Option[Json]]]](Nil)

      // Observer that pushes state to all SSE clients
      observer = new Observer:
        override def update(): Unit =
          val state = Json.obj(
            "game"        -> gameToJson(ctrl.game),
            "browseIndex" -> Json.fromInt(ctrl.browseIndex),
            "totalStates" -> Json.fromInt(ctrl.gameStatesCount),
            "isAtLatest"  -> Json.fromBoolean(ctrl.isAtLatest),
            "isInReplay"  -> Json.fromBoolean(ctrl.isInReplay),
            "statusText"  -> Json.fromString(ctrl.statusText),
          )
          // Fire-and-forget: push to all queues, remove closed ones
          val push = sseQueues.get.flatMap { queues =>
            queues.traverse_(q => q.tryOffer(Some(state)).void)
          }
          push.unsafeRunAndForget()(using cats.effect.unsafe.implicits.global)

      _ <- IO(ctrl.add(observer))

      routes = ControllerRoutes(ctrl, sseQueues)
      app    = CORS.policy.withAllowOriginAll(jsonAppWithNotFound(routes))

      _ <- IO.println(s"Controller-Service starting on port $port ...")
      _ <- EmberServerBuilder
        .default[IO]
        .withHost(host"0.0.0.0")
        .withPort(Port.fromInt(port).get)
        .withHttpApp(app)
        .build
        .useForever
    yield ExitCode.Success
