package chess.model.pgn

import chess.model.{ChessError, Game}

/** Common interface for all PGN parser implementations. */
trait PgnParser:
  /** Parse a PGN string into a PgnGame (tags + SAN tokens). Returns Left with error detail on failure. */
  def parseE(pgn: String): Either[ChessError, PgnGame]

  /** Parse a PGN string into a PgnGame. Returns None on invalid input. */
  def parse(pgn: String): Option[PgnGame] = parseE(pgn).toOption

  /** Parse a PGN string and replay all moves, returning the final Game state. */
  def replayE(pgn: String): Either[ChessError, Game] =
    parseE(pgn).flatMap(PgnSharedLogic.replayMoves)
