package chess.aiworker

import chess.kafka.{AiMoveRequest, AiMoveResponse}
import chess.model.Fen
import chess.model.ai.ChessAI

object AiMoveHandler:

  def handle(request: AiMoveRequest): AiMoveResponse =
    Fen.parseE(request.fen) match
      case Left(error) =>
        AiMoveResponse.failure(request, error.message)
      case Right(game) if game.status.isTerminal =>
        AiMoveResponse.failure(request, s"Game is already over: ${game.status}")
      case Right(game) =>
        ChessAI.selectMove(game, request.timeLimitMs, request.maxDepth) match
          case Some(move) => AiMoveResponse.success(request, move)
          case None       => AiMoveResponse.failure(request, "AI did not find a legal move")
