package chess.aview

import chess.controller.ControllerInterface
import chess.model.{Move, GameStatus, Color}
import chess.model.ai.AIMode
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
    println("  KI-Befehle: 'ai black' | 'ai white' = KI aktivieren, 'ai off' = KI deaktivieren")
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
      case "ai black" =>
        controller.setAIMode(AIMode.PlayingAs(Color.Black))
        println("KI spielt Schwarz.")
        if !controller.game.status.isTerminal && controller.game.currentPlayer == Color.Black then
          controller.doAiMove()
        true
      case "ai white" =>
        controller.setAIMode(AIMode.PlayingAs(Color.White))
        println("KI spielt Weiß.")
        if !controller.game.status.isTerminal && controller.game.currentPlayer == Color.White then
          controller.doAiMove()
        true
      case "ai off" =>
        controller.setAIMode(AIMode.Disabled)
        println("KI deaktiviert.")
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
                case Right(_) =>
                  // Let the AI respond if active
                  if !controller.game.status.isTerminal then
                    controller.doAiMove()
                  true
                case Left(err) =>
                  println(s"Ungültiger Zug: ${err.message}")
                  true
