package chess.tournament.model

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*

// ── Auth ──────────────────────────────────────────────────────────────────────

final case class RegisterRequest(name: String, isBot: Boolean = true)
object RegisterRequest:
  given Encoder[RegisterRequest] = deriveEncoder

final case class RegisterResponse(id: String, token: String)
object RegisterResponse:
  given Decoder[RegisterResponse] = deriveDecoder

// ── Tournament Info ───────────────────────────────────────────────────────────

final case class TournamentClock(limit: Int, increment: Int)
object TournamentClock:
  given Decoder[TournamentClock] = deriveDecoder
  given Encoder[TournamentClock] = deriveEncoder

final case class TournamentInfo(
    id: String,
    fullName: String,
    clock: Option[TournamentClock],
    nbPlayers: Option[Int],
    nbRounds: Option[Int],
    format: Option[String],
    matchesPerPairing: Option[Int],
    startPosition: Option[String],
    rated: Option[Boolean],
    status: Option[String],
    createdBy: Option[String],
)
object TournamentInfo:
  given Decoder[TournamentInfo] = deriveDecoder

final case class TournamentListResponse(
    created: List[TournamentInfo],
    started: List[TournamentInfo],
    finished: List[TournamentInfo],
)
object TournamentListResponse:
  given Decoder[TournamentListResponse] = Decoder.instance { c =>
    for
      created  <- c.getOrElse[List[TournamentInfo]]("created")(Nil)
      started  <- c.getOrElse[List[TournamentInfo]]("started")(Nil)
      finished <- c.getOrElse[List[TournamentInfo]]("finished")(Nil)
    yield TournamentListResponse(created, started, finished)
  }

// ── Tournament Events (NDJSON stream) ────────────────────────────────────────

sealed trait TournamentEvent
object TournamentEvent:
  final case class TournamentStarted()                              extends TournamentEvent
  final case class RoundStarted(round: Int)                        extends TournamentEvent
  final case class GameStart(round: Int, gameId: String, color: String) extends TournamentEvent
  final case class RoundFinished(round: Int)                       extends TournamentEvent
  final case class TournamentFinished(winner: Option[Json])        extends TournamentEvent
  final case class Heartbeat()                                     extends TournamentEvent
  final case class Unknown(raw: Json)                              extends TournamentEvent

  given Decoder[TournamentEvent] = Decoder.instance { c =>
    c.get[String]("type").flatMap {
      case "tournamentStarted"  => Right(TournamentStarted())
      case "roundStarted"       => c.get[Int]("round").map(RoundStarted(_))
      case "gameStart"          =>
        for
          round  <- c.get[Int]("round")
          gameId <- c.get[String]("gameId")
          color  <- c.get[String]("color")
        yield GameStart(round, gameId, color)
      case "roundFinished"      => c.get[Int]("round").map(RoundFinished(_))
      case "tournamentFinished" => Right(TournamentFinished(c.value.hcursor.get[Json]("winner").toOption))
      case "heartbeat"          => Right(Heartbeat())
      case _                    => Right(Unknown(c.value))
    }
  }

// ── Game Events (NDJSON stream) ───────────────────────────────────────────────

sealed trait GameEvent
object GameEvent:
  final case class GameState(
      id: Option[String],
      moves: Option[String],
      fen: Option[String],
      status: Option[String],
      turn: Option[String],
  ) extends GameEvent

  final case class Move(uci: String, fen: String, turn: String) extends GameEvent
  final case class GameEnd(winner: Option[String], status: String) extends GameEvent
  final case class Heartbeat()                                    extends GameEvent
  final case class Unknown(raw: Json)                             extends GameEvent

  given Decoder[GameEvent] = Decoder.instance { c =>
    c.get[String]("type").flatMap {
      case "gameState" =>
        for
          id     <- c.get[Option[String]]("id").orElse(Right(None))
          moves  <- c.get[Option[String]]("moves").orElse(Right(None))
          fen    <- c.get[Option[String]]("fen").orElse(Right(None))
          status <- c.get[Option[String]]("status").orElse(Right(None))
          turn   <- c.get[Option[String]]("turn").orElse(Right(None))
        yield GameState(id, moves, fen, status, turn)
      case "move" =>
        for
          uci  <- c.get[String]("uci")
          fen  <- c.get[String]("fen")
          turn <- c.get[String]("turn")
        yield Move(uci, fen, turn)
      case "gameEnd" =>
        for
          winner <- c.get[Option[String]]("winner").orElse(Right(None))
          status <- c.get[String]("status")
        yield GameEnd(winner, status)
      case "heartbeat" => Right(Heartbeat())
      case _           => Right(Unknown(c.value))
    }
  }

