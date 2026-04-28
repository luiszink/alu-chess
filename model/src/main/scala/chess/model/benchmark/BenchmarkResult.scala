package chess.model.benchmark

import io.circe.Codec

/** Which DAO operation to benchmark. */
enum BenchmarkOp derives Codec.AsObject:
  case Insert, FindAll, FindById, Delete, Mixed

/** User-supplied benchmark parameters.
  *
  * @param operation        DAO operation under test
  * @param recordCount      How many distinct rows to seed (and how many ops
  *                         per iteration for `Insert` / `FindById` /
  *                         `Delete`).
  * @param iterations       Measured iterations.
  * @param warmupIterations Iterations executed before measurement starts.
  * @param seed             Deterministic seed for sample selection.
  */
final case class BenchmarkConfig(
  operation:        BenchmarkOp,
  recordCount:      Int,
  iterations:       Int,
  warmupIterations: Int,
  seed:             Long,
) derives Codec.AsObject

object BenchmarkConfig:
  val Default: BenchmarkConfig = BenchmarkConfig(
    operation        = BenchmarkOp.Mixed,
    recordCount      = 100,
    iterations       = 5,
    warmupIterations = 1,
    seed             = 42L,
  )

/** Aggregated latency statistics. All fields are in milliseconds (or
  * ops/sec for `opsPerSec`). Computed from the per-iteration latencies. */
final case class BenchmarkStats(
  minMs:      Double,
  maxMs:      Double,
  meanMs:     Double,
  medianMs:   Double,
  p95Ms:      Double,
  p99Ms:      Double,
  opsPerSec:  Double,
  totalMs:    Double,
  iterations: Int,
) derives Codec.AsObject

object BenchmarkStats:
  /** Compute aggregate stats from raw per-iteration latencies (ms) and the
    * number of operations executed per iteration. */
  def from(latenciesMs: Vector[Double], opsPerIteration: Int): BenchmarkStats =
    if latenciesMs.isEmpty then
      BenchmarkStats(0, 0, 0, 0, 0, 0, 0, 0, 0)
    else
      val sorted = latenciesMs.sorted
      val total  = sorted.sum
      val mean   = total / sorted.size
      def percentile(p: Double): Double =
        val idx = math.min(sorted.size - 1, math.max(0, math.ceil(p * sorted.size).toInt - 1))
        sorted(idx)
      val totalOps = opsPerIteration.toLong * sorted.size
      val ops      = if total > 0 then totalOps.toDouble * 1000.0 / total else 0.0
      BenchmarkStats(
        minMs      = sorted.head,
        maxMs      = sorted.last,
        meanMs     = mean,
        medianMs   = percentile(0.50),
        p95Ms      = percentile(0.95),
        p99Ms      = percentile(0.99),
        opsPerSec  = ops,
        totalMs    = total,
        iterations = sorted.size,
      )

/** Result of a single benchmark execution. Stored in the run history. */
final case class BenchmarkResult(
  id:              String,
  dao:             String,
  config:          BenchmarkConfig,
  stats:           BenchmarkStats,
  startedAt:       String,
  durationMs:      Double,
  latenciesMs:     Vector[Double],
  opsPerIteration: Int,
) derives Codec.AsObject
