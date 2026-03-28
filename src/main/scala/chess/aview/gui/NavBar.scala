package chess.aview.gui

import scala.swing.*
import java.awt.{Color as AwtColor, Font, Dimension, Cursor, FlowLayout}
import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing.BorderFactory

/** Lichess-inspired top navigation bar.
  * Contains the logo on the left and view-switching items on the right.
  * `onHome` / `onGame` are called when the user clicks the respective nav item.
  * `onTools` opens the tools dialog when in game view.
  */
class NavBar(onHome: () => Unit, onGame: () => Unit, onTools: () => Unit) extends Panel:

  private val barBg    = new AwtColor(32, 30, 28)
  private val logoBg   = new AwtColor(32, 30, 28)
  private val logoFg   = new AwtColor(255, 255, 255)
  private val itemFg   = new AwtColor(180, 180, 180)
  private val itemHover = new AwtColor(240, 240, 240)
  private val activeFg  = new AwtColor(255, 255, 255)
  private val activeUnderline = new AwtColor(186, 202, 68) // lichess green-yellow

  private val logoFont = new Font("SansSerif", Font.BOLD, 22)
  private val itemFont = new Font("SansSerif", Font.PLAIN, 15)

  background = barBg
  preferredSize = new Dimension(Short.MaxValue, 40)
  maximumSize  = new Dimension(Short.MaxValue, 40)
  minimumSize  = new Dimension(400, 40)

  peer.setLayout(new java.awt.BorderLayout(0, 0))
  peer.setBackground(barBg)

  // --- Logo (left) ---
  private val logoLabel = new Label("♟ alu-chess"):
    font = logoFont
    foreground = logoFg
    background = logoBg
    opaque = true
    cursor = new Cursor(Cursor.HAND_CURSOR)
    border = BorderFactory.createEmptyBorder(0, 16, 0, 20)
  logoLabel.peer.addMouseListener(new MouseAdapter:
    override def mouseClicked(e: MouseEvent): Unit = onHome()
  )

  // --- Active view tracking ---
  private var activeView = "home"

  // --- Nav item factory ---
  private def navItem(text: String, viewKey: String, action: () => Unit): Label =
    val lbl = new Label(text):
      font = itemFont
      foreground = if activeView == viewKey then activeFg else itemFg
      background = barBg
      opaque = true
      cursor = new Cursor(Cursor.HAND_CURSOR)
      border = BorderFactory.createEmptyBorder(0, 14, 0, 14)
    lbl.peer.addMouseListener(new MouseAdapter:
      override def mouseEntered(e: MouseEvent): Unit =
        lbl.foreground = itemHover
      override def mouseExited(e: MouseEvent): Unit =
        lbl.foreground = if activeView == viewKey then activeFg else itemFg
      override def mouseClicked(e: MouseEvent): Unit =
        action()
    )
    lbl

  private val homeItem  = navItem("Startseite", "home",  () => onHome())
  private val gameItem  = navItem("Spiel",      "game",  () => onGame())
  private val toolsItem = navItem("⚙",          "tools", () => onTools())
  toolsItem.tooltip = "Werkzeuge"

  // Right-side items container
  private val rightPanel = new Panel:
    background = barBg
    opaque = true
    peer.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0))
    peer.setBackground(barBg)
    peer.add(homeItem.peer)
    peer.add(gameItem.peer)
    peer.add(toolsItem.peer)

  peer.add(logoLabel.peer, java.awt.BorderLayout.WEST)
  peer.add(rightPanel.peer, java.awt.BorderLayout.CENTER)

  // Bottom border line
  border = BorderFactory.createMatteBorder(0, 0, 1, 0, new AwtColor(50, 48, 46))

  def setActive(view: String): Unit =
    activeView = view
    homeItem.foreground = if view == "home" then activeFg else itemFg
    gameItem.foreground = if view == "game" then activeFg else itemFg
    repaint()
