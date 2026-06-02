package chess.aview.gui

import chess.controller.ControllerInterface
import chess.model.TimeControl

import scala.swing.*
import java.awt.{Color as AwtColor, Font, Dimension, Cursor, GridLayout, Graphics, Graphics2D, RenderingHints, AlphaComposite}
import java.awt.event.{MouseAdapter, MouseEvent}
import java.awt.image.{BufferedImage, ConvolveOp, Kernel}
import javax.imageio.ImageIO
import javax.swing.{BorderFactory, ImageIcon}
import javax.swing.border.EmptyBorder

/** Lichess-inspired start/home screen with centered grid of time controls. */
class StartPanel(controller: ControllerInterface, onStart: Option[TimeControl] => Unit)
    extends BoxPanel(Orientation.Vertical):

  private val bg        = new AwtColor(38, 36, 33)
  private val cardBg    = new AwtColor(52, 50, 47, 160)
  private val cardHover = new AwtColor(68, 66, 63, 120)
  private val cardBorder= new AwtColor(60, 58, 55, 140)
  private val titleFg   = new AwtColor(240, 240, 240)
  private val subtitleFg= new AwtColor(140, 140, 140)
  private val accentGreen = new AwtColor(186, 202, 68)

  opaque = true
  background = bg
  border = new EmptyBorder(20, 0, 20, 0)

  // --- Background image (blurred & darkened) ---
  private val bgImage: Option[BufferedImage] = loadBgImage()

  private def loadBgImage(): Option[BufferedImage] =
    try
      Option(getClass.getResourceAsStream("/bg-chess.png")).map { stream =>
        val original = ImageIO.read(stream)
        stream.close()
        original
      }
    catch
      case _: Exception => None

  private def applyBlur(img: BufferedImage, radius: Int): BufferedImage =
    val size = radius * 2 + 1
    val weight = 1.0f / (size * size)
    val data = Array.fill(size * size)(weight)
    val kernel = new Kernel(size, size, data)
    val op = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null)
    val compatible = new BufferedImage(img.getWidth, img.getHeight, BufferedImage.TYPE_INT_ARGB)
    val g = compatible.createGraphics()
    g.drawImage(img, 0, 0, null)
    g.dispose()
    op.filter(compatible, null)

  override def paintComponent(g: Graphics2D): Unit =
    super.paintComponent(g)
    bgImage.foreach { img =>
      val g2 = g.create().asInstanceOf[Graphics2D]
      g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
      val pw = peer.getWidth
      val ph = peer.getHeight
      val iw = img.getWidth
      val ih = img.getHeight
      val scale = math.max(pw.toDouble / iw, ph.toDouble / ih)
      val dw = (iw * scale).toInt
      val dh = (ih * scale).toInt
      val x = (pw - dw) / 2
      val y = (ph - dh) / 2
      // Draw image very subtly
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f))
      g2.drawImage(img, x, y, dw, dh, null)
      g2.dispose()
    }

  // --- Hero icon from resources ---
  private val heroIcon: Option[ImageIcon] =
    try
      Option(getClass.getResourceAsStream("/bg-chess-icon.png")).map { stream =>
        val img = javax.imageio.ImageIO.read(stream)
        stream.close()
        val sz = 80
        val circle = new java.awt.image.BufferedImage(sz, sz, java.awt.image.BufferedImage.TYPE_INT_ARGB)
        val g2 = circle.createGraphics()
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR)
        g2.setClip(new java.awt.geom.Ellipse2D.Float(0, 0, sz, sz))
        g2.drawImage(img, 0, 0, sz, sz, null)
        g2.dispose()
        new ImageIcon(circle)
      }
    catch
      case _: Exception => None

  // --- Hero ---
  private val heroTitle = new Label("alu-chess"):
    font = new Font("SansSerif", Font.BOLD, 42)
    foreground = titleFg
    horizontalAlignment = Alignment.Center
  heroIcon.foreach(ic => heroTitle.icon = ic)
  heroTitle.iconTextGap = 14
  heroTitle.xLayoutAlignment = 0.5

  private val heroSubtitle = new Label("Wähle ein Zeitformat"):
    font = new Font("SansSerif", Font.PLAIN, 16)
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
    TcEntry("30+20", "Klassisch",    new AwtColor(160, 140, 200), Some(TimeControl.Classical30_20)),
    TcEntry("Ohne Uhr", "Freies Spiel", subtitleFg,               None),
  )

  // --- Card factory ---
  private def tcCard(entry: TcEntry): Panel =
    val isNoClockCard = entry.tc.isEmpty && entry.label == "Ohne Uhr"
    val card = new Panel:
      background = cardBg
      opaque = false
      cursor = new Cursor(Cursor.HAND_CURSOR)
      border = BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new AwtColor(cardBorder.getRed, cardBorder.getGreen, cardBorder.getBlue, cardBorder.getAlpha), 1, true),
        BorderFactory.createEmptyBorder(14, 14, 14, 14)
      )
      peer.setLayout(new java.awt.BorderLayout(0, 4))

      override def paintComponent(g: Graphics2D): Unit =
        g.setColor(background)
        g.fillRoundRect(0, 0, peer.getWidth, peer.getHeight, 8, 8)

      val timeLabel = new Label(entry.label):
        font = new Font("SansSerif", Font.BOLD, 26)
        foreground = titleFg
        opaque = false
        horizontalAlignment = Alignment.Center
      peer.add(timeLabel.peer, java.awt.BorderLayout.CENTER)

      val catLabel = new Label(entry.category):
        font = new Font("SansSerif", Font.PLAIN, 13)
        foreground = entry.catColor
        opaque = false
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
    opaque = false
    peer.setLayout(new GridLayout(3, 4, 8, 8))
    for entry <- entries do
      peer.add(tcCard(entry).peer)

  gridPanel.maximumSize = new Dimension(620, 340)
  gridPanel.preferredSize = new Dimension(620, 340)

  // Center the grid horizontally
  private val gridWrapper = new BoxPanel(Orientation.Horizontal):
    opaque = false
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
