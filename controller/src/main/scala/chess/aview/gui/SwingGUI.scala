package chess.aview.gui

import chess.controller.ControllerInterface
import chess.model.TimeControl
import chess.util.Observer

import scala.swing.*
import java.awt.{Color as AwtColor, CardLayout, Dimension}
import javax.swing.{UIManager, WindowConstants, Timer as SwingTimer}

/** Main GUI window – lichess-inspired modern dark theme with start screen and nav bar.
  * Registers as Observer on the Controller, same pattern as TUI. */
class SwingGUI(controller: ControllerInterface) extends Frame with Observer:
  controller.add(this)

  // --- Look & Feel ---
  try
    UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName)
  catch case _: Exception => () // fallback to default

  title = "alu-chess"
  resizable = true

  private val darkBg = new AwtColor(38, 36, 33)

  private val boardPanel   = BoardPanel(controller)
  private val historyPanel = HistoryPanel(controller)
  private val clockPanel   = ClockPanel(controller)
  private val sidePanel = SidePanel(
    controller,
    onNewGame  = () => showGameView(None),
    onQuit     = () => { clockTimer.stop(); dispose(); controller.quit() },
    onAIEnabled = () => boardPanel.triggerAiIfItsTurn()
  )

  // Clock tick timer (100ms interval for smooth display)
  private val clockTimer = new SwingTimer(100, _ => {
    controller.tickClock()
    clockPanel.refresh()
  })
  clockTimer.start()

  // --- CardLayout view switcher ---
  private val CardHome = "home"
  private val CardGame = "game"
  private val CardHistory = "history"

  private val cardLayout = new CardLayout()
  private val cardPanel  = new Panel:
    background = darkBg
    peer.setLayout(cardLayout)

  // --- Start panel (centered in available space) ---
  private val startPanel = new StartPanel(
    controller,
    onStart = tc => showGameView(tc)
  )

  private val startWrapper = new BorderPanel:
    background = darkBg
    layout(startPanel) = BorderPanel.Position.Center

  // --- History list panel ---
  private val historyListPanel = new HistoryListPanel(
    controller,
    onReplay = id => {
      controller.loadReplay(id)
      cardLayout.show(cardPanel.peer, CardGame)
      navBar.setActive("game")
      clockPanel.refresh()
      historyPanel.refresh()
      sidePanel.refresh()
      boardPanel.refresh()
      repaint()
    }
  )

  private val historyWrapper = new BorderPanel:
    background = darkBg
    layout(historyListPanel) = BorderPanel.Position.Center

  // --- Game panel (same layout as before) ---
  private val blackNameLabel = new Label("  Schwarz"):
    font = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12)
    foreground = new AwtColor(160, 160, 160)
    opaque = true
    background = darkBg
  private val whiteNameLabel = new Label("  Weiß"):
    font = new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 12)
    foreground = new AwtColor(160, 160, 160)
    opaque = true
    background = darkBg

  private val topSection = new BoxPanel(Orientation.Vertical):
    background = darkBg
    contents += clockPanel.blackClock
    contents += blackNameLabel

  private val bottomSection = new BoxPanel(Orientation.Vertical):
    background = darkBg
    contents += whiteNameLabel
    contents += clockPanel.whiteClock
    contents += sidePanel

  private val rightPanel = new BorderPanel:
    background = darkBg
    preferredSize = new Dimension(300, boardPanel.preferredSize.height)
    layout(topSection)   = BorderPanel.Position.North
    layout(historyPanel) = BorderPanel.Position.Center
    layout(bottomSection) = BorderPanel.Position.South

  private val gamePanel = new BorderPanel:
    background = darkBg
    layout(boardPanel) = BorderPanel.Position.Center
    layout(rightPanel) = BorderPanel.Position.East

  // --- Nav bar ---
  private val navBar = new NavBar(
    onHome    = () => showHomeView(),
    onGame    = () => showGameView(None),
    onHistory = () => showHistoryView(),
    onTools   = () => sidePanel.openToolsDialog()
  )

  // --- Root layout: NavBar (North) + CardPanel (Center) ---
  private val startScroll = new ScrollPane(startWrapper):
    horizontalScrollBarPolicy = ScrollPane.BarPolicy.Never
    verticalScrollBarPolicy   = ScrollPane.BarPolicy.AsNeeded
    border = javax.swing.BorderFactory.createEmptyBorder()
    peer.getViewport.setBackground(darkBg)
    peer.setBackground(darkBg)

  cardPanel.peer.add(startScroll.peer, CardHome)
  cardPanel.peer.add(gamePanel.peer,   CardGame)
  cardPanel.peer.add(historyWrapper.peer, CardHistory)

  contents = new BorderPanel:
    background = darkBg
    layout(Component.wrap(navBar.peer)) = BorderPanel.Position.North
    layout(cardPanel)                   = BorderPanel.Position.Center

  // Set explicit window size – board is 640px, right panel 300px
  peer.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)
  peer.setSize(new Dimension(960, 720))
  peer.setMinimumSize(new Dimension(800, 600))
  centerOnScreen()
  visible = true

  // Start on home view
  showHomeView()

  // --- View switching helpers ---
  private def showHomeView(): Unit =
    if controller.isInReplay then controller.exitReplay()
    cardLayout.show(cardPanel.peer, CardHome)
    navBar.setActive("home")
    repaint()

  private def showGameView(tc: Option[TimeControl]): Unit =
    if !controller.isInReplay then
      controller.newGameWithClock(tc)
    cardLayout.show(cardPanel.peer, CardGame)
    navBar.setActive("game")
    clockPanel.refresh()
    historyPanel.refresh()
    sidePanel.refresh()
    boardPanel.refresh()
    repaint()
    // If AI plays White it must move first on a fresh game
    boardPanel.triggerAiIfItsTurn()

  private def showHistoryView(): Unit =
    if controller.isInReplay then controller.exitReplay()
    historyListPanel.refresh()
    cardLayout.show(cardPanel.peer, CardHistory)
    navBar.setActive("history")
    repaint()

  override def update(): Unit =
    boardPanel.refresh()
    historyPanel.refresh()
    clockPanel.refresh()
    sidePanel.refresh()
