package chess.model.benchmark

import cats.effect.unsafe.implicits.global
import chess.model.dao.InMemoryGameDao
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class DaoBenchmarkSpec extends AnyWordSpec with Matchers:

  private val baseCfg = BenchmarkConfig(
    operation        = BenchmarkOp.Insert,
    recordCount      = 5,
    iterations       = 3,
    warmupIterations = 1,
    seed             = 1L,
  )

  "DaoBenchmark" should {

    "produce stats with iterations equal to config.iterations" in {
      val res = DaoBenchmark.run("memory", InMemoryGameDao(), baseCfg).unsafeRunSync()
      res.dao shouldBe "memory"
      res.stats.iterations shouldBe baseCfg.iterations
      res.latenciesMs.size shouldBe baseCfg.iterations
      res.stats.opsPerSec should be > 0.0
    }

    "support every BenchmarkOp without crashing" in {
      val ops = List(
        BenchmarkOp.Insert, BenchmarkOp.FindAll,
        BenchmarkOp.FindById, BenchmarkOp.Delete, BenchmarkOp.Mixed,
      )
      ops.foreach { op =>
        val res = DaoBenchmark.run("memory", InMemoryGameDao(), baseCfg.copy(operation = op))
          .unsafeRunSync()
        res.config.operation shouldBe op
        res.latenciesMs should not be empty
        res.stats.minMs should be <= res.stats.maxMs
        res.stats.medianMs should (be >= res.stats.minMs and be <= res.stats.maxMs)
      }
    }

    "compute monotonic percentiles" in {
      val stats = BenchmarkStats.from(Vector(1.0, 2.0, 3.0, 4.0, 5.0), opsPerIteration = 10)
      stats.minMs shouldBe 1.0
      stats.maxMs shouldBe 5.0
      stats.medianMs shouldBe 3.0
      stats.p95Ms should be >= stats.medianMs
      stats.p99Ms should be >= stats.p95Ms
      stats.opsPerSec should be > 0.0
    }
  }

  "RealGameSamples" should {
    "build deterministic rows with unique ids" in {
      val rows = RealGameSamples.buildBatch(20, "test")
      rows.map(_.id).distinct.size shouldBe 20
      rows.foreach(_.pgn should not be empty)
    }
  }
