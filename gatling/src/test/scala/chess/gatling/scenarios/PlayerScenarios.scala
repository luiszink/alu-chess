package chess.gatling.scenarios

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import scala.concurrent.duration.*

object PlayerScenarios:

  val playerScenario = scenario("Player Service")
    // ── Waiting Sessions (vor Registrierung) ─────────────
    .exec(
      http("sessions/waiting")
        .get("/api/player/sessions/waiting")
        .check(status.is(200))
        .check(jsonPath("$.sessions").exists)
    )
    // ── HvAI Flow ────────────────────────────────────────
    .exec(session =>
      session.set("hvaiName", s"HvAI_${session.userId}_${System.currentTimeMillis()}")
    )
    .exec(
      http("register-hvai")
        .post("/api/player/register")
        .body(StringBody("""{"name":"#{hvaiName}"}""")).asJson
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("hvaiPlayerId"))
    )
    .exec(
      http("player-status")
        .get("/api/player/#{hvaiPlayerId}/status")
        .check(status.is(200))
        .check(jsonPath("$.player.id").is("#{hvaiPlayerId}"))
    )
    .exec(
      http("session/hvai")
        .post("/api/player/session/hvai")
        .body(StringBody("""{"playerId":"#{hvaiPlayerId}"}""")).asJson
        .check(status.in(201, 409))
        .checkIf((response, _) => response.status.code == 201)(
          jsonPath("$.id").saveAs("hvaiSessionId")
        )
    )
    .doIf(session => session.contains("hvaiSessionId"))(
      exec(
        http("get-session")
          .get("/api/player/session/#{hvaiSessionId}")
          .check(status.is(200))
          .check(jsonPath("$.id").is("#{hvaiSessionId}"))
      )
    )
    // ── HvH Flow ─────────────────────────────────────────
    .exec(session =>
      val ts = System.currentTimeMillis()
      session
        .set("hvhNameA", s"HvH_A_${session.userId}_$ts")
        .set("hvhNameB", s"HvH_B_${session.userId}_$ts")
    )
    .exec(
      http("register-hvh-a")
        .post("/api/player/register")
        .body(StringBody("""{"name":"#{hvhNameA}"}""")).asJson
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("hvhPlayerA"))
    )
    .exec(
      http("register-hvh-b")
        .post("/api/player/register")
        .body(StringBody("""{"name":"#{hvhNameB}"}""")).asJson
        .check(status.is(201))
        .check(jsonPath("$.id").saveAs("hvhPlayerB"))
    )
    .exec(
      http("session/hvh")
        .post("/api/player/session/hvh")
        .body(StringBody("""{"playerId":"#{hvhPlayerA}"}""")).asJson
        .check(status.in(201, 409))
        .checkIf((response, _) => response.status.code == 201)(
          jsonPath("$.id").saveAs("hvhSessionId")
        )
    )
    .doIf(session => session.contains("hvhSessionId"))(
      exec(
        http("session/hvh/join")
          .post("/api/player/session/#{hvhSessionId}/join")
          .body(StringBody("""{"playerId":"#{hvhPlayerB}"}""")).asJson
          .check(status.in(200, 409))
      )
    )
    .pause(500.milliseconds)
