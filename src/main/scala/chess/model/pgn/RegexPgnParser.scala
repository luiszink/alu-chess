package chess.model.pgn

import chess.model.ChessError

import scala.util.matching.Regex

/** Regex-based PGN parser.
  * Uses compiled regex patterns to extract tag pairs and movetext tokens. */
object RegexPgnParser extends PgnParser:

  private val TagRe: Regex = """\[(\w+)\s+"([^"]*)"\]""".r
  private val ResultRe: Regex = """(1-0|0-1|1/2-1/2|\*)""".r

  override def parseE(pgn: String): Either[ChessError, PgnGame] =
    val tags = TagRe.findAllMatchIn(pgn).map { m =>
      m.group(1) -> m.group(2)
    }.toMap

    val movetextRaw = TagRe.replaceAllIn(pgn, "").trim
    if movetextRaw.isEmpty && tags.isEmpty then
      return Left(ChessError.InvalidPgnFormat("Empty PGN input"))

    val result = PgnSharedLogic.extractResult(movetextRaw)
    val moves = PgnSharedLogic.extractSanTokens(movetextRaw)

    Right(PgnGame(tags, moves, result.orElse(tags.get("Result"))))
