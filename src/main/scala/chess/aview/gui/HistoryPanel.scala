package chess.aview.gui

import chess.controller.ControllerInterface
import chess.model.{Color, MoveEntry}

import scala.swing.*
import java.awt.{Color as AwtColor, Font, Dimension, Cursor, GridBagLayout, GridBagConstraints, Insets}
import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing.BorderFactory

/** Lichess-style move history table with clickable moves and navigation buttons. */
class HistoryPanel(controller: ControllerInterface) extends BoxPanel(Orientation.Vertical):

  private val panelBg     = new AwtColor(38, 36, 33)
  private val rowEvenBg   = new AwtColor(43, 41, 38)
  private val rowOddBg    = new AwtColor(48, 46, 43)
  private val activeBg    = new AwtColor(76, 114, 76)
  private val moveHoverBg = new AwtColor(58, 56, 53)
  private val moveFg      = new AwtColor(190, 190, 190)
  private val moveActiveFg= new AwtColor(240, 240, 240)
  private val moveNumFg   = new AwtColor(100, 98, 95)
  private val navBg       = new AwtColor(48, 46, 43)
  private val navHover    = new AwtColor(62, 60, 57)
  private val navFg       = new AwtColor(160, 160, 160)
  private val sepColor    = new AwtColor(55, 53, 50)

  private val moveFont = new Font("SansSerif", Font.PLAIN, 13)
  private val numFont  = new Font("SansSerif", Font.PLAIN, 11)
  private val navFont  = new Font("SansSerif", Font.PLAIN, 14)

  private val rowHeight = 22
  private val panelWidth = 280

  background = panelBg

  // --- Navigation buttons (thin row, subtle, like lichess) ---
  private def navButton(text: String, onClick: () => Unit): Label =
    val btn = new Label(text):
      font = navFont
      foreground = navFg
      background = navBg
      opaque = true
      horizontalAlignment = Alignment.Center
      cursor = new Cursor(Cursor.HAND_CURSOR)
    btn.peer.addMouseListener(new MouseAdapter:
      override def mouseEntered(e: MouseEvent): Unit =
        btn.foreground = new AwtColor(220, 220, 220)
        btn.background = navHover
      override def mouseExited(e: MouseEvent): Unit =
        btn.foreground = navFg
        btn.background = navBg
      override def mouseClicked(e: MouseEvent): Unit = onClick()
    )
    btn

  private val navPanel = new Panel:
    background = navBg
    peer.setLayout(new java.awt.GridLayout(1, 4, 1, 0))
    peer.add(navButton("⏮", () => controller.browseToStart()).peer)
    peer.add(navButton("◀", () => controller.browseBack()).peer)
    peer.add(navButton("▶", () => controller.browseForward()).peer)
    peer.add(navButton("⏭", () => controller.browseToEnd()).peer)
  navPanel.preferredSize = new Dimension(panelWidth, 26)
  navPanel.maximumSize = new Dimension(Short.MaxValue, 26)
  navPanel.minimumSize = new Dimension(panelWidth, 26)

  // Thin separator line
  private val navSep = new Panel:
    background = sepColor
    preferredSize = new Dimension(panelWidth, 1)
    maximumSize = new Dimension(Short.MaxValue, 1)
    minimumSize = new Dimension(0, 1)

  // --- Move list (non-stretching rows, glue at bottom) ---
  private val moveListPanel = new BoxPanel(Orientation.Vertical):
    background = panelBg

  private val scrollPane = new ScrollPane(moveListPanel):
    horizontalScrollBarPolicy = ScrollPane.BarPolicy.Never
    verticalScrollBarPolicy = ScrollPane.BarPolicy.AsNeeded
    border = BorderFactory.createEmptyBorder()
    peer.getViewport.setBackground(panelBg)

  contents += navPanel
  contents += navSep
  contents += scrollPane

  def refresh(): Unit =
    moveListPanel.contents.clear()
    val fullHistory = getFullHistory()
    val browseIdx = controller.browseIndex

    fullHistory.grouped(2).zipWithIndex.foreach { (pair, pairIdx) =>
      val moveNum = pairIdx + 1
      val rowBg = if pairIdx % 2 == 0 then rowEvenBg else rowOddBg

      val row = new Panel:
        background = rowBg
        peer.setLayout(new java.awt.GridLayout(1, 3, 0, 0))
        preferredSize = new Dimension(panelWidth, rowHeight)
        maximumSize = new Dimension(Short.MaxValue, rowHeight)
        minimumSize = new Dimension(panelWidth, rowHeight)

      // Move number column
      val numCell = new Label(s"$moveNum"):
        font = numFont
        foreground = moveNumFg
        background = rowBg
        opaque = true
        horizontalAlignment = Alignment.Center
      row.peer.add(numCell.peer)

      // White move
      if pair.nonEmpty then
        val whiteIdx = pairIdx * 2 + 1
        row.peer.add(moveCell(pair(0).san, whiteIdx, browseIdx, rowBg).peer)
      else
        row.peer.add(emptyCell(rowBg).peer)

      // Black move
      if pair.length > 1 then
        val blackIdx = pairIdx * 2 + 2
        row.peer.add(moveCell(pair(1).san, blackIdx, browseIdx, rowBg).peer)
      else
        row.peer.add(emptyCell(rowBg).peer)

      moveListPanel.contents += Component.wrap(row.peer)
    }

    // Glue pushes rows to the top, empty space stays at bottom
    moveListPanel.contents += Swing.VGlue

    moveListPanel.revalidate()
    moveListPanel.repaint()

    if controller.isAtLatest then
      javax.swing.SwingUtilities.invokeLater(() =>
        val sb = scrollPane.peer.getVerticalScrollBar
        sb.setValue(sb.getMaximum)
      )

  private def getFullHistory(): Vector[MoveEntry] =
    val lastIdx = controller.gameStatesCount - 1
    val currentBrowse = controller.browseIndex
    controller.browseToMove(lastIdx)
    val history = controller.game.moveHistory
    controller.browseToMove(currentBrowse)
    history

  private def moveCell(san: String, stateIndex: Int, currentBrowse: Int, rowBg: AwtColor): Label =
    val isActive = stateIndex == currentBrowse
    val label = new Label(san):
      font = moveFont
      foreground = if isActive then moveActiveFg else moveFg
      background = if isActive then activeBg else rowBg
      opaque = true
      horizontalAlignment = Alignment.Center
      cursor = new Cursor(Cursor.HAND_CURSOR)

    label.peer.addMouseListener(new MouseAdapter:
      override def mouseEntered(e: MouseEvent): Unit =
        if stateIndex != controller.browseIndex then label.background = moveHoverBg
      override def mouseExited(e: MouseEvent): Unit =
        label.background = if stateIndex == controller.browseIndex then activeBg else rowBg
      override def mouseClicked(e: MouseEvent): Unit =
        controller.browseToMove(stateIndex)
    )
    label

  private def emptyCell(bg: AwtColor): Label =
    new Label(""):
      background = bg
      opaque = true
