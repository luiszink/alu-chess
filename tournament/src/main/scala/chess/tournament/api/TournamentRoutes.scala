package chess.tournament.api

import cats.effect.{IO, Ref}
import cats.effect.std.Queue
import cats.syntax.traverse.*
import chess.tournament.bot.TournamentBot
import chess.tournament.client.TournamentApiClient
import chess.tournament.config.TournamentConfig
import chess.tournament.model.*
import fs2.Stream
import io.circe.{Decoder, Json}
import org.http4s.*
import org.http4s.ServerSentEvent
import org.http4s.circe.CirceEntityCodec.*
import org.http4s.dsl.io.*
import org.http4s.headers.`Content-Type`

import scala.concurrent.duration.*

private final case class CreateRequest(
    name: String,
    nbRounds: Int,
    clockLimit: Int,
    clockIncrement: Int,
    format: Option[String] = None,
    rated: Option[Boolean] = None,
    startPosition: Option[String] = None,
    matchesPerPairing: Option[Int] = None,
    groupSize: Option[Int] = None,
) derives Decoder

object TournamentRoutes:

  private object NbQueryParamMatcher extends OptionalQueryParamDecoderMatcher[Int]("nb")

  private val ndjsonMedia: MediaType = MediaType.unsafeParse("application/x-ndjson")
  private val pgnMedia: MediaType    = MediaType.unsafeParse("application/x-chess-pgn")

  def apply(
      cfg: TournamentConfig,
      directorClient: TournamentApiClient,
      botClient: TournamentApiClient,
      statusRef: Ref[IO, BotStatus],
      logQueue: Queue[IO, String],
  ): HttpRoutes[IO] =

    def ensureDirector: IO[BotIdentity] =
      directorClient.identity match
        case Some(identity) => IO.pure(identity)
        case None           => directorClient.register(cfg.directorName, isBot = false)

    def ensureBot: IO[Unit] =
      if botClient.hasToken then IO.unit
      else botClient.register(cfg.botName, isBot = true).void

    def upstreamError(e: Throwable): IO[Response[IO]] =
      BadGateway(Json.obj(
        "error"  -> Json.fromString("TournamentUpstreamError"),
        "server" -> Json.fromString(cfg.serverUrl),
        "detail" -> Json.fromString(e.getMessage),
      ))

    def create(req: CreateRequest): IO[Json] =
      ensureDirector *> directorClient.createTournament(
        name              = req.name,
        nbRounds          = req.nbRounds,
        clockLimit        = req.clockLimit,
        clockIncrement    = req.clockIncrement,
        format            = req.format.getOrElse("swiss"),
        rated             = req.rated,
        startPosition     = req.startPosition,
        matchesPerPairing = req.matchesPerPairing,
        groupSize         = req.groupSize,
      )

    def createFromForm(form: UrlForm): Either[String, CreateRequest] =
      def value(key: String): Option[String] =
        form.values.get(key).flatMap(_.headOption).map(_.trim).filter(_.nonEmpty)
      def requiredString(key: String): Either[String, String] =
        value(key).toRight(s"Missing '$key'")
      def requiredInt(key: String): Either[String, Int] =
        requiredString(key).flatMap(_.toIntOption.toRight(s"Invalid integer '$key'"))
      def optionalInt(key: String): Either[String, Option[Int]] =
        value(key).traverse(_.toIntOption.toRight(s"Invalid integer '$key'"))
      def optionalBoolean(key: String): Either[String, Option[Boolean]] =
        value(key).traverse(_.toBooleanOption.toRight(s"Invalid boolean '$key'"))

      for
        name              <- requiredString("name")
        nbRounds          <- requiredInt("nbRounds")
        clockLimit        <- requiredInt("clockLimit")
        clockIncrement    <- requiredInt("clockIncrement")
        matchesPerPairing <- optionalInt("matchesPerPairing")
        groupSize         <- optionalInt("groupSize")
        rated             <- optionalBoolean("rated")
      yield CreateRequest(
        name              = name,
        nbRounds          = nbRounds,
        clockLimit        = clockLimit,
        clockIncrement    = clockIncrement,
        format            = value("format"),
        rated             = rated,
        startPosition     = value("startPosition"),
        matchesPerPairing = matchesPerPairing,
        groupSize         = groupSize,
      )

    def statusJson: IO[Json] =
      statusRef.get.map {
        case BotStatus.Idle =>
          Json.obj("status" -> Json.fromString("idle"))
        case BotStatus.InTournament(id, round, games) =>
          Json.obj(
            "status"       -> Json.fromString("playing"),
            "tournamentId" -> Json.fromString(id),
            "round"        -> Json.fromInt(round),
            "gamesActive"  -> Json.fromInt(games),
          )
        case BotStatus.Error(msg) =>
          Json.obj("status" -> Json.fromString("error"), "message" -> Json.fromString(msg))
      }

    def listForUi: IO[Json] =
      ensureDirector *> directorClient.listTournaments.map { resp =>
        val directorId = directorClient.identity.map(_.id)
        val toArr = (ts: List[TournamentInfo]) => Json.arr(ts.map { t =>
          val canStart = t.status.contains("created") && directorId.exists(id => t.createdBy.contains(id))
          Json.obj(
            "id"        -> Json.fromString(t.id),
            "name"      -> Json.fromString(t.fullName),
            "status"    -> Json.fromString(t.status.getOrElse("unknown")),
            "players"   -> Json.fromInt(t.nbPlayers.getOrElse(0)),
            "rounds"    -> Json.fromInt(t.nbRounds.getOrElse(0)),
            "format"    -> Json.fromString(t.format.getOrElse("swiss")),
            "createdBy" -> t.createdBy.map(Json.fromString).getOrElse(Json.Null),
            "canStart"  -> Json.fromBoolean(canStart),
          )
        }*)
        Json.obj(
          "created"  -> toArr(resp.created),
          "started"  -> toArr(resp.started),
          "finished" -> toArr(resp.finished),
        )
      }

    def ndjson(lines: Stream[IO, String]): IO[Response[IO]] =
      Ok(lines.map(line => s"$line\n").through(fs2.text.utf8.encode))
        .map(_.withContentType(`Content-Type`(ndjsonMedia)))

    HttpRoutes.of[IO] {

      case GET -> Root / "api" / "tournament" / "ui" =>
        val loader = Thread.currentThread().getContextClassLoader
        Option(loader.getResourceAsStream("tournament-ui.html")) match
          case Some(html) =>
            val body = new String(html.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            Ok(body).map(_.withContentType(`Content-Type`(MediaType.text.html)))
          case None =>
            NotFound(Json.obj("error" -> Json.fromString("tournament-ui.html not found")))

      case GET -> Root / "api" / "tournament" / "status" =>
        statusJson.flatMap(Ok(_))

      case GET -> Root / "api" / "tournament" / "logs" =>
        val events: Stream[IO, ServerSentEvent] =
          Stream
            .fromQueueUnterminated(logQueue)
            .map(msg => ServerSentEvent(data = Some(msg)))
            .merge(Stream.awakeEvery[IO](15.seconds).map(_ => ServerSentEvent(comment = Some("keep-alive"))))
        Ok(events)

      case GET -> Root / "api" / "tournament" / "list" =>
        listForUi.flatMap(Ok(_)).handleErrorWith(upstreamError)

      case GET -> Root / "api" / "tournament" =>
        listForUi.flatMap(Ok(_)).handleErrorWith(upstreamError)

      case req @ POST -> Root / "api" / "tournament" / "create" =>
        req.as[CreateRequest].flatMap(create).flatMap(Ok(_)).handleErrorWith(upstreamError)

      case req @ POST -> Root / "api" / "tournament" =>
        req.as[UrlForm].attempt.flatMap {
          case Left(_) =>
            BadRequest(Json.obj("error" -> Json.fromString("Expected application/x-www-form-urlencoded body")))
          case Right(form) =>
            createFromForm(form) match
              case Left(msg)  => BadRequest(Json.obj("error" -> Json.fromString(msg)))
              case Right(req) => create(req).flatMap(Created(_)).handleErrorWith(upstreamError)
        }

      case GET -> Root / "api" / "tournament" / "info" / id =>
        directorClient.getTournament(id).flatMap(Ok(_)).handleErrorWith(upstreamError)

      case GET -> Root / "api" / "tournament" / id =>
        directorClient.getTournament(id).flatMap(Ok(_)).handleErrorWith(upstreamError)

      case DELETE -> Root / "api" / "tournament" / id =>
        (ensureDirector *> directorClient.deleteTournament(id)).flatMap(_ => NoContent()).handleErrorWith(upstreamError)

      case POST -> Root / "api" / "tournament" / "join" / id =>
        (ensureBot *> botClient.joinTournament(id)).flatMap(_ => Ok(Json.obj("ok" -> Json.fromBoolean(true)))).handleErrorWith(upstreamError)

      case POST -> Root / "api" / "tournament" / id / "join" =>
        (ensureBot *> botClient.joinTournament(id)).flatMap(_ => Ok(Json.obj("ok" -> Json.fromBoolean(true)))).handleErrorWith(upstreamError)

      case POST -> Root / "api" / "tournament" / "start" / id =>
        (ensureDirector *> directorClient.startTournament(id)).flatMap(Ok(_)).handleErrorWith(upstreamError)

      case POST -> Root / "api" / "tournament" / id / "start" =>
        (ensureDirector *> directorClient.startTournament(id)).flatMap(Ok(_)).handleErrorWith(upstreamError)

      case POST -> Root / "api" / "tournament" / id / "withdraw" =>
        (ensureBot *> botClient.withdrawTournament(id)).flatMap(_ => Ok(Json.obj("ok" -> Json.fromBoolean(true)))).handleErrorWith(upstreamError)

      case req @ POST -> Root / "api" / "tournament" / id / "participants" =>
        req.as[Json].flatMap { body =>
          body.hcursor.get[String]("botId") match
            case Left(_) => BadRequest(Json.obj("error" -> Json.fromString("Missing 'botId'")))
            case Right(botId) =>
              (ensureDirector *> directorClient.addParticipant(id, botId))
                .flatMap(_ => Ok(Json.obj("ok" -> Json.fromBoolean(true))))
                .handleErrorWith(upstreamError)
        }

      case GET -> Root / "api" / "tournament" / id / "results" :? NbQueryParamMatcher(nb) =>
        ndjson(directorClient.results(id, nb)).handleErrorWith(upstreamError)

      case GET -> Root / "api" / "tournament" / id / "round" / IntVar(round) =>
        directorClient.roundPairings(id, round).flatMap(Ok(_)).handleErrorWith(upstreamError)

      case req @ GET -> Root / "api" / "tournament" / id / "export" / "games" =>
        val wantsNdjson =
          req.headers.headers.exists(h =>
            h.name.toString.equalsIgnoreCase("Accept") && h.value.contains("application/x-ndjson")
          )
        directorClient.exportGames(id, wantsNdjson)
          .flatMap { body =>
            Ok(body).map(_.withContentType(`Content-Type`(if wantsNdjson then ndjsonMedia else pgnMedia)))
          }
          .handleErrorWith(upstreamError)

      case GET -> Root / "api" / "tournament" / id / "analytics-export" =>
        directorClient.getAnalyticsExport(id).flatMap(Ok(_)).handleErrorWith(upstreamError)

      case GET -> Root / "api" / "tournament" / id / "stream" =>
        ensureBot *> ndjson(botClient.streamTournamentRaw(id)).handleErrorWith(upstreamError)

      case GET -> Root / "api" / "tournament" / id / "game" / gameId =>
        directorClient.getGame(id, gameId).flatMap(Ok(_)).handleErrorWith(upstreamError)

      case GET -> Root / "api" / "tournament" / id / "game" / gameId / "stream" =>
        ensureBot *> ndjson(botClient.streamGameRaw(id, gameId)).handleErrorWith(upstreamError)

      case POST -> Root / "api" / "tournament" / id / "game" / gameId / "move" / uci =>
        (ensureBot *> botClient.submitMove(id, gameId, uci)).flatMap(_ => Ok(Json.obj("ok" -> Json.fromBoolean(true)))).handleErrorWith(upstreamError)

      case POST -> Root / "api" / "tournament" / "connect" / id =>
        statusRef.get.flatMap {
          case BotStatus.InTournament(running, _, _) if running == id =>
            Conflict(Json.obj("error" -> Json.fromString(s"Already connected to tournament $id")))
          case _ =>
            TournamentBot
              .run(botClient, cfg, id, logQueue, statusRef)
              .start
              .flatMap(_ => Ok(Json.obj("ok" -> Json.fromBoolean(true), "tournamentId" -> Json.fromString(id))))
        }
    }
