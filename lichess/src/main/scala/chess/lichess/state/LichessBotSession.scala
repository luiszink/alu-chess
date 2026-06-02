package chess.lichess.state

import cats.effect.{IO, Ref, Resource}
import cats.effect.kernel.Fiber
import cats.effect.std.AtomicCell
import cats.syntax.all.*
import chess.lichess.bot.GameRunner
import chess.lichess.client.LichessApiClient
import chess.lichess.config.LichessConfig
import chess.lichess.model.*
import fs2.concurrent.Topic
import io.circe.Json
import io.circe.syntax.*

/** Lifecycle holder for the running bot.
  *
  * One session per process: subscribes to `/api/stream/event`, applies the
  * configured challenge policy, spawns a [[GameRunner]] for every accepted
  * game, and publishes a unified JSON event stream to UI subscribers via a
  * [[fs2.concurrent.Topic]]. */
final class LichessBotSession private (
    client: LichessApiClient,
    cfg: LichessConfig,
    identity: GameRunner.Identity,
    topic: Topic[IO, Json],
    activeGames: Ref[IO, Map[String, Fiber[IO, Throwable, Unit]]],
    activeChallenges: Ref[IO, Map[String, LichessChallenge]],
):

  val username: String = identity.username

  def events: fs2.Stream[IO, Json] = topic.subscribe(64)

  // ── Outgoing actions exposed to the UI ───────────────────────────────

  def createChallenge(
      username: String,
      limitSeconds: Int,
      incrementSeconds: Int,
      rated: Boolean,
      color: String,
  ): IO[Json] =
    client
      .createChallenge(username, limitSeconds, incrementSeconds, rated, color)
      .flatMap(raw => IO.fromEither(io.circe.parser.parse(raw)))
      .flatTap(j => publish(Json.obj(
        "type"      -> Json.fromString("challengeCreated"),
        "target"    -> Json.fromString(username),
        "challenge" -> j,
      )))

  def abortGame(gameId: String): IO[Unit] =
    client.abort(gameId) *>
      publish(Json.obj("type" -> Json.fromString("aborted"), "id" -> Json.fromString(gameId)))

  def resignGame(gameId: String): IO[Unit] =
    client.resign(gameId) *>
      publish(Json.obj("type" -> Json.fromString("resigned"), "id" -> Json.fromString(gameId)))

  def snapshot: IO[Json] =
    for
      games      <- activeGames.get.map(_.keys.toList.sorted)
      challenges <- activeChallenges.get.map(_.values.toList)
    yield Json.obj(
      "username"   -> Json.fromString(username),
      "configured" -> Json.True,
      "games"      -> Json.arr(games.map(Json.fromString) *),
      "challenges" -> challenges.asJson,
      "policy"     -> Json.obj(
        "autoAccept"        -> Json.fromBoolean(cfg.autoAccept),
        "acceptRated"       -> Json.fromBoolean(cfg.acceptRated),
        "variants"          -> Json.arr(cfg.acceptVariants.toList.sorted.map(Json.fromString) *),
        "minInitialSeconds" -> Json.fromInt(cfg.minInitialSeconds),
        "maxInitialSeconds" -> Json.fromInt(cfg.maxInitialSeconds),
        "maxGames"          -> Json.fromInt(cfg.maxGamesConcurrent),
      ),
    )

  // ── Internal: event-loop ─────────────────────────────────────────────

  private def runEventLoop: IO[Unit] =
    publish(Json.obj("type" -> Json.fromString("connected"), "username" -> Json.fromString(username))) *>
      client.streamEvents
        .evalMap(onEvent)
        .compile
        .drain

  private def onEvent(ev: LichessEvent): IO[Unit] = ev match
    case LichessEvent.ChallengeEvent(ch) =>
      activeChallenges.update(_ + (ch.id -> ch)) *>
        publish(Json.obj(
          "type"      -> Json.fromString("challenge"),
          "challenge" -> ch.asJson,
        )) *>
        evaluateChallenge(ch)

    case LichessEvent.ChallengeCanceled(ch) =>
      activeChallenges.update(_ - ch.id) *>
        publish(Json.obj("type" -> Json.fromString("challengeCanceled"), "id" -> Json.fromString(ch.id)))

    case LichessEvent.ChallengeDeclined(ch) =>
      activeChallenges.update(_ - ch.id) *>
        publish(Json.obj("type" -> Json.fromString("challengeDeclined"), "id" -> Json.fromString(ch.id)))

    case LichessEvent.GameStart(g) =>
      publish(Json.obj("type" -> Json.fromString("gameStart"), "id" -> Json.fromString(g.id))) *>
        startGame(g.id)

    case LichessEvent.GameFinish(g) =>
      stopGame(g.id) *>
        publish(Json.obj("type" -> Json.fromString("gameFinish"), "id" -> Json.fromString(g.id)))

    case LichessEvent.Other(_) => IO.unit

  // ── Challenge policy ─────────────────────────────────────────────────

  private def evaluateChallenge(ch: LichessChallenge): IO[Unit] =
    if !cfg.autoAccept then IO.unit
    else
      activeGames.get.flatMap { games =>
        decide(ch, games.size) match
          case Right(()) =>
            client.acceptChallenge(ch.id).attempt.flatMap {
              case Right(_) => publish(Json.obj("type" -> Json.fromString("accepted"), "id" -> Json.fromString(ch.id)))
              case Left(t)  => publish(Json.obj(
                "type"    -> Json.fromString("error"),
                "stage"   -> Json.fromString("accept"),
                "id"      -> Json.fromString(ch.id),
                "message" -> Json.fromString(t.getMessage),
              ))
            }
          case Left(reason) =>
            client.declineChallenge(ch.id, reason).attempt.flatMap { _ =>
              publish(Json.obj(
                "type"   -> Json.fromString("declined"),
                "id"     -> Json.fromString(ch.id),
                "reason" -> Json.fromString(reason),
              ))
            }
      }

  private def decide(ch: LichessChallenge, currentGames: Int): Either[String, Unit] =
    if currentGames >= cfg.maxGamesConcurrent then Left("later")
    else if ch.rated && !cfg.acceptRated then Left("casual")
    else if !cfg.acceptVariants.contains(ch.variant.key.toLowerCase) then Left("variant")
    else
      ch.timeControl.limit match
        case None => Left("timeControl")
        case Some(secs) =>
          if secs < cfg.minInitialSeconds then Left("tooFast")
          else if secs > cfg.maxInitialSeconds then Left("tooSlow")
          else Right(())

  // ── Game lifecycle ───────────────────────────────────────────────────

  private def startGame(gameId: String): IO[Unit] =
    activeGames.get.flatMap { current =>
      if current.contains(gameId) then IO.unit
      else
        GameRunner
          .play(client, identity, GameRunner.EngineSettings(cfg.aiTimeLimitMs, cfg.aiMaxDepth), gameId, publish)
          .handleErrorWith(t => publish(Json.obj(
            "type"    -> Json.fromString("error"),
            "stage"   -> Json.fromString("game"),
            "id"      -> Json.fromString(gameId),
            "message" -> Json.fromString(t.getMessage),
          )))
          .guarantee(activeGames.update(_ - gameId))
          .start
          .flatMap(f => activeGames.update(_ + (gameId -> f)))
    }

  private def stopGame(gameId: String): IO[Unit] =
    activeGames.modify { m => (m - gameId, m.get(gameId)) }.flatMap {
      case Some(fiber) => fiber.cancel
      case None        => IO.unit
    }

  private def publish(j: Json): IO[Unit] = topic.publish1(j).void

  private def shutdown: IO[Unit] =
    activeGames.get.flatMap(_.values.toList.traverse_(_.cancel))

object LichessBotSession:

  /** Build a session if a bot token is configured, otherwise return None. */
  def resource(
      client: LichessApiClient,
      cfg: LichessConfig,
  ): Resource[IO, LichessBotSession] =
    val acquire: IO[LichessBotSession] =
      for
        acct    <- client.account
        topic   <- Topic[IO, Json]
        games   <- Ref.of[IO, Map[String, Fiber[IO, Throwable, Unit]]](Map.empty)
        chals   <- Ref.of[IO, Map[String, LichessChallenge]](Map.empty)
        session  = new LichessBotSession(client, cfg, GameRunner.Identity(acct.id, acct.username), topic, games, chals)
        _       <- session.runEventLoop.start
      yield session
    Resource.make(acquire)(_.shutdown)
