package chess.model

trait GameRepository:
  def save(record: GameRecord): Unit
  def findAll(): Vector[GameRecord]
  def findById(id: String): Option[GameRecord]
  def delete(id: String): Unit
  def clear(): Unit