// ── Results & Pairings ───────────────────────────────────────────────────────

final case class BotRef(id: String, name: String)
object BotRef:
  given Decoder[BotRef] = deriveDecoder

final case class Result(
    rank: Int,
    points: Double,
    tieBreak: Option[Double],
    bot: BotRef,
    nbGames: Option[Int],
    wins: Option[Int],
    draws: Option[Int],
    losses: Option[Int],
)
object Result:
  given Decoder[Result] = deriveDecoder

final case class MatchResultEntry(gameId: String, winner: Option[String])
object MatchResultEntry:
  given Decoder[MatchResultEntry] = deriveDecoder

final case class Pairing(
    round: Int,
    white: BotRef,
    black: BotRef,
    gameId: Option[String],
    matchesPerPairing: Option[Int],
    matchResults: Option[List[MatchResultEntry]],
    winner: Option[String],
)
object Pairing:
  given Decoder[Pairing] = deriveDecoder

// ── Analytics Export ──────────────────────────────────────────────────────────

final case class AnalyticsExportGame(
    gameId: String,
    tournamentId: String,
    round: Int,
    whiteBotId: String,
    whiteBotName: String,
    whiteBotFamily: Option[String],
    whiteStrategyType: Option[String],
    whiteEngineType: Option[String],
    whiteModelVersion: Option[String],
    blackBotId: String,
    blackBotName: String,
    blackBotFamily: Option[String],
    blackStrategyType: Option[String],
    blackEngineType: Option[String],
    blackModelVersion: Option[String],
    winner: Option[String],
    winnerBotId: Option[String],
    terminationReason: String,
    totalPly: Int,
    moves: String,
    startedAt: Option[String],
    endedAt: Option[String],
    durationMillis: Option[Long],
)
object AnalyticsExportGame:
  given Decoder[AnalyticsExportGame] = deriveDecoder

final case class AnalyticsExportStanding(
    tournamentId: String,
    botId: String,
    botName: String,
    botFamily: Option[String],
    strategyType: Option[String],
    engineType: Option[String],
    modelVersion: Option[String],
    rank: Int,
    points: Double,
    wins: Int,
    draws: Int,
    losses: Int,
    nbGames: Int,
    tieBreak: Double,
)
object AnalyticsExportStanding:
  given Decoder[AnalyticsExportStanding] = deriveDecoder

final case class AnalyticsExportClock(limit: Int, increment: Int)
object AnalyticsExportClock:
  given Decoder[AnalyticsExportClock] = deriveDecoder

final case class AnalyticsExport(
    schemaVersion: String,
    tournamentId: String,
    format: String,
    clock: AnalyticsExportClock,
    rated: Boolean,
    nbRounds: Int,
    startedAt: Option[String],
    finishedAt: Option[String],
    exportedAt: String,
    standings: List[AnalyticsExportStanding],
    games: List[AnalyticsExportGame],
)
object AnalyticsExport:
  given Decoder[AnalyticsExport] = deriveDecoder

// ── Bot state (internal) ──────────────────────────────────────────────────────

final case class BotIdentity(id: String, name: String, token: String)

enum BotStatus:
  case Idle
  case InTournament(tournamentId: String, round: Int, gamesPlaying: Int)
  case Error(message: String)
