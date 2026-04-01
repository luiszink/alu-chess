package chess.model

import play.api.libs.json.*

/** JSON encoding and decoding for [[Game]].
  *
  * The board state is represented as a FEN string inside the JSON document so
  * that the representation remains compact and human-readable. Decoding simply
  * re-parses the embedded FEN via the currently active [[Fen]] parser.
  *
  * Typical usage:
  * {{{
  *   val json   = GameJson.toJsonString(game)
  *   val result = GameJson.fromJsonString(json) // Either[ChessError, Game]
  * }}}
  */
object GameJson:

  /** Encode a [[Game]] as a [[JsObject]]. */
  def toJson(game: Game): JsObject =
    Json.obj(
      "fen"            -> Fen.toFen(game),
      "status"         -> game.status.toString,
      "currentPlayer"  -> game.currentPlayer.toString,
      "halfMoveClock"  -> game.halfMoveClock,
      "fullMoveNumber" -> game.fullMoveNumber,
      "isTerminal"     -> game.status.isTerminal
    )

  /** Encode a [[Game]] as a compact JSON string. */
  def toJsonString(game: Game): String = Json.stringify(toJson(game))

  /** Decode a [[Game]] from a [[JsValue]].
    * Expects a "fen" field; all other fields are informational and ignored on decode. */
  def fromJson(json: JsValue): Either[ChessError, Game] =
    (json \ "fen").asOpt[String] match
      case Some(fen) => Fen.parseE(fen)
      case None      => Left(ChessError.InvalidFenFormat("Missing 'fen' field in JSON"))

  /** Decode a [[Game]] from a JSON string. */
  def fromJsonString(s: String): Either[ChessError, Game] =
    scala.util.Try(Json.parse(s)).toEither
      .left.map(_ => ChessError.InvalidFenFormat("Invalid JSON"))
      .flatMap(fromJson)
