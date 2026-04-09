package chess.model.api

import cats.effect.*
import org.http4s.*
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityDecoder.*
import io.circe.*
import io.circe.syntax.*
import chess.model.*

object ModelRoutes:

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

  val routes: HttpRoutes[IO] = HttpRoutes.of[IO] {

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

    // GET /health
    case GET -> Root / "health" =>
      Ok(Json.obj("status" -> Json.fromString("ok"), "service" -> Json.fromString("model")))
  }
