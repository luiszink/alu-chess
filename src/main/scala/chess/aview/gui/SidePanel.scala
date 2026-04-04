package chess.aview.gui

import chess.controller.ControllerInterface
import chess.model.{GameStatus, TestPositions, Fen, Move, ChessError, Pgn}

import scala.swing.*
import scala.swing.event.*
import java.awt.{Color as AwtColor, Font, Dimension, Cursor}
import java.awt.event.{MouseAdapter, MouseEvent}
import javax.swing.{JOptionPane, BorderFactory, DefaultListCellRenderer, JTabbedPane}
import javax.swing.border.EmptyBorder

/** Side panel showing game status, move info, and control buttons. */
class SidePanel(controller: ControllerInterface, onNewGame: () => Unit, onQuit: () => Unit) extends BoxPanel(Orientation.Vertical):

  private val panelWidth = 300
  private val contentWidth = 268
  private val smallGap = 6
  private val sectionGap = 10

  // Reuse the start-screen palette for a consistent app look.
  private val bgMain        = new AwtColor(38, 36, 33)
  private val cardBg        = new AwtColor(52, 50, 47)
  private val cardBorder    = new AwtColor(60, 58, 55)
  private val titleFg       = new AwtColor(240, 240, 240)
  private val subtitleFg    = new AwtColor(140, 140, 140)
  private val accentGreen   = new AwtColor(186, 202, 68)

  private val comboInputBg  = new AwtColor(55, 53, 50)
  private val comboSelBg    = new AwtColor(90, 88, 85)
  private val comboFg       = new AwtColor(230, 230, 230)

  background = bgMain
  border = new EmptyBorder(8, 12, 8, 12)

  private def centerAlign(component: Component): Unit =
    component.xLayoutAlignment = 0.5

  private def styledSeparator(): Separator =
    val sep = new Separator
    sep.peer.setForeground(new AwtColor(65, 63, 60))
    sep

  private def sectionCard(title: String, items: Component*): BoxPanel =
    val panel = new BoxPanel(Orientation.Vertical):
      background = cardBg
      border = Swing.CompoundBorder(
        Swing.LineBorder(cardBorder, 1),
        Swing.EmptyBorder(8, 8, 8, 8)
      )
      val titleLabel = new Label(title):
        font = new Font("SansSerif", Font.BOLD, 12)
        foreground = accentGreen
      titleLabel.xLayoutAlignment = 0.0
      contents += titleLabel
      contents += Swing.VStrut(5)
      items.zipWithIndex.foreach { case (item, idx) =>
        contents += item
        if idx < items.size - 1 then contents += Swing.VStrut(4)
      }
    panel.maximumSize = new Dimension(contentWidth, Short.MaxValue)
    centerAlign(panel)
    panel

  private def tabContent(cards: Component*): ScrollPane =
    val content = new BoxPanel(Orientation.Vertical):
      background = bgMain
      border = new EmptyBorder(10, 10, 10, 10)
      cards.zipWithIndex.foreach { case (card, idx) =>
        contents += card
        if idx < cards.size - 1 then contents += Swing.VStrut(8)
      }
      contents += Swing.VGlue

    new ScrollPane(content):
      horizontalScrollBarPolicy = ScrollPane.BarPolicy.Never
      verticalScrollBarPolicy = ScrollPane.BarPolicy.AsNeeded
      border = Swing.EmptyBorder(0, 0, 0, 0)
      peer.getViewport.setBackground(bgMain)

  // --- Status ---
  private val statusLabel = new Label(""):
    font = new Font("SansSerif", Font.BOLD, 16)
    foreground = titleFg
    horizontalAlignment = Alignment.Center
  centerAlign(statusLabel)

  // --- Current player indicator ---
  private val playerLabel = new Label(""):
    font = new Font("SansSerif", Font.BOLD, 14)
    foreground = subtitleFg
    horizontalAlignment = Alignment.Center
  centerAlign(playerLabel)

  // --- Move counter ---
  private val moveLabel = new Label(""):
    font = new Font("SansSerif", Font.PLAIN, 12)
    foreground = subtitleFg
    horizontalAlignment = Alignment.Center
  centerAlign(moveLabel)

  // --- Cached fonts and colors for refresh() ---
  private val statusFontAlert  = new Font("SansSerif", Font.BOLD, 17)
  private val statusFontNormal = new Font("SansSerif", Font.BOLD, 16)
  private val colorCheck       = new AwtColor(255, 180, 70)
  private val colorCheckmate   = new AwtColor(235, 97, 80)
  private val colorDraw        = new AwtColor(180, 180, 100)
  private val colorStatus      = titleFg

  // --- Buttons ---
  private val btnNormal = new AwtColor(64, 62, 59)
  private val btnHover  = new AwtColor(92, 90, 87)
  private val btnPress  = new AwtColor(50, 48, 46)

  private def styledButton(text: String, onClick: () => Unit): Button =
    val btn = new Button(text)
    btn.font = new Font("SansSerif", Font.BOLD, 13)
    btn.foreground = new AwtColor(230, 230, 230)
    btn.background = btnNormal
    btn.opaque = true
    btn.borderPainted = true
    btn.focusPainted = false
    btn.cursor = new Cursor(Cursor.HAND_CURSOR)
    btn.border = BorderFactory.createLineBorder(cardBorder, 1)
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

  private val newGameButton = styledButton("Neues Spiel", () => {
    onNewGame()
  })
  private val quitButton = styledButton("Beenden", onQuit)

  // --- PGN Save/Load ---
  private val pgnSaveLabel = new Label("Spiel speichern"):
    font = new Font("SansSerif", Font.BOLD, 13)
    foreground = subtitleFg
  centerAlign(pgnSaveLabel)

  private val savePgnButton = styledButton("PGN speichern", () => savePgnFile())
  private val loadPgnButton = styledButton("PGN laden", () => loadPgnFile())
  private val saveJsonButton = styledButton("JSON speichern", () => saveJsonFile())
  private val loadJsonButton = styledButton("JSON laden", () => loadJsonFile())

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
        // Use the latest game state for full history
        controller.browseToEnd()
        val pgn = Pgn.toPgn(controller.game)
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

  private def saveJsonFile(): Unit =
    val chooser = new javax.swing.JFileChooser()
    chooser.setDialogTitle("JSON speichern")
    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON-Dateien", "json"))
    chooser.setSelectedFile(new java.io.File("game.json"))
    val result = chooser.showSaveDialog(peer)
    if result == javax.swing.JFileChooser.APPROVE_OPTION then
      val file = chooser.getSelectedFile
      val path = if file.getName.endsWith(".json") then file else new java.io.File(file.getAbsolutePath + ".json")
      scala.util.Try {
        val json = controller.exportCurrentGameAsJson
        val writer = new java.io.PrintWriter(path)
        try writer.write(json) finally writer.close()
      } match
        case scala.util.Failure(ex) => showError(s"Fehler beim Speichern: ${ex.getMessage}")
        case _ => ()

  private def loadJsonFile(): Unit =
    val chooser = new javax.swing.JFileChooser()
    chooser.setDialogTitle("JSON laden")
    chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JSON-Dateien", "json"))
    val result = chooser.showOpenDialog(peer)
    if result == javax.swing.JFileChooser.APPROVE_OPTION then
      scala.util.Try {
        val source = scala.io.Source.fromFile(chooser.getSelectedFile)
        try source.mkString finally source.close()
      } match
        case scala.util.Success(content) =>
          controller.importGameFromJson(content) match
            case Left(err) => showError(err.message)
            case Right(_)  => ()
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

  // --- Werkzeuge dialog (lazy, created on first click) ---
  private lazy val toolsDialog: javax.swing.JDialog = buildToolsDialog()

  private def buildToolsDialog(): javax.swing.JDialog =
    val parentWindow = Option(javax.swing.SwingUtilities.getWindowAncestor(peer))
    val dialog = parentWindow match
      case Some(w) => new javax.swing.JDialog(w, "Werkzeuge")
      case None    => new javax.swing.JDialog(null: java.awt.Frame, "Werkzeuge")
    dialog.getContentPane.setBackground(bgMain)

    val header = new BoxPanel(Orientation.Vertical):
      background = bgMain
      border = new EmptyBorder(12, 14, 8, 14)
      val title = new Label("Werkzeuge"):
        font = new Font("SansSerif", Font.BOLD, 26)
        foreground = titleFg
      val subtitle = new Label("Import, Export und Stellungspflege"):
        font = new Font("SansSerif", Font.PLAIN, 13)
        foreground = subtitleFg
      title.xLayoutAlignment = 0.0
      subtitle.xLayoutAlignment = 0.0
      contents += title
      contents += Swing.VStrut(2)
      contents += subtitle

    val setupTab = tabContent(
      sectionCard("FEN", fenInputLabel, fenInputField, fillFenButton, loadFenButton),
      sectionCard("Teststellungen", testPositionLabel, positionCombo, positionDescLabel, loadPositionButton)
    )

    val importTab = tabContent(
      sectionCard("PGN anwenden", pgnInputLabel, pgnHintLabel, pgnScrollPane, applyPgnButton),
      sectionCard("Import", loadPgnButton, loadJsonButton)
    )

    val exportTab = tabContent(
      sectionCard("Export", pgnSaveLabel, savePgnButton, saveJsonButton)
    )

    val tabs = new JTabbedPane()
    tabs.setBackground(bgMain)
    tabs.setForeground(titleFg)
    tabs.setFont(new Font("SansSerif", Font.BOLD, 12))
    tabs.setFocusable(false)
    tabs.addTab("Stellung", setupTab.peer)
    tabs.addTab("Import", importTab.peer)
    tabs.addTab("Export", exportTab.peer)
    tabs.setSelectedIndex(0)

    val tabsWrapper = Component.wrap(tabs)
    tabsWrapper.preferredSize = new Dimension(360, 560)

    val root = new BorderPanel:
      background = bgMain
      add(header, BorderPanel.Position.North)
      add(tabsWrapper, BorderPanel.Position.Center)

    dialog.add(root.peer)
    dialog.pack()
    dialog.setLocationRelativeTo(peer)
    dialog

  private def toggleToolsDialog(): Unit =
    toolsDialog.setVisible(!toolsDialog.isVisible)

  /** Public entry point so the NavBar's tools icon can open the dialog. */
  def openToolsDialog(): Unit =
    toolsDialog.setVisible(true)

  // --- Action row: compact buttons (Neues Spiel | Beenden) ---
  private val actionRow = new Panel:
    background = new AwtColor(38, 36, 33)
    peer.setLayout(new java.awt.GridLayout(1, 2, 4, 0))
    peer.add(newGameButton.peer)
    peer.add(quitButton.peer)
  actionRow.preferredSize = new Dimension(contentWidth, 32)
  actionRow.maximumSize = new Dimension(Short.MaxValue, 32)
  centerAlign(actionRow)

  // Compact layout – no scrolling needed
  contents += Swing.VStrut(4)
  contents += playerLabel
  contents += Swing.VStrut(2)
  contents += statusLabel
  contents += Swing.VStrut(2)
  contents += moveLabel
  contents += Swing.VStrut(sectionGap)
  contents += styledSeparator()
  contents += Swing.VStrut(smallGap)
  contents += actionRow
  contents += Swing.VStrut(4)

  def refresh(): Unit =
    val game = controller.game
    val isWhite = game.currentPlayer == chess.model.Color.White

    // Update action row for replay mode
    if controller.isInReplay then
      newGameButton.text = "Zurück zum Verlauf"
    else
      newGameButton.text = "Neues Spiel"

    playerLabel.text = if isWhite then "⬜ Weiß" else "⬛ Schwarz"

    statusLabel.text =
      if controller.isInReplay then "Replay"
      else game.status match
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
