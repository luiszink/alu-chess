package chess.aview.gui

import chess.controller.ControllerInterface
import chess.model.{Board, Color, Move, Piece, Position, GameStatus}

import scala.swing.*
import scala.swing.event.*
import java.awt.{Color as AwtColor, Font, Graphics2D, RenderingHints, BasicStroke, GradientPaint}
import java.awt.geom.{Ellipse2D, RoundRectangle2D}

/** Panel that renders the chess board and handles piece selection/movement via clicks. */
class BoardPanel(controller: ControllerInterface, squareSize: Int = 80) extends Panel:

  private var selectedSquare: Option[Position] = None
  private var legalTargets: Set[Position] = Set.empty

  preferredSize = new Dimension(squareSize * 8 + 40, squareSize * 8 + 40)

  listenTo(mouse.clicks)
  reactions += {
    case MouseClicked(_, point, _, _, _) =>
      handleClick(point.x, point.y)
  }

  // --- Colors (modern dark theme inspired by lichess) ---
  private val lightSquare = new AwtColor(240, 217, 181)   // warm cream
  private val darkSquare = new AwtColor(181, 136, 99)      // warm brown
  private val selectedLight = new AwtColor(247, 247, 105)   // yellow highlight
  private val selectedDark = new AwtColor(218, 195, 71)     // darker yellow
  private val legalDotColor     = new AwtColor(0, 0, 0, 70)   // semi-transparent dot
  private val legalCaptureColor = new AwtColor(0, 0, 0, 130) // capture ring – higher opacity for visibility
  private val lastMoveLight = new AwtColor(205, 210, 106)   // light green
  private val lastMoveDark = new AwtColor(170, 162, 58)     // dark green
  private val checkColor = new AwtColor(235, 97, 80, 180)   // red glow for check
  private val borderColor = new AwtColor(48, 46, 43)        // dark border
  private val coordColor = new AwtColor(140, 130, 120)      // coordinate labels

  private val margin = 20

  // --- Cached fonts and colors (avoid allocations on every repaint) ---
  private val pieceFont       = new Font("Segoe UI Symbol", Font.PLAIN, (squareSize * 0.78).toInt)
  private val coordFont       = new Font("SansSerif", Font.BOLD, 11)
  private val whitePieceColor = new AwtColor(255, 255, 255)
  private val blackPieceColor = new AwtColor(30, 30, 30)
  private val pieceShadowColor = new AwtColor(0, 0, 0, 80)

  // --- Unicode chess pieces ---
  private def pieceUnicode(piece: Piece): String = piece match
    case Piece.King(Color.White)   => "\u2654"
    case Piece.Queen(Color.White)  => "\u2655"
    case Piece.Rook(Color.White)   => "\u2656"
    case Piece.Bishop(Color.White) => "\u2657"
    case Piece.Knight(Color.White) => "\u2658"
    case Piece.Pawn(Color.White)   => "\u2659"
    case Piece.King(Color.Black)   => "\u265A"
    case Piece.Queen(Color.Black)  => "\u265B"
    case Piece.Rook(Color.Black)   => "\u265C"
    case Piece.Bishop(Color.Black) => "\u265D"
    case Piece.Knight(Color.Black) => "\u265E"
    case Piece.Pawn(Color.Black)   => "\u265F"

  override def paintComponent(g: Graphics2D): Unit =
    super.paintComponent(g)
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB)

    val board = controller.game.board
    val game = controller.game

    // Background
    g.setColor(borderColor)
    g.fillRect(0, 0, size.width, size.height)

    // Draw board squares
    for
      row <- 0 until 8
      col <- 0 until 8
    do
      val displayRow = 7 - row // flip for display (row 0 = bottom)
      val x = margin + col * squareSize
      val y = margin + displayRow * squareSize
      val pos = Position(row, col)
      val isLight = (row + col) % 2 == 0

      // Determine square color
      val baseColor = if isLight then lightSquare else darkSquare
      val lastMoveColor = if isLight then lastMoveLight else lastMoveDark
      val selColor = if isLight then selectedLight else selectedDark

      val isLastMoveSquare = game.lastMove.exists(m => m.from == pos || m.to == pos)
      val isSelected = selectedSquare.contains(pos)

      val squareColor =
        if isSelected then selColor
        else if isLastMoveSquare then lastMoveColor
        else baseColor

      g.setColor(squareColor)
      g.fillRect(x, y, squareSize, squareSize)

      // Check highlight (red glow on king square)
      val isKingInCheck = game.status match
        case GameStatus.Check | GameStatus.Checkmate =>
          board.cell(pos).exists {
            case Piece.King(c) => c == game.currentPlayer
            case _ => false
          }
        case _ => false

      if isKingInCheck then
        val cx = x + squareSize / 2
        val cy = y + squareSize / 2
        val radius = squareSize / 2
        val gradient = new GradientPaint(
          cx.toFloat, cy.toFloat, new AwtColor(235, 97, 80, 220),
          (cx + radius).toFloat, cy.toFloat, new AwtColor(235, 97, 80, 0)
        )
        g.setPaint(gradient)
        g.fillOval(x, y, squareSize, squareSize)

      // Draw piece
      board.cell(pos).foreach { piece =>
        g.setFont(pieceFont)
        val text = pieceUnicode(piece)
        val fm = g.getFontMetrics
        val tx = x + (squareSize - fm.stringWidth(text)) / 2
        val ty = y + (squareSize - fm.getHeight) / 2 + fm.getAscent

        // Shadow
        g.setColor(pieceShadowColor)
        g.drawString(text, tx + 1, ty + 1)

        // Piece
        g.setColor(if piece.color == Color.White then whitePieceColor else blackPieceColor)
        g.drawString(text, tx, ty)
      }

      // Legal move indicators
      if legalTargets.contains(pos) then
        val isCapture = board.cell(pos).isDefined
        if isCapture then
          // Ring around capturable piece
          g.setColor(legalCaptureColor)
          g.setStroke(new BasicStroke(3.0f))
          val inset = 4
          g.drawOval(x + inset, y + inset, squareSize - 2 * inset, squareSize - 2 * inset)
          g.setStroke(new BasicStroke(1.0f))
        else
          // Small dot for empty target
          g.setColor(legalDotColor)
          val dotSize = squareSize / 4
          val dx = x + (squareSize - dotSize) / 2
          val dy = y + (squareSize - dotSize) / 2
          g.fillOval(dx, dy, dotSize, dotSize)

    // Draw coordinates
    g.setFont(coordFont)
    g.setColor(coordColor)
    for col <- 0 until 8 do
      val letter = ('a' + col).toChar.toString
      val x = margin + col * squareSize + squareSize / 2 - 3
      g.drawString(letter, x, margin + 8 * squareSize + 14)
    for row <- 0 until 8 do
      val displayRow = 7 - row
      val y = margin + displayRow * squareSize + squareSize / 2 + 4
      g.drawString((row + 1).toString, 5, y)

  def handleClick(mx: Int, my: Int): Unit =
    val col = (mx - margin) / squareSize
    val displayRow = (my - margin) / squareSize
    val row = 7 - displayRow

    if col < 0 || col > 7 || row < 0 || row > 7 then return

    val pos = Position(row, col)
    val game = controller.game
    val board = game.board
    val isGameOver = game.status match
      case GameStatus.Checkmate | GameStatus.Stalemate | GameStatus.Resigned | GameStatus.Draw | GameStatus.TimeOut => true
      case _ => false

    if isGameOver || !controller.isAtLatest then return

    selectedSquare match
      case Some(from) if legalTargets.contains(pos) =>
        // Execute move
        val piece = board.cell(from)
        val isPromotion = piece.exists {
          case Piece.Pawn(Color.White) => from.row == 6 && pos.row == 7
          case Piece.Pawn(Color.Black) => from.row == 1 && pos.row == 0
          case _ => false
        }
        val promotion = if isPromotion then
          PromotionDialog.show(this, game.currentPlayer)
        else None

        val move = Move(from, pos, promotion)
        controller.doMove(move)
        selectedSquare = None
        legalTargets = Set.empty
        repaint()

      case Some(from) if from == pos =>
        // Deselect
        selectedSquare = None
        legalTargets = Set.empty
        repaint()

      case _ =>
        // Select a piece of current player
        board.cell(pos) match
          case Some(piece) if piece.color == game.currentPlayer =>
            selectedSquare = Some(pos)
            // Calculate legal targets for this piece
            val allLegal = chess.model.MoveValidator.legalMoves(
              board, game.currentPlayer, game.movedPieces, game.lastMove
            )
            legalTargets = allLegal.filter(_.from == pos).map(_.to).toSet
            repaint()
          case _ =>
            selectedSquare = None
            legalTargets = Set.empty
            repaint()

  /** Called by GUI when controller notifies update. */
  def refresh(): Unit =
    selectedSquare = None
    legalTargets = Set.empty
    repaint()
