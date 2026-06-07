package chess.model.api

import cats.effect.*
import cats.effect.std.Dispatcher
import chess.kafka.{StockfishEngineRequest, StockfishEngineResponse}
import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax.*
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.kafka.scaladsl.{Consumer, Producer}
import org.apache.pekko.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import org.apache.pekko.stream.{OverflowStrategy, QueueOfferResult}
import org.apache.pekko.stream.Materializer.matFromSystem
import org.apache.pekko.stream.scaladsl.{Keep, Sink, Source}
import org.http4s.Status

import java.util.UUID
import scala.concurrent.Future
import scala.concurrent.duration.*

final class KafkaEngineProxy private (
  system:        ActorSystem[Nothing],
  dispatcher:    Dispatcher[IO],
  producerQueue: org.apache.pekko.stream.scaladsl.SourceQueueWithComplete[ProducerRecord[String, String]],
  pending:       Ref[IO, Map[String, Deferred[IO, StockfishEngineResponse]]],
  clientId:      String,
  requestTopic:  String,
  defaultTimeout: FiniteDuration,
) extends ModelRoutes.EngineProxy:

  override def health: IO[Either[(Status, Json), Json]] =
    send(StockfishEngineRequest.HealthOperation, Json.obj())

  override def bestMove(payload: Json): IO[Either[(Status, Json), Json]] =
    send(StockfishEngineRequest.BestMoveOperation, payload)

  override def evaluate(payload: Json): IO[Either[(Status, Json), Json]] =
    send(StockfishEngineRequest.EvaluateOperation, payload)

  private def send(operation: String, payload: Json): IO[Either[(Status, Json), Json]] =
    for
      requestId <- IO(UUID.randomUUID().toString)
      deferred  <- Deferred[IO, StockfishEngineResponse]
      request    = StockfishEngineRequest(requestId, clientId, operation, payload)
      record     = ProducerRecord[String, String](requestTopic, requestId, request.asJson.noSpaces)
      timeout    = timeoutFor(payload)
      _         <- pending.update(_ + (requestId -> deferred))
      offer     <- IO.fromFuture(IO(producerQueue.offer(record)))
      result    <- offer match
                     case QueueOfferResult.Enqueued =>
                       deferred.get
                         .timeoutTo(timeout, IO.pure(timeoutResponse(requestId, operation, timeout)))
                         .map(KafkaEngineProxy.responseToResult)
                     case QueueOfferResult.Dropped =>
                       IO.pure(Left(Status.ServiceUnavailable -> serviceError(
                         "KafkaEngineQueueDropped",
                         s"Kafka engine request was dropped for operation '$operation'",
                       )))
                     case QueueOfferResult.QueueClosed =>
                       IO.pure(Left(Status.ServiceUnavailable -> serviceError(
                         "KafkaEngineQueueClosed",
                         "Kafka engine request queue is closed",
                       )))
                     case QueueOfferResult.Failure(ex) =>
                       IO.pure(Left(Status.ServiceUnavailable -> serviceError(
                         "KafkaEngineQueueFailure",
                         Option(ex.getMessage).getOrElse("Kafka engine request queue failed"),
                       )))
      _         <- pending.update(_ - requestId)
    yield result

  private def timeoutFor(payload: Json): FiniteDuration =
    val engineThinkTime = payload.hcursor
      .get[Long]("thinkTimeMs")
      .toOption
      .filter(_ > 0)
      .map(_.millis + 1.second)
      .getOrElse(defaultTimeout)
    if engineThinkTime > defaultTimeout then engineThinkTime else defaultTimeout

  private def timeoutResponse(requestId: String, operation: String, timeout: FiniteDuration): StockfishEngineResponse =
    StockfishEngineResponse(
      requestId = requestId,
      clientId = clientId,
      ok = false,
      status = Status.GatewayTimeout.code,
      body = serviceError(
        "KafkaEngineTimeout",
        s"Kafka engine request '$operation' timed out after ${timeout.toMillis} ms",
      ),
    )

  private def serviceError(kind: String, message: String): Json = Json.obj(
    "error"   -> Json.fromString(kind),
    "message" -> Json.fromString(message),
  )

