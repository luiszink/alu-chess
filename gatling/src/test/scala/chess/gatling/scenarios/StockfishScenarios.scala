package chess.gatling.scenarios

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import chess.gatling.config.GatlingConfig.*
import scala.concurrent.duration.*

object StockfishScenarios:

  val stockfishScenario = scenario("Stockfish via Model Service")
    .exec(
      http("stockfish/health")
        .get("/api/model/stockfish/health")
        .check(status.is(200))
    )
    .exec(
      http("stockfish/best-move")
        .post("/api/model/stockfish/best-move")
        .body(StringBody(s"""{"fen":"$startingFen","thinkTimeMs":50}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.move").exists)
    )
    .exec(
      http("stockfish/evaluate")
        .post("/api/model/stockfish/evaluate")
        .body(StringBody(s"""{"fen":"$startingFen","thinkTimeMs":50}""")).asJson
        .check(status.is(200))
    )
    .pause(1.second)
