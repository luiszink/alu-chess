package chess.controller.api

import cats.effect.*
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import io.circe.*
import io.circe.syntax.*

import chess.controller.perf.DaoRegistry
import chess.model.benchmark.{
  BenchmarkConfig,
  BenchmarkResult,
  BenchmarkRunDao,
  DaoBenchmark,
}

/** REST endpoints for DAO performance benchmarking.
  *
  * `GET  /api/perf/dao/list`              — list available DAO backends + status
  * `POST /api/perf/dao/{name}/benchmark`  — run benchmark against a single DAO
  * `POST /api/perf/dao/compare`           — run same config against multiple DAOs
  * `GET  /api/perf/runs?limit=N`          — list recent benchmark runs
  * `GET  /api/perf/runs/{id}`             — full run incl. raw latencies
  */
object PerfRoutes:

  /** Body of the compare endpoint. */
  final case class CompareRequest(daos: List[String], config: BenchmarkConfig)
      derives io.circe.Codec.AsObject

  def apply(registry: DaoRegistry, runDao: BenchmarkRunDao): HttpRoutes[IO] =

    def daoListJson: Json =
      val all = (registry.available ++ registry.errors.keys).distinct.sorted
      Json.obj(
        "daos" -> all.map { name =>
          val available = registry.lookup(name).isDefined
          Json.obj(
            "name"      -> Json.fromString(name),
            "available" -> Json.fromBoolean(available),
            "error"     -> registry.errorFor(name).fold(Json.Null)(Json.fromString),
          )
        }.asJson
      )

    def runOne(name: String, cfg: BenchmarkConfig): IO[Either[String, BenchmarkResult]] =
      registry.lookup(name) match
        case None      => IO.pure(Left(s"DAO '$name' is not available"))
        case Some(dao) =>
          DaoBenchmark.run(name, dao, cfg)
            .flatTap(runDao.insert)
            .attempt
            .map(_.left.map(_.getMessage))

    HttpRoutes.of[IO] {

      case GET -> Root / "api" / "perf" / "dao" / "list" =>
        Ok(daoListJson)

      case req @ POST -> Root / "api" / "perf" / "dao" / name / "benchmark" =>
        for
          cfg <- req.as[BenchmarkConfig]
          res <- runOne(name, cfg)
          out <- res match
            case Right(r) => Ok(r.asJson)
            case Left(e)  =>
              ServiceUnavailable(Json.obj(
                "error"   -> Json.fromString("DaoUnavailable"),
                "message" -> Json.fromString(e),
                "dao"     -> Json.fromString(name),
              ))
        yield out

      case req @ POST -> Root / "api" / "perf" / "dao" / "compare" =>
        for
          body <- req.as[CompareRequest]
          // Run benchmarks sequentially — concurrent runs would skew shared
          // network/disk metrics for Postgres + Mongo on the same host.
          results <- body.daos.distinct.traverse { name =>
            runOne(name, body.config).map(name -> _)
          }
          out <- Ok(Json.obj(
            "config"  -> body.config.asJson,
            "results" -> results.map { case (name, res) =>
              res match
                case Right(r) => Json.obj(
                    "dao"    -> Json.fromString(name),
                    "ok"     -> Json.fromBoolean(true),
                    "result" -> r.asJson,
                  )
                case Left(e) => Json.obj(
                    "dao"     -> Json.fromString(name),
                    "ok"      -> Json.fromBoolean(false),
                    "message" -> Json.fromString(e),
                  )
            }.asJson,
          ))
        yield out

      case GET -> Root / "api" / "perf" / "runs" :? LimitMatcher(limit) =>
        runDao.findAll(limit.getOrElse(50)).flatMap { runs =>
          Ok(Json.obj("runs" -> runs.map(stripLatencies).asJson))
        }

      case GET -> Root / "api" / "perf" / "runs" / id =>
        runDao.findById(id).flatMap {
          case Some(r) => Ok(r.asJson)
          case None    => NotFound(Json.obj(
            "error"   -> Json.fromString("NotFound"),
            "message" -> Json.fromString(s"Run '$id' not found"),
          ))
        }
    }

  // Strip raw latencies from list view to keep the payload compact.
  private def stripLatencies(r: BenchmarkResult): BenchmarkResult =
    r.copy(latenciesMs = Vector.empty)

  private object LimitMatcher
      extends org.http4s.dsl.impl.OptionalQueryParamDecoderMatcher[Int]("limit")
