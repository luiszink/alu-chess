package chess.model.benchmark

import cats.effect.IO
import io.circe.parser.decode
import io.circe.syntax.*

import java.util.concurrent.ConcurrentHashMap
import scala.jdk.CollectionConverters.*

/** Thread-safe in-memory implementation of [[BenchmarkRunDao]]. Used as
  * fallback when neither Postgres nor Mongo is reachable. */
class InMemoryBenchmarkRunDao extends BenchmarkRunDao:
  private val store: ConcurrentHashMap[String, BenchmarkResult] =
    new ConcurrentHashMap[String, BenchmarkResult]()

  override def init(): IO[Unit] = IO.unit

  override def insert(result: BenchmarkResult): IO[Unit] =
    IO(store.put(result.id, result)).void

  override def findAll(limit: Int): IO[Vector[BenchmarkResult]] =
    IO {
      store.values.iterator.asScala.toVector
        .sortBy(_.startedAt)(Ordering[String].reverse)
        .take(limit)
    }

  override def findById(id: String): IO[Option[BenchmarkResult]] =
    IO(Option(store.get(id)))

  override def clear(): IO[Unit] = IO(store.clear())

object InMemoryBenchmarkRunDao:
  def apply(): InMemoryBenchmarkRunDao = new InMemoryBenchmarkRunDao()
