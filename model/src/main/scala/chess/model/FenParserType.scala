package chess.model

import chess.model.fen.{CombinatorFenParser, FastFenParser, FenParser, RegexFenParser}

/** Selects which FEN parser implementation is used by [[Fen]].
  *
  * Switch the active parser at any time:
  * {{{
  *   Fen.activeParser = FenParserType.Regex
  *   Fen.activeParser = FenParserType.Combinator
  *   Fen.activeParser = FenParserType.Fast   // back to default
  * }}}
  */
enum FenParserType:
  case Fast, Regex, Combinator

  def instance: FenParser = this match
    case Fast       => FastFenParser
    case Regex      => RegexFenParser
    case Combinator => CombinatorFenParser
