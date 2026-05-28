package chess.lichess.api

import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.syntax.all.*
import chess.lichess.client.LichessApiClient
import chess.lichess.config.LichessConfig
import chess.lichess.state.LichessBotSession
import com.comcast.ip4s.{Host, Port}
import org.http4s.HttpRoutes
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.CORS

/** Entry point for the lichess microservice. */
object LichessServer extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    val cfg = LichessConfig.fromEnv()
    program(cfg).useForever.as(ExitCode.Success)

  private def program(cfg: LichessConfig): Resource[IO, Unit] =
    for
      _      <- Resource.eval(IO.println(s"[lichess] starting on port ${cfg.port}, configured=${cfg.hasToken}"))
      http   <- EmberClientBuilder.default[IO].build
      session <- cfg.botToken match
                  case Some(tok) if tok.nonEmpty =>
                    Resource.eval(LichessApiClient.make(http, cfg.baseUrl, tok))
                      .flatMap(LichessBotSession.resource(_, cfg).map(Some(_)))
                      .handleErrorWith { t =>
                        Resource.eval(IO.println(s"[lichess] bot session failed to start: ${t.getMessage}"))
                          .as(Option.empty[LichessBotSession])
                      }
                  case _ =>
                    Resource.eval(IO.println("[lichess] no LICHESS_BOT_TOKEN provided; running in disabled mode")).as(None)
      routes  = CORS.policy.withAllowOriginAll(LichessRoutes(cfg, session))
      port   <- Resource.eval(IO.fromOption(Port.fromInt(cfg.port))(new IllegalArgumentException(s"Invalid port ${cfg.port}")))
      _      <- EmberServerBuilder
                  .default[IO]
                  .withHost(Host.fromString("0.0.0.0").get)
                  .withPort(port)
                  .withHttpApp(routes.orNotFound)
                  .build
    yield ()
