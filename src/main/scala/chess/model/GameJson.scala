package chess.model

import play.api.libs.json.*
import java.time.LocalDateTime

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

  private def moveToUci(move: Move): String =
    val promo = move.promotion.map(_.toLower).map(_.toString).getOrElse("")
    s"${move.from}${move.to}$promo"

  private def toStateJson(game: Game, ply: Int): JsObject =
    Json.obj(
      "ply"           -> ply,
      "fen"           -> Fen.toFen(game),
      "status"        -> game.status.toString,
      "currentPlayer" -> game.currentPlayer.toString,
      "san"           -> game.moveHistory.lastOption.map(_.san),
      "uci"           -> game.lastMove.map(moveToUci)
    )

  private def parseTimeControl(json: JsValue): Either[ChessError, Option[TimeControl]] =
    (json \ "timeControl").toOption match
      case None | Some(JsNull) => Right(None)
      case Some(tcJson) =>
        for
          initial <- (tcJson \ "initialTimeMs").asOpt[Long]
            .toRight(ChessError.InvalidFenFormat("Missing 'timeControl.initialTimeMs' field in JSON"))
          increment <- (tcJson \ "incrementMs").asOpt[Long]
            .toRight(ChessError.InvalidFenFormat("Missing 'timeControl.incrementMs' field in JSON"))
          name <- (tcJson \ "name").asOpt[String]
            .toRight(ChessError.InvalidFenFormat("Missing 'timeControl.name' field in JSON"))
        yield Some(TimeControl(initial, increment, name))

  private def parseStates(json: JsValue): Either[ChessError, Vector[Game]] =
    (json \ "moves").asOpt[JsArray] match
      case Some(moves) if moves.value.nonEmpty =>
        moves.value.toVector.zipWithIndex.foldLeft[Either[ChessError, Vector[Game]]](Right(Vector.empty)) {
          case (Left(err), _) => Left(err)
          case (Right(acc), (entry, idx)) =>
            (entry \ "fen").asOpt[String] match
              case Some(fen) => Fen.parseE(fen).map(game => acc :+ game)
              case None      => Left(ChessError.InvalidFenFormat(s"Missing 'moves[$idx].fen' field in JSON"))
        }
      case Some(_) => Left(ChessError.InvalidFenFormat("'moves' field in JSON must not be empty"))
      case None => fromJson(json).map(game => Vector(game))

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

  /** Encode a complete [[GameRecord]] as a [[JsObject]] with FEN per ply. */
  def toRecordJson(record: GameRecord): JsObject =
    Json.obj(
      "id"         -> record.id,
      "datePlayed" -> record.datePlayed.toString,
      "result"     -> record.result,
      "moveCount"  -> record.moveCount,
      "pgn"        -> record.pgn,
      "timeControl" -> record.timeControl.map(tc =>
        Json.obj(
          "initialTimeMs" -> tc.initialTimeMs,
          "incrementMs"   -> tc.incrementMs,
          "name"          -> tc.name
        )
      ),
      "moves" -> record.gameStates.zipWithIndex.map { case (game, idx) => toStateJson(game, idx) }
    )

  /** Encode a complete [[GameRecord]] as a compact JSON string. */
  def toRecordJsonString(record: GameRecord): String = Json.stringify(toRecordJson(record))

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

  /** Decode a [[GameRecord]] from a [[JsValue]].
    * Accepts either:
    * - the extended format with a non-empty "moves" list, or
    * - a single-game fallback with only "fen".
    */
  def fromRecordJson(json: JsValue): Either[ChessError, GameRecord] =
    for
      states <- parseStates(json)
      timeControl <- parseTimeControl(json)
      id = (json \ "id").asOpt[String].getOrElse(java.util.UUID.randomUUID().toString)
      datePlayed = (json \ "datePlayed").asOpt[String]
        .flatMap(s => scala.util.Try(LocalDateTime.parse(s)).toOption)
        .getOrElse(LocalDateTime.now())
      result = (json \ "result").asOpt[String].getOrElse("*")
      pgn = (json \ "pgn").asOpt[String].getOrElse(Pgn.toPgn(states.last, timeControl = timeControl))
      moveCount = (json \ "moveCount").asOpt[Int].getOrElse(math.max(states.size - 1, 0))
    yield GameRecord(
      id = id,
      datePlayed = datePlayed,
      result = result,
      timeControl = timeControl,
      moveCount = moveCount,
      pgn = pgn,
      gameStates = states
    )

  /** Decode a [[GameRecord]] from a JSON string. */
  def fromRecordJsonString(s: String): Either[ChessError, GameRecord] =
    scala.util.Try(Json.parse(s)).toEither
      .left.map(_ => ChessError.InvalidFenFormat("Invalid JSON"))
      .flatMap(fromRecordJson)
