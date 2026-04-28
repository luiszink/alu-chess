package chess.model.benchmark

import cats.effect.IO

/** Persistence interface for benchmark run history. Decoupled from the
  * `GameDao` so the benchmark history can live in a separate
  * collection/table from the application's actual game data. */
trait BenchmarkRunDao:
  def init(): IO[Unit]
  def insert(result: BenchmarkResult): IO[Unit]
  def findAll(limit: Int): IO[Vector[BenchmarkResult]]
  def findById(id: String): IO[Option[BenchmarkResult]]
  def clear(): IO[Unit]
