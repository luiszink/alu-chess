package chess.model.pgn

import chess.model.ChessError

/** Fast PGN parser using direct string operations.
  * Single-pass line-by-line parsing, no regex compilation overhead. */
object FastPgnParser extends PgnParser:

  override def parseE(pgn: String): Either[ChessError, PgnGame] =
    val lines = pgn.linesIterator.toVector
    val (tagLines, rest) = lines.span(_.startsWith("["))

    val tags = tagLines.flatMap(parseSingleTag).toMap

    val movetext = rest.mkString(" ").trim
    if movetext.isEmpty && tags.isEmpty then
      return Left(ChessError.InvalidPgnFormat("Empty PGN input"))

    val result = PgnSharedLogic.extractResult(movetext)
    val moves = PgnSharedLogic.extractSanTokens(movetext)

    Right(PgnGame(tags, moves, result.orElse(tags.get("Result"))))

  private def parseSingleTag(line: String): Option[(String, String)] =
    val trimmed = line.trim
    if !trimmed.startsWith("[") || !trimmed.endsWith("]") then return None
    val inner = trimmed.drop(1).dropRight(1).trim
    val spaceIdx = inner.indexOf(' ')
    if spaceIdx < 0 then return None
    val key = inner.substring(0, spaceIdx)
    val rawValue = inner.substring(spaceIdx + 1).trim
    if rawValue.startsWith("\"") && rawValue.endsWith("\"") then
      Some(key -> rawValue.drop(1).dropRight(1))
    else None
