package chess.spark

import com.mongodb.client.{MongoClient, MongoClients, MongoCollection}
import com.mongodb.client.model.{Filters, Indexes, ReplaceOptions}
import org.bson.Document

import scala.jdk.CollectionConverters._

final class MongoAnalyticsRepository(client: MongoClient, dbName: String) {
  private val db = client.getDatabase(dbName)
  private val analytics: MongoCollection[Document] = db.getCollection(MongoAnalyticsRepository.AnalyticsCollection)
  private val summary: MongoCollection[Document] = db.getCollection("game_analytics_summary")

  analytics.createIndex(Indexes.ascending("recordId"))

  def upsert(document: GameAnalyticsDocument): Unit =
    analytics.replaceOne(
      Filters.eq("recordId", document.recordId),
      toBson(document),
      new ReplaceOptions().upsert(true)
    )

  def delete(recordId: String): Unit =
    analytics.deleteOne(Filters.eq("recordId", recordId))

  def clear(): Unit = {
    analytics.deleteMany(new Document())
    summary.deleteMany(new Document())
  }

  def replaceSummary(value: GameAnalyticsSummary): Unit =
    summary.replaceOne(
      Filters.eq("_id", value.id),
      toBson(value),
      new ReplaceOptions().upsert(true)
    )

  private def toBson(value: GameAnalyticsDocument): Document = {
    val document = new Document()
      .append("recordId", value.recordId)
      .append("datePlayed", value.datePlayed)
      .append("result", value.result)
      .append("moveCount", value.moveCount)
      .append("finalFen", value.finalFen)
      .append("pgn", value.pgn)

    value.timeControlName.foreach(document.append("timeControlName", _))
    value.initialTimeMs.foreach(v => document.append("initialTimeMs", java.lang.Long.valueOf(v)))
    value.incrementMs.foreach(v => document.append("incrementMs", java.lang.Long.valueOf(v)))
    document
  }

  private def toBson(value: GameAnalyticsSummary): Document =
    new Document()
      .append("_id", value.id)
      .append("generatedAt", value.generatedAt)
      .append("totalGames", java.lang.Long.valueOf(value.totalGames))
      .append("averageMoveCount", java.lang.Double.valueOf(value.averageMoveCount))
      .append("resultCounts", new Document(value.resultCounts.view.mapValues(java.lang.Long.valueOf).toMap.asJava))
      .append("timeControlCounts", new Document(value.timeControlCounts.view.mapValues(java.lang.Long.valueOf).toMap.asJava))
}

object MongoAnalyticsRepository {
  val AnalyticsCollection = "game_analytics"

  def withClient[A](uri: String, dbName: String)(f: MongoAnalyticsRepository => A): A = {
    val client = MongoClients.create(uri)
    try f(new MongoAnalyticsRepository(client, dbName))
    finally client.close()
  }
}

