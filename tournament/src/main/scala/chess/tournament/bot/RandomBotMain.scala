package chess.tournament.bot

import cats.effect.{IO, Ref, IOApp, ExitCode}
import cats.effect.std.Queue
import cats.syntax.all.*
import chess.tournament.client.TournamentApiClient
import chess.tournament.config.TournamentConfig
import chess.tournament.model.*
import chess.model.Color
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.Uri
import scala.concurrent.duration.*

/** A bot that:
  * 1. Registers itself
  * 2. Polls for pending tournaments every 3 seconds
  * 3. Joins the first one found
  * 4. Plays random legal moves (never resigns immediately)
  *
  * Env vars:
  *   TOURNAMENT_SERVER_URL  (default: http://localhost:8086)
  *   AUTO_JOIN_BOT_NAME     (default: RandomBot-Alice)
  */
object RandomBotMain extends IOApp:

  def run(args: List[String]): IO[ExitCode] =
    val cfg = TournamentConfig.fromEnv()
    val botName = sys.env.getOrElse("AUTO_JOIN_BOT_NAME", cfg.botName)
    val baseUri = Uri.unsafeFromString(cfg.serverUrl)

    EmberClientBuilder.default[IO].build.use { httpClient =>
      val client = TournamentApiClient(httpClient, baseUri)
      for
        logQueue  <- Queue.unbounded[IO, String]
        statusRef <- Ref.of[IO, BotStatus](BotStatus.Idle)
        _         <- runAutoJoin(client, botName, logQueue, statusRef)
      yield ExitCode.Success
    }

  private def runAutoJoin(
      client: TournamentApiClient,
      botName: String,
      logQueue: Queue[IO, String],
      statusRef: Ref[IO, BotStatus],
  ): IO[Unit] =
    def log(msg: String): IO[Unit] =
      IO.println(s"[$botName] $msg") *> logQueue.offer(msg).void

    def pollForTournament: IO[String] =
      client.listTournaments.flatMap { resp =>
        val open = resp.created
        open.headOption match
          case Some(t) =>
            log(s"Found tournament: ${t.fullName} (${t.id})").as(t.id)
          case None =>
            log("No open tournaments, retrying in 3s...") *>
              IO.sleep(3.seconds) *>
              pollForTournament
      }.handleErrorWith { err =>
        log(s"Error listing tournaments: ${err.getMessage}; retrying in 3s...") *>
          IO.sleep(3.seconds) *>
          pollForTournament
      }

    def streamLoop(tournamentId: String): IO[Unit] =
      client
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

          case TournamentEvent.GameStart(_, gameId, colorStr) =>
            val myColor = if colorStr == "white" then Color.White else Color.Black
            log(s"Game started: $gameId as $colorStr") *>
              statusRef.update {
                case BotStatus.InTournament(id, r, g) => BotStatus.InTournament(id, r, g + 1)
                case s => s
              } *>
              RandomBot
                .play(client, tournamentId, gameId, myColor, log)
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

          case TournamentEvent.TournamentFinished(_) =>
            log(s"Tournament finished!") *> statusRef.set(BotStatus.Idle)

          case TournamentEvent.Heartbeat() => IO.unit
          case TournamentEvent.Unknown(_)  => IO.unit
        }
        .compile
        .drain
        .handleErrorWith { err =>
          log(s"Stream error: ${err.getMessage}; reconnecting in 3s...") *>
            IO.sleep(3.seconds) *>
            streamLoop(tournamentId)
        }

    for
      _    <- log("Registering...")
      id   <- client.register(botName).map(_.id)
      _    <- log(s"Registered as $id")
      _    <- (for
        tId  <- pollForTournament
        _    <- log(s"Joining tournament $tId...")
        _    <- client.joinTournament(tId).handleErrorWith(e => log(s"Join warning: ${e.getMessage}"))
        _    <- log("Joined. Waiting for start...")
        _    <- statusRef.set(BotStatus.InTournament(tId, 0, 0))
        _    <- streamLoop(tId)
        _    <- statusRef.set(BotStatus.Idle)
        _    <- log("Tournament done. Looking for next one...")
      yield ()).foreverM
    yield ()
