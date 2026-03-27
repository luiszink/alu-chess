package chess.controller

import chess.model.{Game, Move, GameStatus, Fen, ChessError, ChessClock, TimeControl, MoveEntry}
import chess.util.{Observable, Observer}

class Controller extends ControllerInterface with Observable:
  private var _game: Game = Game.newGame
  private var _gameStates: Vector[Game] = Vector(Game.newGame)
  private var _browseIdx: Int = 0
  private var _clock: Option[ChessClock] = None

  override def game: Game = _gameStates(_browseIdx)

  private def latestGame: Game = _gameStates.last

  override def newGame(): Unit =
    _game = Game.newGame
    _gameStates = Vector(Game.newGame)
    _browseIdx = 0
    _clock = None
    notifyObservers()

  override def newGameWithClock(timeControl: Option[TimeControl]): Unit =
    _game = Game.newGame
    _gameStates = Vector(Game.newGame)
    _browseIdx = 0
    _clock = timeControl.map(tc => ChessClock.fromTimeControl(tc).start(System.nanoTime()))
    notifyObservers()

  private def doMoveE(move: Move): Either[ChessError, Game] =
    Either.cond(!latestGame.status.isTerminal, (), ChessError.GameAlreadyOver(latestGame.status))
      .flatMap(_ => latestGame.applyMoveE(move))

  override def doMove(move: Move): Boolean =
    if !isAtLatest then return false
    doMoveE(move) match
      case Right(updated) =>
        _game = updated
        _gameStates = _gameStates :+ updated
        _browseIdx = _gameStates.size - 1
        _clock = _clock.map(_.press(System.nanoTime()))
        if updated.status.isTerminal then _clock = _clock.map(_.stop)
        notifyObservers()
        true
      case Left(_) => false

  override def doMoveResult(move: Move): Either[ChessError, Game] =
    if !isAtLatest then return Left(ChessError.GameAlreadyOver(latestGame.status))
    doMoveE(move) match
      case Right(updated) =>
        _game = updated
        _gameStates = _gameStates :+ updated
        _browseIdx = _gameStates.size - 1
        _clock = _clock.map(_.press(System.nanoTime()))
        if updated.status.isTerminal then _clock = _clock.map(_.stop)
        notifyObservers()
        Right(_game)
      case Left(err) => Left(err)

  private def loadFenE(fen: String): Either[ChessError, Game] =
    Fen.parseE(fen)

  override def loadFen(fen: String): Boolean =
    loadFenE(fen) match
      case Right(game) =>
        _game = game
        _gameStates = Vector(game)
        _browseIdx = 0
        _clock = None
        notifyObservers()
        true
      case Left(_) => false

  override def loadFenResult(fen: String): Either[ChessError, Game] =
    loadFenE(fen) match
      case Right(game) =>
        _game = game
        _gameStates = Vector(game)
        _browseIdx = 0
        _clock = None
        notifyObservers()
        Right(_game)
      case Left(err) => Left(err)

  override def quit(): Unit =
    sys.exit(0)

  override def boardToString: String =
    game.board.toString

  override def statusText: String =
    val g = game
    g.status match
      case GameStatus.Playing    => s"${g.currentPlayer} am Zug"
      case GameStatus.Check      => s"${g.currentPlayer} am Zug – Schach!"
      case GameStatus.Checkmate  => s"Schachmatt! ${g.currentPlayer.opposite} gewinnt"
      case GameStatus.Stalemate  => s"Patt – Unentschieden"
      case GameStatus.Resigned   => s"Aufgegeben"
      case GameStatus.Draw       => s"Remis – Unentschieden"
      case GameStatus.TimeOut    => s"Zeit abgelaufen! ${g.currentPlayer.opposite} gewinnt"

  // --- History Navigation ---

  override def browseBack(): Unit =
    if _browseIdx > 0 then
      _browseIdx -= 1
      notifyObservers()

  override def browseForward(): Unit =
    if _browseIdx < _gameStates.size - 1 then
      _browseIdx += 1
      notifyObservers()

  override def browseToStart(): Unit =
    if _browseIdx != 0 then
      _browseIdx = 0
      notifyObservers()

  override def browseToEnd(): Unit =
    val last = _gameStates.size - 1
    if _browseIdx != last then
      _browseIdx = last
      notifyObservers()

  override def browseToMove(index: Int): Unit =
    val clamped = math.max(0, math.min(index, _gameStates.size - 1))
    if _browseIdx != clamped then
      _browseIdx = clamped
      notifyObservers()

  override def isAtLatest: Boolean = _browseIdx == _gameStates.size - 1
  override def browseIndex: Int = _browseIdx
  override def gameStatesCount: Int = _gameStates.size

  // --- Clock ---

  override def clock: Option[ChessClock] =
    _clock.map(c => if c.activeColor.isDefined then c.tick(System.nanoTime()) else c)

  override def tickClock(): Unit =
    _clock match
      case Some(c) if c.activeColor.isDefined && !latestGame.status.isTerminal =>
        val ticked = c.tick(System.nanoTime())
        _clock = Some(ticked)
        ticked.expired match
          case Some(_) =>
            _clock = Some(ticked.stop)
            _game = latestGame.copy(status = GameStatus.TimeOut)
            _gameStates = _gameStates.init :+ _game
            _browseIdx = _gameStates.size - 1
            notifyObservers()
          case None => // GUI refreshes clock display separately
      case _ => ()
