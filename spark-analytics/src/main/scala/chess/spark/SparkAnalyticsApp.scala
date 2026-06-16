package chess.spark

import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.col
import org.apache.spark.sql.streaming.Trigger

object SparkAnalyticsApp {

  def main(args: Array[String]): Unit = {
    val config = SparkAnalyticsConfig.fromEnv()

    val spark = SparkSession
      .builder()
      .appName("alu-chess-spark-analytics")
      .master(config.sparkMaster)
      .getOrCreate()

    spark.sparkContext.setLogLevel(sys.env.getOrElse("SPARK_LOG_LEVEL", "WARN"))

    println(
      s"Spark analytics consuming '${config.inputTopic}' from ${config.kafkaBootstrapServers} " +
        s"and writing analytics to MongoDB ${config.mongoDb} (${config.mongoUri})"
    )

    val kafkaEvents = spark
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", config.kafkaBootstrapServers)
      .option("subscribe", config.inputTopic)
      .option("startingOffsets", "earliest")
      .option("failOnDataLoss", "false")
      // Bound each micro-batch so the initial backlog read (startingOffsets=earliest)
      // cannot overwhelm the driver when the whole batch is collected below.
      .option("maxOffsetsPerTrigger", config.maxOffsetsPerTrigger)
      .load()

    val query = kafkaEvents
      .writeStream
      .foreachBatch { (batch: DataFrame, batchId: Long) =>
        processBatch(batch, batchId, config, spark)
      }
      .option("checkpointLocation", config.checkpointDir)
      .trigger(Trigger.ProcessingTime("10 seconds"))
      .start()

    query.awaitTermination()
  }

  private[spark] def processBatch(
    batch: DataFrame,
    batchId: Long,
    config: SparkAnalyticsConfig,
    spark: SparkSession
  ): Unit = {
    val values = batch
      .select(
        col("partition"),
        col("offset"),
        col("value").cast("string").as("value")
      )
      .orderBy(col("partition"), col("offset"))
      .collect()
      .map(_.getAs[String]("value"))
      .toVector

    if (values.nonEmpty) {
      MongoAnalyticsRepository.withClient(config.mongoUri, config.mongoDb) { repository =>
        val commands = values.flatMap { value =>
          AnalyticsTransformer.commandFromJson(value) match {
            case Right(command) =>
              Some(command)
            case Left(error) =>
              println(s"[Spark analytics] discarding event in batch $batchId: $error")
              None
          }
        }

        if (commands.nonEmpty) {
          val shouldRecompute = SparkAnalyticsJob.applyCommands(commands, repository)
          if (shouldRecompute) {
            val summary = SparkAnalyticsJob.recomputeSummary(spark, config.mongoUri, config.mongoDb, repository)
            println(
              s"[Spark analytics] batch $batchId processed ${commands.size} command(s); " +
                s"summary totalGames=${summary.totalGames}, averageMoveCount=${summary.averageMoveCount}"
            )
          } else {
            println(s"[Spark analytics] batch $batchId cleared analytics collections")
          }
        }
      }
    }
  }
}

