package chess.kafka

import io.circe.Json
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class StockfishEngineEventsSpec extends AnyWordSpec with Matchers:

  "StockfishEngineRequest" should {

    "roundtrip through JSON" in {
      val request = StockfishEngineRequest(
        requestId = "request-1",
        clientId = "client-1",
        operation = StockfishEngineRequest.BestMoveOperation,
        payload = Json.obj("fen" -> Json.fromString("fen"), "thinkTimeMs" -> Json.fromInt(1000)),
      )

      decode[StockfishEngineRequest](request.asJson.noSpaces) shouldBe Right(request)
    }
  }

  "StockfishEngineResponse" should {

    "roundtrip through JSON" in {
      val response = StockfishEngineResponse(
        requestId = "request-1",
        clientId = "client-1",
        ok = true,
        status = 200,
        body = Json.obj("status" -> Json.fromString("ok")),
      )

      decode[StockfishEngineResponse](response.asJson.noSpaces) shouldBe Right(response)
    }
  }
