package chess.model.dao

import cats.effect.{IO, Resource}
import io.circe.Codec
import mongo4cats.client.MongoClient
import mongo4cats.collection.MongoCollection
import mongo4cats.operations.Filter
import mongo4cats.circe.given

/** Read model for the analytics summary that the Spark service writes into the
  * `game_analytics_summary` collection (single document with `_id = "current"`).
  * The `_id` field is intentionally omitted – the circe-derived codec ignores it
  * on decode, exactly as [[GameRow]] ignores Mongo's generated `_id`. */
final case class GameAnalyticsSummaryRow(
  generatedAt:       String,
  totalGames:        Long,
  averageMoveCount:  Double,
  resultCounts:      Map[String, Long],
  timeControlCounts: Map[String, Long],
) derives Codec.AsObject

/** Read-only accessor for the Spark-produced analytics summary. Deliberately
  * independent of `DB_TYPE`: Spark always persists analytics to MongoDB, so the
  * summary is read from Mongo regardless of the primary game store. */
class MongoAnalyticsSummaryDao(collection: MongoCollection[IO, GameAnalyticsSummaryRow]):

  /** Returns the current global summary, or `None` if Spark has not produced one yet. */
  def currentSummary: IO[Option[GameAnalyticsSummaryRow]] =
    collection.find(Filter.eq("_id", "current")).first

object MongoAnalyticsSummaryDao:
  val SummaryId = "current"

  /** Lazily opens the Mongo connection. Acquisition performs no network I/O, so
    * the controller still starts when Mongo is unavailable; only reads fail and
    * are handled gracefully by the route. */
  def resource(uri: String, dbName: String): Resource[IO, MongoAnalyticsSummaryDao] =
    MongoClient.fromConnectionString[IO](uri).evalMap { client =>
      for
        db         <- client.getDatabase(dbName)
        collection <- db.getCollectionWithCodec[GameAnalyticsSummaryRow]("game_analytics_summary")
      yield new MongoAnalyticsSummaryDao(collection)
    }
