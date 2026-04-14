package chess.aview

import chess.controller.ControllerInterface
import chess.model.{Move, GameStatus, Color}
import chess.model.ai.AIMode
import chess.util.Observer
import scala.io.StdIn
import scala.util.{Try, Success, Failure}

class TUI(controller: ControllerInterface) extends Observer:
  controller.add(this)
  private val AiVsAiDelayMs = 500L
  @volatile private var aiVsAiRunning = false

  override def update(): Unit =
    println(controller.boardToString)
    println(controller.statusText)
    controller.aiMode match
      case AIMode.Disabled          => ()
      case AIMode.PlayingAs(color)  => println(s"[KI spielt $color]")
      case AIMode.PlayingBoth       => println("[KI spielt beide Seiten]")

  def inputLoop(): Unit =
    println("alu-chess  –  Befehle:")
    println("  n           = neues Spiel")
    println("  q           = beenden")
    println("  e2 e4       = Zug eingeben  (Promotion: e7 e8 Q)")
    println("  ai black    = KI spielt Schwarz (du spielst Weiß)")
    println("  ai white    = KI spielt Weiß  (du spielst Schwarz)")
    println("  ai both     = KI gegen KI")
    println("  ai off      = KI deaktivieren (Mensch vs. Mensch)")
    println()
    update()
    var running = true
    while running do
      val prompt = controller.aiMode match
        case AIMode.Disabled         => "> "
        case AIMode.PlayingAs(color) => s"[KI=$color] > "
        case AIMode.PlayingBoth      => "[KI=beide] > "
      Try(StdIn.readLine(prompt)) match
        case Success(input) => running = processInput(input)
        case Failure(_)     => running = false

  def processInput(input: String): Boolean =
    val trimmed = input.trim

    if aiVsAiRunning && trimmed != "ai off" && trimmed != "q" then
      println("KI-gegen-KI läuft. Nur 'ai off' oder 'q' sind erlaubt.")
      return true

    trimmed match

      case "q" =>
        println("Auf Wiedersehen!")
        false

      case "n" =>
        controller.newGame()
        // If AI plays White it must move first
        triggerAiIfItsTurn()
        true

      case "" =>
        true

      case "ai black" =>
        controller.setAIMode(AIMode.PlayingAs(Color.Black))
        println("Modus: Du spielst Weiß, KI spielt Schwarz.")
        triggerAiIfItsTurn()
        true

      case "ai white" =>
        controller.setAIMode(AIMode.PlayingAs(Color.White))
        println("Modus: Du spielst Schwarz, KI spielt Weiß.")
        triggerAiIfItsTurn()
        true

      case "ai off" =>
        controller.setAIMode(AIMode.Disabled)
        println("Modus: Mensch vs. Mensch.")
        true

      case "ai both" =>
        controller.setAIMode(AIMode.PlayingBoth)
        println("Modus: KI gegen KI.")
        triggerAiIfItsTurn()
        true

      case moveStr =>
        if controller.game.status.isTerminal then
          println("Spiel beendet – 'n' für neues Spiel, 'q' zum Beenden.")
          true
        else
          Move.fromStringT(moveStr) match
            case Failure(ex) =>
              println(s"Eingabe nicht erkannt: ${ex.getMessage}")
              true
            case Success(move) =>
              controller.doMoveResult(move) match
                case Right(_) =>
                  // KI antwortet automatisch, wenn sie dran ist
                  triggerAiIfItsTurn()
                  true
                case Left(err) =>
                  println(s"Ungültiger Zug: ${err.message}")
                  true

  /** Let the AI play immediately if the mode is active and it is the AI's turn. */
  private def triggerAiIfItsTurn(): Unit =
    controller.aiMode match
      case AIMode.PlayingAs(color)
        if !controller.game.status.isTerminal && controller.game.currentPlayer == color =>
        controller.doAiMove()
      case AIMode.PlayingBoth if !controller.game.status.isTerminal =>
        startAiVsAiLoop()
      case _ => ()

  private def startAiVsAiLoop(): Unit =
    if aiVsAiRunning then return
    aiVsAiRunning = true
    val worker = new Thread(() =>
      try
        var running = true
        while running do
          if controller.aiMode != AIMode.PlayingBoth || controller.game.status.isTerminal then
            running = false
          else
            val moved = controller.doAiMove()
            if !moved || controller.game.status.isTerminal then
              running = false
            else
              Thread.sleep(AiVsAiDelayMs)
      finally
        aiVsAiRunning = false
    , "tui-ai-vs-ai")
    worker.setDaemon(true)
    worker.start()
