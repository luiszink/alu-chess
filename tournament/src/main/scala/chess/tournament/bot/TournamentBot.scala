package chess.tournament.bot

import cats.effect.{IO, Ref}
import cats.effect.std.Queue
import cats.syntax.all.*
import chess.tournament.client.TournamentApiClient
import chess.tournament.config.TournamentConfig
import chess.tournament.model.*
import chess.model.Color
import io.circe.Json
import io.circe.syntax.*

/** Manages the full tournament lifecycle for one bot identity:
  *
  *  1. Register identity + get JWT
  *  2. Join a tournament
  *  3. Stream tournament events
  *  4. For each `gameStart`, spin up a `TournamentGameRunner` in a fiber
  *
  * All log lines are pushed into the `logQueue` for the SSE UI endpoint. */
object TournamentBot:

  def run(
      client: TournamentApiClient,
      cfg: TournamentConfig,
      tournamentId: String,
      logQueue: Queue[IO, String],
      statusRef: Ref[IO, BotStatus],
  ): IO[Unit] =
    val settings = TournamentGameRunner.Settings(cfg.aiTimeLimitMs, cfg.aiMaxDepth)

    def log(msg: String): IO[Unit] =
      IO.println(s"[tournament] $msg") *> logQueue.offer(msg)

    for
      _        <- log(s"Registering as '${cfg.botName}'...")
      identity <- client.register(cfg.botName)
      _        <- log(s"Registered: id=${identity.id}")

      _ <- log(s"Joining tournament $tournamentId...")
      _ <- client.joinTournament(tournamentId)
          .handleErrorWith(e => log(s"Join warning (may already be joined): ${e.getMessage}"))
      _ <- log("Joined. Waiting for tournament start...")

      _ <- statusRef.set(BotStatus.InTournament(tournamentId, 0, 0))

      _ <- client
             .streamTournament(tournamentId)
             .evalMap {
               case TournamentEvent.TournamentStarted() =>
                 log("Tournament started!")

               case TournamentEvent.RoundStarted(round) =>
                 log(s"Round $round started") *>
                   statusRef.update {
                     case BotStatus.InTournament(id, _, g) => BotStatus.InTournament(id, round, g)
                     case s => s
                   }

               case TournamentEvent.GameStart(round, gameId, colorStr) =>
                 val myColor = if colorStr == "white" then Color.White else Color.Black
                 log(s"Game started: $gameId as $colorStr (round $round)") *>
                   statusRef.update {
                     case BotStatus.InTournament(id, r, g) => BotStatus.InTournament(id, r, g + 1)
                     case s => s
                   } *>
                   TournamentGameRunner
                     .play(client, tournamentId, gameId, myColor, settings, log)
                     .flatMap(_ =>
                       statusRef.update {
                         case BotStatus.InTournament(id, r, g) => BotStatus.InTournament(id, r, (g - 1).max(0))
                         case s => s
                       }
                     )
                     .start
                     .void

               case TournamentEvent.RoundFinished(round) =>
                 log(s"Round $round finished")

               case TournamentEvent.TournamentFinished(winner) =>
                 val w = winner.flatMap(_.hcursor.get[String]("name").toOption).getOrElse("unknown")
                 log(s"Tournament finished! Winner: $w") *>
                   statusRef.set(BotStatus.Idle)

               case TournamentEvent.Unknown(raw) =>
                 log(s"Unknown event: $raw")
             }
             .compile
             .drain
             .handleErrorWith(e => log(s"Stream error: ${e.getMessage}") *> statusRef.set(BotStatus.Error(e.getMessage)))
    yield ()
