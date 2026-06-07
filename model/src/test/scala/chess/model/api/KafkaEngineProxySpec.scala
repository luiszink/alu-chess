package chess.model.api

import chess.kafka.StockfishEngineResponse
import io.circe.Json
import org.http4s.Status
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class KafkaEngineProxySpec extends AnyWordSpec with Matchers:

  "KafkaEngineProxy.responseToResult" should {

    "map successful responses to Right JSON" in {
      val body = Json.obj("engine" -> Json.fromString("stockfish"))
      val response = StockfishEngineResponse("request-1", "client-1", ok = true, status = 200, body)

      KafkaEngineProxy.responseToResult(response) shouldBe Right(body)
    }

    "map failed responses to Left status and JSON" in {
      val body = Json.obj("error" -> Json.fromString("StockfishError"))
      val response = StockfishEngineResponse("request-1", "client-1", ok = false, status = 503, body)

      KafkaEngineProxy.responseToResult(response) shouldBe Left(Status.ServiceUnavailable -> body)
    }

    "map invalid status codes to BadGateway" in {
      val body = Json.obj()
      val response = StockfishEngineResponse("request-1", "client-1", ok = false, status = 999, body)

      KafkaEngineProxy.responseToResult(response) shouldBe Left(Status.BadGateway -> body)
    }
  }
