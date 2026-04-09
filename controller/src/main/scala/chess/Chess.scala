package chess

import chess.controller.Controller
import chess.aview.TUI
import chess.aview.gui.SwingGUI

// $COVERAGE-OFF$ main entry point
@main def aluChess(): Unit =
  val controller = Controller()
  val gui = SwingGUI(controller)
  val tui = TUI(controller)
  tui.inputLoop()
// $COVERAGE-ON$
