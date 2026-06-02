package chess.lichess.model

import io.circe.*
import io.circe.generic.semiauto.*

/** Subset of Lichess Bot-API DTOs we actually use.
  *
  * Many fields in the real responses are ignored; circe's auto-derivation
  * therefore only sees what we care about. Anything unknown stays in the raw
  * envelope JSON that we still forward to the UI over SSE. */

final case class LichessAccount(
    id: String,
    username: String,
    title: Option[String],
)
object LichessAccount:
  given Decoder[LichessAccount] = deriveDecoder
  given Encoder[LichessAccount] = deriveEncoder

final case class LichessPlayer(
    id: Option[String]     = None,
    name: Option[String]   = None,
    title: Option[String]  = None,
    rating: Option[Int]    = None,
)
object LichessPlayer:
  given Decoder[LichessPlayer] = deriveDecoder
  given Encoder[LichessPlayer] = deriveEncoder

final case class LichessVariant(
    key: String,
    name: Option[String] = None,
)
object LichessVariant:
  given Decoder[LichessVariant] = deriveDecoder
  given Encoder[LichessVariant] = deriveEncoder

final case class LichessTimeControl(
    `type`: String,
    limit: Option[Int]    = None, // initial seconds
    increment: Option[Int] = None,
)
object LichessTimeControl:
  given Decoder[LichessTimeControl] = deriveDecoder
  given Encoder[LichessTimeControl] = deriveEncoder

final case class LichessChallenge(
    id: String,
    url: Option[String]                 = None,
    status: Option[String]              = None,
    rated: Boolean                      = false,
    variant: LichessVariant,
    timeControl: LichessTimeControl,
    challenger: Option[LichessPlayer]   = None,
    destUser: Option[LichessPlayer]     = None,
    speed: Option[String]               = None,
)
object LichessChallenge:
  given Decoder[LichessChallenge] = deriveDecoder
  given Encoder[LichessChallenge] = deriveEncoder

final case class LichessGameRef(
    id: String,
    color: Option[String]      = None, // "white" | "black"
    fen: Option[String]        = None,
    opponent: Option[LichessPlayer] = None,
)
object LichessGameRef:
  given Decoder[LichessGameRef] = deriveDecoder
  given Encoder[LichessGameRef] = deriveEncoder

/** Top-level event delivered on `/api/stream/event`. */
enum LichessEvent:
  case ChallengeEvent(challenge: LichessChallenge)
  case ChallengeCanceled(challenge: LichessChallenge)
  case ChallengeDeclined(challenge: LichessChallenge)
  case GameStart(game: LichessGameRef)
  case GameFinish(game: LichessGameRef)
  case Other(raw: Json)

object LichessEvent:
  given Decoder[LichessEvent] = Decoder.instance { c =>
    c.downField("type").as[String].flatMap {
      case "challenge"         => c.downField("challenge").as[LichessChallenge].map(ChallengeEvent.apply)
      case "challengeCanceled" => c.downField("challenge").as[LichessChallenge].map(ChallengeCanceled.apply)
      case "challengeDeclined" => c.downField("challenge").as[LichessChallenge].map(ChallengeDeclined.apply)
      case "gameStart"         => c.downField("game").as[LichessGameRef].map(GameStart.apply)
      case "gameFinish"        => c.downField("game").as[LichessGameRef].map(GameFinish.apply)
      case _                   => Right(Other(c.value))
    }
  }

/** Per-game stream messages from `/api/bot/game/stream/{gameId}`.
  *
  * `gameFull` is the first frame and contains the initial position; every
  * subsequent `gameState` frame carries the full move list as a single
  * space-separated UCI string. */
enum LichessBotGameMessage:
  case Full(
      id: String,
      initialFen: String,
      whiteId: Option[String],
      blackId: Option[String],
      whiteName: Option[String],
      blackName: Option[String],
      variant: String,
      state: LichessGameState,
  )
  case State(state: LichessGameState)
  case Chat(username: String, text: String, room: String)
  case Other(raw: Json)

final case class LichessGameState(
    moves: String,
    wtime: Option[Long]    = None,
    btime: Option[Long]    = None,
    winc: Option[Long]     = None,
    binc: Option[Long]     = None,
    status: String         = "started",
    winner: Option[String] = None,
)
object LichessGameState:
  given Decoder[LichessGameState] = deriveDecoder
  given Encoder[LichessGameState] = deriveEncoder

object LichessBotGameMessage:
  given Decoder[LichessBotGameMessage] = Decoder.instance { c =>
    c.downField("type").as[String].flatMap {
      case "gameFull" =>
        for
          id      <- c.downField("id").as[String]
          fen     <- c.downField("initialFen").as[String]
          variant <- c.downField("variant").downField("key").as[String]
          state   <- c.downField("state").as[LichessGameState]
          whiteId   = c.downField("white").downField("id").as[String].toOption
          blackId   = c.downField("black").downField("id").as[String].toOption
          whiteName = c.downField("white").downField("name").as[String].toOption
          blackName = c.downField("black").downField("name").as[String].toOption
        yield Full(id, fen, whiteId, blackId, whiteName, blackName, variant, state)
      case "gameState" =>
        c.value.as[LichessGameState].map(State.apply)
      case "chatLine" =>
        for
          u <- c.downField("username").as[String]
          t <- c.downField("text").as[String]
          r <- c.downField("room").as[String]
        yield Chat(u, t, r)
      case _ => Right(Other(c.value))
    }
  }
