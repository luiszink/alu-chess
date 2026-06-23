package chess.tournament.bot

import cats.effect.IO
import cats.effect.std.AtomicCell
import chess.tournament.client.TournamentApiClient
import chess.tournament.model.GameEvent
import chess.model.{Color, Fen, Game, Move}
import chess.model.ai.ChessAI

/** Plays a single tournament game from start to finish.
  *
  * The NowChess game stream sends:
  *  - An initial `gameState` with the full move history
  *  - A `move` event for each subsequent move
  *  - A `gameEnd` event when the game concludes
  *
  * Whenever it is our turn we ask ChessAI for a move and POST it. */
object TournamentGameRunner:

  final case class Settings(timeLimitMs: Long, maxDepth: Int)

  private final case class Ctx(
      myColor: Color,
      game: Game,
      movesApplied: Int,
      finished: Boolean,
  )

  def play(
      client: TournamentApiClient,
      tournamentId: String,
      gameId: String,
      myColor: Color,
      settings: Settings,
      onEvent: String => IO[Unit],
  ): IO[Unit] =
    onEvent(s"[game $gameId] connecting to game stream...") *>
    AtomicCell[IO].of(Option.empty[Ctx]).flatMap { cell =>
      client
        .streamGame(tournamentId, gameId)
        .evalMap(handle(client, tournamentId, gameId, myColor, settings, cell, onEvent))
        .compile
        .drain
        .handleErrorWith { err =>
          onEvent(s"[game $gameId] stream error: ${err.getMessage}")
        }
    }

  // ── Event handling ────────────────────────────────────────────────────────

  private def handle(
      client: TournamentApiClient,
      tournamentId: String,
      gameId: String,
      myColor: Color,
      settings: Settings,
      cell: AtomicCell[IO, Option[Ctx]],
      onEvent: String => IO[Unit],
  )(event: GameEvent): IO[Unit] = event match

    case gs: GameEvent.GameState =>
      val moves = gs.moves.getOrElse("").trim
      val ucis  = moves.split(" ").filter(_.nonEmpty).toList
      gameFromSnapshot(gs, ucis) match
        case Left(err) =>
          onEvent(s"[game $gameId] could not build initial state: $err")
        case Right(game) =>
          val applied = ucis.size
          // pending = created but not yet active; treat like ongoing (wait for moves)
          val isFinished = gs.status.exists(s => s != "ongoing" && s != "pending")
          val ctx = Ctx(myColor, game, applied, isFinished)
          cell.set(Some(ctx)) *>
            onEvent(s"[game $gameId] started as ${gs.status.getOrElse("unknown")}, I am ${myColor}, ${applied} moves in") *>
            maybeMove(client, tournamentId, gameId, settings, cell, onEvent)

    case GameEvent.Move(uci, _, _) =>
      cell.update(_.map { ctx =>
        applyUci(ctx.game, uci) match
          case Right(g) => ctx.copy(game = g, movesApplied = ctx.movesApplied + 1)
          case Left(_)  => ctx
      }) *> maybeMove(client, tournamentId, gameId, settings, cell, onEvent)

    case GameEvent.GameEnd(winner, status) =>
      cell.update(_.map(_.copy(finished = true))) *>
        onEvent(s"[game $gameId] ended: $status, winner: ${winner.getOrElse("draw")}")

    case GameEvent.Heartbeat() =>
      IO.unit

    case GameEvent.Unknown(raw) =>
      onEvent(s"[game $gameId] unknown event: $raw")

  // ── Move logic ────────────────────────────────────────────────────────────

  private def maybeMove(
      client: TournamentApiClient,
      tournamentId: String,
      gameId: String,
      settings: Settings,
      cell: AtomicCell[IO, Option[Ctx]],
      onEvent: String => IO[Unit],
  ): IO[Unit] =
    cell.get.flatMap {
      case None => IO.unit
      case Some(ctx) if ctx.finished => IO.unit
      case Some(ctx) if ctx.game.currentPlayer != ctx.myColor => IO.unit
      case Some(ctx) =>
        IO.blocking {
          ChessAI.selectMove(ctx.game, settings.timeLimitMs, settings.maxDepth)
        }.flatMap {
          case None =>
            onEvent(s"[game $gameId] AI found no move (game over?)")
          case Some(move) =>
            val uci = moveToUci(move)
            client.submitMove(tournamentId, gameId, uci)
              .handleErrorWith(e => onEvent(s"[game $gameId] move error: ${e.getMessage}")) *>
              onEvent(s"[game $gameId] played $uci")
        }
    }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private def buildGame(start: Game, ucis: List[String]): Either[String, Game] =
    ucis.foldLeft[Either[String, Game]](Right(start)) { (acc, uci) =>
      acc.flatMap(g => applyUci(g, uci))
    }

  private def gameFromSnapshot(gs: GameEvent.GameState, ucis: List[String]): Either[String, Game] =
    gs.fen.map(_.trim).filter(_.nonEmpty) match
      case Some(currentFen) =>
        Fen.parseE(currentFen).left.map(_.message)
      case None =>
        val start =
          gs.startPosition.map(_.trim).filter(s => s.nonEmpty && s != "standard") match
            case Some(startFen) => Fen.parseE(startFen).left.map(_.message)
            case None           => Right(Game.newGame)
        start.flatMap(game => buildGame(game, ucis))

  private def applyUci(game: Game, uci: String): Either[String, Game] =
    Move.fromString(uci) match
      case Some(move) =>
        game.applyMoveE(move).left.map(_.message)
      case None => Left(s"Cannot parse UCI: $uci")

  private def moveToUci(move: Move): String =
    val from = move.from.toString
    val to   = move.to.toString
    move.promotion.map(p => s"$from$to${p.toLower}").getOrElse(s"$from$to")
