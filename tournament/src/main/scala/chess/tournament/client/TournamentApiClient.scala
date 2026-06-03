package chess.tournament.client

import cats.effect.IO
import chess.tournament.model.*
import io.circe.*
import io.circe.parser as JsonParser
import io.circe.syntax.*
import org.http4s.*
import org.http4s.Method.*
import org.http4s.client.Client
import org.http4s.headers.Authorization
import org.http4s.circe.*

/** HTTP client for the NowChess Tournament API.
  *
  * All authenticated requests carry the JWT returned by `register`.
  * NDJSON streams ignore blank keep-alive lines automatically. */
final class TournamentApiClient(
    client: Client[IO],
    baseUri: Uri,
):

  private var _token: String = ""
  private def auth: Header.ToRaw = Authorization(Credentials.Token(AuthScheme.Bearer, _token))
  private def authed(req: Request[IO]): Request[IO] = req.putHeaders(auth)

  // ── Auth ────────────────────────────────────────────────────────────────

  /** Register a bot identity and store the JWT for subsequent requests. */
  def register(name: String): IO[BotIdentity] =
    val body = Json.obj("name" -> Json.fromString(name), "isBot" -> Json.fromBoolean(true))
    val req  = Request[IO](POST, baseUri / "api" / "auth" / "register").withEntity(body)
    rawString(req).flatMap { raw =>
      parse[RegisterResponse](raw).flatMap { resp =>
        _token = resp.token
        IO.pure(BotIdentity(resp.id, name, resp.token))
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
  ): IO[Json] =
    val form = UrlForm(
      "name"           -> name,
      "nbRounds"       -> nbRounds.toString,
      "clockLimit"     -> clockLimit.toString,
      "clockIncrement" -> clockIncrement.toString,
      "format"         -> format,
    )
    val req = authed(Request[IO](POST, baseUri / "api" / "tournament").withEntity(form))
    rawString(req).flatMap(parseJson)

  def joinTournament(id: String): IO[Unit] =
    val req = authed(Request[IO](POST, baseUri / "api" / "tournament" / id / "join"))
    rawString(req).void

  def startTournament(id: String): IO[Json] =
    val req = authed(Request[IO](POST, baseUri / "api" / "tournament" / id / "start"))
    rawString(req).flatMap(parseJson)

  def getTournament(id: String): IO[Json] =
    val req = Request[IO](GET, baseUri / "api" / "tournament" / id)
    rawString(req).flatMap(parseJson)

  // ── Move submission ───────────────────────────────────────────────────────

  def submitMove(tournamentId: String, gameId: String, uci: String): IO[Unit] =
    val req = authed(Request[IO](
      POST,
      baseUri / "api" / "tournament" / tournamentId / "game" / gameId / "move" / uci,
    ))
    rawString(req).void

  // ── NDJSON Streams ───────────────────────────────────────────────────────

  def streamTournament(tournamentId: String): fs2.Stream[IO, TournamentEvent] =
    val req = authed(Request[IO](GET, baseUri / "api" / "tournament" / tournamentId / "stream"))
    ndjson(req).evalMap(decode[TournamentEvent])

  def streamGame(tournamentId: String, gameId: String): fs2.Stream[IO, GameEvent] =
    val req = authed(Request[IO](
      GET,
      baseUri / "api" / "tournament" / tournamentId / "game" / gameId / "stream",
    ))
    ndjson(req).evalMap(decode[GameEvent])

  // ── Internals ─────────────────────────────────────────────────────────────

  /** Read response body as raw UTF-8 string, bypassing any Circe EntityDecoder. */
  private def rawString(req: Request[IO]): IO[String] =
    client.run(req).use { resp =>
      resp.body.through(fs2.text.utf8.decode).compile.string.flatMap { body =>
        if resp.status.isSuccess then IO.pure(body)
        else IO.raiseError(new RuntimeException(s"${resp.status.code} ${resp.status.reason}: $body"))
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
              IO.raiseError(new RuntimeException(s"NDJSON stream ${resp.status.code}: $body"))
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
