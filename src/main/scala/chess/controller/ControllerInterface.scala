package chess.controller

import chess.model.{Game, Move, ChessError, MoveEntry, ChessClock, TimeControl}
import chess.util.Observer

trait ControllerInterface:
  def game: Game
  def newGame(): Unit
  def newGameWithClock(timeControl: Option[TimeControl]): Unit
  def doMove(move: Move): Boolean
  def doMoveResult(move: Move): Either[ChessError, Game]
  def loadFen(fen: String): Boolean
  def loadFenResult(fen: String): Either[ChessError, Game]
  def quit(): Unit
  def boardToString: String
  def statusText: String
  def add(observer: Observer): Unit
  def remove(observer: Observer): Unit
  // History navigation
  def browseBack(): Unit
  def browseForward(): Unit
  def browseToStart(): Unit
  def browseToEnd(): Unit
  def browseToMove(index: Int): Unit
  def isAtLatest: Boolean
  def browseIndex: Int
  def gameStatesCount: Int
  // Clock
  def clock: Option[ChessClock]
  def tickClock(): Unit
