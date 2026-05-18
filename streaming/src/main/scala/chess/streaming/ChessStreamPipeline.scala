package chess.streaming

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Flow, Framing}
import org.apache.pekko.util.ByteString
import chess.model.{Game, GameStatus}
import chess.model.ai.Evaluator

import scala.concurrent.{ExecutionContext, Future}

object ChessStreamPipeline:

  // ── Flow 1: Framing + Parsing ──────────────────────────────────────────────
  // Zerlegt den ByteString-Strom in Zeilen, filtert Kommentare/Leerzeilen
  // und parst jede Zeile in ein ParsedMove.
  //
  // DSL-Format: "<from> <to> [promotion=<Q|R|B|N>]"  z.B. "e2 e4" oder "e7 e8 Q"
  //
  // Nächste Woche (Kafka): Diese Flow bleibt unverändert.
  // Der Kafka-Consumer liefert ByteStrings pro Message statt aus einer Datei.
  val parseFlow: Flow[ByteString, ParsedMove, NotUsed] =
    Framing
      .delimiter(ByteString("\n"), maximumFrameLength = 256, allowTruncation = true)
      .map(_.utf8String.trim)
      .filter(line => line.nonEmpty && !line.startsWith("#"))
      .map(parseLine)
      .collect { case Some(m) => m }

  private def parseLine(line: String): Option[ParsedMove] =
    line.split("\\s+").toList match
      case from :: to :: Nil       => Some(ParsedMove(from, to, None))
      case from :: to :: p :: Nil  => Some(ParsedMove(from, to, p.headOption))
      case _                       => None

  // ── Flow 2: Zustandsbehaftete Spielzug-Verarbeitung ───────────────────────
  // scan() hält den aktuellen Game-Zustand und wendet jeden ParsedMove an.
  // Ungültige Züge werden gezählt und übersprungen (kein Stream-Abbruch).
  //
  // Backpressure-Demo:
  //   buffer(16, OverflowStrategy.backpressure) — wenn Flow 3 (Evaluation)
  //   zu langsam ist, stoppt der Buffer bei 16 Elementen und signalisiert
  //   dem Source: "Sende nichts mehr, bis Platz frei ist."
  //   Das ist der Kern von Reactive Streams / Backpressure.
  //
  // Nächste Woche (Kafka): Flow bleibt unverändert.
  def gameProcessingFlow(initial: Game): Flow[ParsedMove, Either[ParsedMove, GameEvent], NotUsed] =
    import org.apache.pekko.stream.OverflowStrategy
    Flow[ParsedMove]
      .buffer(16, OverflowStrategy.backpressure)
      .statefulMap(() => initial -> 0)(
        { case ((game, moveNr), pm) =>
          pm.toMove match
            case None =>
              (game -> moveNr) -> Left(pm)
            case Some(move) =>
              game.applyMoveE(move) match
                case Left(_) =>
                  (game -> moveNr) -> Left(pm)
                case Right(next) =>
                  val ev = GameEvent(moveNr + 1, pm, game, next)
                  (next -> (moveNr + 1)) -> Right(ev)
        },
        _ => None,
      )

  // ── Flow 3: Evaluation (langsamer Schritt → Backpressure via mapAsync) ─────
  // Evaluiert die Stellung nach jedem Zug mit dem bestehenden Evaluator.
  // mapAsync(parallelism = 4): maximal 4 parallele Auswertungen gleichzeitig.
  // Wenn alle 4 Slots belegt sind, signalisiert Pekko dem Upstream: Stop.
  // Das ist Backpressure auf Operator-Ebene.
  //
  // Nächste Woche (Kafka): Flow bleibt unverändert.
  def enrichFlow(using ec: ExecutionContext): Flow[Either[ParsedMove, GameEvent], EnrichedEvent, NotUsed] =
    Flow[Either[ParsedMove, GameEvent]].mapAsync(parallelism = 4) {
      case Left(invalid) =>
        Future.successful(
          EnrichedEvent(
            GameEvent(0, invalid, Game.newGame, Game.newGame),
            evalScore = 0,
          )
        )
      case Right(ev) =>
        Future {
          val score = ev.gameAfter.status match
            case GameStatus.Checkmate => if ev.gameAfter.currentPlayer == chess.model.Color.White then Int.MinValue else Int.MaxValue
            case GameStatus.Stalemate => 0
            case _                    => Evaluator.evaluate(ev.gameAfter.board)
          EnrichedEvent(ev, score)
        }
    }

  // ── Aggregation (für Sink.fold) ────────────────────────────────────────────
  def aggregate(stats: GameStats, ev: EnrichedEvent): GameStats =
    val isInvalid = ev.event.moveNumber == 0
    if isInvalid then
      stats.copy(invalidMoves = stats.invalidMoves + 1)
    else
      stats.copy(
        totalMoves  = stats.totalMoves + 1,
        captures    = stats.captures + (if ev.event.isCapture then 1 else 0),
        checks      = stats.checks + (if ev.event.isCheck then 1 else 0),
        finalEval   = ev.evalScore,
        finalStatus = ev.event.gameAfter.status,
      )
