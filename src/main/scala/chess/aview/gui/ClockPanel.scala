package chess.aview.gui

import chess.controller.ControllerInterface
import chess.model.{Color, ChessClock}

import scala.swing.*
import java.awt.{Color as AwtColor, Font, Dimension}
import javax.swing.BorderFactory

/** Single clock display for one player – placed above/below history like lichess. */
class ClockDisplay extends Panel:

  private val activeTimeBg = new AwtColor(48, 46, 43)   // dark bg for active clock
  private val activeTimeFg = new AwtColor(255, 255, 255) // bright white text when active
  private val idleTimeBg   = new AwtColor(48, 46, 43)
  private val idleTimeFg   = new AwtColor(120, 120, 120) // dimmed for inactive
  private val lowTimeBg    = new AwtColor(186, 202, 68)  // lichess green only for low time
  private val lowTimeFg    = new AwtColor(20, 20, 20)    // dark text on green
  private val expiredBg    = new AwtColor(160, 40, 40)

  private val timeFont = new Font("Monospaced", Font.BOLD, 30)

  private val timeLabel = new Label("--:--"):
    font = timeFont
    foreground = idleTimeFg
    background = idleTimeBg
    opaque = true
    horizontalAlignment = Alignment.Right
    border = BorderFactory.createEmptyBorder(8, 12, 8, 12)

  private val wrapper = new BorderPanel:
    background = new AwtColor(38, 36, 33)
    layout(timeLabel) = BorderPanel.Position.Center

  peer.setLayout(new java.awt.BorderLayout)
  peer.add(wrapper.peer)
  preferredSize = new Dimension(300, 52)
  maximumSize = new Dimension(Short.MaxValue, 52)
  minimumSize = new Dimension(200, 52)

  def update(clock: ChessClock, color: Color): Unit =
    val remaining = clock.remainingMs(color)
    timeLabel.text = ChessClock.formatTime(remaining)

    val isActive = clock.activeColor.contains(color)
    val isLow = remaining < 20_000 && remaining > 0
    val isExpired = remaining <= 0

    if isExpired then
      timeLabel.background = expiredBg
      timeLabel.foreground = lowTimeFg
    else if isActive && isLow then
      timeLabel.background = lowTimeBg
      timeLabel.foreground = lowTimeFg
    else if isActive then
      timeLabel.background = activeTimeBg
      timeLabel.foreground = activeTimeFg
    else
      timeLabel.background = idleTimeBg
      timeLabel.foreground = idleTimeFg

  def setHidden(): Unit =
    timeLabel.text = ""
    timeLabel.background = new AwtColor(38, 36, 33)
    timeLabel.foreground = new AwtColor(38, 36, 33)

/** Container managing both clock displays. */
class ClockPanel(controller: ControllerInterface):

  val blackClock = new ClockDisplay
  val whiteClock = new ClockDisplay

  def refresh(): Unit =
    controller.clock match
      case Some(clock) =>
        blackClock.update(clock, Color.Black)
        whiteClock.update(clock, Color.White)
        blackClock.visible = true
        whiteClock.visible = true
      case None =>
        blackClock.visible = false
        whiteClock.visible = false
