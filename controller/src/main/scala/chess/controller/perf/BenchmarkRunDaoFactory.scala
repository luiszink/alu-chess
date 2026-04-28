package chess.controller.perf

import cats.effect.{IO, Resource}
import cats.syntax.all.*
import chess.model.benchmark.{
  BenchmarkRunDao,
  InMemoryBenchmarkRunDao,
  MongoBenchmarkRunDao,
  SlickBenchmarkRunDao,
}

/** Selects a BenchmarkRunDao backend based on `DB_TYPE`. Falls back to
  * in-memory storage if persistence cannot be opened. */
object BenchmarkRunDaoFactory:

  def make: Resource[IO, BenchmarkRunDao] =
    val dbType = sys.env.getOrElse("DB_TYPE", "memory").toLowerCase

    def fallback(reason: String): Resource[IO, BenchmarkRunDao] =
      Resource.eval(
        IO.println(s"[Perf] BenchmarkRunDao falling back to memory: $reason")
      ) *> Resource.pure[IO, BenchmarkRunDao](InMemoryBenchmarkRunDao())

    dbType match
      case "postgres" =>
        val url  = sys.env.getOrElse("DB_URL",      "jdbc:postgresql://localhost:5432/chess")
        val user = sys.env.getOrElse("DB_USER",     "chess")
        val pass = sys.env.getOrElse("DB_PASSWORD", "chess")
        Resource.eval(IO.blocking(SlickBenchmarkRunDao.create(url, user, pass)).attempt)
          .flatMap {
            case Right(dao) => Resource.pure[IO, BenchmarkRunDao](dao)
            case Left(err)  => fallback(err.getMessage)
          }
      case "mongo" =>
        val uri    = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
        val dbName = sys.env.getOrElse("MONGO_DB",  "chess")
        MongoBenchmarkRunDao.resource(uri, dbName)
          .map(d => d: BenchmarkRunDao)
          .handleErrorWith((e: Throwable) => fallback(e.getMessage))
      case _ =>
        Resource.pure[IO, BenchmarkRunDao](InMemoryBenchmarkRunDao())
