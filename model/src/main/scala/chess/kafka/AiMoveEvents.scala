package chess.kafka

import chess.model.{Move, Position}
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class AiMoveRequest(
  requestId:   String,
  gameId:      String,
  fen:         String,
  aiColor:     String,
  timeLimitMs: Long,
  maxDepth:    Int,
)

object AiMoveRequest:
  given Encoder[AiMoveRequest] = deriveEncoder
  given Decoder[AiMoveRequest] = deriveDecoder

case class AiMoveResponse(
  requestId: String,
  gameId:    String,
  from:      Option[String],
  to:        Option[String],
  promotion: Option[String],
  error:     Option[String],
):
  def toMoveEither: Either[String, Move] =
    error match
      case Some(message) => Left(message)
      case None =>
        for
          fromStr <- from.toRight("Missing 'from' in AI response")
          toStr   <- to.toRight("Missing 'to' in AI response")
          fromPos <- Position.fromStringE(fromStr).left.map(_.message)
          toPos   <- Position.fromStringE(toStr).left.map(_.message)
          promo   <- promotion match
                       case None | Some("") => Right(None)
                       case Some(value) if value.length == 1 && "QRBN".contains(value.head.toUpper) =>
                         Right(Some(value.head.toUpper))
                       case Some(value) => Left(s"Invalid promotion in AI response: $value")
        yield Move(fromPos, toPos, promo)

object AiMoveResponse:
  given Encoder[AiMoveResponse] = deriveEncoder
  given Decoder[AiMoveResponse] = deriveDecoder

  def success(request: AiMoveRequest, move: Move): AiMoveResponse =
    AiMoveResponse(
      requestId = request.requestId,
      gameId = request.gameId,
      from = Some(move.from.toString),
      to = Some(move.to.toString),
      promotion = move.promotion.map(_.toString),
      error = None,
    )

  def failure(request: AiMoveRequest, message: String): AiMoveResponse =
    AiMoveResponse(
      requestId = request.requestId,
      gameId = request.gameId,
      from = None,
      to = None,
      promotion = None,
      error = Some(message),
    )
