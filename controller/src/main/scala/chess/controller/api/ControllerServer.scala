package chess.controller.api

import cats.effect.*
import cats.effect.std.Queue
import cats.syntax.foldable.*
import cats.syntax.semigroupk.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.server.middleware.CORS
import io.circe.*
import io.circe.syntax.*
import chess.model.*
import chess.controller.{Controller, GameRegistry}
import chess.model.{InMemoryGameRepository, PostgresGameRepository}
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

    EmberClientBuilder.default[IO].build.use { httpClient =>
      for
        // ── Repository: Postgres wenn DB_URL gesetzt, sonst In-Memory ──
        repo <- IO.blocking {
          sys.env.get("DB_URL") match
            case Some(url) =>
              val user     = sys.env.getOrElse("DB_USER", "chess")
              val password = sys.env.getOrElse("DB_PASSWORD", "chess")
              PostgresGameRepository.create(url, user, password)
            case None =>
              InMemoryGameRepository()
        }

        // ── Legacy single-game support (unchanged) ────────────
        ctrl      <- IO(Controller(repo))
        sseQueues <- Ref.of[IO, List[Queue[IO, Option[Json]]]](Nil)

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
            val push = sseQueues.get.flatMap { queues =>
              queues.traverse_(q => q.tryOffer(Some(state)).void)
            }
            push.unsafeRunAndForget()(using cats.effect.unsafe.implicits.global)

        _ <- IO(ctrl.add(observer))

        // ── Multi-game infrastructure ─────────────────────────
        gameRegistry <- GameRegistry.make
        playerClient  = PlayerServiceClient(httpClient)

        legacyRoutes = ControllerRoutes(ctrl, sseQueues)
        multiRoutes  = MultiGameRoutes(gameRegistry, playerClient)
        combined     = legacyRoutes <+> multiRoutes
        app          = CORS.policy.withAllowOriginAll(jsonAppWithNotFound(combined))

        _ <- IO.println(s"Controller-Service starting on port $port ...")
        _ <- EmberServerBuilder
          .default[IO]
          .withHost(host"0.0.0.0")
          .withPort(Port.fromInt(port).get)
          .withHttpApp(app)
          .build
          .useForever
      yield ExitCode.Success
    }
