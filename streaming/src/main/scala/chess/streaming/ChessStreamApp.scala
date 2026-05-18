package chess.streaming

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.{FileIO, Sink, Source, StreamConverters}
import org.apache.pekko.util.ByteString
import chess.model.Game

import java.nio.file.Paths
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

/** Einstiegspunkt für die Pekko-Streams-Demo.
  *
  * Anforderungen:
  *   1. Implizites ActorSystem  → `given system`
  *   2. Source                  → FileIO / StreamConverters / ControllerStreamBridge
  *   3. Mindestens ein Flow     → parseFlow, gameProcessingFlow, enrichFlow (3 Stück)
  *   4. Externe DSL             → sample-game.dsl
  *   5. Sink gibt auf Konsole   → Sink.foreach(printMove)
  *   6. .via() für Flows        → source.via(f1).via(f2).via(f3)
  *   7. .runWith() für Sink     → .runWith(sink)
  *   8. Modular für Kafka       → Source-Parameter tauschen, Flows unverändert
  *
  * Backpressure:
  *   - buffer(16, backpressure) in gameProcessingFlow: puffert bis 16 Elemente,
  *     danach stoppt die Source automatisch (Upstream wird gestoppt).
  *   - mapAsync(4) in enrichFlow: max. 4 parallele Evaluierungen;
  *     bei vollem Slot signalisiert Pekko dem Upstream: "Warte."
  *
  * Nächste Woche (Kafka):
  *   Source: FileIO.fromPath(...) → Consumer.plainSource(settings, Subscriptions.topics("chess-moves"))
  *   Sink:   Sink.foreach(...)   → Producer.plainSink(settings)
  *   Flows:  alle drei bleiben unverändert ✓
  */
object ChessStreamApp:

  /** Startet die Pipeline mit einer beliebigen ByteString-Source.
    * Nächste Woche: Source gegen Kafka-Consumer tauschen — diese Methode bleibt gleich.
    */
  def run(source: Source[ByteString, ?])
         (using system: ActorSystem[Nothing]): Future[Done] =
    given ec: ExecutionContext = system.executionContext
    var stats = GameStats.empty
    val sink: Sink[EnrichedEvent, Future[Done]] =
      Sink.foreach[EnrichedEvent] { ev =>
        printMove(ev)
        stats = ChessStreamPipeline.aggregate(stats, ev)
      }
    source
      .via(ChessStreamPipeline.parseFlow)
      .via(ChessStreamPipeline.gameProcessingFlow(Game.newGame))
      .via(ChessStreamPipeline.enrichFlow)
      .runWith(sink)
      .map { done =>
        println("─" * 60)
        println(stats)
        done
      }(ec)

  def main(args: Array[String]): Unit =

    // 1. Implizites ActorSystem — stellt Threads und Materializer bereit.
    given system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "chess-stream")

    given ec: ExecutionContext = system.executionContext

    // 2. Source — liest die DSL-Datei als ByteString-Strom.
    //    Nächste Woche: durch Kafka-Consumer ersetzen (run-Parameter).
    val source = args.headOption match
      case Some(path) =>
        println(s"Verarbeite Datei: $path")
        FileIO.fromPath(Paths.get(path))
      case None =>
        println("Verarbeite Classpath-Resource: /sample-game.dsl")
        StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/sample-game.dsl"))

    println("─" * 60)

    run(source).onComplete {
      case Success(_) =>
        system.terminate()
      case Failure(ex) =>
        println(s"Stream-Fehler: ${ex.getMessage}")
        system.terminate()
    }

    scala.concurrent.Await.ready(
      system.whenTerminated,
      5.minutes,
    )

  private def printMove(ev: EnrichedEvent): Unit =
    val e = ev.event
    if e.moveNumber == 0 then
      println(s"[UNGÜLTIG] ${e.raw.from} ${e.raw.to}")
    else
      val player  = if e.moveNumber % 2 == 1 then "Weiß" else "Schwarz"
      val capture = if e.isCapture then " x" else "  "
      val check   = if e.isCheck   then " +" else "  "
      val eval    = if ev.evalScore >= 0 then f"+${ev.evalScore}%4d" else f"${ev.evalScore}%5d"
      val status  = e.gameAfter.status
      println(
        f"[Zug ${e.moveNumber}%3d] $player%-8s  ${e.raw.from}->${e.raw.to}$capture$check  Eval: $eval cP  $status"
      )
