package chess.aview

import chess.controller.ControllerInterface
import chess.model.{Move, GameStatus}
import chess.util.Observer
import scala.io.StdIn
import scala.util.{Try, Success, Failure}

class TUI(controller: ControllerInterface) extends Observer:
  controller.add(this)

  override def update(): Unit =
    println(controller.boardToString)
    println(controller.statusText)

  def inputLoop(): Unit =
    println("alu-chess – Befehle: 'n' = neues Spiel, 'q' = beenden, oder Zug z.B. 'e2 e4' (Promotion: 'e7 e8 Q')")
    update()
    var running = true
    while running do
      Try(StdIn.readLine("> ")) match
        case Success(input) => running = processInput(input)
        case Failure(_)     => running = false

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
        if controller.game.status.isTerminal then
          println("Spiel ist beendet. 'n' für neues Spiel oder 'q' zum Beenden.")
          true
        else
          Move.fromStringT(moveStr) match
            case Failure(ex) =>
              println(s"Eingabe nicht erkannt: ${ex.getMessage}")
              true
            case Success(move) =>
              controller.doMoveResult(move) match
                case Right(_)    => true
                case Left(err)   =>
                  println(s"Ungültiger Zug: ${err.message}")
                  true
