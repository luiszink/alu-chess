package chess.model.fen

import chess.model.{Board, ChessError, Color, Game, GameStatus, Move, Piece, Position}

import scala.util.parsing.combinator.RegexParsers

/** Parser-combinator FEN parser using scala-parser-combinators.
  * Defines the FEN grammar as composable parser rules, demonstrating the
  * combinator approach to parsing. */
object CombinatorFenParser extends RegexParsers with FenParser:

  override val skipWhitespace: Boolean = false

  private def pieceChar: Parser[Piece] =
    "[KQRBNPkqrbnp]".r >> { s =>
      FenSharedLogic.charToPiece(s.head) match
        case Some(p) => success(p)
        case None    => failure(s"Unexpected piece character: $s")
    }

  private def emptySquares: Parser[Vector[Option[Piece]]] =
    "[1-8]".r ^^ (s => Vector.fill(s.head.asDigit)(None))

  private def rankToken: Parser[Vector[Option[Piece]]] =
    emptySquares | (pieceChar ^^ (p => Vector(Some(p))))

  private def rankLine: Parser[Vector[Option[Piece]]] =
    rep1(rankToken) >> { parts =>
      val cells = parts.flatten.toVector
      if cells.length == 8 then success(cells)
      else failure(s"Rank has ${cells.length} squares, expected 8")
    }

  private def boardP: Parser[Board] =
    rankLine ~ repN(7, "/" ~> rankLine) ^^ { case r0 ~ rest =>
      Board((r0 :: rest).reverse.toVector)
    }

  private def colorP: Parser[Color] =
    ("w" ^^^ Color.White) | ("b" ^^^ Color.Black)

  private def castlingP: Parser[String] =
    "[KQkq]+".r | "-"

  private def epP: Parser[Option[Move]] =
    ("[a-h][36]".r ^^ (s => FenSharedLogic.parseEnPassant(s))) |
    ("-" ^^^ None)

  private def intP: Parser[Int] =
    "\\d+".r ^^ (_.toInt)

  private def sp: Parser[String] = "\\s+".r

  private def fenP: Parser[Game] =
    boardP ~ (sp ~> colorP) ~ (sp ~> castlingP) ~ (sp ~> epP) ~ (sp ~> intP) ~ (sp ~> intP) ^^ {
      case b ~ c ~ cast ~ ep ~ half ~ full =>
        val movedPieces = FenSharedLogic.castlingToMovedPieces(cast)
        val g = Game(b, c, GameStatus.Playing, movedPieces, ep, half, fullMoveNumber = full)
        FenSharedLogic.computeInitialStatus(g)
    }

  override def parseE(fen: String): Either[ChessError, Game] =
    val rawParts = fen.trim.split("\\s+")
    FenSharedLogic.normalizeFenParts(rawParts) match
      case Left(err) => Left(err)
      case Right(normed) =>
        val half = normed.lift(4).getOrElse("0")
        val full = normed.lift(5).getOrElse("1")
        val normalized = s"${normed(0)} ${normed(1)} ${normed(2)} ${normed(3)} $half $full"
        parseAll(fenP, normalized) match
          case Success(game, _) => Right(game)
          case Failure(msg, _)  => Left(ChessError.InvalidFenFormat(msg))
          case Error(msg, _)    => Left(ChessError.InvalidFenFormat(msg))
