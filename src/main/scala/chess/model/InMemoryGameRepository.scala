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

  override def exportRecordAsJson(id: String): Either[ChessError, String] =
    findById(id)
      .map(record => Right(GameJson.toRecordJsonString(record)))
      .getOrElse(Left(ChessError.InvalidFenFormat(s"No game record with id '$id'")))

  override def importRecordFromJson(json: String): Either[ChessError, GameRecord] =
    GameJson.fromRecordJsonString(json).map { record =>
      records = record +: records.filterNot(_.id == record.id)
      record
    }
