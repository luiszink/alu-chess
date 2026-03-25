package chess.controller

import chess.model.{Game, Move, GameStatus, Fen, ChessError}
import chess.util.{Observable, Observer}

class Controller extends ControllerInterface with Observable:
  private var _game: Game = Game.newGame

  override def game: Game = _game

  override def newGame(): Unit =
    _game = Game.newGame
    notifyObservers()

  private def doMoveE(move: Move): Either[ChessError, Game] =
    Either.cond(!_game.status.isTerminal, (), ChessError.GameAlreadyOver(_game.status))
      .flatMap(_ => _game.applyMoveE(move))

  override def doMove(move: Move): Boolean =
    doMoveE(move) match
      case Right(updated) =>
        _game = updated
        notifyObservers()
        true
      case Left(_) => false

  override def doMoveResult(move: Move): Either[ChessError, Game] =
    doMoveE(move) match
      case Right(updated) =>
        _game = updated
        notifyObservers()
        Right(_game)
      case Left(err) => Left(err)

  private def loadFenE(fen: String): Either[ChessError, Game] =
    Fen.parseE(fen)

  override def loadFen(fen: String): Boolean =
    loadFenE(fen) match
      case Right(game) =>
        _game = game
        notifyObservers()
        true
      case Left(_) => false

  override def loadFenResult(fen: String): Either[ChessError, Game] =
    loadFenE(fen) match
      case Right(game) =>
        _game = game
        notifyObservers()
        Right(_game)
      case Left(err) => Left(err)

  override def quit(): Unit =
    sys.exit(0)

  override def boardToString: String =
    _game.board.toString

  override def statusText: String =
    _game.status match
      case GameStatus.Playing    => s"${_game.currentPlayer} am Zug"
      case GameStatus.Check      => s"${_game.currentPlayer} am Zug – Schach!"
      case GameStatus.Checkmate  => s"Schachmatt! ${_game.currentPlayer.opposite} gewinnt"
      case GameStatus.Stalemate  => s"Patt – Unentschieden"
      case GameStatus.Resigned   => s"Aufgegeben"
      case GameStatus.Draw       => s"Remis – Unentschieden"
