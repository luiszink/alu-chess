package chess.model.dao

import cats.effect.IO

trait GameDao:
  def init(): IO[Unit]
  def insert(row: GameRow): IO[Unit]
  def findAll(): IO[Vector[GameRow]]
  def findById(id: String): IO[Option[GameRow]]
  def delete(id: String): IO[Unit]
  def clear(): IO[Unit]
