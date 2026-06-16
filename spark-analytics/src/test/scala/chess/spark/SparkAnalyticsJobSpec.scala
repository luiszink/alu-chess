package chess.spark

import org.apache.spark.sql.SparkSession
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class SparkAnalyticsJobSpec extends AnyWordSpec with Matchers with BeforeAndAfterAll {
  private var sparkStarted = false

  private lazy val spark = {
    sparkStarted = true
    SparkSession
      .builder()
      .appName("spark-analytics-test")
      .master("local[1]")
      .config("spark.ui.enabled", "false")
      .config("spark.sql.shuffle.partitions", "1")
      .getOrCreate()
  }

  override def afterAll(): Unit = {
    if (sparkStarted) spark.stop()
    super.afterAll()
  }

  "SparkAnalyticsJob.buildSummary" should {

    "return an empty summary for no games" in {
      assumeSparkSupportedByCurrentJdk()
      import spark.implicits._
      val summary = SparkAnalyticsJob.buildSummary(spark.createDataset(Seq.empty[GameAnalyticsDocument]).toDF())

      summary.id shouldBe SparkAnalyticsJob.CurrentSummaryId
      summary.totalGames shouldBe 0L
      summary.averageMoveCount shouldBe 0.0
      summary.resultCounts shouldBe Map.empty
      summary.timeControlCounts shouldBe Map.empty
    }

    "aggregate result and time-control counts with Spark" in {
      assumeSparkSupportedByCurrentJdk()
      import spark.implicits._
      val documents = Seq(
        document("game-1", result = "1-0", moveCount = 40, timeControlName = Some("Blitz")),
        document("game-2", result = "0-1", moveCount = 20, timeControlName = Some("Blitz")),
        document("game-3", result = "1-0", moveCount = 30, timeControlName = None)
      )

      val summary = SparkAnalyticsJob.buildSummary(spark.createDataset(documents).toDF())

      summary.totalGames shouldBe 3L
      summary.averageMoveCount shouldBe 30.0
      summary.resultCounts shouldBe Map("1-0" -> 2L, "0-1" -> 1L)
      summary.timeControlCounts shouldBe Map("Blitz" -> 2L, "none" -> 1L)
    }
  }

  private def document(
    id: String,
    result: String,
    moveCount: Int,
    timeControlName: Option[String]
  ): GameAnalyticsDocument =
    GameAnalyticsDocument(
      recordId = id,
      datePlayed = "2026-06-11T12:00:00",
      result = result,
      moveCount = moveCount,
      timeControlName = timeControlName,
      initialTimeMs = None,
      incrementMs = None,
      finalFen = "fen",
      pgn = "pgn"
    )

  private def assumeSparkSupportedByCurrentJdk(): Unit =
    assume(
      Runtime.version().feature() <= 21,
      s"Spark 3.5 local tests require Java 21 or older; current Java is ${Runtime.version()}"
    )
}
