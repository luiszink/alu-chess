package chess.model.fen

import chess.model.{ChessError, Color, Game, GameStatus, Position}

import scala.util.matching.Regex

/** Regex-based FEN parser.
  * Validates the overall FEN structure with compiled regex patterns, then delegates
  * per-field parsing to the shared logic utilities. */
object RegexFenParser extends FenParser:

  private val boardPat     = "[KQRBNPkqrbnp1-8/]+"
  private val colorPat     = "(w|b)"
  private val castlingPat  = "([KQkq]+|-)"
  private val epPat        = "([a-h][36]|-)"
  private val clkPat       = "(\\d+)"

  private val Full6Re: Regex =
    s"""^($boardPat)\\s+$colorPat\\s+$castlingPat\\s+$epPat\\s+$clkPat\\s+$clkPat$$""".r

  private val Full4Re: Regex =
    s"""^($boardPat)\\s+$colorPat\\s+$castlingPat\\s+$epPat$$""".r

  private val BoardOnlyRe: Regex =
    s"""^($boardPat)$$""".r

  override def parseE(fen: String): Either[ChessError, Game] =
    fen.trim match
      case Full6Re(board, color, castling, ep, half, full) =>
        buildGame(board, color, castling, ep,
                  half.toIntOption.getOrElse(0), full.toIntOption.getOrElse(1))
      case Full4Re(board, color, castling, ep) =>
        buildGame(board, color, castling, ep, 0, 1)
      case BoardOnlyRe(board) =>
        buildGame(board, "w", "-", "-", 0, 1)
      case _ =>
        Left(ChessError.InvalidFenFormat("FEN requires either 1 field (board only) or at least 4 fields"))

  private def buildGame(board: String, color: String, castling: String, ep: String,
                        half: Int, full: Int): Either[ChessError, Game] =
    for
      b <- FenSharedLogic.parseBoardE(board)
      c <- FenSharedLogic.parseColorE(color)
    yield
      val movedPieces = FenSharedLogic.castlingToMovedPieces(castling)
      val lastMove    = FenSharedLogic.parseEnPassant(ep)
      val g = Game(b, c, GameStatus.Playing, movedPieces, lastMove, half, fullMoveNumber = full)
      FenSharedLogic.computeInitialStatus(g)
