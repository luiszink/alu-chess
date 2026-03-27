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

  // Player name labels (small, between clocks and history – like lichess)
  private val blackNameLabel = new Label("  Schwarz"):
    font = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12)
    foreground = new AwtColor(160, 160, 160)
    opaque = true
    background = new AwtColor(38, 36, 33)
  private val whiteNameLabel = new Label("  Weiß"):
    font = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12)
    foreground = new AwtColor(160, 160, 160)
    opaque = true
    background = new AwtColor(38, 36, 33)

  // Top section: black clock + player name
  private val topSection = new BoxPanel(Orientation.Vertical):
    background = new AwtColor(38, 36, 33)
    contents += clockPanel.blackClock
    contents += blackNameLabel

  // Bottom section: player name + white clock + compact controls
  private val bottomSection = new BoxPanel(Orientation.Vertical):
    background = new AwtColor(38, 36, 33)
    contents += whiteNameLabel
    contents += clockPanel.whiteClock
    contents += sidePanel

  // Right panel: BorderPanel so history fills all available vertical space
  private val rightPanel = new BorderPanel:
    background = new AwtColor(38, 36, 33)
    preferredSize = new Dimension(300, boardPanel.preferredSize.height)
    layout(topSection) = BorderPanel.Position.North
    layout(historyPanel) = BorderPanel.Position.Center
    layout(bottomSection) = BorderPanel.Position.South

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
