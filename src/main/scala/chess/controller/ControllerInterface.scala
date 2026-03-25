package chess.controller

import chess.model.{Game, Move}
import chess.util.Observer

trait ControllerInterface:
  def game: Game
  def newGame(): Unit
  def doMove(move: Move): Boolean
  def loadFen(fen: String): Boolean
  def quit(): Unit
  def boardToString: String
  def statusText: String
  def add(observer: Observer): Unit
  def remove(observer: Observer): Unit
