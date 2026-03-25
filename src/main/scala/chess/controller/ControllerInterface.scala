package chess.controller

import chess.model.{Game, Move, ChessError}
import chess.util.Observer

trait ControllerInterface:
  def game: Game
  def newGame(): Unit
  def doMove(move: Move): Boolean
  def doMoveResult(move: Move): Either[ChessError, Game]
  def loadFen(fen: String): Boolean
  def loadFenResult(fen: String): Either[ChessError, Game]
  def quit(): Unit
  def boardToString: String
  def statusText: String
  def add(observer: Observer): Unit
  def remove(observer: Observer): Unit
