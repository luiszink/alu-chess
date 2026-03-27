package chess.aview.gui

import chess.controller.ControllerInterface
import chess.model.{Color, MoveEntry}

import scala.swing.*
import java.awt.{Color as AwtColor, Font, Dimension, Cursor, Graphics2D, RenderingHints}
import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing.border.EmptyBorder
import javax.swing.BorderFactory

/** Lichess-style move history panel with clickable moves and navigation buttons. */
class HistoryPanel(controller: ControllerInterface) extends BoxPanel(Orientation.Vertical):

  private val panelBg     = new AwtColor(38, 36, 33)
  private val moveBg      = new AwtColor(48, 46, 43)
  private val moveHoverBg = new AwtColor(62, 60, 57)
  private val activeBg    = new AwtColor(80, 120, 80)
  private val moveFg      = new AwtColor(220, 220, 220)
  private val moveNumFg   = new AwtColor(140, 140, 140)
  private val btnBg       = new AwtColor(58, 56, 53)
  private val btnHover    = new AwtColor(78, 76, 73)
  private val btnFg       = new AwtColor(200, 200, 200)

  private val moveFont = new Font("SansSerif", Font.PLAIN, 13)
  private val numFont  = new Font("SansSerif", Font.PLAIN, 11)
  private val btnFont  = new Font("SansSerif", Font.BOLD, 16)

  background = panelBg

  // --- Move list area ---
  private val moveListPanel = new BoxPanel(Orientation.Vertical):
    background = panelBg

  private val scrollPane = new ScrollPane(moveListPanel):
    horizontalScrollBarPolicy = ScrollPane.BarPolicy.Never
    verticalScrollBarPolicy = ScrollPane.BarPolicy.AsNeeded
    border = BorderFactory.createEmptyBorder()
    preferredSize = new Dimension(260, 200)
    minimumSize = new Dimension(260, 60)
    peer.getViewport.setBackground(panelBg)
    peer.getVerticalScrollBar.setBackground(panelBg)

  // --- Navigation buttons ---
  private def navButton(text: String, onClick: () => Unit): Button =
    val btn = new Button(text):
      font = btnFont
      foreground = btnFg
      background = btnBg
      opaque = true
      borderPainted = false
      focusPainted = false
      cursor = new Cursor(Cursor.HAND_CURSOR)
      preferredSize = new Dimension(50, 30)
    btn.peer.addMouseListener(new MouseAdapter:
      override def mouseEntered(e: MouseEvent): Unit = btn.background = btnHover
      override def mouseExited(e: MouseEvent): Unit  = btn.background = btnBg
    )
    btn.listenTo(btn)
    btn.reactions += { case event.ButtonClicked(_) => onClick() }
    btn

  private val navPanel = new FlowPanel(FlowPanel.Alignment.Center)(
    navButton("⟨⟨", () => controller.browseToStart()),
    navButton("⟨",  () => controller.browseBack()),
    navButton("⟩",  () => controller.browseForward()),
    navButton("⟩⟩", () => controller.browseToEnd())
  ):
    background = panelBg

  contents += scrollPane
  contents += navPanel

  def refresh(): Unit =
    moveListPanel.contents.clear()
    val entries = controller.game.moveHistory
    // Use the latest game's moveHistory for the full list
    val allEntries = if controller.isAtLatest then entries
      else {
        val latestIdx = controller.gameStatesCount - 1
        // The latest game always has the complete history
        val states = (0 to latestIdx).map(i => { controller.browseToMove(latestIdx); controller.game })
        // Actually, just get moveHistory from the latest state directly
        // We need to be careful here - let's get it from a separate method
        entries // fallback: use current browsed game's history
      }
    // Get the full history from the last game state
    val fullHistory = getFullHistory()
    val browseIdx = controller.browseIndex // 0 = initial, 1 = after first move, etc.

    fullHistory.grouped(2).zipWithIndex.foreach { (pair, pairIdx) =>
      val moveNum = pairIdx + 1
      val row = new FlowPanel(FlowPanel.Alignment.Left)()
      row.background = panelBg
      row.hGap = 0
      row.vGap = 0

      // Move number
      val numLabel = new Label(s"$moveNum."):
        font = numFont
        foreground = moveNumFg
        preferredSize = new Dimension(30, 24)
        horizontalAlignment = Alignment.Right
      row.contents += numLabel

      // White move (entry index: pairIdx * 2)
      val whiteIdx = pairIdx * 2
      if pair.nonEmpty then
        row.contents += moveLabel(pair(0).san, whiteIdx + 1, browseIdx)

      // Black move (entry index: pairIdx * 2 + 1)
      if pair.length > 1 then
        val blackIdx = pairIdx * 2 + 1
        row.contents += moveLabel(pair(1).san, blackIdx + 1, browseIdx)

      moveListPanel.contents += row
    }

    moveListPanel.revalidate()
    moveListPanel.repaint()

    // Auto-scroll to bottom if at latest
    if controller.isAtLatest then
      javax.swing.SwingUtilities.invokeLater(() =>
        val sb = scrollPane.peer.getVerticalScrollBar
        sb.setValue(sb.getMaximum)
      )

  private def getFullHistory(): Vector[MoveEntry] =
    // Browse to end temporarily to get full history, then restore
    // Actually, the latest game state has the complete history
    // We access it through gameStatesCount
    val lastIdx = controller.gameStatesCount - 1
    val currentBrowse = controller.browseIndex
    controller.browseToMove(lastIdx)
    val history = controller.game.moveHistory
    controller.browseToMove(currentBrowse)
    history

  private def moveLabel(san: String, stateIndex: Int, currentBrowse: Int): Label =
    val isActive = stateIndex == currentBrowse
    val label = new Label(san):
      font = moveFont
      foreground = moveFg
      background = if isActive then activeBg else moveBg
      opaque = true
      horizontalAlignment = Alignment.Center
      preferredSize = new Dimension(70, 24)
      border = BorderFactory.createEmptyBorder(2, 6, 2, 6)
      cursor = new Cursor(Cursor.HAND_CURSOR)

    label.peer.addMouseListener(new MouseAdapter:
      override def mouseEntered(e: MouseEvent): Unit =
        if stateIndex != currentBrowse then label.background = moveHoverBg
      override def mouseExited(e: MouseEvent): Unit =
        label.background = if stateIndex == controller.browseIndex then activeBg else moveBg
      override def mouseClicked(e: MouseEvent): Unit =
        controller.browseToMove(stateIndex)
    )
    label
