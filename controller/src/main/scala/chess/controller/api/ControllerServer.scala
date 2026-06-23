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
import chess.model.dao.{GameRecordMapper, MongoAnalyticsSummaryDao, MongoGameDao, SlickGameDao}
import chess.controller.{Controller, ControllerStreamBridge, GameRegistry, KafkaAiCoordinator}
import chess.controller.persistence.{KafkaGamePersistencePublisher, KafkaPublishingGameRepository}
import chess.streaming.ChessStreamApp
import chess.tournament.api.TournamentRoutes
import chess.tournament.client.TournamentApiClient
import chess.tournament.config.TournamentConfig
import chess.tournament.model.BotStatus
import chess.util.Observer
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import scala.concurrent.duration.Duration

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

  // ── Dependency Injection: DB via DB_TYPE Env-Variable ─────────
  private def selectedDbType: String =
    sys.env.getOrElse("DB_TYPE", "mongo").trim.toLowerCase

  private def selectedPersistenceTransport: String =
    sys.env.getOrElse("PERSISTENCE_TRANSPORT", "kafka").trim.toLowerCase

  private def directRepository(dbType: String): Resource[IO, GameRepository] =
    dbType match
      case "postgres" =>
        val url  = sys.env.getOrElse("DB_URL",      "jdbc:postgresql://localhost:5432/chess")
        val user = sys.env.getOrElse("DB_USER",     "chess")
        val pass = sys.env.getOrElse("DB_PASSWORD", "chess")
        SlickGameDao.resource(url, user, pass).map(PersistentGameRepository(_))
      case "mongo" =>
        val uri    = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
        val dbName = sys.env.getOrElse("MONGO_DB",  "chess")
        MongoGameDao.resource(uri, dbName).map(PersistentGameRepository(_))
      case _ =>
        Resource.pure[IO, GameRepository](InMemoryGameRepository())

  private def loadInitialRecords(dbType: String): IO[Vector[GameRecord]] =
    dbType match
      case "postgres" =>
        val url  = sys.env.getOrElse("DB_URL",      "jdbc:postgresql://localhost:5432/chess")
        val user = sys.env.getOrElse("DB_USER",     "chess")
        val pass = sys.env.getOrElse("DB_PASSWORD", "chess")
        SlickGameDao.resource(url, user, pass).use { dao =>
          dao.findAll().map(_.flatMap(GameRecordMapper.fromRow))
        }
      case "mongo" =>
        val uri    = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
        val dbName = sys.env.getOrElse("MONGO_DB",  "chess")
        MongoGameDao.resource(uri, dbName).use { dao =>
          dao.findAll().map(_.flatMap(GameRecordMapper.fromRow))
        }
      case _ =>
        IO.pure(Vector.empty)

  private def makeRepository: Resource[IO, GameRepository] =
    val dbType = selectedDbType
    selectedPersistenceTransport match
      case "kafka" =>
        for
          initialRecords <- Resource.eval(loadInitialRecords(dbType))
          publisher      <- KafkaGamePersistencePublisher.resourceFromEnv()
        yield KafkaPublishingGameRepository(InMemoryGameRepository(initialRecords), publisher)
      case _ =>
        directRepository(dbType)

  // Analytics summary is always read from Mongo, independent of DB_TYPE, because
  // the Spark analytics service persists its results to Mongo regardless of the
  // primary game store.
  private def analyticsDaoResource: Resource[IO, MongoAnalyticsSummaryDao] =
    val uri    = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
    val dbName = sys.env.getOrElse("MONGO_DB",  "chess")
    MongoAnalyticsSummaryDao.resource(uri, dbName)

  override def run(args: List[String]): IO[ExitCode] =
    val port = sys.env.getOrElse("PORT", "8081").toInt

    makeRepository.use { repo =>
      EmberClientBuilder.default[IO].withTimeout(Duration.Inf).build.use { httpClient =>
       analyticsDaoResource.use { analyticsDao =>
        for
          ctrl        <- IO(Controller(repo))
          pekkoSystem <- IO(ActorSystem[Nothing](Behaviors.empty, "chess-stream"))
          bridge      <- IO(ControllerStreamBridge(ctrl)(using pekkoSystem))
          _           <- IO(ChessStreamApp.run(bridge.gameSource)(using pekkoSystem))
          sseQueues   <- Ref.of[IO, List[Queue[IO, Option[Json]]]](Nil)

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

          gameRegistry <- GameRegistry.make(repo)
          playerClient  = PlayerServiceClient(httpClient)
          aiCoordinator <- IO(KafkaAiCoordinator(gameRegistry, playerClient.finishSession)(using pekkoSystem))
          tournamentCfg = TournamentConfig.fromEnv()
          tournamentBaseUri <- IO.fromEither(
            Uri.fromString(tournamentCfg.serverUrl)
              .left
              .map(e => new IllegalArgumentException(s"Invalid TOURNAMENT_SERVER_URL: ${e.getMessage}"))
          )
          tournamentDirectorClient = TournamentApiClient(httpClient, tournamentBaseUri)
          tournamentBotClient      = TournamentApiClient(httpClient, tournamentBaseUri)
          tournamentStatusRef     <- Ref.of[IO, BotStatus](BotStatus.Idle)
          tournamentLogQueue      <- Queue.circularBuffer[IO, String](500)
          // Warm-up: register director eagerly so the first UI request doesn't timeout
          _ <- tournamentDirectorClient
                 .register(tournamentCfg.directorName, isBot = false)
                 .handleErrorWith(e => IO.println(s"[tournament] warm-up registration failed: ${e.getMessage}"))

          legacyRoutes = ControllerRoutes(ctrl, sseQueues)
          multiRoutes  = MultiGameRoutes(gameRegistry, playerClient, aiCoordinator)
          tournamentRoutes = TournamentRoutes(
            tournamentCfg,
            tournamentDirectorClient,
            tournamentBotClient,
            tournamentStatusRef,
            tournamentLogQueue,
          )
          analyticsRoutes = AnalyticsRoutes(analyticsDao)
          combined     = legacyRoutes <+> multiRoutes <+> tournamentRoutes <+> analyticsRoutes
          // Normalise a single trailing slash (except "/") before routing, so that
          // calls the frontend is forced to make with a trailing slash — e.g.
          // POST/GET /api/tournament/ (nginx `location /api/tournament/`) — still
          // match the collection routes registered without one.
          normalized   = HttpRoutes[IO] { req =>
            val p = req.uri.path
            if p.endsWithSlash && p.renderString != "/" then
              combined.run(req.withUri(req.uri.withPath(p.dropEndsWithSlash)))
            else
              combined.run(req)
          }
          app          = CORS.policy.withAllowOriginAll(jsonAppWithNotFound(normalized))

          _ <- IO.println(
            s"Controller-Service starting on port $port (DB_TYPE=$selectedDbType, PERSISTENCE_TRANSPORT=$selectedPersistenceTransport, tournament=${tournamentCfg.serverUrl}) ..."
          )
          _ <- EmberServerBuilder
            .default[IO]
            .withHost(host"0.0.0.0")
            .withPort(Port.fromInt(port).get)
            .withHttpApp(app)
            .build
            .useForever
        yield ExitCode.Success
       }
      }
    }
