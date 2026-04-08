package chess.model.fen

import chess.model.{ChessError, Color, Game, GameStatus, Position}

/** Fast FEN parser using direct string operations.
  * This is the original implementation – O(n) single-pass, no allocations beyond the result. */
object FastFenParser extends FenParser:

  override def parseE(fen: String): Either[ChessError, Game] =
    val rawParts = fen.trim.split("\\s+")
    FenSharedLogic.normalizeFenParts(rawParts).flatMap { parts =>
      for
        board <- FenSharedLogic.parseBoardE(parts(0))
        color <- FenSharedLogic.parseColorE(parts(1))
      yield
        val castling    = parts(2)
        val movedPieces = FenSharedLogic.castlingToMovedPieces(castling)
        val epStr       = parts(3)
        val lastMove    = FenSharedLogic.parseEnPassant(epStr)
        val halfMoveClock  = if parts.length > 4 then parts(4).toIntOption.getOrElse(0) else 0
        val fullMoveNumber = if parts.length > 5 then parts(5).toIntOption.getOrElse(1) else 1
        val initial = Game(board, color, GameStatus.Playing, movedPieces, lastMove,
                          halfMoveClock, fullMoveNumber = fullMoveNumber)
        FenSharedLogic.computeInitialStatus(initial)
    }
