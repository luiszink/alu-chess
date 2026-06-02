package chess.model

/** Immutable time control configuration. */
case class TimeControl(initialTimeMs: Long, incrementMs: Long, name: String)

object TimeControl:
  val Bullet1_0    = TimeControl(60_000L, 0L, "1+0 Bullet")
  val Bullet2_1    = TimeControl(120_000L, 1_000L, "2+1 Bullet")
  val Blitz3_0     = TimeControl(180_000L, 0L, "3+0 Blitz")
  val Blitz3_2     = TimeControl(180_000L, 2_000L, "3+2 Blitz")
  val Blitz5_0     = TimeControl(300_000L, 0L, "5+0 Blitz")
  val Blitz5_3     = TimeControl(300_000L, 3_000L, "5+3 Blitz")
  val Rapid10_0    = TimeControl(600_000L, 0L, "10+0 Rapid")
  val Rapid10_5    = TimeControl(600_000L, 5_000L, "10+5 Rapid")
  val Rapid15_10   = TimeControl(900_000L, 10_000L, "15+10 Rapid")
  val Classical30_0  = TimeControl(1_800_000L, 0L, "30+0 Classical")
  val Classical30_20 = TimeControl(1_800_000L, 20_000L, "30+20 Classical")

  val presets: Vector[TimeControl] = Vector(
    Bullet1_0, Bullet2_1, Blitz3_0, Blitz3_2,
    Blitz5_0, Blitz5_3, Rapid10_0, Rapid10_5,
    Rapid15_10, Classical30_0, Classical30_20
  )

/** Immutable chess clock. Tracks remaining time for both players.
  * Uses System.nanoTime-based timestamps for elapsed time calculation. */
case class ChessClock(
  whiteTimeMs: Long,
  blackTimeMs: Long,
  incrementMs: Long,
  activeColor: Option[Color] = None,
  lastTickNanos: Long = 0L
):

  def start(now: Long): ChessClock =
    copy(activeColor = Some(Color.White), lastTickNanos = now)

  /** Update the active player's remaining time based on elapsed nanos. */
  def tick(now: Long): ChessClock =
    activeColor match
      case Some(color) =>
        val elapsedMs = (now - lastTickNanos) / 1_000_000
        color match
          case Color.White => copy(whiteTimeMs = whiteTimeMs - elapsedMs, lastTickNanos = now)
          case Color.Black => copy(blackTimeMs = blackTimeMs - elapsedMs, lastTickNanos = now)
      case None => this

  /** Switch the clock after a move: update time, add increment, switch active player. */
  def press(now: Long): ChessClock =
    val ticked = tick(now)
    ticked.activeColor match
      case Some(Color.White) =>
        ticked.copy(
          whiteTimeMs = ticked.whiteTimeMs + incrementMs,
          activeColor = Some(Color.Black),
          lastTickNanos = now
        )
      case Some(Color.Black) =>
        ticked.copy(
          blackTimeMs = ticked.blackTimeMs + incrementMs,
          activeColor = Some(Color.White),
          lastTickNanos = now
        )
      case None => ticked

  def stop: ChessClock =
    val ticked = if activeColor.isDefined then tick(System.nanoTime()) else this
    ticked.copy(activeColor = None)

  /** Returns the color whose time has expired, if any. */
  def expired: Option[Color] =
    if whiteTimeMs <= 0 then Some(Color.White)
    else if blackTimeMs <= 0 then Some(Color.Black)
    else None

  def remainingMs(color: Color): Long = color match
    case Color.White => math.max(0, whiteTimeMs)
    case Color.Black => math.max(0, blackTimeMs)

object ChessClock:
  def fromTimeControl(tc: TimeControl): ChessClock =
    ChessClock(
      whiteTimeMs = tc.initialTimeMs,
      blackTimeMs = tc.initialTimeMs,
      incrementMs = tc.incrementMs
    )

  def formatTime(ms: Long): String =
    val clamped = math.max(0L, ms)
    val totalSeconds = clamped / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val tenths = (clamped % 1000) / 100
    if clamped < 20_000 then f"$minutes:$seconds%02d.$tenths"
    else f"$minutes%02d:$seconds%02d"
