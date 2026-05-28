package chess.lichess.client

import cats.effect.IO
import cats.syntax.all.*
import chess.lichess.model.*
import io.circe.parser as JsonParser
import org.http4s.*
import org.http4s.Method.*
import org.http4s.client.Client
import org.http4s.headers.{Authorization, `User-Agent`}
import org.http4s.implicits.*

/** Thin wrapper around the Lichess HTTP API used by the bot service.
  *
  * All requests carry the `Bearer` token; all streaming endpoints return
  * `fs2.Stream`s of already-parsed model objects. NDJSON keep-alive blank
  * lines are silently dropped by `ndjsonLines`. */
final class LichessApiClient(
    client: Client[IO],
    baseUri: Uri,
    token: String,
):

  private val authHeader: Header.ToRaw =
    Authorization(Credentials.Token(AuthScheme.Bearer, token))

  private val ua: Header.ToRaw =
    `User-Agent`(ProductId("alu-chess-bot", Some("0.1")))

  private def authed(req: Request[IO]): Request[IO] = req.putHeaders(authHeader, ua)

  // ── Account ────────────────────────────────────────────────────────────

  def account: IO[LichessAccount] =
    val req = authed(Request[IO](GET, baseUri / "api" / "account"))
    client.expect[String](req).flatMap(parseJson[LichessAccount])

  // ── Streams (NDJSON) ──────────────────────────────────────────────────

  def streamEvents: fs2.Stream[IO, LichessEvent] =
    val req = authed(Request[IO](GET, baseUri / "api" / "stream" / "event"))
    ndjsonLines(req).evalMap(decode[LichessEvent])

  def streamBotGame(gameId: String): fs2.Stream[IO, LichessBotGameMessage] =
    val req = authed(Request[IO](GET, baseUri / "api" / "bot" / "game" / "stream" / gameId))
    ndjsonLines(req).evalMap(decode[LichessBotGameMessage])

  // ── Bot game actions ──────────────────────────────────────────────────

  def submitMove(gameId: String, uci: String): IO[Unit] =
    val req = authed(Request[IO](
      POST,
      baseUri / "api" / "bot" / "game" / gameId / "move" / uci,
    ))
    client.expect[String](req).void

  def resign(gameId: String): IO[Unit] =
    val req = authed(Request[IO](POST, baseUri / "api" / "bot" / "game" / gameId / "resign"))
    client.expect[String](req).void

  def abort(gameId: String): IO[Unit] =
    val req = authed(Request[IO](POST, baseUri / "api" / "bot" / "game" / gameId / "abort"))
    client.expect[String](req).void

  // ── Challenge actions ─────────────────────────────────────────────────

  def acceptChallenge(challengeId: String): IO[Unit] =
    val req = authed(Request[IO](POST, baseUri / "api" / "challenge" / challengeId / "accept"))
    client.expect[String](req).void

  def declineChallenge(challengeId: String, reason: String = "generic"): IO[Unit] =
    val req = authed(
      Request[IO](POST, baseUri / "api" / "challenge" / challengeId / "decline")
        .withEntity(UrlForm("reason" -> reason))
    )
    client.expect[String](req).void

  // ── Helpers ───────────────────────────────────────────────────────────

  private def ndjsonLines(req: Request[IO]): fs2.Stream[IO, String] =
    client.stream(req).flatMap { resp =>
      resp.body
        .through(fs2.text.utf8.decode)
        .through(fs2.text.lines)
        .filter(_.trim.nonEmpty)
    }

  private def decode[A: io.circe.Decoder](raw: String): IO[A] =
    IO.fromEither(JsonParser.decode[A](raw))

  private def parseJson[A: io.circe.Decoder](raw: String): IO[A] =
    IO.fromEither(JsonParser.decode[A](raw))

object LichessApiClient:
  def make(client: Client[IO], baseUrl: String, token: String): IO[LichessApiClient] =
    IO.fromEither(Uri.fromString(baseUrl))
      .map(uri => new LichessApiClient(client, uri, token))
