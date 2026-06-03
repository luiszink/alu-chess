package chess.tournament.api

import cats.effect.{ExitCode, IO, IOApp, Ref, Resource}
import cats.effect.std.Queue
import chess.tournament.client.TournamentApiClient
import chess.tournament.config.TournamentConfig
import chess.tournament.model.BotStatus
import com.comcast.ip4s.{Host, Port}
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.CORS
import org.http4s.Uri
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.{SSLContext, TrustManager, X509TrustManager}

object TournamentServer extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    val cfg = TournamentConfig.fromEnv()
    // Install a trust-all SSLContext as JVM default so Ember accepts staging certs
    IO(installTrustAllSsl()) >> program(cfg).useForever.as(ExitCode.Success)

  private def installTrustAllSsl(): Unit =
    val trustAll = Array[TrustManager](new X509TrustManager {
      def getAcceptedIssuers: Array[X509Certificate] = Array.empty
      def checkClientTrusted(certs: Array[X509Certificate], authType: String): Unit = ()
      def checkServerTrusted(certs: Array[X509Certificate], authType: String): Unit = ()
    })
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(null, trustAll, new SecureRandom())
    SSLContext.setDefault(ctx)

  private def program(cfg: TournamentConfig): Resource[IO, Unit] =
    for
      _ <- Resource.eval(IO.println(s"[tournament] starting on port ${cfg.port}, server=${cfg.serverUrl}"))

      http <- EmberClientBuilder.default[IO].build

      baseUri <- Resource.eval(
        IO.fromEither(
          Uri.fromString(cfg.serverUrl).left.map(e => new IllegalArgumentException(s"Invalid server URL: ${e.getMessage}"))
        )
      )

      apiClient  = TournamentApiClient(http, baseUri)
      statusRef <- Resource.eval(Ref.of[IO, BotStatus](BotStatus.Idle))
      logQueue  <- Resource.eval(Queue.circularBuffer[IO, String](500))

      routes = CORS.policy.withAllowOriginAll(
        TournamentRoutes(cfg, apiClient, statusRef, logQueue)
      )

      port <- Resource.eval(
        IO.fromOption(Port.fromInt(cfg.port))(new IllegalArgumentException(s"Invalid port ${cfg.port}"))
      )

      _ <- EmberServerBuilder
             .default[IO]
             .withHost(Host.fromString("0.0.0.0").get)
             .withPort(port)
             .withHttpApp(routes.orNotFound)
             .build

      _ <- Resource.eval(IO.println(s"[tournament] ready on http://0.0.0.0:${cfg.port}"))
    yield ()
