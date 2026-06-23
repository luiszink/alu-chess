package chess.tournament.bot

import cats.effect.IO
import cats.effect.std.AtomicCell
import chess.tournament.client.TournamentApiClient
import chess.tournament.model.GameEvent
import chess.model.{Color, Game, Move, MoveValidator}
import scala.util.Random

/** Plays random legal moves in tournament games.
  * The bot always makes the first legal move it finds.
  * Used for testing the UI without needing the full AI engine. */
object RandomBot:

  def play(
      client: TournamentApiClient,
      tournamentId: String,
      gameId: String,
      myColor: Color,
      onEvent: String => IO[Unit],
  ): IO[Unit] =
    onEvent(s"[game $gameId] RandomBot connecting...") *>
    AtomicCell[IO].of(Option.empty[Ctx]).flatMap { cell =>
      client
        .streamGame(tournamentId, gameId)
        .evalMap(handle(client, tournamentId, gameId, myColor, cell, onEvent))
        .compile
        .drain
        .handleErrorWith { err =>
          onEvent(s"[game $gameId] RandomBot stream error: ${err.getMessage}")
        }
    }

  // movePending prevents duplicate submissions while waiting for server confirmation
  private final case class Ctx(myColor: Color, game: Game, finished: Boolean, movePending: Boolean = false)

  private def handle(
      client: TournamentApiClient,
      tournamentId: String,
      gameId: String,
      myColor: Color,
      cell: AtomicCell[IO, Option[Ctx]],
      onEvent: String => IO[Unit],
  )(event: GameEvent): IO[Unit] = event match

    case gs: GameEvent.GameState =>
      val moves = gs.moves.getOrElse("").trim
      val game0 = Game.newGame
      buildGame(game0, moves.split(" ").filter(_.nonEmpty).toList) match
        case Left(err) =>
          onEvent(s"[game $gameId] RandomBot: could not build state: $err")
        case Right(game) =>
          val isFinished = gs.status.exists(s => s != "ongoing" && s != "pending")
          cell.set(Some(Ctx(myColor, game, isFinished))) *>
            onEvent(s"[game $gameId] RandomBot playing as ${myColor}") *>
            maybeMove(client, tournamentId, gameId, cell, onEvent)

    case GameEvent.Move(uci, _, _) =>
      // Clear movePending when server confirms any move, then check if it's our turn
      cell.update(_.map { ctx =>
        applyUci(ctx.game, uci) match
          case Right(g) => ctx.copy(game = g, movePending = false)
          case Left(_)  => ctx.copy(movePending = false)
      }) *> maybeMove(client, tournamentId, gameId, cell, onEvent)

    case GameEvent.GameEnd(winner, status) =>
      cell.update(_.map(_.copy(finished = true))) *>
        onEvent(s"[game $gameId] RandomBot ended: $status, winner: ${winner.getOrElse("draw")}")

    case GameEvent.Heartbeat() => IO.unit
    case GameEvent.Unknown(_)  => IO.unit

  private def maybeMove(
      client: TournamentApiClient,
      tournamentId: String,
      gameId: String,
      cell: AtomicCell[IO, Option[Ctx]],
      onEvent: String => IO[Unit],
  ): IO[Unit] =
    cell.get.flatMap {
      case None                                                => IO.unit
      case Some(ctx) if ctx.finished                          => IO.unit
      case Some(ctx) if ctx.movePending                       => IO.unit
      case Some(ctx) if ctx.game.currentPlayer != ctx.myColor => IO.unit
      case Some(ctx) =>
        IO.blocking {
          val moves = MoveValidator.legalMoves(ctx.game.board, ctx.myColor, ctx.game.movedPieces, ctx.game.lastMove)
          if moves.isEmpty then None
          else Some(moves(Random.nextInt(moves.length)))
        }.flatMap {
          case None =>
            onEvent(s"[game $gameId] RandomBot: no legal moves")
          case Some(move) =>
            val uci = moveToUci(move)
            // Set pending before submitting to block concurrent move attempts
            cell.update(_.map(_.copy(movePending = true))) *>
              client.submitMove(tournamentId, gameId, uci)
                .handleErrorWith { e =>
                  // On error clear the pending flag so we can retry on next event
                  cell.update(_.map(_.copy(movePending = false))) *>
                    onEvent(s"[game $gameId] RandomBot move error: ${e.getMessage}")
                } *>
              onEvent(s"[game $gameId] RandomBot played $uci")
        }
    }

  private def buildGame(start: Game, ucis: List[String]): Either[String, Game] =
    ucis.foldLeft[Either[String, Game]](Right(start)) { (acc, uci) =>
      acc.flatMap(g => applyUci(g, uci))
    }

  private def applyUci(game: Game, uci: String): Either[String, Game] =
    Move.fromString(uci) match
      case Some(move) => game.applyMoveE(move).left.map(_.message)
      case None       => Left(s"Cannot parse UCI: $uci")

  private def moveToUci(move: Move): String =
    val from = move.from.toString
    val to   = move.to.toString
    move.promotion.map(p => s"$from$to${p.toLower}").getOrElse(s"$from$to")
