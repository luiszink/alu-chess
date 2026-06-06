package chess.controller

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import chess.kafka.{AiMoveRequest, AiMoveResponse}
import chess.model.Fen
import io.circe.parser.decode
import io.circe.syntax.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.kafka.scaladsl.{Consumer, Producer}
import org.apache.pekko.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import org.apache.pekko.stream.{OverflowStrategy, QueueOfferResult}
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}

import java.util.UUID
import scala.collection.concurrent.TrieMap
import scala.concurrent.{ExecutionContext, Future}

class KafkaAiCoordinator(
  registry:       GameRegistry,
  onGameFinished: String => IO[Unit] = _ => IO.unit,
)(using system: ActorSystem[?]) extends AiMoveRequester:

  private given ec: ExecutionContext = system.executionContext

  private val bootstrapServers =
    sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  private val requestTopic =
    sys.env.getOrElse("KAFKA_TOPIC_AI_REQUESTS", "chess-ai-requests")
  private val responseTopic =
    sys.env.getOrElse("KAFKA_TOPIC_AI_RESPONSES", "chess-ai-responses")
  private val responseGroupId =
    sys.env.getOrElse("KAFKA_AI_RESPONSE_GROUP_ID", s"alu-chess-controller-ai-${UUID.randomUUID()}")
  private val timeLimitMs =
    sys.env.getOrElse("AI_TIME_LIMIT_MS", "2000").toLongOption.getOrElse(2000L)
  private val maxDepth =
    sys.env.getOrElse("AI_MAX_DEPTH", "4").toIntOption.getOrElse(4)

  private val pendingRequests = TrieMap.empty[String, String]

  private val producerSettings =
    ProducerSettings(
      system.settings.config.getConfig("pekko.kafka.producer"),
      new StringSerializer,
      new StringSerializer,
    ).withBootstrapServers(bootstrapServers)

  private val consumerSettings =
    ConsumerSettings(
      system.settings.config.getConfig("pekko.kafka.consumer"),
      new StringDeserializer,
      new StringDeserializer,
    )
      .withBootstrapServers(bootstrapServers)
      .withGroupId(responseGroupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")

  private val (producerQueue, producerDone) =
    Source
      .queue[ProducerRecord[String, String]](bufferSize = 64, OverflowStrategy.backpressure)
      .toMat(Producer.plainSink(producerSettings))(Keep.both)
      .run()

  producerDone.failed.foreach { ex =>
    println(s"Kafka AI request producer failed: ${ex.getMessage}")
  }

  private val responseDone =
    Consumer
      .plainSource(consumerSettings, Subscriptions.topics(responseTopic))
      .mapAsync(1)(record => handleResponseRecord(record.value()))
      .runWith(Sink.ignore)

  responseDone.failed.foreach { ex =>
    println(s"Kafka AI response consumer failed: ${ex.getMessage}")
  }

  override def requestMove(gameId: String, controller: ControllerInterface): IO[Unit] =
    val requestId = UUID.randomUUID().toString
    val request = AiMoveRequest(
      requestId = requestId,
      gameId = gameId,
      fen = Fen.toFen(controller.game),
      aiColor = controller.game.currentPlayer.toString,
      timeLimitMs = timeLimitMs,
      maxDepth = maxDepth,
    )

    pendingRequests.putIfAbsent(gameId, requestId) match
      case Some(_) =>
        IO.unit
      case None =>
        val record = ProducerRecord[String, String](requestTopic, gameId, request.asJson.noSpaces)
        IO.fromFuture(IO(producerQueue.offer(record))).flatMap {
          case QueueOfferResult.Enqueued =>
            IO.unit
          case QueueOfferResult.Dropped =>
            IO(pendingRequests.remove(gameId)) >>
              IO.println(s"Kafka AI request queue dropped request $requestId for game $gameId")
          case QueueOfferResult.QueueClosed =>
            IO(pendingRequests.remove(gameId)) >>
              IO.println(s"Kafka AI request queue is closed for game $gameId")
          case QueueOfferResult.Failure(ex) =>
            IO(pendingRequests.remove(gameId)) >>
              IO.println(s"Kafka AI request queue failed for game $gameId: ${ex.getMessage}")
        }

  private def handleResponseRecord(value: String): Future[Unit] =
    decode[AiMoveResponse](value) match
      case Left(error) =>
        Future.successful(println(s"Discarding invalid AI response JSON: ${error.getMessage}"))
      case Right(response) =>
        handleResponse(response).unsafeToFuture()

  private def handleResponse(response: AiMoveResponse): IO[Unit] =
    pendingRequests.get(response.gameId) match
      case Some(expectedRequestId) if expectedRequestId == response.requestId =>
        pendingRequests.remove(response.gameId)
        registry.get(response.gameId).flatMap {
          case None =>
            IO.println(s"Ignoring AI response for unknown game ${response.gameId}")
          case Some(entry) =>
            response.toMoveEither match
              case Left(message) =>
                IO.println(s"AI response failed for game ${response.gameId}: $message")
              case Right(move) =>
                IO(entry.controller.doMoveResult(move)).flatMap {
                  case Right(updated) if updated.status.isTerminal =>
                    onGameFinished(response.gameId)
                  case Right(_) =>
                    IO.unit
                  case Left(error) =>
                    IO.println(s"Could not apply AI move for game ${response.gameId}: ${error.message}")
                }
        }
      case _ =>
        IO.println(s"Ignoring stale AI response ${response.requestId} for game ${response.gameId}")
