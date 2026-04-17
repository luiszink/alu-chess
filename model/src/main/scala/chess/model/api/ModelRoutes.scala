package chess.model.api

import cats.effect.*
import scala.concurrent.duration.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.client.Client
import io.circe.*
import io.circe.syntax.*
import chess.model.*

object ModelRoutes:

  private type EngineFailure = (Status, Json)

  trait EngineProxy:
    def health: IO[Either[EngineFailure, Json]]
    def bestMove(payload: Json): IO[Either[EngineFailure, Json]]
    def evaluate(payload: Json): IO[Either[EngineFailure, Json]]

  private def serviceError(kind: String, message: String): Json = Json.obj(
    "error"   -> Json.fromString(kind),
    "message" -> Json.fromString(message),
  )

  private final class HttpEngineProxy(client: Client[IO]) extends EngineProxy:
    private val DefaultEngineBaseUrl = "http://localhost:8000"
    private val DefaultTimeout: FiniteDuration = 5.seconds

    private val engineBaseUrl: String =
      sys.env.getOrElse("ENGINE_BASE_URL", DefaultEngineBaseUrl).stripSuffix("/")

    private val timeout: FiniteDuration =
      sys.env
        .get("ENGINE_TIMEOUT_MS")
        .flatMap(_.toLongOption)
        .filter(_ > 0)
        .map(_.millis)
        .getOrElse(DefaultTimeout)

    private def decodeBody(resp: Response[IO]): IO[Json] =
      resp.as[String].map { raw =>
        val body = raw.trim
        if body.isEmpty then Json.obj()
        else io.circe.parser.parse(body).getOrElse(Json.obj(
          "error"   -> Json.fromString("NonJsonEngineResponse"),
          "message" -> Json.fromString(raw)
        ))
      }

    private def forward(method: Method, path: String, payload: Option[Json]): IO[Either[EngineFailure, Json]] =
      val target = s"$engineBaseUrl$path"
      Uri.fromString(target) match
        case Left(parseFailure) =>
          IO.pure(Left(Status.InternalServerError -> serviceError(
            "EngineConfigError",
            s"Invalid engine URL '$target': ${parseFailure.sanitized}"
          )))

        case Right(uri) =>
          val req0 = Request[IO](method = method, uri = uri)
          val req = payload.fold(req0)(json => req0.withEntity(json))

          client.run(req).use { resp =>
            decodeBody(resp).map { json =>
              if resp.status.isSuccess then Right(json)
              else Left(resp.status -> json)
            }
          }
            .timeoutTo(
              timeout,
              IO.pure(Left(Status.GatewayTimeout -> serviceError(
                "EngineTimeout",
                s"Engine request timed out after ${timeout.toMillis} ms"
              )))
            )
            .handleError(ex =>
              Left(Status.ServiceUnavailable -> serviceError(
                "EngineUnavailable",
                Option(ex.getMessage).getOrElse("Engine service unavailable")
              ))
            )

    override def health: IO[Either[EngineFailure, Json]] =
      forward(Method.GET, "/health", None)

    override def bestMove(payload: Json): IO[Either[EngineFailure, Json]] =
      forward(Method.POST, "/best-move", Some(payload))

    override def evaluate(payload: Json): IO[Either[EngineFailure, Json]] =
      forward(Method.POST, "/evaluate", Some(payload))

  private def gameToJson(game: Game): Json = Json.obj(
    "fen"            -> Json.fromString(Fen.toFen(game)),
    "status"         -> Json.fromString(game.status.toString),
    "currentPlayer"  -> Json.fromString(game.currentPlayer.toString),
    "halfMoveClock"  -> Json.fromInt(game.halfMoveClock),
    "fullMoveNumber" -> Json.fromInt(game.fullMoveNumber),
    "isTerminal"     -> Json.fromBoolean(game.status.isTerminal),
  )

  private def moveToJson(move: Move): Json = Json.obj(
    "from"      -> Json.fromString(move.from.toString),
    "to"        -> Json.fromString(move.to.toString),
    "promotion" -> move.promotion.map(c => Json.fromString(c.toString)).getOrElse(Json.Null),
  )

  private def errorResponse(err: ChessError): Json = Json.obj(
    "error"   -> Json.fromString(err.getClass.getSimpleName.stripSuffix("$")),
    "message" -> Json.fromString(err.message),
  )

  private def errorStatus(err: ChessError): Status = err match
    case _: ChessError.GameAlreadyOver => Status.Conflict
    case _: ChessError.NoPieceAtSource | _: ChessError.WrongColorPiece |
         _: ChessError.FriendlyFire | _: ChessError.IllegalMovePattern |
         _: ChessError.LeavesKingInCheck => Status.UnprocessableEntity
    case _ => Status.BadRequest

  private def validateFenInBody(body: Json): Either[ChessError, String] =
    for
      fen <- body.hcursor.get[String]("fen").left.map(_ => ChessError.InvalidFenFormat("Missing 'fen' field"))
      _   <- Fen.parseE(fen).map(_ => ())
    yield fen

  private def validateMovePayload(json: Json, fieldName: String): Either[ChessError, Unit] =
    for
      moveJson <- json.hcursor.get[Json](fieldName).left.map(_ => ChessError.InvalidMoveFormat(s"Engine response missing '$fieldName' field"))
      from     <- moveJson.hcursor.get[String]("from").left.map(_ => ChessError.InvalidMoveFormat(s"Engine response missing '$fieldName.from'"))
      to       <- moveJson.hcursor.get[String]("to").left.map(_ => ChessError.InvalidMoveFormat(s"Engine response missing '$fieldName.to'"))
      _        <- Position.fromStringE(from).map(_ => ())
      _        <- Position.fromStringE(to).map(_ => ())
      _        <- moveJson.hcursor.get[String]("promotion").toOption match
        case Some(promo) if promo.nonEmpty =>
          val ch = promo.head.toUpper
          if promo.length == 1 && "QRBN".contains(ch) then Right(())
          else Left(ChessError.InvalidPromotionPiece(ch))
        case _ => Right(())
    yield ()

  private def toProxyResponse(result: Either[EngineFailure, Json]): IO[Response[IO]] =
    result match
      case Right(json)          => Ok(json)
      case Left((status, json)) => IO.pure(Response[IO](status).withEntity(json))

  def routesWith(engineProxy: EngineProxy): HttpRoutes[IO] = HttpRoutes.of[IO] {

    // GET /api/model/new-game
    case GET -> Root / "api" / "model" / "new-game" =>
      Ok(gameToJson(Game.newGame))

    // POST /api/model/validate-move  { "fen": "...", "from": "e2", "to": "e4", "promotion": null }
    case req @ POST -> Root / "api" / "model" / "validate-move" =>
      req.as[Json].flatMap { body =>
        val c = body.hcursor
        val result = for
          fen  <- c.get[String]("fen").left.map(_ => ChessError.InvalidFenFormat("Missing 'fen' field"))
          from <- c.get[String]("from").left.map(_ => ChessError.InvalidMoveFormat("Missing 'from' field"))
          to   <- c.get[String]("to").left.map(_ => ChessError.InvalidMoveFormat("Missing 'to' field"))
          promo = c.get[String]("promotion").toOption.flatMap(_.headOption)
          game <- Fen.parseE(fen)
          fromPos <- Position.fromStringE(from)
          toPos   <- Position.fromStringE(to)
          move = Move(fromPos, toPos, promo)
          updated <- game.applyMoveE(move)
        yield updated

        result match
          case Right(game) => Ok(gameToJson(game))
          case Left(err)   => IO.pure(Response[IO](errorStatus(err)).withEntity(errorResponse(err)))
      }

    // POST /api/model/legal-moves  { "fen": "..." }
    case req @ POST -> Root / "api" / "model" / "legal-moves" =>
      req.as[Json].flatMap { body =>
        body.hcursor.get[String]("fen") match
          case Left(_) => BadRequest(errorResponse(ChessError.InvalidFenFormat("Missing 'fen' field")))
          case Right(fenStr) =>
            Fen.parseE(fenStr) match
              case Left(err) => BadRequest(errorResponse(err))
              case Right(game) =>
                val moves = MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove)
                Ok(Json.obj("moves" -> Json.fromValues(moves.map(moveToJson))))
      }

    // POST /api/model/legal-moves-for-square  { "fen": "...", "square": "e2" }
    case req @ POST -> Root / "api" / "model" / "legal-moves-for-square" =>
      req.as[Json].flatMap { body =>
        val c = body.hcursor
        val result = for
          fenStr <- c.get[String]("fen").left.map(_ => ChessError.InvalidFenFormat("Missing 'fen' field"))
          sq     <- c.get[String]("square").left.map(_ => ChessError.InvalidPositionString("Missing 'square' field"))
          game   <- Fen.parseE(fenStr)
          pos    <- Position.fromStringE(sq)
        yield (game, pos)

        result match
          case Left(err) => BadRequest(errorResponse(err))
          case Right((game, pos)) =>
            val allMoves = MoveValidator.legalMoves(game.board, game.currentPlayer, game.movedPieces, game.lastMove)
            val filtered = allMoves.filter(_.from == pos)
            val piece = game.board.cell(pos)
            Ok(Json.obj(
              "square" -> Json.fromString(pos.toString),
              "piece"  -> piece.map(p => Json.obj(
                "type"   -> Json.fromString(p.productPrefix),
                "color"  -> Json.fromString(p.color.toString),
                "symbol" -> Json.fromString(p.symbol.toString),
              )).getOrElse(Json.Null),
              "moves" -> Json.fromValues(filtered.map { m =>
                val isCapture = game.board.cell(m.to).isDefined
                Json.obj(
                  "to"        -> Json.fromString(m.to.toString),
                  "isCapture" -> Json.fromBoolean(isCapture),
                  "promotion" -> m.promotion.map(c => Json.fromString(c.toString)).getOrElse(Json.Null),
                )
              })
            ))
      }

    // POST /api/model/parse-fen  { "fen": "..." }
    case req @ POST -> Root / "api" / "model" / "parse-fen" =>
      req.as[Json].flatMap { body =>
        body.hcursor.get[String]("fen") match
          case Left(_) => BadRequest(errorResponse(ChessError.InvalidFenFormat("Missing 'fen' field")))
          case Right(fenStr) =>
            Fen.parseE(fenStr) match
              case Right(game) => Ok(gameToJson(game))
              case Left(err)   => BadRequest(errorResponse(err))
      }

    // POST /api/model/to-fen  { "fen": "..." } (accepts game JSON with fen)
    case req @ POST -> Root / "api" / "model" / "to-fen" =>
      req.as[Json].flatMap { body =>
        GameJson.fromJson(body) match
          case Right(game) => Ok(Json.obj("fen" -> Json.fromString(Fen.toFen(game))))
          case Left(err)   => BadRequest(errorResponse(err))
      }

    // POST /api/model/parse-pgn  { "pgn": "1. e4 e5 2. Nf3 ..." }
    case req @ POST -> Root / "api" / "model" / "parse-pgn" =>
      req.as[Json].flatMap { body =>
        body.hcursor.get[String]("pgn") match
          case Left(_) => BadRequest(errorResponse(ChessError.InvalidPgnFormat("Missing 'pgn' field")))
          case Right(pgnStr) =>
            Pgn.replayE(pgnStr) match
              case Right(game) => Ok(gameToJson(game))
              case Left(err)   => BadRequest(errorResponse(err))
      }

    // POST /api/model/to-pgn  { "fen": "..." }  (game → PGN string)
    case req @ POST -> Root / "api" / "model" / "to-pgn" =>
      req.as[Json].flatMap { body =>
        GameJson.fromJson(body) match
          case Right(game) => Ok(Json.obj("pgn" -> Json.fromString(Pgn.toPgn(game))))
          case Left(err)   => BadRequest(errorResponse(err))
      }

    // GET /api/model/test-positions
    case GET -> Root / "api" / "model" / "test-positions" =>
      val positions = TestPositions.positions.map { tp =>
        Json.obj(
          "name"        -> Json.fromString(tp.name),
          "fen"         -> Json.fromString(tp.fen),
          "description" -> Json.fromString(tp.description),
        )
      }
      Ok(Json.obj("positions" -> Json.fromValues(positions)))

    // GET /api/model/stockfish/health
    case GET -> Root / "api" / "model" / "stockfish" / "health" =>
      engineProxy.health.flatMap(toProxyResponse)

    // POST /api/model/stockfish/best-move  { "fen": "...", "thinkTimeMs": 1000, ... }
    case req @ POST -> Root / "api" / "model" / "stockfish" / "best-move" =>
      req.as[Json].flatMap { body =>
        validateFenInBody(body) match
          case Left(err) => BadRequest(errorResponse(err))
          case Right(_)  =>
            engineProxy.bestMove(body).flatMap {
              case Right(json) =>
                validateMovePayload(json, "move") match
                  case Right(_)  => Ok(json)
                  case Left(err) => BadGateway(serviceError("InvalidEngineResponse", err.message))
              case Left((status, json)) =>
                IO.pure(Response[IO](status).withEntity(json))
            }
      }

    // POST /api/model/stockfish/evaluate  { "fen": "...", "thinkTimeMs": 1000, ... }
    case req @ POST -> Root / "api" / "model" / "stockfish" / "evaluate" =>
      req.as[Json].flatMap { body =>
        validateFenInBody(body) match
          case Left(err) => BadRequest(errorResponse(err))
          case Right(_)  =>
            engineProxy.evaluate(body).flatMap {
              case Right(json) =>
                json.hcursor.get[Json]("bestMove").toOption match
                  case Some(_) =>
                    validateMovePayload(json, "bestMove") match
                      case Right(_)  => Ok(json)
                      case Left(err) => BadGateway(serviceError("InvalidEngineResponse", err.message))
                  case None => Ok(json)
              case Left((status, json)) =>
                IO.pure(Response[IO](status).withEntity(json))
            }
      }

    // GET /health
    case GET -> Root / "health" =>
      Ok(Json.obj("status" -> Json.fromString("ok"), "service" -> Json.fromString("model")))
  }

  def routesResource: Resource[IO, HttpRoutes[IO]] =
    EmberClientBuilder.default[IO].build.map(client => routesWith(new HttpEngineProxy(client)))
