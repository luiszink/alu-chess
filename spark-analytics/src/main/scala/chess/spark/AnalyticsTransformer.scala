package chess.spark

import io.circe.{HCursor, Json}
import io.circe.parser.parse

object AnalyticsTransformer {

  def commandFromJson(value: String): Either[String, AnalyticsCommand] =
    parse(value)
      .left
      .map(error => s"Invalid event JSON: ${error.message}")
      .flatMap(commandFromEventJson)

  private def commandFromEventJson(json: Json): Either[String, AnalyticsCommand] = {
    val cursor = json.hcursor
    cursor.get[String]("eventType").left.map(_ => "Missing eventType").flatMap {
      case GamePersistenceEventType.UpsertRequested =>
        cursor.downField("record").focus match {
          case Some(record) if !record.isNull =>
            documentFromRecord(record).map(AnalyticsCommand.Upsert.apply)
          case _ =>
            Left("Upsert event without record payload")
        }

      case GamePersistenceEventType.DeleteRequested =>
        cursor.get[String]("recordId").left.map(_ => "Delete event without recordId").map(AnalyticsCommand.Delete.apply)

      case GamePersistenceEventType.ClearRequested =>
        Right(AnalyticsCommand.Clear)

      case other =>
        Left(s"Unsupported eventType '$other'")
    }
  }

  private def documentFromRecord(record: Json): Either[String, GameAnalyticsDocument] = {
    val cursor = record.hcursor
    for {
      id         <- requiredString(cursor, "id")
      datePlayed <- requiredString(cursor, "datePlayed")
      result     <- requiredString(cursor, "result")
      moveCount  <- cursor.get[Int]("moveCount").left.map(_ => "Missing or invalid moveCount")
      pgn        <- requiredString(cursor, "pgn")
      finalFen   <- finalFen(cursor)
    } yield GameAnalyticsDocument(
      recordId = id,
      datePlayed = datePlayed,
      result = result,
      moveCount = moveCount,
      timeControlName = optionalTimeControlString(cursor, "name"),
      initialTimeMs = optionalTimeControlLong(cursor, "initialTimeMs"),
      incrementMs = optionalTimeControlLong(cursor, "incrementMs"),
      finalFen = finalFen,
      pgn = pgn
    )
  }

  private def requiredString(cursor: HCursor, field: String): Either[String, String] =
    cursor.get[String](field).left.map(_ => s"Missing or invalid $field")

  private def optionalTimeControlString(cursor: HCursor, field: String): Option[String] =
    cursor.downField("timeControl").get[String](field).toOption

  private def optionalTimeControlLong(cursor: HCursor, field: String): Option[Long] =
    cursor.downField("timeControl").get[Long](field).toOption

  private def finalFen(cursor: HCursor): Either[String, String] =
    cursor.downField("moves").focus.flatMap(_.asArray) match {
      case Some(moves) if moves.nonEmpty =>
        moves.last.hcursor.get[String]("fen").left.map(_ => "Last move entry has no fen")
      case _ =>
        Left("Record moves array is missing or empty")
    }
}

