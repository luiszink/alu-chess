package chess.model

import io.circe.*
import io.circe.syntax.*
import io.circe.parser.{parse as parseJson}
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

  private def toStateJson(game: Game, ply: Int): Json =
    Json.obj(
      "ply"           -> Json.fromInt(ply),
      "fen"           -> Json.fromString(Fen.toFen(game)),
      "status"        -> Json.fromString(game.status.toString),
      "currentPlayer" -> Json.fromString(game.currentPlayer.toString),
      "san"           -> game.moveHistory.lastOption.map(_.san).asJson,
      "uci"           -> game.lastMove.map(moveToUci).asJson
    )

  private def parseTimeControl(json: Json): Either[ChessError, Option[TimeControl]] =
    json.hcursor.downField("timeControl").focus match
      case None | Some(Json.Null) => Right(None)
      case Some(tcJson) =>
        val c = tcJson.hcursor
        for
          initial <- c.get[Long]("initialTimeMs")
            .left.map(_ => ChessError.InvalidFenFormat("Missing 'timeControl.initialTimeMs' field in JSON"))
          increment <- c.get[Long]("incrementMs")
            .left.map(_ => ChessError.InvalidFenFormat("Missing 'timeControl.incrementMs' field in JSON"))
          name <- c.get[String]("name")
            .left.map(_ => ChessError.InvalidFenFormat("Missing 'timeControl.name' field in JSON"))
        yield Some(TimeControl(initial, increment, name))

  private def parseStates(json: Json): Either[ChessError, Vector[Game]] =
    json.hcursor.downField("moves").focus match
      case Some(movesJson) =>
        movesJson.asArray match
          case Some(arr) if arr.nonEmpty =>
            arr.zipWithIndex.foldLeft[Either[ChessError, Vector[Game]]](Right(Vector.empty)) {
              case (Left(err), _) => Left(err)
              case (Right(acc), (entry, idx)) =>
                entry.hcursor.get[String]("fen") match
                  case Right(fen) => Fen.parseE(fen).map(game => acc :+ game)
                  case Left(_)    => Left(ChessError.InvalidFenFormat(s"Missing 'moves[$idx].fen' field in JSON"))
            }
          case Some(_) => Left(ChessError.InvalidFenFormat("'moves' field in JSON must not be empty"))
          case None    => Left(ChessError.InvalidFenFormat("'moves' field in JSON must be an array"))
      case None => fromJson(json).map(game => Vector(game))

  /** Encode a [[Game]] as a [[Json]]. */
  def toJson(game: Game): Json =
    Json.obj(
      "fen"            -> Json.fromString(Fen.toFen(game)),
      "status"         -> Json.fromString(game.status.toString),
      "currentPlayer"  -> Json.fromString(game.currentPlayer.toString),
      "halfMoveClock"  -> Json.fromInt(game.halfMoveClock),
      "fullMoveNumber" -> Json.fromInt(game.fullMoveNumber),
      "isTerminal"     -> Json.fromBoolean(game.status.isTerminal)
    )

  /** Encode a [[Game]] as a compact JSON string. */
  def toJsonString(game: Game): String = toJson(game).noSpaces

  /** Encode a complete [[GameRecord]] as a [[Json]] with FEN per ply. */
  def toRecordJson(record: GameRecord): Json =
    Json.obj(
      "id"         -> Json.fromString(record.id),
      "datePlayed" -> Json.fromString(record.datePlayed.toString),
      "result"     -> Json.fromString(record.result),
      "moveCount"  -> Json.fromInt(record.moveCount),
      "pgn"        -> Json.fromString(record.pgn),
      "timeControl" -> record.timeControl.map(tc =>
        Json.obj(
          "initialTimeMs" -> Json.fromLong(tc.initialTimeMs),
          "incrementMs"   -> Json.fromLong(tc.incrementMs),
          "name"          -> Json.fromString(tc.name)
        )
      ).getOrElse(Json.Null),
      "moves" -> Json.fromValues(record.gameStates.zipWithIndex.map { case (game, idx) => toStateJson(game, idx) })
    )

  /** Encode a complete [[GameRecord]] as a compact JSON string. */
  def toRecordJsonString(record: GameRecord): String = toRecordJson(record).noSpaces

  /** Decode a [[Game]] from a [[Json]].
    * Expects a "fen" field; all other fields are informational and ignored on decode. */
  def fromJson(json: Json): Either[ChessError, Game] =
    json.hcursor.get[String]("fen") match
      case Right(fen) => Fen.parseE(fen)
      case Left(_)    => Left(ChessError.InvalidFenFormat("Missing 'fen' field in JSON"))

  /** Decode a [[Game]] from a JSON string. */
  def fromJsonString(s: String): Either[ChessError, Game] =
    parseJson(s)
      .left.map(_ => ChessError.InvalidFenFormat("Invalid JSON"))
      .flatMap(fromJson)

  /** Decode a [[GameRecord]] from a [[Json]].
    * Accepts either:
    * - the extended format with a non-empty "moves" list, or
    * - a single-game fallback with only "fen".
    */
  def fromRecordJson(json: Json): Either[ChessError, GameRecord] =
    val c = json.hcursor
    for
      states <- parseStates(json)
      timeControl <- parseTimeControl(json)
      id = c.get[String]("id").getOrElse(java.util.UUID.randomUUID().toString)
      datePlayed = c.get[String]("datePlayed").toOption
        .flatMap(s => scala.util.Try(LocalDateTime.parse(s)).toOption)
        .getOrElse(LocalDateTime.now())
      result = c.get[String]("result").getOrElse("*")
      pgn = c.get[String]("pgn").getOrElse(Pgn.toPgn(states.last, timeControl = timeControl))
      moveCount = c.get[Int]("moveCount").getOrElse(math.max(states.size - 1, 0))
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
    parseJson(s)
      .left.map(_ => ChessError.InvalidFenFormat("Invalid JSON"))
      .flatMap(fromRecordJson)
