package chess.gatling.scenarios

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import chess.gatling.config.GatlingConfig.*
import scala.concurrent.duration.*

object ControllerScenarios:

  val controllerScenario = scenario("Controller Service")
    .exec(
      http("new-game")
        .post("/api/controller/new-game")
        .check(status.is(200))
        .check(jsonPath("$.game.fen").exists)
    )
    .exec(
      http("state")
        .get("/api/controller/state")
        .check(status.is(200))
        .check(jsonPath("$.game.fen").exists)
    )
    .exec(
      http("load-fen")
        .post("/api/controller/load-fen")
        .body(StringBody(s"""{"fen":"$startingFen"}""")).asJson
        .check(status.is(200))
    )
    .exec(
      // 409 = Spiel bereits beendet, 422 = illegaler Zug
      http("move")
        .post("/api/controller/move")
        .body(StringBody("""{"from":"e2","to":"e4"}""")).asJson
        .check(status.in(200, 409, 422))
    )
    .exec(
      http("move-history")
        .get("/api/controller/move-history")
        .check(status.is(200))
        .check(jsonPath("$.moves").exists)
    )
    .exec(
      http("browse/back")
        .post("/api/controller/browse/back")
        .check(status.is(200))
    )
    .exec(
      http("browse/forward")
        .post("/api/controller/browse/forward")
        .check(status.is(200))
    )
    .exec(
      http("browse/to-start")
        .post("/api/controller/browse/to-start")
        .check(status.is(200))
    )
    .exec(
      http("browse/to-end")
        .post("/api/controller/browse/to-end")
        .check(status.is(200))
    )
    .exec(
      http("browse/to-move")
        .post("/api/controller/browse/to-move")
        .body(StringBody("""{"index":0}""")).asJson
        .check(status.is(200))
    )
    .exec(
      http("games")
        .get("/api/controller/games")
        .check(status.is(200))
        .check(jsonPath("$.games").exists)
    )
    .exec(
      http("export")
        .get("/api/controller/export")
        .check(status.is(200))
    )
    .exec(
      http("resign")
        .post("/api/controller/resign")
        .check(status.is(200))
    )
    .exec(
      http("replay/exit")
        .post("/api/controller/replay/exit")
        .check(status.is(200))
    )
    .pause(500.milliseconds)
