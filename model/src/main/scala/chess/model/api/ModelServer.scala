package chess.model.api

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.server.middleware.CORS
import io.circe.Json

object ModelServer extends IOApp:

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

  override def run(args: List[String]): IO[ExitCode] =
    val port = sys.env.getOrElse("PORT", "8082").toInt

    ModelRoutes.routesResource
      .flatMap { routes =>
        val app = CORS.policy.withAllowOriginAll(jsonAppWithNotFound(routes))
        EmberServerBuilder
          .default[IO]
          .withHost(host"0.0.0.0")
          .withPort(Port.fromInt(port).getOrElse(port"8082"))
          .withHttpApp(app)
          .build
      }
      .use { _ =>
        IO.println(s"Model-Service running on http://localhost:$port") *>
          IO.never
      }
      .as(ExitCode.Success)
