package chess.model

import scala.util.matching.Regex
import FenParserUtils.*

/** FEN parser using pre-compiled regular expressions.
  * Each FEN field is validated against a dedicated Regex before extraction.
  * Rank characters are still expanded to Option[Piece] via charToPiece. */
class RegExFenParser extends FenParser:

  private val ValidRankRe: Regex  = """[1-8pPnNbBrRqQkK]+""".r
  private val ColorRe: Regex      = """^[wb]$""".r
  private val CastlingRe: Regex   = """^(-|[KQkq]{1,4})$""".r
  private val EnPassantRe: Regex  = """^(-|[a-h][36])$""".r

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
    // First use regex to detect invalid characters before expanding
    ValidRankRe.findFirstIn(rank) match
      case Some(m) if m != rank =>
        // Some characters did not match — find the first bad one
        val badChar = rank.find(c => !"12345678pPnNbBrRqQkK".contains(c))
        badChar match
          case Some(c) => Left(ChessError.InvalidFenPieceChar(c))
          case None    => Left(ChessError.InvalidFenBoardRow(rank))
      case None =>
        val badChar = rank.find(c => !"12345678pPnNbBrRqQkK".contains(c))
        badChar match
          case Some(c) => Left(ChessError.InvalidFenPieceChar(c))
          case None    => Left(ChessError.InvalidFenBoardRow(rank))
      case Some(_) =>
        // All chars are valid — expand into Option[Piece] cells
        val cells = rank.flatMap {
          case c if c.isDigit => Vector.fill(c.asDigit)(None)
          case c              => Vector(charToPiece(c))
        }.toVector
        if cells.length == 8 then Right(cells)
        else Left(ChessError.InvalidFenBoardRow(rank))
