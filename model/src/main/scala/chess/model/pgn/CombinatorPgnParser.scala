package chess.model.pgn

import chess.model.ChessError

import scala.util.parsing.combinator.RegexParsers

/** Parser-combinator PGN parser using scala-parser-combinators.
  * Defines the PGN grammar as composable parser rules, demonstrating the
  * combinator approach to parsing. */
object CombinatorPgnParser extends RegexParsers with PgnParser:

  override val skipWhitespace: Boolean = true

  private def tagValue: Parser[String] =
    "\"" ~> """[^"]*""".r <~ "\""

  private def tagPair: Parser[(String, String)] =
    "[" ~> """\w+""".r ~ tagValue <~ "]" ^^ { case k ~ v => (k, v) }

  private def tagSection: Parser[Map[String, String]] =
    rep(tagPair) ^^ (_.toMap)

  private def moveNumber: Parser[Unit] =
    """\d+\.+""".r ^^^ (())

  private def comment: Parser[Unit] =
    """\{[^}]*\}""".r ^^^ (())

  private def variation: Parser[Unit] =
    """\([^)]*\)""".r ^^^ (())

  private def resultToken: Parser[String] =
    "1-0" | "0-1" | "1/2-1/2" | "*"

  private def sanToken: Parser[String] =
    """[A-Za-z][A-Za-z0-9+#=\-]*""".r

  private def movetextToken: Parser[Option[String]] =
    (moveNumber ^^^ None) |
    (comment ^^^ None) |
    (variation ^^^ None) |
    (resultToken ^^ (r => Some(r))) |
    (sanToken ^^ (s => Some(s)))

  private def movetext: Parser[(Vector[String], Option[String])] =
    rep(movetextToken) ^^ { tokens =>
      val flat = tokens.flatten
      val result = flat.lastOption.filter(PgnSharedLogic.Results.contains)
      val moves = flat.filterNot(PgnSharedLogic.Results.contains).toVector
      (moves, result)
    }

  private def pgnP: Parser[PgnGame] =
    tagSection ~ movetext ^^ { case tags ~ mt =>
      val (moves, result) = mt
      PgnGame(tags, moves, result.orElse(tags.get("Result")))
    }

  override def parseE(pgn: String): Either[ChessError, PgnGame] =
    if pgn.trim.isEmpty then
      return Left(ChessError.InvalidPgnFormat("Empty PGN input"))
    parseAll(pgnP, pgn) match
      case Success(game, _) => Right(game)
      case Failure(msg, _)  => Left(ChessError.InvalidPgnFormat(msg))
      case Error(msg, _)    => Left(ChessError.InvalidPgnFormat(msg))
