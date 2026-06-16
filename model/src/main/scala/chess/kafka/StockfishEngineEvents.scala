package chess.kafka

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class StockfishEngineRequest(
  requestId: String,
  clientId:  String,
  operation: String,
  payload:   Json,
)

object StockfishEngineRequest:
  val HealthOperation: String = "health"
  val BestMoveOperation: String = "best-move"
  val EvaluateOperation: String = "evaluate"

  given Encoder[StockfishEngineRequest] = deriveEncoder
  given Decoder[StockfishEngineRequest] = deriveDecoder

case class StockfishEngineResponse(
  requestId: String,
  clientId:  String,
  ok:        Boolean,
  status:    Int,
  body:      Json,
)

object StockfishEngineResponse:
  given Encoder[StockfishEngineResponse] = deriveEncoder
  given Decoder[StockfishEngineResponse] = deriveDecoder
