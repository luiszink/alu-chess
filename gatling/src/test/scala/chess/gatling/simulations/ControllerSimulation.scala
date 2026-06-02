package chess.gatling.simulations

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import chess.gatling.config.GatlingConfig.*
import chess.gatling.scenarios.ControllerScenarios.*
import scala.concurrent.duration.*

class ControllerSimulation extends Simulation:

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  setUp(
    controllerScenario.inject(
      constantUsersPerSec(smokeVus.toDouble).during(smokeDurSec.seconds),
      nothingFor((loadStartSec - smokeDurSec).seconds),
      atOnceUsers(loadVus),
    ).protocols(httpProtocol)
  ).assertions(
    global.failedRequests.percent.lt(1),
    global.responseTime.percentile(95).lt(loadP95Ms),
  )
