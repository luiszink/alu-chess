package chess.gatling.simulations

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import chess.gatling.config.GatlingConfig.*
import chess.gatling.scenarios.StockfishScenarios.*
import scala.concurrent.duration.*

class StockfishSimulation extends Simulation:

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  setUp(
    stockfishScenario.inject(
      constantUsersPerSec(smokeVus.toDouble).during(smokeDurSec.seconds),
      nothingFor((loadStartSec - smokeDurSec).seconds),
      atOnceUsers(sfLoadVus),
    ).protocols(httpProtocol)
  ).assertions(
    global.failedRequests.percent.lt(1),
    global.responseTime.percentile(95).lt(sfLoadP95Ms),
  )
