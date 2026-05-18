package chess.streaming

import org.apache.pekko.Done
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.stream.scaladsl.{FileIO, Sink, StreamConverters}
import chess.model.Game

import java.nio.file.Paths
import scala.concurrent.Future
import scala.concurrent.duration.*
import scala.util.{Failure, Success}

/** Einstiegspunkt für die Akka-Streams-Demo.
  *
  * Anforderungen des Profs:
  *   1. Implizites ActorSystem  → `given system`
  *   2. Source                  → FileIO / StreamConverters
  *   3. Mindestens ein Flow     → parseFlow, gameProcessingFlow, enrichFlow (3 Stück)
  *   4. Externe DSL             → sample-game.dsl
  *   5. Sink gibt auf Konsole   → Sink.foreach(printMove)
  *   6. .via() für Flows        → source.via(f1).via(f2).via(f3)
  *   7. .runWith() für Sink     → .runWith(sink)
  *   8. Modular für Kafka       → Kommentare zeigen Swap-Punkte
  *
  * Backpressure:
  *   - buffer(16, backpressure) in gameProcessingFlow: puffert bis 16 Elemente,
  *     danach stoppt FileIO automatisch (Upstream wird gestoppt).
  *   - mapAsync(4) in enrichFlow: max. 4 parallele Evaluierungen;
  *     bei vollem Slot signalisiert Pekko dem Upstream: "Warte."
  *
  * Nächste Woche (Kafka):
  *   Source: FileIO.fromPath(...) → Consumer.plainSource(settings, Subscriptions.topics("chess-moves"))
  *   Sink:   Sink.foreach(...)   → Producer.plainSink(settings)
  *   Flows:  alle drei bleiben unverändert ✓
  */
object ChessStreamApp:

  def main(args: Array[String]): Unit =

    // 1. Implizites ActorSystem — stellt Threads und Materializer bereit.
    //    `given` ist Scala 3 für `implicit val`.
    given system: ActorSystem[Nothing] =
      ActorSystem(Behaviors.empty, "chess-stream")

    given ec: scala.concurrent.ExecutionContext = system.executionContext

    // 2. Source — liest die DSL-Datei als ByteString-Strom.
    //    Nächste Woche: durch Kafka-Consumer ersetzen.
    val source = args.headOption match
      case Some(path) =>
        println(s"Verarbeite Datei: $path")
        FileIO.fromPath(Paths.get(path))
      case None =>
        println("Verarbeite Classpath-Resource: /sample-game.dsl")
        StreamConverters.fromInputStream(() => getClass.getResourceAsStream("/sample-game.dsl"))

    println("─" * 60)

    // Akkumulator für die Endstatistik — wird im Sink befüllt.
    // Kein Race Condition: mapAsync preserviert Reihenfolge, Elemente kommen sequenziell an.
    var stats = GameStats.empty

    // 5. Sink — gibt jedes Element direkt auf der Konsole aus und sammelt Statistik.
    //    Nächste Woche: durch Producer.plainSink(kafkaSettings) ersetzen.
    val sink: Sink[EnrichedEvent, Future[Done]] =
      Sink.foreach[EnrichedEvent] { ev =>
        printMove(ev)
        stats = ChessStreamPipeline.aggregate(stats, ev)
      }

    // 6+7. Pipeline: Source –.via()–> Flows –.runWith()–> Sink
    //      .runWith() materialisiert den Graph und startet den Stream.
    //      Gibt den materialisierten Wert des Sinks zurück (Future[Done]).
    val futureDone: Future[Done] = source
      .via(ChessStreamPipeline.parseFlow)
      .via(ChessStreamPipeline.gameProcessingFlow(Game.newGame))
      .via(ChessStreamPipeline.enrichFlow)
      .runWith(sink)

    futureDone.onComplete {
      case Success(_) =>
        println("─" * 60)
        println(stats)
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
