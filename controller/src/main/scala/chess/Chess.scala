package chess

import chess.controller.{Controller, ControllerStreamBridge}
import chess.streaming.ChessStreamApp
import chess.aview.TUI
import chess.aview.gui.SwingGUI
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors

// $COVERAGE-OFF$ main entry point
@main def aluChess(): Unit =
  given system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "chess-stream")
  val controller = Controller()
  val bridge     = ControllerStreamBridge(controller)
  ChessStreamApp.run(bridge.gameSource)
  val gui = SwingGUI(controller)
  val tui = TUI(controller)
  tui.inputLoop()
// $COVERAGE-ON$
