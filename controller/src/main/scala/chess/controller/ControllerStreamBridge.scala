package chess.controller

import chess.util.Observer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.{StringDeserializer, StringSerializer}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.kafka.scaladsl.{Consumer, Producer}
import org.apache.pekko.kafka.{ConsumerSettings, ProducerSettings, Subscriptions}
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.{OverflowStrategy, QueueOfferResult}
import org.apache.pekko.util.ByteString

import java.util.UUID
import scala.concurrent.ExecutionContext

/** Connects controller move notifications to Kafka and exposes them as a Pekko Source.
  *
  * Moves are written as the existing DSL line format, for example "e2 e4".
  * The stream consumer reads the same topic and filters by this controller session key.
  */
class ControllerStreamBridge(controller: Controller)(using system: ActorSystem[?])
    extends Observer:

  private given ec: ExecutionContext = system.executionContext

  private val bootstrapServers =
    sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
  private val movesTopic =
    sys.env.getOrElse("KAFKA_TOPIC_MOVES", "chess-moves")
  private val sessionId =
    sys.env.getOrElse("KAFKA_SESSION_ID", UUID.randomUUID().toString)
  private val consumerGroupId =
    sys.env.getOrElse("KAFKA_CONSUMER_GROUP_ID", s"alu-chess-streaming-$sessionId")

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
      .withGroupId(consumerGroupId)
      .withProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
      .withProperty(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true")

  private val (producerQueue, producerDone) =
    Source
      .queue[ProducerRecord[String, String]](bufferSize = 64, OverflowStrategy.backpressure)
      .toMat(Producer.plainSink(producerSettings))(org.apache.pekko.stream.scaladsl.Keep.both)
      .run()

  producerDone.failed.foreach { ex =>
    println(s"Kafka producer stream failed: ${ex.getMessage}")
  }

  private var lastSentMoveCount: Int = 0

  controller.add(this)

  override def update(): Unit =
    val moves = controller.latestMoveHistory
    if moves.size < lastSentMoveCount then lastSentMoveCount = 0

    if moves.size > lastSentMoveCount then
      for entry <- moves.drop(lastSentMoveCount) do
        val promoStr = entry.move.promotion.map(p => s" $p").getOrElse("")
        val line = s"${entry.move.from} ${entry.move.to}$promoStr\n"
        val record = ProducerRecord[String, String](movesTopic, sessionId, line)

        producerQueue.offer(record).foreach {
          case QueueOfferResult.Enqueued =>
            ()
          case QueueOfferResult.Dropped =>
            println(s"Kafka producer queue dropped move: $line")
          case QueueOfferResult.QueueClosed =>
            println(s"Kafka producer queue is closed; move was not sent: $line")
          case QueueOfferResult.Failure(ex) =>
            println(s"Kafka producer queue failed for move '$line': ${ex.getMessage}")
        }

      lastSentMoveCount = moves.size

  def gameSource: Source[ByteString, ?] =
    Consumer
      .plainSource(consumerSettings, Subscriptions.topics(movesTopic))
      .filter(record => record.key() == sessionId)
      .map(record => ByteString(record.value()))
