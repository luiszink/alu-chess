package chess.aiworker

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success}

object KafkaWorkerApp:

  def main(args: Array[String]): Unit =
    given system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "chess-kafka-worker")
    given ExecutionContext = system.executionContext

    val aiDone = KafkaAiWorker.run()
    val persistenceDone =
      if persistenceConsumerEnabled then KafkaGamePersistenceConsumer.run()
      else
        println("Kafka game persistence consumer disabled by configuration.")
        Future.successful(Done)

    val done =
      Future.sequence(List(aiDone, persistenceDone))
        .map(_ => Done)

    done.onComplete {
      case Success(_) =>
        println("Kafka worker app stopped.")
        system.terminate()
      case Failure(ex) =>
        println(s"Kafka worker app failed: ${ex.getMessage}")
        system.terminate()
    }(system.executionContext)

    Await.ready(system.whenTerminated, Duration.Inf)

  private def persistenceConsumerEnabled: Boolean =
    val transport = sys.env.getOrElse("PERSISTENCE_TRANSPORT", "kafka").trim.toLowerCase
    val enabled = sys.env.getOrElse("KAFKA_PERSISTENCE_CONSUMER_ENABLED", "true").trim.toLowerCase
    transport == "kafka" && enabled != "false"
