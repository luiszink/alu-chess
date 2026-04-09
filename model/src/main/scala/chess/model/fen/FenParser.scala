package chess.model.fen

import chess.model.{Game, ChessError}

/** Common interface for all FEN parser implementations. */
trait FenParser:
  /** Parse a FEN string into a Game. Returns Left with error detail on failure. */
  def parseE(fen: String): Either[ChessError, Game]

  /** Parse a FEN string into a Game. Returns Failure with exception on invalid input. */
  def parseT(fen: String): scala.util.Try[Game] =
    parseE(fen).left.map(e => new Exception(e.message)).toTry

  /** Parse a FEN string into a Game. Returns None on invalid input. */
  def parse(fen: String): Option[Game] = parseE(fen).toOption
