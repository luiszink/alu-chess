package chess.playerservice.api

import cats.effect.*
import com.comcast.ip4s.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.CORS
import io.circe.*
import chess.playerservice.PlayerRegistry

object PlayerServer extends IOApp:

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
    val port = sys.env.getOrElse("PORT", "8083").toInt

    PlayerRegistry.make.flatMap { registry =>
      val routes = PlayerRoutes(registry)
      val app    = CORS.policy.withAllowOriginAll(jsonAppWithNotFound(routes))

      IO.println(s"PlayerService starting on port $port ...") >>
        EmberServerBuilder
          .default[IO]
          .withHost(host"0.0.0.0")
          .withPort(Port.fromInt(port).get)
          .withHttpApp(app)
          .build
          .useForever
    }.as(ExitCode.Success)
