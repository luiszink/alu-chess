package chess.aview.gui

import chess.controller.ControllerInterface
import chess.util.Observer

import scala.swing.*
import java.awt.{Color as AwtColor, Dimension}
import javax.swing.{UIManager, WindowConstants, Timer as SwingTimer}

/** Main GUI window – lichess-inspired modern dark theme.
  * Registers as Observer on the Controller, same pattern as TUI. */
class SwingGUI(controller: ControllerInterface) extends Frame with Observer:
  controller.add(this)

  // --- Look & Feel ---
  try
    UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName)
  catch case _: Exception => () // fallback to default

  title = "alu-chess"
  resizable = false

  private val boardPanel = BoardPanel(controller)
  private val historyPanel = HistoryPanel(controller)
  private val clockPanel = ClockPanel(controller)
  private val sidePanel = SidePanel(
    controller,
    onNewGame = () => controller.newGame(),
    onQuit = () => { clockTimer.stop(); dispose(); controller.quit() }
  )

  // Clock tick timer (100ms interval for smooth display)
  private val clockTimer = new SwingTimer(100, _ => {
    controller.tickClock()
    clockPanel.refresh()
  })
  clockTimer.start()

  private val sideScroll = new ScrollPane(sidePanel):
    horizontalScrollBarPolicy = ScrollPane.BarPolicy.Never
    verticalScrollBarPolicy = ScrollPane.BarPolicy.AsNeeded
    border = Swing.EmptyBorder(0, 0, 0, 0)
    preferredSize = new Dimension(280, boardPanel.preferredSize.height - 250)

  // Right panel: lichess-style – black clock, nav+history, white clock, controls
  // Uses BorderPanel so history fills available space between clocks and controls
  private val clockAndHistoryPanel = new BorderPanel:
    background = new AwtColor(38, 36, 33)
    layout(clockPanel.blackClock) = BorderPanel.Position.North
    layout(historyPanel) = BorderPanel.Position.Center
    layout(clockPanel.whiteClock) = BorderPanel.Position.South
    preferredSize = new Dimension(280, 250)
    maximumSize = new Dimension(Short.MaxValue, 250)

  private val rightPanel = new BoxPanel(Orientation.Vertical):
    background = new AwtColor(38, 36, 33)
    preferredSize = new Dimension(280, boardPanel.preferredSize.height)
    contents += clockAndHistoryPanel
    contents += sideScroll

  contents = new BorderPanel:
    background = new AwtColor(38, 36, 33)
    layout(boardPanel) = BorderPanel.Position.Center
    layout(rightPanel) = BorderPanel.Position.East

  peer.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
  pack()
  centerOnScreen()
  visible = true

  // Initial display
  clockPanel.refresh()
  historyPanel.refresh()
  sidePanel.refresh()

  override def update(): Unit =
    boardPanel.refresh()
    historyPanel.refresh()
    clockPanel.refresh()
    sidePanel.refresh()
