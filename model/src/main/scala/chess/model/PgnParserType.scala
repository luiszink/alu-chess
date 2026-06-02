package chess.model

import chess.model.pgn.{CombinatorPgnParser, FastPgnParser, PgnParser, RegexPgnParser}

/** Selects which PGN parser implementation is used by [[Pgn]].
  *
  * Switch the active parser at any time:
  * {{{
  *   Pgn.activeParser = PgnParserType.Regex
  *   Pgn.activeParser = PgnParserType.Combinator
  *   Pgn.activeParser = PgnParserType.Fast   // back to default
  * }}}
  */
enum PgnParserType:
  case Fast, Regex, Combinator

  def instance: PgnParser = this match
    case Fast       => FastPgnParser
    case Regex      => RegexPgnParser
    case Combinator => CombinatorPgnParser
