package chess.aiworker

import chess.kafka.{AiMoveRequest, AiMoveResponse}
import io.circe.parser.decode
import io.circe.syntax.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.kafka.scaladsl.{Consumer, Producer}
import org.apache.pekko.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success}

object KafkaAiWorker:

  def main(args: Array[String]): Unit =
    given system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "chess-ai-worker")

    val done = run()
    done.onComplete {
      case Success(_) =>
        println("Kafka AI worker stopped.")
        system.terminate()
      case Failure(ex) =>
        println(s"Kafka AI worker failed: ${ex.getMessage}")
        system.terminate()
    }(system.executionContext)

    Await.ready(system.whenTerminated, Duration.Inf)

  def run()(using system: ActorSystem[?]): Future[Done] =
    val bootstrapServers =
      sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val requestTopic =
      sys.env.getOrElse("KAFKA_TOPIC_AI_REQUESTS", "chess-ai-requests")
    val responseTopic =
      sys.env.getOrElse("KAFKA_TOPIC_AI_RESPONSES", "chess-ai-responses")
    val groupId =
      sys.env.getOrElse("KAFKA_AI_WORKER_GROUP_ID", "alu-chess-ai-workers")

    val consumerSettings =
      ConsumerSettings(
        system.settings.config.getConfig("pekko.kafka.consumer"),
        new StringDeserializer,
        new StringDeserializer,
      )
        .withBootstrapServers(bootstrapServers)
        .withGroupId(groupId)
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")

    val producerSettings =
      ProducerSettings(
        system.settings.config.getConfig("pekko.kafka.producer"),
        new StringSerializer,
        new StringSerializer,
      ).withBootstrapServers(bootstrapServers)

    println(
      s"Kafka AI worker listening on '$requestTopic' and publishing to '$responseTopic' ($bootstrapServers)"
    )

    Consumer
      .plainSource(consumerSettings, Subscriptions.topics(requestTopic))
      .map(record => decode[AiMoveRequest](record.value()))
      .mapConcat {
        case Right(request) =>
          val response = AiMoveHandler.handle(request)
          List(ProducerRecord[String, String](responseTopic, response.gameId, response.asJson.noSpaces))
        case Left(error) =>
          println(s"Discarding invalid AI request JSON: ${error.getMessage}")
          Nil
      }
      .runWith(Producer.plainSink(producerSettings))
