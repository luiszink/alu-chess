package chess.gatling.scenarios

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import chess.gatling.config.GatlingConfig.*
import scala.concurrent.duration.*

object ModelScenarios:

  val modelScenario = scenario("Model Service")
    .exec(
      http("new-game")
        .get("/api/model/new-game")
        .check(status.is(200))
        .check(jsonPath("$.fen").exists)
    )
    .exec(
      http("parse-fen")
        .post("/api/model/parse-fen")
        .body(StringBody(s"""{"fen":"$startingFen"}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.fen").exists)
    )
    .exec(
      http("legal-moves")
        .post("/api/model/legal-moves")
        .body(StringBody(s"""{"fen":"$startingFen"}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.moves[0]").exists)
    )
    .exec(
      http("legal-moves-for-square")
        .post("/api/model/legal-moves-for-square")
        .body(StringBody(s"""{"fen":"$startingFen","square":"e2"}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.square").is("e2"))
    )
    .exec(
      http("validate-move")
        .post("/api/model/validate-move")
        .body(StringBody(s"""{"fen":"$startingFen","from":"e2","to":"e4"}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.fen").exists)
    )
    .exec(
      http("to-fen")
        .post("/api/model/to-fen")
        .body(StringBody(s"""{"fen":"$startingFen"}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.fen").exists)
    )
    .exec(
      http("parse-pgn")
        .post("/api/model/parse-pgn")
        .body(StringBody("""{"pgn":"1. e4 e5 2. Nf3 Nc6 3. Bb5"}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.fen").exists)
    )
    .exec(
      http("to-pgn")
        .post("/api/model/to-pgn")
        .body(StringBody(s"""{"fen":"$startingFen"}""")).asJson
        .check(status.is(200))
        .check(jsonPath("$.pgn").exists)
    )
    .exec(
      http("test-positions")
        .get("/api/model/test-positions")
        .check(status.is(200))
        .check(jsonPath("$.positions[0]").exists)
    )
    .pause(500.milliseconds)
