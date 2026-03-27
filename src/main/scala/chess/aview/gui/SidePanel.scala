package chess.aview.gui

import chess.controller.ControllerInterface
import chess.model.{GameStatus, TestPositions, Fen, Move, ChessError, TimeControl, Pgn}

import scala.swing.*
import scala.swing.event.*
import java.awt.{Color as AwtColor, Font, Dimension, Cursor}
import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing.{JOptionPane, BorderFactory, DefaultListCellRenderer}
import javax.swing.border.EmptyBorder

/** Side panel showing game status, move info, and control buttons. */
class SidePanel(controller: ControllerInterface, onNewGame: () => Unit, onQuit: () => Unit) extends BoxPanel(Orientation.Vertical):

  private val panelWidth = 320
  private val contentWidth = 250
  private val smallGap = 6
  private val sectionGap = 14

  background = new AwtColor(38, 36, 33)
  preferredSize = new Dimension(panelWidth, 900)
  minimumSize = new Dimension(panelWidth, 200)
  border = new EmptyBorder(20, 16, 20, 16)

  private def centerAlign(component: Component): Unit =
    component.xLayoutAlignment = 0.5

  private def styledSeparator(): Separator =
    val sep = new Separator
    sep.peer.setForeground(new AwtColor(65, 63, 60))
    sep

  // --- Title ---
  private val titleLabel = new Label("alu-chess"):
    font = new Font("SansSerif", Font.BOLD, 22)
    foreground = new AwtColor(230, 230, 230)
    horizontalAlignment = Alignment.Center
  centerAlign(titleLabel)

  // --- Status ---
  private val statusLabel = new Label(""):
    font = new Font("SansSerif", Font.PLAIN, 15)
    foreground = new AwtColor(200, 200, 200)
    horizontalAlignment = Alignment.Center
  centerAlign(statusLabel)

  // --- Current player indicator ---
  private val playerLabel = new Label(""):
    font = new Font("SansSerif", Font.BOLD, 14)
    foreground = new AwtColor(180, 180, 180)
    horizontalAlignment = Alignment.Center
  centerAlign(playerLabel)

  // --- Move counter ---
  private val moveLabel = new Label(""):
    font = new Font("SansSerif", Font.PLAIN, 12)
    foreground = new AwtColor(140, 140, 140)
    horizontalAlignment = Alignment.Center
  centerAlign(moveLabel)

  // --- Cached fonts and colors for refresh() ---
  private val statusFontAlert  = new Font("SansSerif", Font.BOLD, 17)
  private val statusFontNormal = new Font("SansSerif", Font.PLAIN, 15)
  private val colorCheck       = new AwtColor(255, 180, 70)
  private val colorCheckmate   = new AwtColor(235, 97, 80)
  private val colorDraw        = new AwtColor(180, 180, 100)
  private val colorStatus      = new AwtColor(200, 200, 200)

  // --- Buttons ---
  private val btnNormal = new AwtColor(68, 66, 63)
  private val btnHover  = new AwtColor(92, 90, 87)
  private val btnPress  = new AwtColor(50, 48, 46)

  private def styledButton(text: String, onClick: () => Unit): Button =
    val btn = new Button(text)
    btn.font = new Font("SansSerif", Font.BOLD, 13)
    btn.foreground = new AwtColor(230, 230, 230)
    btn.background = btnNormal
    btn.opaque = true
    btn.borderPainted = false
    btn.focusPainted = false
    btn.cursor = new Cursor(Cursor.HAND_CURSOR)
    btn.preferredSize = new Dimension(contentWidth, 34)
    btn.maximumSize = new Dimension(contentWidth, 34)
    btn.peer.addMouseListener(new MouseAdapter:
      override def mouseEntered(e: MouseEvent): Unit = btn.background = btnHover
      override def mouseExited(e: MouseEvent): Unit  = btn.background = btnNormal
      override def mousePressed(e: MouseEvent): Unit = btn.background = btnPress
      override def mouseReleased(e: MouseEvent): Unit =
        btn.background = if btn.peer.getModel.isRollover then btnHover else btnNormal
    )
    btn.listenTo(btn)
    btn.reactions += { case ButtonClicked(_) => onClick() }
    centerAlign(btn)
    btn

  private def styleTextInput(input: TextComponent): Unit =
    input.font = new Font("Monospaced", Font.PLAIN, 12)
    input.foreground = new AwtColor(230, 230, 230)
    input.background = new AwtColor(55, 53, 50)
    input.caret.color = new AwtColor(230, 230, 230)
    input.peer.setBorder(BorderFactory.createCompoundBorder(
      BorderFactory.createLineBorder(new AwtColor(75, 73, 70), 1),
      BorderFactory.createEmptyBorder(5, 8, 5, 8)
    ))
    centerAlign(input)

  private def showError(message: String): Unit =
    JOptionPane.showMessageDialog(peer, message, "Fehler", JOptionPane.ERROR_MESSAGE)

  private def loadFenAndReport(fen: String): Unit =
    controller.loadFenResult(fen) match
      case Left(err)  => showError(err.message)
      case Right(_)   => fenInputField.text = Fen.toFen(controller.game)

  private def parseCoordinateToken(token: String): Either[ChessError, Move] =
    val stripped = token.trim.replaceAll("[+#?!]+$", "")
    if stripped.matches("^[a-h][1-8][a-h][1-8]$") then Move.fromStringE(stripped)
    else if stripped.matches("^[a-h][1-8][a-h][1-8][qrbnQRBN]$") then
      val from = stripped.substring(0, 2)
      val to = stripped.substring(2, 4)
      val promo = stripped.substring(4, 5).toUpperCase
      Move.fromStringE(s"$from $to $promo")
    else Left(ChessError.InvalidMoveFormat(token))

  private def extractPgnTokens(text: String): Vector[String] =
    val withoutComments = text
      .replaceAll("\\{[^}]*\\}", " ")
      .replaceAll("\\([^)]*\\)", " ")
      .replaceAll("\\d+\\.(\\.\\.)?", " ")
    withoutComments
      .split("\\s+")
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .filterNot(t => t == "1-0" || t == "0-1" || t == "1/2-1/2" || t == "*")

  // --- Time Control ---
  private val timeControlLabel = new Label("Zeitkontrolle"):
    font = new Font("SansSerif", Font.BOLD, 13)
    foreground = new AwtColor(180, 180, 180)
  centerAlign(timeControlLabel)

  private val tcOptions = Vector("Keine Uhr") ++ TimeControl.presets.map(_.name)
  private val timeControlCombo = new ComboBox(tcOptions):
    font = new Font("SansSerif", Font.PLAIN, 12)
    preferredSize = new Dimension(contentWidth, 30)
    maximumSize = new Dimension(contentWidth, 30)
    peer.setBackground(comboInputBg)
    peer.setForeground(comboFg)
    peer.setRenderer(new DefaultListCellRenderer:
      override def getListCellRendererComponent(
          list: javax.swing.JList[?], value: Object, index: Int,
          isSelected: Boolean, cellHasFocus: Boolean): java.awt.Component =
        val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        c.setFont(new Font("SansSerif", Font.PLAIN, 12))
        if isSelected then
          c.setBackground(comboSelBg)
          c.setForeground(comboFg)
        else
          c.setBackground(comboInputBg)
          c.setForeground(comboFg)
        c
    )
  centerAlign(timeControlCombo)

  private def selectedTimeControl: Option[TimeControl] =
    val idx = timeControlCombo.selection.index
    if idx <= 0 then None else Some(TimeControl.presets(idx - 1))

  private val newGameButton = styledButton("Neues Spiel", () => {
    controller.newGameWithClock(selectedTimeControl)
  })
  private val quitButton = styledButton("Beenden", onQuit)

  // --- PGN Save/Load ---
  private val pgnSaveLabel = new Label("Spiel speichern"):
    font = new Font("SansSerif", Font.BOLD, 13)
    foreground = new AwtColor(180, 180, 180)
  centerAlign(pgnSaveLabel)

  private val savePgnButton = styledButton("PGN speichern", () => savePgnFile())
  private val loadPgnButton = styledButton("PGN laden", () => loadPgnFile())

  private def savePgnFile(): Unit =
    val chooser = new javax.swing.JFileChooser()
    chooser.setDialogTitle("PGN speichern")
    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PGN-Dateien", "pgn"))
    chooser.setSelectedFile(new java.io.File("game.pgn"))
    val result = chooser.showSaveDialog(peer)
    if result == javax.swing.JFileChooser.APPROVE_OPTION then
      val file = chooser.getSelectedFile
      val path = if file.getName.endsWith(".pgn") then file else new java.io.File(file.getAbsolutePath + ".pgn")
      scala.util.Try {
        val tc = selectedTimeControl
        // Use the latest game state for full history
        controller.browseToEnd()
        val pgn = Pgn.toPgn(controller.game, timeControl = tc)
        val writer = new java.io.PrintWriter(path)
        try writer.write(pgn) finally writer.close()
      } match
        case scala.util.Failure(ex) => showError(s"Fehler beim Speichern: ${ex.getMessage}")
        case _ => ()

  private def loadPgnFile(): Unit =
    val chooser = new javax.swing.JFileChooser()
    chooser.setDialogTitle("PGN laden")
    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("PGN-Dateien", "pgn"))
    val result = chooser.showOpenDialog(peer)
    if result == javax.swing.JFileChooser.APPROVE_OPTION then
      scala.util.Try {
        val source = scala.io.Source.fromFile(chooser.getSelectedFile)
        try source.mkString finally source.close()
      } match
        case scala.util.Success(content) =>
          controller.newGame()
          Pgn.replayPgn(content) match
            case Right(_) =>
              // Replay moves through controller
              replayPgnThroughController(content)
            case Left(err) => showError(err)
        case scala.util.Failure(ex) => showError(s"Fehler beim Laden: ${ex.getMessage}")

  private def replayPgnThroughController(pgn: String): Unit =
    controller.newGame()
    val movetext = pgn.linesIterator.filterNot(_.startsWith("[")).mkString(" ").trim
    val tokens = movetext
      .replaceAll("\\{[^}]*\\}", " ")
      .replaceAll("\\([^)]*\\)", " ")
      .replaceAll("\\d+\\.", " ")
      .split("\\s+")
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .filterNot(t => t == "1-0" || t == "0-1" || t == "1/2-1/2" || t == "*")
    var currentGame = controller.game
    var failed = false
    for token <- tokens if !currentGame.status.isTerminal && !failed do
      Pgn.parseSAN(token, currentGame) match
        case Some(move) =>
          controller.doMove(move)
          currentGame = controller.game
        case None =>
          showError(s"Zug '$token' nicht erkannt")
          failed = true

  // --- FEN Input ---
  private val fenInputLabel = new Label("FEN"):
    font = new Font("SansSerif", Font.BOLD, 13)
    foreground = new AwtColor(180, 180, 180)
  centerAlign(fenInputLabel)

  private val fenInputField = new TextField:
    columns = 24
    maximumSize = new Dimension(contentWidth, 32)
    preferredSize = new Dimension(contentWidth, 32)
  styleTextInput(fenInputField)

  private val fillFenButton = styledButton("Aktuelle FEN", () => fenInputField.text = Fen.toFen(controller.game))
  private val loadFenButton = styledButton("FEN laden", () =>
    val fen = fenInputField.text.trim
    if fen.isEmpty then showError("Bitte einen FEN-String eingeben.")
    else loadFenAndReport(fen)
  )

  // --- PGN Input ---
  private val pgnInputLabel = new Label("PGN"):
    font = new Font("SansSerif", Font.BOLD, 13)
    foreground = new AwtColor(180, 180, 180)
  centerAlign(pgnInputLabel)

  private val pgnHintLabel = new Label("Akzeptiert Koordinatenzuege, z. B. e2e4 e7e5"):
    font = new Font("SansSerif", Font.ITALIC, 11)
    foreground = new AwtColor(150, 150, 150)
    horizontalAlignment = Alignment.Center
  centerAlign(pgnHintLabel)

  private val pgnInputArea = new TextArea:
    rows = 5
    lineWrap = true
    wordWrap = true
  styleTextInput(pgnInputArea)

  private val pgnScrollPane = new ScrollPane(pgnInputArea):
    preferredSize = new Dimension(contentWidth, 110)
    maximumSize = new Dimension(contentWidth, 110)
    peer.setBorder(BorderFactory.createLineBorder(new AwtColor(75, 73, 70), 1))
    peer.getViewport.setBackground(new AwtColor(55, 53, 50))
    peer.getVerticalScrollBar.setBackground(new AwtColor(55, 53, 50))
  centerAlign(pgnScrollPane)

  private val applyPgnButton = styledButton("PGN anwenden", () =>
    val raw = pgnInputArea.text.trim
    if raw.isEmpty then showError("Bitte PGN-Text eingeben.")
    else
      val tokens = extractPgnTokens(raw)
      if tokens.isEmpty then showError("Keine Zug-Tokens im PGN gefunden.")
      else
        val applyResult = tokens.zipWithIndex.foldLeft[Either[String, Unit]](Right(())) {
          case (Left(err), _) => Left(err)
          case (Right(_), (token, idx)) =>
            for
              move <- parseCoordinateToken(token).left.map(err => s"Zug ${idx + 1} ('$token'): ${err.message}")
              _ <- controller.doMoveResult(move).left.map(err => s"Zug ${idx + 1} ('$token'): ${err.message}")
            yield ()
        }
        applyResult.left.foreach(showError)
  )

  // --- Test Positions ---
  private val testPositionLabel = new Label("Teststellungen"):
    font = new Font("SansSerif", Font.BOLD, 13)
    foreground = new AwtColor(180, 180, 180)
    horizontalAlignment = Alignment.Center
  centerAlign(testPositionLabel)

  private val selectableTestPositions = TestPositions.positions.filter(_.fen.nonEmpty)

  private val comboInputBg  = new AwtColor(55, 53, 50)
  private val comboSelBg    = new AwtColor(90, 88, 85)
  private val comboFg       = new AwtColor(230, 230, 230)

  private val positionCombo = new ComboBox(selectableTestPositions.map(_.name)):
    font = new Font("SansSerif", Font.PLAIN, 12)
    preferredSize = new Dimension(contentWidth, 30)
    maximumSize = new Dimension(contentWidth, 30)
    peer.setBackground(comboInputBg)
    peer.setForeground(comboFg)
    peer.setRenderer(new DefaultListCellRenderer:
      override def getListCellRendererComponent(
          list: javax.swing.JList[?], value: Object, index: Int,
          isSelected: Boolean, cellHasFocus: Boolean): java.awt.Component =
        val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
        c.setFont(new Font("SansSerif", Font.PLAIN, 12))
        if isSelected then
          c.setBackground(comboSelBg)
          c.setForeground(comboFg)
        else
          c.setBackground(comboInputBg)
          c.setForeground(comboFg)
        c
    )
  centerAlign(positionCombo)

  private val positionDescLabel = new Label(""):
    font = new Font("SansSerif", Font.ITALIC, 11)
    foreground = new AwtColor(160, 160, 160)
    horizontalAlignment = Alignment.Center
    preferredSize = new Dimension(contentWidth, 40)
    maximumSize = new Dimension(contentWidth, 40)
  centerAlign(positionDescLabel)

  private val loadPositionButton = styledButton("Stellung laden", () => loadSelectedPosition())

  private def loadSelectedPosition(): Unit =
    val idx = positionCombo.selection.index
    if idx < 0 || idx >= selectableTestPositions.size then return
    val tp = selectableTestPositions(idx)
    loadFenAndReport(tp.fen)

  listenTo(positionCombo.selection)
  reactions += {
    case SelectionChanged(`positionCombo`) =>
      val idx = positionCombo.selection.index
      if idx >= 0 && idx < selectableTestPositions.size then
        positionDescLabel.text = s"<html><center>${selectableTestPositions(idx).description}</center></html>"
  }

  // Layout
  contents += titleLabel
  contents += Swing.VStrut(sectionGap)
  contents += styledSeparator()
  contents += Swing.VStrut(sectionGap)
  contents += playerLabel
  contents += Swing.VStrut(smallGap)
  contents += statusLabel
  contents += Swing.VStrut(smallGap)
  contents += moveLabel
  contents += Swing.VStrut(sectionGap)
  contents += styledSeparator()
  contents += Swing.VStrut(sectionGap)
  contents += timeControlLabel
  contents += Swing.VStrut(smallGap)
  contents += timeControlCombo
  contents += Swing.VStrut(smallGap)
  contents += newGameButton
  contents += Swing.VStrut(smallGap)
  contents += quitButton
  contents += Swing.VStrut(sectionGap)
  contents += styledSeparator()
  contents += Swing.VStrut(sectionGap)
  contents += fenInputLabel
  contents += Swing.VStrut(smallGap)
  contents += fenInputField
  contents += Swing.VStrut(smallGap)
  contents += fillFenButton
  contents += Swing.VStrut(smallGap)
  contents += loadFenButton
  contents += Swing.VStrut(sectionGap)
  contents += styledSeparator()
  contents += Swing.VStrut(sectionGap)
  contents += testPositionLabel
  contents += Swing.VStrut(smallGap)
  contents += positionCombo
  contents += Swing.VStrut(4)
  contents += positionDescLabel
  contents += Swing.VStrut(smallGap)
  contents += loadPositionButton
  contents += Swing.VStrut(sectionGap)
  contents += styledSeparator()
  contents += Swing.VStrut(sectionGap)
  contents += pgnInputLabel
  contents += Swing.VStrut(4)
  contents += pgnHintLabel
  contents += Swing.VStrut(smallGap)
  contents += pgnScrollPane
  contents += Swing.VStrut(smallGap)
  contents += applyPgnButton
  contents += Swing.VStrut(sectionGap)
  contents += styledSeparator()
  contents += Swing.VStrut(sectionGap)
  contents += pgnSaveLabel
  contents += Swing.VStrut(smallGap)
  contents += savePgnButton
  contents += Swing.VStrut(smallGap)
  contents += loadPgnButton
  contents += Swing.VStrut(10)
  contents += Swing.VGlue

  def refresh(): Unit =
    val game = controller.game
    val isWhite = game.currentPlayer == chess.model.Color.White

    playerLabel.text = if isWhite then "⬜ Weiß" else "⬛ Schwarz"

    statusLabel.text = game.status match
      case GameStatus.Playing   => "am Zug"
      case GameStatus.Check     => "Schach!"
      case GameStatus.Checkmate => "Schachmatt!"
      case GameStatus.Stalemate => "Patt – Remis"
      case GameStatus.Resigned  => "Aufgegeben"
      case GameStatus.Draw      => "Remis"
      case GameStatus.TimeOut   => "Zeit abgelaufen!"

    val isAlert = game.status == GameStatus.Check || game.status == GameStatus.Checkmate
    statusLabel.font = if isAlert then statusFontAlert else statusFontNormal

    statusLabel.foreground = game.status match
      case GameStatus.Check     => colorCheck
      case GameStatus.Checkmate | GameStatus.TimeOut => colorCheckmate
      case GameStatus.Stalemate | GameStatus.Draw => colorDraw
      case _ => colorStatus

    val lastMoveStr = game.lastMove.map(_.toString).getOrElse("–")
    moveLabel.text = s"Letzter Zug: $lastMoveStr"

    if !fenInputField.hasFocus then
      fenInputField.text = Fen.toFen(game)

    repaint()
