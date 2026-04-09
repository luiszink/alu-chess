package chess.aview.gui

import chess.model.Color

import scala.swing.*
import java.awt.{Color as AwtColor, Font, Dimension, BorderLayout, GridLayout, Cursor, Graphics2D, RenderingHints}
import javax.swing.{JDialog, JLabel, JPanel, JButton, SwingConstants, BorderFactory, WindowConstants, SwingUtilities}
import java.awt.event.{ActionEvent, MouseAdapter, MouseEvent}

/** Custom styled dialog for pawn promotion piece selection. */
object PromotionDialog:

  private val bgColor   = new AwtColor(38, 36, 33)
  private val btnColor  = new AwtColor(60, 58, 55)
  private val btnHover  = new AwtColor(85, 83, 80)
  private val fgColor   = new AwtColor(230, 230, 230)
  private val subColor  = new AwtColor(160, 160, 160)

  def show(parent: Component, color: Color): Option[Char] =
    val pieces = Seq(
      ('Q', "Dame",     if color == Color.White then "\u2655" else "\u265B"),
      ('R', "Turm",     if color == Color.White then "\u2656" else "\u265C"),
      ('B', "Läufer",   if color == Color.White then "\u2657" else "\u265D"),
      ('N', "Springer", if color == Color.White then "\u2658" else "\u265E")
    )

    var result: Option[Char] = Some('Q')

    val owner = SwingUtilities.getWindowAncestor(parent.peer)
    val dialog = new JDialog(owner)
    dialog.setTitle("Bauernumwandlung")
    dialog.setModal(true)
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE)
    dialog.setResizable(false)

    val root = new JPanel(new BorderLayout(0, 16))
    root.setBackground(bgColor)
    root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24))

    val header = new JLabel("Figur wählen", SwingConstants.CENTER)
    header.setFont(new Font("SansSerif", Font.BOLD, 15))
    header.setForeground(new AwtColor(210, 210, 210))
    root.add(header, BorderLayout.NORTH)

    val grid = new JPanel(new GridLayout(1, 4, 10, 0))
    grid.setBackground(bgColor)

    val isWhite = color == Color.White
    // For black pieces on dark background we draw a light outline to ensure visibility
    val pieceColor      = if isWhite then new AwtColor(255, 255, 255) else new AwtColor(20, 20, 20)
    val pieceShadowColor = if isWhite then new AwtColor(0, 0, 0, 110) else new AwtColor(220, 220, 220, 100)

    for (char, name, symbol) <- pieces do
      val card = new JPanel(new BorderLayout(0, 6))
      card.setBackground(btnColor)
      card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(new AwtColor(80, 78, 75), 1),
        BorderFactory.createEmptyBorder(10, 8, 10, 8)
      ))
      card.setPreferredSize(new Dimension(72, 96))
      card.setCursor(new Cursor(Cursor.HAND_CURSOR))

      // Custom panel that renders the piece symbol with a contrasting shadow
      val symbolPanel = new JPanel:
        setOpaque(false)
        override def paintComponent(g: java.awt.Graphics): Unit =
          super.paintComponent(g)
          val g2 = g.asInstanceOf[Graphics2D]
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
          g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)
          g2.setFont(new Font("Segoe UI Symbol", Font.PLAIN, 38))
          val fm = g2.getFontMetrics
          val tx = (getWidth - fm.stringWidth(symbol)) / 2
          val ty = (getHeight - fm.getHeight) / 2 + fm.getAscent
          g2.setColor(pieceShadowColor)
          g2.drawString(symbol, tx + 1, ty + 1)
          g2.setColor(pieceColor)
          g2.drawString(symbol, tx, ty)

      val nameLbl = new JLabel(name, SwingConstants.CENTER)
      nameLbl.setFont(new Font("SansSerif", Font.PLAIN, 11))
      nameLbl.setForeground(subColor)
      nameLbl.setOpaque(false)

      card.add(symbolPanel, BorderLayout.CENTER)
      card.add(nameLbl, BorderLayout.SOUTH)

      // Hover effect
      card.addMouseListener(new MouseAdapter:
        override def mouseEntered(e: MouseEvent): Unit =
          card.setBackground(btnHover)
        override def mouseExited(e: MouseEvent): Unit =
          card.setBackground(btnColor)
        override def mouseClicked(e: MouseEvent): Unit =
          result = Some(char)
          dialog.dispose()
      )

      grid.add(card)

    root.add(grid, BorderLayout.CENTER)
    dialog.setContentPane(root)
    dialog.pack()
    dialog.setLocationRelativeTo(parent.peer)
    dialog.setVisible(true)

    result
