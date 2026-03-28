package chess.aview.gui

import chess.controller.ControllerInterface
import chess.model.TimeControl

import scala.swing.*
import java.awt.{Color as AwtColor, Font, Dimension, Cursor, GridLayout, Graphics, Graphics2D, RenderingHints}
import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing.BorderFactory
import javax.swing.border.EmptyBorder

/** Lichess-inspired start/home screen with centered grid of time controls. */
class StartPanel(controller: ControllerInterface, onStart: Option[TimeControl] => Unit)
    extends BoxPanel(Orientation.Vertical):

  private val bg        = new AwtColor(38, 36, 33)
  private val cardBg    = new AwtColor(52, 50, 47)
  private val cardHover = new AwtColor(68, 66, 63)
  private val cardBorder= new AwtColor(60, 58, 55)
  private val titleFg   = new AwtColor(240, 240, 240)
  private val subtitleFg= new AwtColor(140, 140, 140)
  private val accentGreen = new AwtColor(186, 202, 68)

  background = bg
  border = new EmptyBorder(20, 0, 20, 0)

  // --- Hero ---
  private val heroTitle = new Label("alu-chess"):
    font = new Font("SansSerif", Font.BOLD, 36)
    foreground = titleFg
    horizontalAlignment = Alignment.Center
  heroTitle.xLayoutAlignment = 0.5

  private val heroSubtitle = new Label("Wähle ein Zeitformat"):
    font = new Font("SansSerif", Font.PLAIN, 14)
    foreground = subtitleFg
    horizontalAlignment = Alignment.Center
  heroSubtitle.xLayoutAlignment = 0.5

  // --- Time control data ---
  private case class TcEntry(label: String, category: String, catColor: AwtColor, tc: Option[TimeControl])

  private val entries = Vector(
    TcEntry("1+0",   "Bullet",       new AwtColor(210, 100, 60),  Some(TimeControl.Bullet1_0)),
    TcEntry("2+1",   "Bullet",       new AwtColor(210, 100, 60),  Some(TimeControl.Bullet2_1)),
    TcEntry("3+0",   "Blitz",        accentGreen,                 Some(TimeControl.Blitz3_0)),
    TcEntry("3+2",   "Blitz",        accentGreen,                 Some(TimeControl.Blitz3_2)),
    TcEntry("5+0",   "Blitz",        accentGreen,                 Some(TimeControl.Blitz5_0)),
    TcEntry("5+3",   "Blitz",        accentGreen,                 Some(TimeControl.Blitz5_3)),
    TcEntry("10+0",  "Schnell",      new AwtColor(100, 170, 220), Some(TimeControl.Rapid10_0)),
    TcEntry("10+5",  "Schnell",      new AwtColor(100, 170, 220), Some(TimeControl.Rapid10_5)),
    TcEntry("15+10", "Schnell",      new AwtColor(100, 170, 220), Some(TimeControl.Rapid15_10)),
    TcEntry("30+0",  "Klassisch",    new AwtColor(160, 140, 200), Some(TimeControl.Classical30_0)),
    TcEntry("30+20", "Klassisch",    new AwtColor(160, 140, 200), None), // placeholder – no preset yet
    TcEntry("Ohne Uhr", "Freies Spiel", subtitleFg,               None),
  )

  // --- Card factory ---
  private def tcCard(entry: TcEntry): Panel =
    val isNoClockCard = entry.tc.isEmpty && entry.label == "Ohne Uhr"
    val card = new Panel:
      background = cardBg
      opaque = true
      cursor = new Cursor(Cursor.HAND_CURSOR)
      border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(cardBorder, 1, true),
        BorderFactory.createEmptyBorder(10, 10, 10, 10)
      )
      peer.setLayout(new java.awt.BorderLayout(0, 2))

      val timeLabel = new Label(entry.label):
        font = new Font("SansSerif", Font.BOLD, 22)
        foreground = titleFg
        horizontalAlignment = Alignment.Center
      peer.add(timeLabel.peer, java.awt.BorderLayout.CENTER)

      val catLabel = new Label(entry.category):
        font = new Font("SansSerif", Font.PLAIN, 11)
        foreground = entry.catColor
        horizontalAlignment = Alignment.Center
      peer.add(catLabel.peer, java.awt.BorderLayout.SOUTH)

    card.peer.addMouseListener(new MouseAdapter:
      override def mouseEntered(e: MouseEvent): Unit =
        card.background = cardHover; card.repaint()
      override def mouseExited(e: MouseEvent): Unit =
        card.background = cardBg; card.repaint()
      override def mouseClicked(e: MouseEvent): Unit =
        if isNoClockCard then onStart(None)
        else entry.tc.foreach(t => onStart(Some(t)))
    )
    card

  // --- Grid: 4 columns x 3 rows ---
  private val gridPanel = new Panel:
    background = bg
    opaque = true
    peer.setLayout(new GridLayout(3, 4, 6, 6))
    for entry <- entries do
      peer.add(tcCard(entry).peer)

  gridPanel.maximumSize = new Dimension(520, 280)
  gridPanel.preferredSize = new Dimension(520, 280)

  // Center the grid horizontally
  private val gridWrapper = new BoxPanel(Orientation.Horizontal):
    background = bg
    contents += Swing.HGlue
    contents += Component.wrap(gridPanel.peer)
    contents += Swing.HGlue

  // --- Assemble ---
  contents += Swing.VGlue
  contents += heroTitle
  contents += Swing.VStrut(6)
  contents += heroSubtitle
  contents += Swing.VStrut(28)
  contents += gridWrapper
  contents += Swing.VGlue
