package chess.model.dao

import scala.concurrent.Future

trait GameDao:
  def init(): Future[Unit]
  def insert(row: GameRow): Future[Unit]
  def findAll(): Future[Vector[GameRow]]
  def findById(id: String): Future[Option[GameRow]]
  def delete(id: String): Future[Unit]
  def clear(): Future[Unit]
