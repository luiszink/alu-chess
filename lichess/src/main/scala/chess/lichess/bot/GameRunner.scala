package chess.lichess.bot

import cats.effect.IO
import cats.effect.std.AtomicCell
import cats.syntax.all.*
import chess.lichess.client.LichessApiClient
import chess.lichess.model.*
import chess.model.{Color, Fen, Game, Move}
import io.circe.Json
import io.circe.syntax.*

/** Plays a single Lichess bot game from start to finish.
  *
  * Lichess only ever sends the *full* move list in each `gameState` frame, so
  * after parsing the initial position we just replay the suffix that we have
  * not seen yet. Whenever it is our turn we ask the local engine for a move
  * and POST it via the Bot API. The runner returns once the game's status is
  * no longer `started`. */
object GameRunner:

  /** Static identity of the bot, derived from the `/api/account` response. */
  final case class Identity(id: String, username: String)

  /** Settings forwarded to the engine. */
  final case class EngineSettings(timeLimitMs: Long, maxDepth: Int)

  /** All info needed to play one game; held in an `AtomicCell` so the
    * stream handler can update it incrementally. */
  private final case class Ctx(
      myColor: Color,
      game: Game,
      movesPlayed: Int,
      finished: Boolean,
  )

  def play(
      client: LichessApiClient,
      me: Identity,
      engine: EngineSettings,
      gameId: String,
      onEvent: Json => IO[Unit],
  ): IO[Unit] =
    AtomicCell[IO].of(Option.empty[Ctx]).flatMap { cell =>
      client
        .streamBotGame(gameId)
        .evalMap(handle(client, me, engine, gameId, cell, onEvent))
        .takeWhile(_ => true) // stream completes when Lichess closes connection
        .compile
        .drain
    }

  // ── Internals ──────────────────────────────────────────────────────────

  private def handle(
      client: LichessApiClient,
      me: Identity,
      engine: EngineSettings,
      gameId: String,
      cell: AtomicCell[IO, Option[Ctx]],
      onEvent: Json => IO[Unit],
  )(msg: LichessBotGameMessage): IO[Unit] = msg match
    case full: LichessBotGameMessage.Full =>
      onEvent(encodeEvent("gameFull", gameId, full.state)) *>
        initFromFull(me, full).flatMap {
          case Left(err) => onEvent(encodeError(gameId, err))
          case Right(c0) =>
            cell.set(Some(c0)) *>
              maybeRespond(client, engine, gameId, cell, onEvent)
        }

    case LichessBotGameMessage.State(state) =>
      onEvent(encodeEvent("gameState", gameId, state)) *>
        cell.get.flatMap {
          case None      => IO.unit
          case Some(ctx) =>
            applyNewMoves(ctx, state.moves) match
              case Left(err) => onEvent(encodeError(gameId, err))
              case Right(updated) =>
                val finished = state.status != "started"
                cell.set(Some(updated.copy(finished = finished))) *>
                  (if finished then IO.unit
                   else maybeRespond(client, engine, gameId, cell, onEvent))
        }

    case _ => IO.unit

  private def initFromFull(
      me: Identity,
      full: LichessBotGameMessage.Full,
  ): IO[Either[String, Ctx]] = IO {
    val myColor =
      if full.whiteId.contains(me.id) then Color.White
      else if full.blackId.contains(me.id) then Color.Black
      else Color.White // fallback; should never happen
    val baseGame =
      if full.initialFen.equalsIgnoreCase("startpos") || full.initialFen.isEmpty
      then Right(Game.newGame)
      else Fen.parseE(full.initialFen).left.map(_.message)

    baseGame.flatMap { g0 =>
      applyUci(g0, full.state.moves)
        .map(g => Ctx(myColor, g, countMoves(full.state.moves), finished = false))
    }
  }

  private def applyNewMoves(ctx: Ctx, allMoves: String): Either[String, Ctx] =
    val tokens = tokenize(allMoves)
    if tokens.length <= ctx.movesPlayed then Right(ctx)
    else
      val newTokens = tokens.drop(ctx.movesPlayed)
      newTokens.foldLeft[Either[String, Game]](Right(ctx.game)) {
        case (Right(g), uci) => stepUci(g, uci)
        case (l, _)          => l
      }.map(g => ctx.copy(game = g, movesPlayed = tokens.length))

  private def maybeRespond(
      client: LichessApiClient,
      engine: EngineSettings,
      gameId: String,
      cell: AtomicCell[IO, Option[Ctx]],
      onEvent: Json => IO[Unit],
  ): IO[Unit] =
    cell.get.flatMap {
      case Some(ctx) if !ctx.finished && ctx.game.currentPlayer == ctx.myColor =>
        IO(chess.model.ai.ChessAI.selectMove(ctx.game, engine.timeLimitMs, engine.maxDepth))
          .flatMap {
            case None => IO.unit
            case Some(mv) =>
              val uci = moveToUci(mv)
              client.submitMove(gameId, uci).attempt.flatMap {
                case Right(_) => onEvent(encodeMyMove(gameId, uci))
                case Left(t)  => onEvent(encodeError(gameId, s"submitMove failed: ${t.getMessage}"))
              }
          }
      case _ => IO.unit
    }

  // ── UCI helpers ────────────────────────────────────────────────────────

  private def tokenize(moves: String): Array[String] =
    if moves.trim.isEmpty then Array.empty else moves.trim.split("\\s+")

  private def countMoves(moves: String): Int = tokenize(moves).length

  private def applyUci(start: Game, moves: String): Either[String, Game] =
    tokenize(moves).foldLeft[Either[String, Game]](Right(start)) {
      case (Right(g), uci) => stepUci(g, uci)
      case (l, _)          => l
    }

  private def stepUci(g: Game, uci: String): Either[String, Game] =
    parseUci(uci).flatMap(m => g.applyMoveE(m).left.map(_.message))

  /** Lichess sends UCI as `e2e4` or `e7e8q`. Our `Move.fromStringE` accepts the
    * 4-char form and a space-separated 3-token form for promotions. */
  private def parseUci(uci: String): Either[String, Move] =
    val trimmed = uci.trim
    val normalised =
      if trimmed.length == 5
      then s"${trimmed.substring(0, 2)} ${trimmed.substring(2, 4)} ${trimmed.charAt(4)}"
      else trimmed
    Move.fromStringE(normalised).left.map(_.message)

  private def moveToUci(m: Move): String =
    val promo = m.promotion.map(_.toLower.toString).getOrElse("")
    s"${m.from}${m.to}$promo"

  // ── Event JSON (for SSE fan-out to the UI) ─────────────────────────────

  private def encodeEvent(kind: String, gameId: String, state: LichessGameState): Json =
    Json.obj(
      "type"   -> Json.fromString(kind),
      "gameId" -> Json.fromString(gameId),
      "state"  -> state.asJson,
    )

  private def encodeMyMove(gameId: String, uci: String): Json =
    Json.obj(
      "type"   -> Json.fromString("myMove"),
      "gameId" -> Json.fromString(gameId),
      "uci"    -> Json.fromString(uci),
    )

  private def encodeError(gameId: String, msg: String): Json =
    Json.obj(
      "type"    -> Json.fromString("error"),
      "gameId"  -> Json.fromString(gameId),
      "message" -> Json.fromString(msg),
    )
