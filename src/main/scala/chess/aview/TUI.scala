package chess.aview

import chess.controller.ControllerInterface
import chess.model.Move
import chess.util.Observer
import scala.io.StdIn

class TUI(controller: ControllerInterface) extends Observer:
  controller.add(this)

  override def update(): Unit =
    println(controller.boardToString)
    println(controller.statusText)

  def inputLoop(): Unit =
    println("alu-chess – Befehle: 'n' = neues Spiel, 'q' = beenden, oder Zug z.B. 'e2 e4'")
    update()
    var running = true
    while running do
      val input = StdIn.readLine("> ")
      running = processInput(input)

  def processInput(input: String): Boolean =
    input.trim match
      case "q" =>
        println("Auf Wiedersehen!")
        false
      case "n" =>
        controller.newGame()
        true
      case "" =>
        true
      case moveStr =>
        Move.fromString(moveStr) match
          case Some(move) =>
            if controller.doMove(move) then
              true
            else
              println(s"Ungültiger Zug: $moveStr")
              true
          case None =>
            println(s"Eingabe nicht erkannt: '$moveStr'. Format: 'e2 e4'")
            true
