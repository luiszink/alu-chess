package chess.controller

import chess.model.{Game, Board, Color, Move, GameStatus, Fen}
import chess.util.{Observable, Observer}

class Controller extends ControllerInterface with Observable:
  private var _game: Game = Game.newGame

  override def game: Game = _game

  override def newGame(): Unit =
    _game = Game.newGame
    notifyObservers()

  override def doMove(move: Move): Boolean =
    _game.applyMove(move) match
      case Some(updated) =>
        _game = updated
        notifyObservers()
        true
      case None => false

  override def loadFen(fen: String): Boolean =
    Fen.parse(fen) match
      case Some(game) =>
        _game = game
        notifyObservers()
        true
      case None => false

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
