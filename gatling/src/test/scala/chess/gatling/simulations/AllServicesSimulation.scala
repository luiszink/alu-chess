package chess.gatling.simulations

import io.gatling.core.Predef.*
import io.gatling.http.Predef.*
import chess.gatling.config.GatlingConfig.*
import chess.gatling.scenarios.ModelScenarios.*
import chess.gatling.scenarios.ControllerScenarios.*
import chess.gatling.scenarios.PlayerScenarios.*
import chess.gatling.scenarios.StockfishScenarios.*
import scala.concurrent.duration.*

// Kombiniert alle Dienste — spiegelt k6/scripts/all.js
// Jedes Szenario nutzt einen einzigen inject()-Aufruf (Gatling verlangt eindeutige Szenario-Namen).
class AllServicesSimulation extends Simulation:

  val httpProtocol = http
    .baseUrl(baseUrl)
    .acceptHeader("application/json")
    .contentTypeHeader("application/json")

  // Model: smoke (0–10s) + load (12s)
  val modelPop = modelScenario.inject(
    constantUsersPerSec(1.0).during(10.seconds),
    nothingFor(2.seconds),
    atOnceUsers(10),
  ).protocols(httpProtocol)

  // Player: smoke + load
  val playerPop = playerScenario.inject(
    constantUsersPerSec(1.0).during(10.seconds),
    nothingFor(2.seconds),
    atOnceUsers(10),
  ).protocols(httpProtocol)

  // Stockfish: smoke + load (5 VUs)
  val sfPop = stockfishScenario.inject(
    constantUsersPerSec(1.0).during(10.seconds),
    nothingFor(2.seconds),
    atOnceUsers(5),
  ).protocols(httpProtocol)

  // Controller: smoke (0–10s) + load (12s) + Ramp (45s+)
  // nothingFor-Zeiten summieren sich: 10s smoke + 2s gap = 12s load-Start
  // nach atOnceUsers direkt nothingFor(33s) → Ramp startet bei 12+33=45s
  val controllerPop = controllerScenario.inject(
    constantUsersPerSec(1.0).during(10.seconds),
    nothingFor(2.seconds),
    atOnceUsers(10),
    nothingFor(33.seconds),
    rampUsersPerSec(0.0).to(10.0).during(10.seconds),
    constantUsersPerSec(10.0).during(20.seconds),
    rampUsersPerSec(10.0).to(20.0).during(10.seconds),
    constantUsersPerSec(20.0).during(20.seconds),
    rampUsersPerSec(20.0).to(0.0).during(5.seconds),
  ).protocols(httpProtocol)

  setUp(modelPop, controllerPop, playerPop, sfPop).assertions(
    global.failedRequests.percent.lt(1),
    global.responseTime.percentile(95).lt(loadP95Ms),
    details("stockfish/best-move").responseTime.percentile(95).lt(sfLoadP95Ms),
  )
