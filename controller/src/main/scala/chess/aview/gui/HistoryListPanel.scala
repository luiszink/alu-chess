package chess.aview.gui

import chess.controller.ControllerInterface
import chess.model.GameRecord

import scala.swing.*
import java.awt.{Color as AwtColor, Font, Dimension, Cursor, Graphics2D, RenderingHints}
import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing.BorderFactory
import javax.swing.border.EmptyBorder
import java.time.format.DateTimeFormatter

/** Panel showing a list of past games, similar to Lichess game history. */
class HistoryListPanel(controller: ControllerInterface, onReplay: String => Unit)
    extends BoxPanel(Orientation.Vertical):

  private val bg         = new AwtColor(38, 36, 33)
  private val cardBg     = new AwtColor(48, 46, 43)
  private val cardHover  = new AwtColor(62, 60, 57)
  private val titleFg    = new AwtColor(240, 240, 240)
  private val subtitleFg = new AwtColor(150, 150, 150)
  private val dateFg     = new AwtColor(120, 120, 120)
  private val winColor   = new AwtColor(130, 190, 80)
  private val lossColor  = new AwtColor(210, 90, 70)
  private val drawColor  = new AwtColor(170, 170, 170)
  private val accentGreen = new AwtColor(186, 202, 68)

  private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
  private val titleFont  = new Font("SansSerif", Font.BOLD, 32)
  private val emptyFont  = new Font("SansSerif", Font.PLAIN, 16)
  private val resultFont = new Font("SansSerif", Font.BOLD, 18)
  private val infoFont   = new Font("SansSerif", Font.PLAIN, 13)
  private val dateFont   = new Font("SansSerif", Font.PLAIN, 12)

  background = bg
  border = new EmptyBorder(20, 40, 20, 40)

  private val listPanel = new BoxPanel(Orientation.Vertical):
    background = bg

  def refresh(): Unit =
    listPanel.contents.clear()
    val records = controller.gameHistory

    // Title
    val header = new Label("Spielverlauf"):
      font = titleFont
      foreground = titleFg
      horizontalAlignment = Alignment.Center
    header.xLayoutAlignment = 0.5
    listPanel.contents += header
    listPanel.contents += Swing.VStrut(20)

    if records.isEmpty then
      val emptyLabel = new Label("Noch keine Spiele gespielt"):
        font = emptyFont
        foreground = subtitleFg
        horizontalAlignment = Alignment.Center
      emptyLabel.xLayoutAlignment = 0.5
      listPanel.contents += Swing.VGlue
      listPanel.contents += emptyLabel
      listPanel.contents += Swing.VGlue
    else
      for record <- records do
        listPanel.contents += gameCard(record)
        listPanel.contents += Swing.VStrut(6)
      listPanel.contents += Swing.VGlue

    listPanel.revalidate()
    listPanel.repaint()

  private def gameCard(record: GameRecord): Component =
    val card = new Panel:
      opaque = false
      cursor = new Cursor(Cursor.HAND_CURSOR)
      preferredSize = new Dimension(600, 60)
      maximumSize = new Dimension(Short.MaxValue, 60)
      peer.setLayout(new java.awt.BorderLayout(12, 0))
      border = BorderFactory.createEmptyBorder(8, 16, 8, 16)

      private var hovered = false

      override def paintComponent(g: Graphics2D): Unit =
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setColor(if hovered then cardHover else cardBg)
        g.fillRoundRect(0, 0, peer.getWidth, peer.getHeight, 10, 10)

      // Left: result indicator
      val resultColor = record.result match
        case "1-0" => winColor
        case "0-1" => lossColor
        case "½-½" => drawColor
        case _     => subtitleFg
      val resultText = record.result match
        case "1-0" => "1-0"
        case "0-1" => "0-1"
        case "½-½" => "½-½"
        case _     => "?"
      val resultLabel = new Label(resultText):
        font = resultFont
        foreground = resultColor
        opaque = false
        preferredSize = new Dimension(55, 40)
        horizontalAlignment = Alignment.Center
      peer.add(resultLabel.peer, java.awt.BorderLayout.WEST)

      // Center: game info
      val tcName = record.timeControl.map(_.name).getOrElse("Ohne Uhr")
      val moveText = s"${record.moveCount} Züge"
      val infoLabel = new Label(s"$tcName  ·  $moveText"):
        font = infoFont
        foreground = titleFg
        opaque = false
      peer.add(infoLabel.peer, java.awt.BorderLayout.CENTER)

      // Right: date
      val dateLabel = new Label(record.datePlayed.format(dateFormat)):
        font = dateFont
        foreground = dateFg
        opaque = false
      peer.add(dateLabel.peer, java.awt.BorderLayout.EAST)

    card.peer.addMouseListener(new MouseAdapter:
      override def mouseEntered(e: MouseEvent): Unit =
        card.peer.putClientProperty("hovered", true)
        card.repaint()
      override def mouseExited(e: MouseEvent): Unit =
        card.peer.putClientProperty("hovered", false)
        card.repaint()
      override def mouseClicked(e: MouseEvent): Unit =
        onReplay(record.id)
    )

    // Override paintComponent to use the hovered state
    val wrapper = new Panel:
      opaque = false
      peer.setLayout(new java.awt.BorderLayout())
      peer.add(card.peer, java.awt.BorderLayout.CENTER)
      maximumSize = new Dimension(Short.MaxValue, 60)

    wrapper.xLayoutAlignment = 0.5
    wrapper

  // Assemble
  private val scrollPane = new ScrollPane(listPanel):
    horizontalScrollBarPolicy = ScrollPane.BarPolicy.Never
    verticalScrollBarPolicy = ScrollPane.BarPolicy.AsNeeded
    border = BorderFactory.createEmptyBorder()
    peer.getViewport.setBackground(bg)
    peer.setBackground(bg)

  contents += scrollPane
