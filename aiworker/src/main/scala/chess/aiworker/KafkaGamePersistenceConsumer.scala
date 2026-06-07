package chess.aiworker

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import chess.kafka.GamePersistenceEvent
import chess.model.GameJson
import chess.model.dao.{GameDao, GameRecordMapper, MongoGameDao, SlickGameDao}
import io.circe.parser.decode
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.kafka.scaladsl.Consumer
import org.apache.pekko.kafka.{ConsumerSettings, Subscriptions}
import org.apache.pekko.stream.scaladsl.Sink

import scala.concurrent.{ExecutionContext, Future}

object KafkaGamePersistenceConsumer:

  def run()(using system: ActorSystem[?]): Future[Done] =
    val dbType = sys.env.getOrElse("DB_TYPE", "mongo").trim.toLowerCase
    if dbType == "memory" then
      println("Kafka game persistence consumer disabled because DB_TYPE=memory.")
      Future.successful(Done)
    else
      daoResource(dbType).use { dao =>
        IO.fromFuture(IO(runWithDao(dao)))
      }.unsafeToFuture()

  private def daoResource(dbType: String): Resource[IO, GameDao] =
    dbType match
      case "postgres" =>
        val url  = sys.env.getOrElse("DB_URL",      "jdbc:postgresql://localhost:5432/chess")
        val user = sys.env.getOrElse("DB_USER",     "chess")
        val pass = sys.env.getOrElse("DB_PASSWORD", "chess")
        SlickGameDao.resource(url, user, pass)
      case "mongo" =>
        val uri    = sys.env.getOrElse("MONGO_URI", "mongodb://localhost:27017")
        val dbName = sys.env.getOrElse("MONGO_DB",  "chess")
        MongoGameDao.resource(uri, dbName)
      case other =>
        Resource.eval(IO.raiseError(new IllegalArgumentException(s"Unsupported DB_TYPE '$other' for Kafka persistence")))

  private def runWithDao(dao: GameDao)(using system: ActorSystem[?]): Future[Done] =
    given ExecutionContext = system.executionContext

    val bootstrapServers =
      sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val topic =
      sys.env.getOrElse("KAFKA_TOPIC_GAME_PERSISTENCE", "game-persistence-requests")
    val groupId =
      sys.env.getOrElse("KAFKA_PERSISTENCE_WORKER_GROUP_ID", "alu-chess-persistence-workers")

    val consumerSettings =
      ConsumerSettings(
        system.settings.config.getConfig("pekko.kafka.consumer"),
        new StringDeserializer,
        new StringDeserializer,
      )
        .withBootstrapServers(bootstrapServers)
        .withGroupId(groupId)
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false")

    println(s"Kafka game persistence consumer listening on '$topic' ($bootstrapServers, DB_TYPE=${sys.env.getOrElse("DB_TYPE", "mongo")})")

    Consumer
      .committableSource(consumerSettings, Subscriptions.topics(topic))
      .mapAsync(1) { message =>
        processMessage(message.record.value(), dao)
          .unsafeToFuture()
          .flatMap(_ => message.committableOffset.commitScaladsl())
      }
      .runWith(Sink.ignore)

  private def processMessage(value: String, dao: GameDao): IO[Unit] =
    decode[GamePersistenceEvent](value) match
      case Left(error) =>
        IO.println(s"[Kafka persistence] discarding invalid event JSON: ${error.getMessage}")
      case Right(event) =>
        handleEvent(event, dao)

  private def handleEvent(event: GamePersistenceEvent, dao: GameDao): IO[Unit] =
    event.eventType match
      case GamePersistenceEvent.UpsertRequested =>
        event.record match
          case Some(recordJson) =>
            GameJson.fromRecordJson(recordJson) match
              case Right(record) =>
                dao.insert(GameRecordMapper.toRow(record)) >>
                  IO.println(s"[Kafka persistence] upserted game record ${record.id}")
              case Left(error) =>
                IO.println(s"[Kafka persistence] discarding invalid game record in event ${event.eventId}: $error")
          case None =>
            IO.println(s"[Kafka persistence] discarding upsert event ${event.eventId} without record payload")

      case GamePersistenceEvent.DeleteRequested =>
        event.recordId match
          case Some(recordId) =>
            dao.delete(recordId) >>
              IO.println(s"[Kafka persistence] deleted game record $recordId")
          case None =>
            IO.println(s"[Kafka persistence] discarding delete event ${event.eventId} without recordId")

      case GamePersistenceEvent.ClearRequested =>
        dao.clear() >> IO.println("[Kafka persistence] cleared all game records")

      case other =>
        IO.println(s"[Kafka persistence] discarding unknown event type '$other' in event ${event.eventId}")
