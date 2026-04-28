package chess.model.benchmark

import cats.effect.IO
import cats.syntax.all.*
import chess.model.dao.{GameDao, GameRow}

import java.util.UUID
import java.time.LocalDateTime

/** Pure cats-effect benchmark engine. Runs a configurable number of warmup
  * + measured iterations against any `GameDao` implementation and returns
  * a [[BenchmarkResult]] including the raw per-iteration latencies (so the
  * UI can render histograms).
  *
  * The engine always seeds the DAO with `recordCount` real-PGN-based rows
  * and, when applicable, cleans up after itself so consecutive runs don't
  * accumulate state.
  */
object DaoBenchmark:

  private def nowIso(): String = LocalDateTime.now().toString

  /** Time a single IO action and return its result + duration in
    * milliseconds (using the cats-effect monotonic clock). */
  private def time[A](io: IO[A]): IO[(A, Double)] =
    for
      t0 <- IO.monotonic
      a  <- io
      t1 <- IO.monotonic
    yield (a, (t1 - t0).toNanos.toDouble / 1_000_000.0)

  /** Execute a full benchmark run. The given `daoName` is purely
    * informational and will be embedded in the result. */
  def run(daoName: String, dao: GameDao, cfg: BenchmarkConfig): IO[BenchmarkResult] =
    val seedPrefix = s"bench-${UUID.randomUUID().toString.take(8)}"

    def seedRows: Vector[GameRow] =
      RealGameSamples.buildBatch(cfg.recordCount, seedPrefix)

    def insertAll(rows: Vector[GameRow]): IO[Unit] =
      rows.traverse_(dao.insert)

    val startedAt = nowIso()

    val benchmarkBody: IO[(Vector[Double], Int)] = cfg.operation match
      case BenchmarkOp.Insert =>
        // Each iteration inserts a fresh batch (with iteration-scoped ids) and
        // wipes after measurement so DB sizes stay comparable across iterations.
        val opsPerIter = cfg.recordCount
        def oneIter(idx: Int): IO[Double] =
          val rows = RealGameSamples.buildBatch(cfg.recordCount, s"$seedPrefix-i$idx")
          time(insertAll(rows)).flatMap { case (_, ms) =>
            dao.clear().as(ms)
          }
        for
          _ <- (0 until cfg.warmupIterations).toVector.traverse_(oneIter)
          ts <- (0 until cfg.iterations).toVector.traverse(oneIter)
        yield (ts, opsPerIter)

      case BenchmarkOp.FindAll =>
        // Seed once, measure findAll repeatedly.
        val opsPerIter = 1
        for
          _ <- dao.clear()
          _ <- insertAll(seedRows)
          _ <- (0 until cfg.warmupIterations).toVector.traverse_(_ => dao.findAll())
          ts <- (0 until cfg.iterations).toVector.traverse { _ =>
            time(dao.findAll()).map(_._2)
          }
          _ <- dao.clear()
        yield (ts, opsPerIter)

      case BenchmarkOp.FindById =>
        // Seed once, then look up each id once per iteration.
        val opsPerIter = cfg.recordCount
        val rows       = seedRows
        val ids        = rows.map(_.id)
        for
          _ <- dao.clear()
          _ <- insertAll(rows)
          _ <- (0 until cfg.warmupIterations).toVector.traverse_ { _ =>
            ids.traverse_(dao.findById)
          }
          ts <- (0 until cfg.iterations).toVector.traverse { _ =>
            time(ids.traverse_(dao.findById)).map(_._2)
          }
          _ <- dao.clear()
        yield (ts, opsPerIter)

      case BenchmarkOp.Delete =>
        // Each iteration: insert N rows, then time deleting them all.
        val opsPerIter = cfg.recordCount
        def oneIter(idx: Int): IO[Double] =
          val rows = RealGameSamples.buildBatch(cfg.recordCount, s"$seedPrefix-d$idx")
          val ids  = rows.map(_.id)
          for
            _   <- insertAll(rows)
            res <- time(ids.traverse_(dao.delete))
          yield res._2
        for
          _ <- (0 until cfg.warmupIterations).toVector.traverse_(oneIter)
          ts <- (0 until cfg.iterations).toVector.traverse(oneIter)
          _ <- dao.clear()
        yield (ts, opsPerIter)

      case BenchmarkOp.Mixed =>
        // 1 insert-batch + 1 findAll + N findById + N delete per iteration.
        val opsPerIter = cfg.recordCount * 3 + 1
        def oneIter(idx: Int): IO[Double] =
          val rows = RealGameSamples.buildBatch(cfg.recordCount, s"$seedPrefix-m$idx")
          val ids  = rows.map(_.id)
          val combined =
            insertAll(rows) *>
              dao.findAll().void *>
              ids.traverse_(dao.findById) *>
              ids.traverse_(dao.delete)
          time(combined).map(_._2)
        for
          _ <- (0 until cfg.warmupIterations).toVector.traverse_(oneIter)
          ts <- (0 until cfg.iterations).toVector.traverse(oneIter)
          _ <- dao.clear()
        yield (ts, opsPerIter)

    for
      tStart <- IO.monotonic
      pair   <- benchmarkBody
      tEnd   <- IO.monotonic
      (lats, opsPerIter) = pair
      total  = (tEnd - tStart).toNanos.toDouble / 1_000_000.0
      stats  = BenchmarkStats.from(lats, opsPerIter)
    yield BenchmarkResult(
      id              = UUID.randomUUID().toString,
      dao             = daoName,
      config          = cfg,
      stats           = stats,
      startedAt       = startedAt,
      durationMs      = total,
      latenciesMs     = lats,
      opsPerIteration = opsPerIter,
    )
