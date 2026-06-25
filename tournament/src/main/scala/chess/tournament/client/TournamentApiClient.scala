package chess.tournament.client

import cats.effect.IO
import chess.tournament.model.*
import io.circe.*
import io.circe.parser as JsonParser
import io.circe.syntax.*
import org.http4s.*
import org.http4s.Method.*
import org.http4s.client.Client
import org.http4s.headers.{Authorization, `Content-Type`}
import org.http4s.circe.*
import org.typelevel.ci.CIStringSyntax

/** HTTP client for the NowChess Tournament API.
  *
  * All authenticated requests carry the JWT returned by `register`.
  * NDJSON streams ignore blank keep-alive lines automatically. */
final class TournamentApiClient(
    client: Client[IO],
    val baseUri: Uri,
):

  private var _token: String = ""
  private var _identity: Option[BotIdentity] = None
  // Remembers the identity used for the last `register` so the client can
  // re-register itself if the upstream server rejects a cached token (e.g. after
  // a JWT secret rotation → "401 invalid signature").
  private var _lastRegister: Option[(String, Boolean)] = None
  def hasToken: Boolean = _token.nonEmpty
  def identity: Option[BotIdentity] = _identity
  private def auth: Header.ToRaw = Authorization(Credentials.Token(AuthScheme.Bearer, _token))
  private def authed(req: Request[IO]): Request[IO] =
    if hasToken then req.putHeaders(auth) else req

  /** Drop the cached token and register again with the remembered identity. */
  private def reRegister: IO[Unit] =
    _lastRegister match
      case Some((name, isBot)) =>
        _token = ""
        _identity = None
        register(name, isBot).void
      case None => IO.unit

  /** Run an authed call; on a 401 from the upstream server, re-register once and
    * retry exactly once. The block is by-name so the request is rebuilt with the
    * fresh token after re-registration. */
  private def withReauth[A](io: => IO[A]): IO[A] =
    io.handleErrorWith {
      case _: UnauthorizedException if _lastRegister.isDefined =>
        reRegister *> io
      case e => IO.raiseError(e)
    }

  /** NDJSON variant of [[withReauth]]: on a 401, re-register and re-subscribe once. */
  private def authedStream(build: => Request[IO]): fs2.Stream[IO, String] =
    ndjson(build).handleErrorWith {
      case _: UnauthorizedException if _lastRegister.isDefined =>
        fs2.Stream.eval(reRegister).drain ++ ndjson(build)
      case e => fs2.Stream.raiseError[IO](e)
    }

  private val forwardedRequestHeaders: Set[String] =
    Set("authorization", "content-type", "accept")

  private def endpoint(parts: String*): Uri =
    parts.foldLeft(baseUri)((uri, part) => uri / part)

  private def withForwardedHeaders(from: Request[IO], to: Request[IO]): Request[IO] =
    val headers = from.headers.headers.filter(h => forwardedRequestHeaders.contains(h.name.toString.toLowerCase))
    to.withHeaders(Headers(headers))

  /** Forward a normal request to the upstream tournament server and preserve status/body/content-type.
    *
    * This is intentionally not used for long-lived NDJSON streams, because those
    * must stay streaming instead of being buffered into memory.
    */
  def proxy(req: Request[IO], parts: String*): IO[Response[IO]] =
    val upstreamUri = endpoint(parts*).copy(query = req.uri.query)
    val upstreamReq =
      withForwardedHeaders(req, Request[IO](req.method, upstreamUri).withBodyStream(req.body))

    client.run(upstreamReq).use { resp =>
      resp.body.through(fs2.text.utf8.decode).compile.string.map { body =>
        val base = Response[IO](status = resp.status).withEntity(body)
        resp.headers.get[`Content-Type`].fold(base)(ct => base.withContentType(ct))
      }
    }

  def proxyStream(req: Request[IO], contentType: MediaType, parts: String*): IO[Response[IO]] =
    val upstreamUri = endpoint(parts*).copy(query = req.uri.query)
    val upstreamReq =
      withForwardedHeaders(req, Request[IO](req.method, upstreamUri).withBodyStream(req.body))
    client.run(upstreamReq).allocated.map { case (resp, release) =>
      val base =
        Response[IO](resp.status)
          .withBodyStream(resp.body.onFinalize(release))
      resp.headers
        .get[`Content-Type`]
        .fold(base.withContentType(`Content-Type`(contentType)))(ct => base.withContentType(ct))
    }

  // ── Auth ────────────────────────────────────────────────────────────────

  /** Passthrough: forward a raw register request body to the upstream server and return the raw JSON string. */
  def registerRaw(body: String, contentType: String): IO[String] =
    val req = Request[IO](POST, baseUri / "api" / "auth" / "register")
      .withEntity(body)
      .putHeaders(org.http4s.headers.`Content-Type`(MediaType.unsafeParse(contentType)))
    rawString(req)

  /** Register a bot identity and store the JWT for subsequent requests. */
  def register(name: String, isBot: Boolean = true): IO[BotIdentity] =
    val body = Json.obj("name" -> Json.fromString(name), "isBot" -> Json.fromBoolean(isBot))
    val req  = Request[IO](POST, baseUri / "api" / "auth" / "register").withEntity(body)
    rawString(req).flatMap { raw =>
      parse[RegisterResponse](raw).flatMap { resp =>
        _token = resp.token
        _identity = Some(BotIdentity(resp.id, name, resp.token))
        _lastRegister = Some((name, isBot))
        IO.pure(_identity.get)
      }
    }

  // ── Tournament list ──────────────────────────────────────────────────────

  def listTournaments: IO[TournamentListResponse] =
    val req = Request[IO](GET, baseUri / "api" / "tournament")
    client.run(req).use { resp =>
      val bodyStr = resp.body.through(fs2.text.utf8.decode).compile.string
      if resp.status.isSuccess then
        bodyStr.flatMap(parse[TournamentListResponse])
      else if resp.status == Status.NotFound then
        IO.pure(TournamentListResponse(Nil, Nil, Nil))
      else
        bodyStr.flatMap(body =>
          IO.raiseError(new RuntimeException(s"${resp.status.code} ${resp.status.reason}: $body"))
        )
    }

  // ── Tournament lifecycle ─────────────────────────────────────────────────

  def createTournament(
      name: String,
      nbRounds: Int,
      clockLimit: Int,
      clockIncrement: Int,
      format: String = "swiss",
      rated: Option[Boolean] = None,
      startPosition: Option[String] = None,
      matchesPerPairing: Option[Int] = None,
      groupSize: Option[Int] = None,
      opening: Option[String] = None,
      bots: Option[String] = None,
      maxConcurrentGames: Option[Int] = None,
      openings: Option[String] = None,
  ): IO[Json] =
    val fields =
      List(
        "name"           -> name,
        "nbRounds"       -> nbRounds.toString,
        "clockLimit"     -> clockLimit.toString,
        "clockIncrement" -> clockIncrement.toString,
        "format"         -> format,
      ) ++
        rated.map(v => "rated" -> v.toString) ++
        startPosition.map(v => "startPosition" -> v) ++
        matchesPerPairing.map(v => "matchesPerPairing" -> v.toString) ++
        groupSize.map(v => "groupSize" -> v.toString) ++
        opening.map(v => "opening" -> v) ++
        bots.map(v => "bots" -> v) ++
        maxConcurrentGames.map(v => "maxConcurrentGames" -> v.toString) ++
        openings.map(v => "openings" -> v)
    val form = UrlForm(fields*)
    withReauth {
      val req = authed(Request[IO](POST, baseUri / "api" / "tournament").withEntity(form))
      rawString(req)
    }.flatMap(parseJson)

  def joinTournament(id: String): IO[Unit] =
    withReauth {
      val req = authed(Request[IO](POST, baseUri / "api" / "tournament" / id / "join"))
      rawString(req)
    }.void

  def startTournament(id: String): IO[Json] =
    withReauth {
      val req = authed(Request[IO](POST, baseUri / "api" / "tournament" / id / "start"))
      rawString(req)
    }.flatMap(parseJson)

  def getTournament(id: String): IO[Json] =
    val req = Request[IO](GET, baseUri / "api" / "tournament" / id)
    rawString(req).flatMap(parseJson)

  def deleteTournament(id: String): IO[Unit] =
    withReauth {
      val req = authed(Request[IO](DELETE, baseUri / "api" / "tournament" / id))
      rawString(req)
    }.void

  def withdrawTournament(id: String): IO[Unit] =
    withReauth {
      val req = authed(Request[IO](POST, baseUri / "api" / "tournament" / id / "withdraw"))
      rawString(req)
    }.void

  def addParticipant(tournamentId: String, botId: String): IO[Unit] =
    val body = Json.obj("botId" -> Json.fromString(botId))
    withReauth {
      val req = authed(Request[IO](POST, baseUri / "api" / "tournament" / tournamentId / "participants").withEntity(body))
      rawString(req)
    }.void

  def roundPairings(id: String, round: Int): IO[Json] =
    val req = Request[IO](GET, baseUri / "api" / "tournament" / id / "round" / round.toString)
    rawString(req).flatMap(parseJson)

  def getGame(tournamentId: String, gameId: String): IO[Json] =
    val req = Request[IO](GET, baseUri / "api" / "tournament" / tournamentId / "game" / gameId)
    rawString(req).flatMap(parseJson)

  // ── Move submission ───────────────────────────────────────────────────────

  def submitMove(tournamentId: String, gameId: String, uci: String): IO[Unit] =
    withReauth {
      val req = authed(Request[IO](
        POST,
        baseUri / "api" / "tournament" / tournamentId / "game" / gameId / "move" / uci,
      ))
      rawString(req)
    }.void

  // ── NDJSON Streams ───────────────────────────────────────────────────────

  def streamTournament(tournamentId: String): fs2.Stream[IO, TournamentEvent] =
    authedStream(authed(Request[IO](GET, baseUri / "api" / "tournament" / tournamentId / "stream")))
      .evalMap(decode[TournamentEvent])

  def streamGame(tournamentId: String, gameId: String): fs2.Stream[IO, GameEvent] =
    authedStream(authed(Request[IO](
      GET,
      baseUri / "api" / "tournament" / tournamentId / "game" / gameId / "stream",
    ))).evalMap(decode[GameEvent])

  def results(tournamentId: String, nb: Option[Int]): fs2.Stream[IO, String] =
    val uri0 = baseUri / "api" / "tournament" / tournamentId / "results"
    val uri  = nb.fold(uri0)(n => uri0.withQueryParam("nb", n))
    ndjson(Request[IO](GET, uri))

  def exportGames(tournamentId: String, ndjsonFormat: Boolean): IO[String] =
    val accept =
      if ndjsonFormat then "application/x-ndjson" else "application/x-chess-pgn"
    val req = Request[IO](GET, baseUri / "api" / "tournament" / tournamentId / "export" / "games")
      .putHeaders(Header.Raw(ci"Accept", accept))
    rawString(req)

  def analyticsExport(tournamentId: String): IO[AnalyticsExport] =
    val req = Request[IO](GET, baseUri / "api" / "tournament" / tournamentId / "analytics-export")
    rawString(req).flatMap(parse[AnalyticsExport])

  /** Raw JSON proxy — used by the route to avoid re-serialisation. */
  def getAnalyticsExport(tournamentId: String): IO[Json] =
    val req = Request[IO](GET, baseUri / "api" / "tournament" / tournamentId / "analytics-export")
    rawString(req).flatMap(parseJson)

  def streamTournamentRaw(tournamentId: String): fs2.Stream[IO, String] =
    authedStream(authed(Request[IO](GET, baseUri / "api" / "tournament" / tournamentId / "stream")))

  def streamGameRaw(tournamentId: String, gameId: String): fs2.Stream[IO, String] =
    authedStream(authed(Request[IO](
      GET,
      baseUri / "api" / "tournament" / tournamentId / "game" / gameId / "stream",
    )))

  // ── Internals ─────────────────────────────────────────────────────────────

  /** Read response body as raw UTF-8 string, bypassing any Circe EntityDecoder. */
  private def rawString(req: Request[IO]): IO[String] =
    client.run(req).use { resp =>
      resp.body.through(fs2.text.utf8.decode).compile.string.flatMap { body =>
        val msg = s"${resp.status.code} ${resp.status.reason}: $body"
        if resp.status.isSuccess then IO.pure(body)
        else if resp.status == Status.Unauthorized then IO.raiseError(new UnauthorizedException(msg))
        else IO.raiseError(new RuntimeException(msg))
      }
    }

  private def ndjson(req: Request[IO]): fs2.Stream[IO, String] =
    client
      .stream(req)
      .flatMap { resp =>
        if resp.status.isSuccess then
          resp.body.through(fs2.text.utf8.decode).through(fs2.text.lines)
        else
          fs2.Stream.eval(
            resp.body.through(fs2.text.utf8.decode).compile.string.flatMap { body =>
              val msg = s"NDJSON stream ${resp.status.code}: $body"
              if resp.status == Status.Unauthorized then IO.raiseError(new UnauthorizedException(msg))
              else IO.raiseError(new RuntimeException(msg))
            }
          ).drain
      }
      .filter(_.trim.nonEmpty)

  private def parse[A: Decoder](raw: String): IO[A] =
    IO.fromEither(JsonParser.decode[A](raw).left.map(e => new RuntimeException(e.getMessage)))

  private def parseJson(raw: String): IO[Json] =
    IO.fromEither(JsonParser.parse(raw).left.map(e => new RuntimeException(e.getMessage)))

  private def decode[A: Decoder](line: String): IO[A] =
    IO.fromEither(JsonParser.decode[A](line).left.map(e => new RuntimeException(s"Decode error: ${e.getMessage} on: $line")))

/** Raised when the upstream tournament server responds with 401 Unauthorized
  * (e.g. a cached JWT signed with a now-rotated secret → "invalid signature").
  * Callers re-register and retry once when they see this. */
final class UnauthorizedException(message: String) extends RuntimeException(message)
