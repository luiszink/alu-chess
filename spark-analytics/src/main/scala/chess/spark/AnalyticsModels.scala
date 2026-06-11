package chess.spark

final case class SparkAnalyticsConfig(
  kafkaBootstrapServers: String,
  inputTopic: String,
  mongoUri: String,
  mongoDb: String,
  checkpointDir: String,
  sparkMaster: String,
  maxOffsetsPerTrigger: String
)

object SparkAnalyticsConfig {
  def fromEnv(env: Map[String, String] = sys.env): SparkAnalyticsConfig =
    SparkAnalyticsConfig(
      kafkaBootstrapServers = env.getOrElse("SPARK_KAFKA_BOOTSTRAP_SERVERS", env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "kafka:9092")),
      inputTopic = env.getOrElse("SPARK_INPUT_TOPIC", env.getOrElse("KAFKA_TOPIC_GAME_PERSISTENCE", "game-persistence-requests")),
      mongoUri = env.getOrElse("SPARK_MONGO_URI", env.getOrElse("MONGO_URI", "mongodb://mongo:27017")),
      mongoDb = env.getOrElse("SPARK_MONGO_DB", env.getOrElse("MONGO_DB", "chess")),
      checkpointDir = env.getOrElse("SPARK_CHECKPOINT_DIR", "/tmp/spark-checkpoints/chess-analytics"),
      sparkMaster = env.getOrElse("SPARK_MASTER", "local[*]"),
      maxOffsetsPerTrigger = env.getOrElse("SPARK_MAX_OFFSETS_PER_TRIGGER", "5000")
    )
}

object GamePersistenceEventType {
  val UpsertRequested = "game-record-upsert-requested"
  val DeleteRequested = "game-record-delete-requested"
  val ClearRequested  = "game-records-clear-requested"
}

sealed trait AnalyticsCommand

object AnalyticsCommand {
  final case class Upsert(document: GameAnalyticsDocument) extends AnalyticsCommand
  final case class Delete(recordId: String) extends AnalyticsCommand
  case object Clear extends AnalyticsCommand
}

final case class GameAnalyticsDocument(
  recordId: String,
  datePlayed: String,
  result: String,
  moveCount: Int,
  timeControlName: Option[String],
  initialTimeMs: Option[Long],
  incrementMs: Option[Long],
  finalFen: String,
  pgn: String
)

final case class GameAnalyticsSummary(
  id: String,
  generatedAt: String,
  totalGames: Long,
  averageMoveCount: Double,
  resultCounts: Map[String, Long],
  timeControlCounts: Map[String, Long]
)

