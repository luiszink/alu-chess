package chess.model

import FenParserUtils.*

/** FEN parser using direct character-by-character processing.
  * Each rank is parsed with a foldLeft over its characters — no regex overhead. */
class FastFenParser extends FenParser:

  def parseE(fen: String): Either[ChessError, Game] =
    val rawParts = fen.trim.split("\\s+")
    normalizeFenParts(rawParts).flatMap { parts =>
      for
        board <- parseBoardE(parts(0))
        color <- parseColorE(parts(1))
      yield
        val castling    = parts(2)
        val movedPieces = castlingToMovedPieces(castling)
        val enPassantStr = if parts.length > 3 then parts(3)
          // $COVERAGE-OFF$ normalizeFenParts guarantees length >= 4
          else "-"
          // $COVERAGE-ON$
        val lastMove       = parseEnPassant(enPassantStr)
        val halfMoveClock  = if parts.length > 4 then parts(4).toIntOption.getOrElse(0) else 0
        val fullMoveNumber = if parts.length > 5 then parts(5).toIntOption.getOrElse(1) else 1
        val initial = Game(board, color, GameStatus.Playing, movedPieces, lastMove, halfMoveClock, fullMoveNumber = fullMoveNumber)
        computeInitialStatus(initial)
    }

  private def parseBoardE(placement: String): Either[ChessError, Board] =
    val ranks = placement.split('/')
    if ranks.length != 8 then Left(ChessError.InvalidFenFormat("Expected 8 ranks"))
    else
      val rows = ranks.reverse.map(parseRankE)
      rows.find(_.isLeft) match
        case Some(Left(err)) => Left(err)
        case _ =>
          val cells = rows.map(_.getOrElse(Vector.empty))
          Right(Board(cells.toVector))

  private def parseRankE(rank: String): Either[ChessError, Vector[Option[Piece]]] =
    val result = rank.foldLeft[Either[ChessError, Vector[Option[Piece]]]](Right(Vector.empty)) {
      case (Left(err), _) => Left(err)
      case (Right(acc), ch) if ch.isDigit =>
        val n = ch.asDigit
        if n < 1 || n > 8 then Left(ChessError.InvalidFenBoardRow(rank))
        else Right(acc ++ Vector.fill(n)(None))
      case (Right(acc), ch) =>
        charToPiece(ch) match
          case Some(p) => Right(acc :+ Some(p))
          case None    => Left(ChessError.InvalidFenPieceChar(ch))
    }
    result.filterOrElse(_.length == 8, ChessError.InvalidFenBoardRow(rank))
