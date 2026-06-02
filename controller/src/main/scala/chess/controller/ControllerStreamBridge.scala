package chess.controller

import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.stream.OverflowStrategy
import org.apache.pekko.util.ByteString
import chess.util.Observer

/** Verbindet das Observer-Pattern des Controllers mit einer Pekko-Stream-Source.
  *
  * Jeder Spielzug ruft update() auf → wird als DSL-Zeile ("e2 e4") in die Queue gepusht.
  * Die gameSource kann direkt in ChessStreamApp.run() übergeben werden.
  *
  * Nächste Woche (Kafka): Diese Bridge fällt weg; stattdessen wird ein
  * Kafka-Producer in update() aufgerufen und ein Consumer als Source genutzt.
  */
class ControllerStreamBridge(controller: Controller)(using ActorSystem[?])
    extends Observer:

  private val (queue, _source) =
    Source.queue[ByteString](bufferSize = 64, OverflowStrategy.dropHead)
      .preMaterialize()

  // DropHead: wenn die Pipeline zu langsam ist, wird der älteste Eintrag verworfen.
  // So blockiert Streaming nie die UI.

  private var lastSentMoveCount: Int = 0

  controller.add(this)

  override def update(): Unit =
    val moves = controller.latestMoveHistory
    // Spielneustart: Zähler zurücksetzen
    if moves.size < lastSentMoveCount then lastSentMoveCount = 0
    // Neue Züge senden
    if moves.size > lastSentMoveCount then
      for entry <- moves.drop(lastSentMoveCount) do
        val promoStr = entry.move.promotion.map(p => s" $p").getOrElse("")
        queue.offer(ByteString(s"${entry.move.from} ${entry.move.to}$promoStr\n"))
      lastSentMoveCount = moves.size

  def gameSource: Source[ByteString, ?] = _source
