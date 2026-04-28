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
import chess.model.dao.{SlickGameDao, MongoGameDao}
import chess.controller.{Controller, GameRegistry}
import chess.controller.perf.{BenchmarkRunDaoFactory, DaoRegistry}
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

  // ── Dependency Injection: DB via DB_TYPE Env-Variable ─────────
  private def makeRepository: Resource[IO, GameRepository] =
    sys.env.getOrElse("DB_TYPE", "memory") match
      case "postgres" =>
        Resource.eval(IO.blocking {
          val url  = sys.env.getOrElse("DB_URL",      "jdbc:postgresql://localhost:5432/chess")
          val user = sys.env.getOrElse("DB_USER",     "chess")
          val pass = sys.env.getOrElse("DB_PASSWORD", "chess")
          PersistentGameRepository(SlickGameDao.create(url, user, pass))
        })
      case "mongo" =>
        val uri    = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
        val dbName = sys.env.getOrElse("MONGO_DB",  "chess")
        MongoGameDao.resource(uri, dbName).map(PersistentGameRepository(_))
      case _ =>
        Resource.pure[IO, GameRepository](InMemoryGameRepository())

  override def run(args: List[String]): IO[ExitCode] =
    val port = sys.env.getOrElse("PORT", "8081").toInt

    val resources =
      for
        repo       <- makeRepository
        httpClient <- EmberClientBuilder.default[IO].build
        daoReg     <- DaoRegistry.make
        runDao     <- BenchmarkRunDaoFactory.make
        _          <- Resource.eval(runDao.init())
      yield (repo, httpClient, daoReg, runDao)

    resources.use { case (repo, httpClient, daoReg, runDao) =>
        for
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

          gameRegistry <- GameRegistry.make(repo)
          playerClient  = PlayerServiceClient(httpClient)

          legacyRoutes = ControllerRoutes(ctrl, sseQueues)
          multiRoutes  = MultiGameRoutes(gameRegistry, playerClient)
          perfRoutes   = PerfRoutes(daoReg, runDao)
          combined     = legacyRoutes <+> multiRoutes <+> perfRoutes
          app          = CORS.policy.withAllowOriginAll(jsonAppWithNotFound(combined))

          _ <- IO.println(
            s"Controller-Service starting on port $port " +
            s"(DB_TYPE=${sys.env.getOrElse("DB_TYPE", "memory")}, " +
            s"perfDaos=${daoReg.available.mkString(",")}) ..."
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
