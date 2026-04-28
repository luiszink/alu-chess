package chess.model.benchmark

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*
import slick.jdbc.PostgresProfile.api.*

import scala.concurrent.Await
import scala.concurrent.duration.*

/** Persists benchmark runs to Postgres. The full result (including raw
  * latencies) is serialized to JSON in a `payload` column to keep the
  * schema simple and forward-compatible. */
class SlickBenchmarkRunDao(db: Database) extends BenchmarkRunDao:
  import SlickBenchmarkRunDao.*

  override def init(): IO[Unit] =
    IO.fromFuture(IO.delay(db.run(runs.schema.createIfNotExists)))

  override def insert(result: BenchmarkResult): IO[Unit] =
    val row = (result.id, result.dao, result.config.operation.toString,
               result.startedAt, result.stats.meanMs, result.stats.opsPerSec,
               result.asJson.noSpaces)
    IO.fromFuture(IO.delay(db.run(runs.insertOrUpdate(row)))).void

  override def findAll(limit: Int): IO[Vector[BenchmarkResult]] =
    IO.fromFuture(IO.delay(
      db.run(runs.sortBy(_.startedAt.desc).take(limit).result)
    )).map { rows =>
      rows.toVector.flatMap(row => decode[BenchmarkResult](row._7).toOption)
    }

  override def findById(id: String): IO[Option[BenchmarkResult]] =
    IO.fromFuture(IO.delay(
      db.run(runs.filter(_.id === id).result.headOption)
    )).map(_.flatMap(row => decode[BenchmarkResult](row._7).toOption))

  override def clear(): IO[Unit] =
    IO.fromFuture(IO.delay(db.run(runs.delete))).void

object SlickBenchmarkRunDao:
  // Tuple-encoded table to avoid an extra case-class mapping for an
  // append-only audit log.
  class RunsTable(tag: Tag)
      extends Table[(String, String, String, String, Double, Double, String)](
        tag, "benchmark_runs"
      ):
    def id        = column[String]("id", O.PrimaryKey)
    def dao       = column[String]("dao")
    def operation = column[String]("operation")
    def startedAt = column[String]("started_at")
    def meanMs    = column[Double]("mean_ms")
    def opsPerSec = column[Double]("ops_per_sec")
    def payload   = column[String]("payload")
    def *         = (id, dao, operation, startedAt, meanMs, opsPerSec, payload)

  val runs: TableQuery[RunsTable] = TableQuery[RunsTable]

  def create(url: String, user: String, password: String): SlickBenchmarkRunDao =
    val db  = Database.forURL(url = url, user = user, password = password,
                              driver = "org.postgresql.Driver")
    val dao = new SlickBenchmarkRunDao(db)
    Await.result(db.run(runs.schema.createIfNotExists), 30.seconds)
    dao
