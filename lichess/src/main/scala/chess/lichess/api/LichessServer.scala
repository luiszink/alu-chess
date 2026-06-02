package chess.lichess.api

import cats.effect.{ExitCode, IO, IOApp, Ref, Resource}
import cats.syntax.all.*
import chess.lichess.client.LichessApiClient
import chess.lichess.config.LichessConfig
import chess.lichess.state.{BotState, LichessBotSession}
import com.comcast.ip4s.{Host, Port}
import org.http4s.HttpRoutes
import org.http4s.client.Client
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.CORS

import scala.concurrent.duration.*

/** Entry point for the lichess microservice. */
object LichessServer extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    val cfg = LichessConfig.fromEnv()
    program(cfg).useForever.as(ExitCode.Success)

  private def program(cfg: LichessConfig): Resource[IO, Unit] =
    for
      _      <- Resource.eval(IO.println(s"[lichess] starting on port ${cfg.port}, token=${if cfg.hasToken then "set" else "missing"}"))
      http   <- EmberClientBuilder.default[IO].build
      stateRef <- Resource.eval(Ref.of[IO, BotState](
                    if cfg.hasToken then BotState.Connecting else BotState.NotConfigured
                  ))
      _      <- cfg.botToken match
                  case Some(tok) if tok.nonEmpty =>
                    Resource.eval(IO.println("[lichess] starting bot connect loop in background")) *>
                      backgroundConnect(http, cfg, tok, stateRef).background.void
                  case _ =>
                    Resource.eval(IO.println("[lichess] no LICHESS_BOT_TOKEN provided; running in disabled mode"))
      routes  = CORS.policy.withAllowOriginAll(LichessRoutes(cfg, stateRef))
      port   <- Resource.eval(IO.fromOption(Port.fromInt(cfg.port))(new IllegalArgumentException(s"Invalid port ${cfg.port}")))
      _      <- EmberServerBuilder
                  .default[IO]
                  .withHost(Host.fromString("0.0.0.0").get)
                  .withPort(port)
                  .withHttpApp(routes.orNotFound)
                  .build
    yield ()

  /** Try to establish the bot session; on failure, log + record the reason and
    * retry with backoff. Once running, blocks until the inner Resource releases
    * (e.g. unexpected stream completion) and then loops back to retry. */
  private def backgroundConnect(
      http: Client[IO],
      cfg: LichessConfig,
      tok: String,
      stateRef: Ref[IO, BotState],
  ): IO[Unit] =
    val attempt: IO[Unit] =
      LichessApiClient.make(http, cfg.baseUrl, tok).flatMap { client =>
        stateRef.set(BotState.Connecting) *>
          LichessBotSession
            .resource(client, cfg)
            .use { session =>
              stateRef.set(BotState.Running(session)) *>
                IO.println(s"[lichess] connected as ${session.username}") *>
                IO.never
            }
      }

    def loop(delay: FiniteDuration): IO[Unit] =
      attempt.handleErrorWith { t =>
        val msg = Option(t.getMessage).filter(_.nonEmpty).getOrElse(t.getClass.getSimpleName)
        stateRef.set(BotState.Failed(msg)) *>
          IO.println(s"[lichess] connect failed: $msg; retrying in $delay") *>
          IO.sleep(delay) *>
          loop((delay * 2).min(60.seconds))
      }

    loop(5.seconds)
