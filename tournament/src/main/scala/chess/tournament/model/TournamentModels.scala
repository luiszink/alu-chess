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
    status: Option[String],
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
      case _ => Right(Unknown(c.value))
    }
  }

// ── Bot state (internal) ──────────────────────────────────────────────────────

final case class BotIdentity(id: String, name: String, token: String)

enum BotStatus:
  case Idle
  case InTournament(tournamentId: String, round: Int, gamesPlaying: Int)
  case Error(message: String)
