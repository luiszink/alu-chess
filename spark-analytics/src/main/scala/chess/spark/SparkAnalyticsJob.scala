package chess.spark

import chess.spark.AnalyticsCommand.{Clear, Delete, Upsert}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.{avg, coalesce, col, lit}
import org.apache.spark.sql.types.{IntegerType, LongType, StringType, StructField, StructType}

import java.time.Instant

object SparkAnalyticsJob {
  val CurrentSummaryId = "current"

  /** Stable schema for `game_analytics` so the connector never has to sample
    * documents and the DataFrame keeps all columns even on an empty collection. */
  private val AnalyticsSchema: StructType = StructType(
    Seq(
      StructField("recordId", StringType),
      StructField("datePlayed", StringType),
      StructField("result", StringType),
      StructField("moveCount", IntegerType),
      StructField("timeControlName", StringType),
      StructField("initialTimeMs", LongType),
      StructField("incrementMs", LongType),
      StructField("finalFen", StringType),
      StructField("pgn", StringType)
    )
  )

  def applyCommands(commands: Seq[AnalyticsCommand], repository: MongoAnalyticsRepository): Boolean =
    commands.foldLeft(false) {
      case (_, Upsert(document)) =>
        repository.upsert(document)
        true
      case (_, Delete(recordId)) =>
        repository.delete(recordId)
        true
      case (_, Clear) =>
        repository.clear()
        false
    }

  /** Reads `game_analytics` as a distributed Spark DataFrame via the MongoDB
    * Spark connector, aggregates it, and persists the summary. The collection
    * is never materialised on the driver – only the small aggregated result is. */
  def recomputeSummary(
    spark: SparkSession,
    mongoUri: String,
    mongoDb: String,
    repository: MongoAnalyticsRepository
  ): GameAnalyticsSummary = {
    val analytics = readAnalytics(spark, mongoUri, mongoDb)
    val summary = buildSummary(analytics)
    repository.replaceSummary(summary)
    summary
  }

  private def readAnalytics(spark: SparkSession, mongoUri: String, mongoDb: String): DataFrame =
    spark.read
      .format("mongodb")
      .option("connection.uri", mongoUri)
      .option("database", mongoDb)
      .option("collection", MongoAnalyticsRepository.AnalyticsCollection)
      .schema(AnalyticsSchema)
      .load()

  /** Aggregates the analytics DataFrame with Spark SQL. All grouping/averaging
    * runs in Spark; only the (tiny) aggregated rows are collected to the driver. */
  def buildSummary(analytics: DataFrame): GameAnalyticsSummary = {
    val totalGames = analytics.count()

    val averageMoveCount =
      if (totalGames == 0L) 0.0
      else {
        val row = analytics.agg(avg(col("moveCount")).as("avg")).head()
        if (row.isNullAt(0)) 0.0 else row.getDouble(0)
      }

    val resultCounts = countByColumn(analytics, "result")

    val timeControlCounts = countByColumn(
      analytics.withColumn("timeControl", coalesce(col("timeControlName"), lit("none"))),
      "timeControl"
    )

    GameAnalyticsSummary(
      id = CurrentSummaryId,
      generatedAt = Instant.now().toString,
      totalGames = totalGames,
      averageMoveCount = averageMoveCount,
      resultCounts = resultCounts,
      timeControlCounts = timeControlCounts
    )
  }

  private def countByColumn(df: DataFrame, column: String): Map[String, Long] =
    df.groupBy(column)
      .count()
      .collect()
      .map(row => row.getString(0) -> row.getLong(1))
      .toMap
}
