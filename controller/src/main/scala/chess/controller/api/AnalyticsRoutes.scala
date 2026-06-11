package chess.controller.api

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import io.circe.*
import chess.model.dao.{GameAnalyticsSummaryRow, MongoAnalyticsSummaryDao}

/** Exposes the analytics that the Spark service computes into MongoDB.
  *
  * The route degrades gracefully: while Spark has not produced a summary yet, or
  * if Mongo is unreachable, it returns an empty summary with `available = false`
  * instead of failing, so the web UI can render an explicit empty state. */
object AnalyticsRoutes:

  private def countsJson(counts: Map[String, Long]): Json =
    Json.obj(counts.toSeq.sortBy(-_._2).map { case (k, v) => k -> Json.fromLong(v) }*)

  private def summaryJson(row: GameAnalyticsSummaryRow): Json = Json.obj(
    "available"         -> Json.fromBoolean(true),
    "generatedAt"       -> Json.fromString(row.generatedAt),
    "totalGames"        -> Json.fromLong(row.totalGames),
    "averageMoveCount"  -> Json.fromDoubleOrNull(row.averageMoveCount),
    "resultCounts"      -> countsJson(row.resultCounts),
    "timeControlCounts" -> countsJson(row.timeControlCounts),
  )

  private val emptyJson: Json = Json.obj(
    "available"         -> Json.fromBoolean(false),
    "generatedAt"       -> Json.Null,
    "totalGames"        -> Json.fromLong(0L),
    "averageMoveCount"  -> Json.fromDoubleOrNull(0.0),
    "resultCounts"      -> Json.obj(),
    "timeControlCounts" -> Json.obj(),
  )

  def apply(dao: MongoAnalyticsSummaryDao): HttpRoutes[IO] = HttpRoutes.of[IO] {

    // GET /api/controller/analytics/summary
    case GET -> Root / "api" / "controller" / "analytics" / "summary" =>
      dao.currentSummary.attempt.flatMap {
        case Right(Some(row)) => Ok(summaryJson(row))
        case Right(None)      => Ok(emptyJson)
        case Left(_)          => Ok(emptyJson)
      }
  }
