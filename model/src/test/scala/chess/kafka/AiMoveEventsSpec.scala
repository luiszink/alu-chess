package chess.kafka

import chess.model.{Move, Position}
import io.circe.parser.decode
import io.circe.syntax.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AiMoveEventsSpec extends AnyWordSpec with Matchers:

  "AiMoveRequest" should {

    "roundtrip through JSON" in {
      val request = AiMoveRequest(
        requestId = "request-1",
        gameId = "game-1",
        fen = "fen",
        aiColor = "Black",
        timeLimitMs = 2000,
        maxDepth = 4,
      )

      decode[AiMoveRequest](request.asJson.noSpaces) shouldBe Right(request)
    }
  }

  "AiMoveResponse" should {

    "roundtrip through JSON" in {
      val response = AiMoveResponse(
        requestId = "request-1",
        gameId = "game-1",
        from = Some("e7"),
        to = Some("e5"),
        promotion = None,
        error = None,
      )

      decode[AiMoveResponse](response.asJson.noSpaces) shouldBe Right(response)
    }

    "convert a successful response to a Move" in {
      val response = AiMoveResponse(
        requestId = "request-1",
        gameId = "game-1",
        from = Some("e7"),
        to = Some("e8"),
        promotion = Some("Q"),
        error = None,
      )

      response.toMoveEither shouldBe Right(Move(Position(6, 4), Position(7, 4), Some('Q')))
    }

    "prefer the error field over move fields" in {
      val response = AiMoveResponse(
        requestId = "request-1",
        gameId = "game-1",
        from = Some("e7"),
        to = Some("e5"),
        promotion = None,
        error = Some("boom"),
      )

      response.toMoveEither shouldBe Left("boom")
    }
  }
