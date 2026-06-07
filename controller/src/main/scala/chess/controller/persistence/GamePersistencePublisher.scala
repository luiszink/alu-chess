package chess.controller.persistence

import cats.effect.{IO, Resource}
import chess.kafka.GamePersistenceEvent
import io.circe.syntax.*
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerConfig, ProducerRecord}
import org.apache.kafka.common.serialization.StringSerializer

import java.util.Properties

trait GamePersistencePublisher:
  def publish(event: GamePersistenceEvent): Unit
  def close(): Unit = ()

object GamePersistencePublisher:
  val noop: GamePersistencePublisher = new GamePersistencePublisher:
    override def publish(event: GamePersistenceEvent): Unit = ()

final class KafkaGamePersistencePublisher private (
  topic:    String,
  producer: KafkaProducer[String, String],
) extends GamePersistencePublisher:

  override def publish(event: GamePersistenceEvent): Unit =
    val key = event.recordId.getOrElse(event.eventType)
    val record = ProducerRecord[String, String](topic, key, event.asJson.noSpaces)
    producer.send(
      record,
      (_, exception) =>
        if exception != null then
          System.err.println(s"[Kafka persistence] publish failed for $key: ${exception.getMessage}")
    )

  override def close(): Unit =
    producer.flush()
    producer.close()

object KafkaGamePersistencePublisher:
  def resourceFromEnv(): Resource[IO, GamePersistencePublisher] =
    val bootstrapServers =
      sys.env.getOrElse("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092")
    val topic =
      sys.env.getOrElse("KAFKA_TOPIC_GAME_PERSISTENCE", "game-persistence-requests")

    Resource.make {
      IO {
        val props = Properties()
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers)
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, classOf[StringSerializer].getName)
        props.put(ProducerConfig.ACKS_CONFIG, "all")

        KafkaGamePersistencePublisher(
          topic,
          KafkaProducer[String, String](props),
        )
      }
    } { publisher =>
      IO(publisher.close())
    }
