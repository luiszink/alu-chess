package chess.model

class InMemoryGameRepository extends GameRepository:
  private var records: Vector[GameRecord] = Vector.empty

  override def save(record: GameRecord): Unit =
    records = record +: records

  override def findAll(): Vector[GameRecord] = records

  override def findById(id: String): Option[GameRecord] =
    records.find(_.id == id)

  override def delete(id: String): Unit =
    records = records.filterNot(_.id == id)

  override def clear(): Unit =
    records = Vector.empty
