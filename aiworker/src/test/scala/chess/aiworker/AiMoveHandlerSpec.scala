package chess.aiworker

import chess.kafka.{AiMoveRequest, AiMoveResponse}
import chess.model.{Fen, Game, GameStatus}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class AiMoveHandlerSpec extends AnyWordSpec with Matchers:

  private def request(fen: String): AiMoveRequest =
    AiMoveRequest(
      requestId = "request-1",
      gameId = "game-1",
      fen = fen,
      aiColor = "Black",
      timeLimitMs = 100,
      maxDepth = 1,
    )

  "AiMoveHandler.handle" should {

    "return a legal move for a valid non-terminal position" in {
      val response = AiMoveHandler.handle(request(Fen.toFen(Game.newGame)))

      response.error shouldBe None
      response.from shouldBe defined
      response.to shouldBe defined
      response.toMoveEither shouldBe a[Right[?, ?]]
    }

    "return an error for invalid FEN" in {
      val response = AiMoveHandler.handle(request("not a fen"))

      response.error shouldBe defined
      response.from shouldBe None
      response.to shouldBe None
    }

    "return an error for a terminal game" in {
      val checkmateFen = "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3"
      val response = AiMoveHandler.handle(request(checkmateFen))

      response.error.getOrElse("") should include("already over")
    }
  }