object KafkaEngineProxy:

  def resource: Resource[IO, ModelRoutes.EngineProxy] =
    for
      dispatcher <- Dispatcher.parallel[IO]
      system <- Resource.make(IO(ActorSystem[Nothing](Behaviors.empty, "stockfish-engine-kafka"))) { sys =>
        IO(sys.terminate()) *> IO.fromFuture(IO(sys.whenTerminated)).void
      }
      proxy <- Resource.eval(make(system, dispatcher))
    yield proxy

  def responseToResult(response: StockfishEngineResponse): Either[(Status, Json), Json] =
    val status = Status.fromInt(response.status).fold(_ => Status.BadGateway, identity)
    if response.ok && status.isSuccess then Right(response.body)
    else Left(status -> response.body)

  private def make(
    system:     ActorSystem[Nothing],
    dispatcher: Dispatcher[IO],
  ): IO[KafkaEngineProxy] =
    val bootstrapServers =
      sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val requestTopic =
      sys.env.getOrElse("KAFKA_TOPIC_STOCKFISH_REQUESTS", "stockfish-engine-requests")
    val responseTopic =
      sys.env.getOrElse("KAFKA_TOPIC_STOCKFISH_RESPONSES", "stockfish-engine-responses")
    val clientId =
      sys.env.getOrElse("KAFKA_STOCKFISH_CLIENT_ID", UUID.randomUUID().toString)
    val responseGroupId =
      sys.env.getOrElse("KAFKA_STOCKFISH_RESPONSE_GROUP_ID", s"alu-chess-model-stockfish-$clientId")
    val defaultTimeout =
      sys.env
        .get("ENGINE_TIMEOUT_MS")
        .flatMap(_.toLongOption)
        .filter(_ > 0)
        .map(_.millis)
        .getOrElse(5.seconds)

    val producerSettings =
      ProducerSettings(
        system.settings.config.getConfig("pekko.kafka.producer"),
        new StringSerializer,
        new StringSerializer,
      ).withBootstrapServers(bootstrapServers)

    val consumerSettings =
      ConsumerSettings(
        system.settings.config.getConfig("pekko.kafka.consumer"),
        new StringDeserializer,
        new StringDeserializer,
      )
        .withBootstrapServers(bootstrapServers)
        .withGroupId(responseGroupId)
        .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest")
        .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")

    for
      pending <- Ref.of[IO, Map[String, Deferred[IO, StockfishEngineResponse]]](Map.empty)
      queueDone <- IO {
        given ActorSystem[Nothing] = system
        Source
          .queue[ProducerRecord[String, String]](bufferSize = 64, OverflowStrategy.backpressure)
          .toMat(Producer.plainSink(producerSettings))(Keep.both)
          .run()
      }
      (producerQueue, producerDone) = queueDone
      _ <- IO {
        producerDone.failed.foreach { ex =>
          println(s"Kafka engine request producer failed: ${ex.getMessage}")
        }(system.executionContext)
      }
      _ <- IO {
        given ActorSystem[Nothing] = system
        Consumer
          .plainSource(consumerSettings, Subscriptions.topics(responseTopic))
          .mapAsync(1)(record => dispatcher.unsafeToFuture(handleRecord(record.value(), clientId, pending)))
          .runWith(Sink.ignore)
          .failed
          .foreach { ex =>
            println(s"Kafka engine response consumer failed: ${ex.getMessage}")
          }(system.executionContext)
      }
    yield KafkaEngineProxy(
      system,
      dispatcher,
      producerQueue,
      pending,
      clientId,
      requestTopic,
      defaultTimeout,
    )

  private def handleRecord(
    value:    String,
    clientId: String,
    pending:  Ref[IO, Map[String, Deferred[IO, StockfishEngineResponse]]],
  ): IO[Unit] =
    decode[StockfishEngineResponse](value) match
      case Left(error) =>
        IO.println(s"Discarding invalid Stockfish engine response JSON: ${error.getMessage}")
      case Right(response) if response.clientId != clientId =>
        IO.unit
      case Right(response) =>
        pending.get.flatMap { requests =>
          requests.get(response.requestId) match
            case Some(deferred) => deferred.complete(response).void
            case None           => IO.unit
        }
