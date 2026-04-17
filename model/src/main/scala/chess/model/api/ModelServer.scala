package chess.model.api

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.CORS

object ModelServer extends IOApp:

  override def run(args: List[String]): IO[ExitCode] =
    val port = sys.env.getOrElse("PORT", "8082").toInt

    ModelRoutes.routesResource
      .flatMap { routes =>
        val corsRoutes = CORS.policy.withAllowOriginAll(routes)
        EmberServerBuilder
          .default[IO]
          .withHost(host"0.0.0.0")
          .withPort(Port.fromInt(port).getOrElse(port"8082"))
          .withHttpApp(corsRoutes.orNotFound)
          .build
      }
      .use { _ =>
        IO.println(s"Model-Service running on http://localhost:$port") *>
          IO.never
      }
      .as(ExitCode.Success)
