package chess.model

import scala.util.parsing.combinator.RegexParsers

/** FEN parser using parser combinators (scala-parser-combinators).
  * The grammar lives in FenCombinatorGrammar so that RegexParsers path-
  * dependent types (Parser, Success, NoSuccess) are always top-level and
  * unambiguous. FenCombinatorParser itself only implements FenParser. */
class FenCombinatorParser extends FenParser:

  def parseE(fen: String): Either[ChessError, Game] =
    FenCombinatorGrammar.doParse(fen)

// ---------------------------------------------------------------------------

/** Grammar object: owns the RegexParsers context so all path-dependent types
  * (Parser[T], Success, NoSuccess, ^^, ~>, repN …) are unambiguously in scope. */
private object FenCombinatorGrammar extends RegexParsers:

  import FenParserUtils.*

  override val skipWhitespace: Boolean = false

  // --- public entry point ---

  def doParse(fen: String): Either[ChessError, Game] =
    val trimmed = fen.trim
    val input = if !trimmed.contains(' ') then
      s"$trimmed $DefaultColor $DefaultCastling $DefaultEnPassant"
    else trimmed
    parseAll(fenP, input) match
      case Success(result, _) => result
      case Failure(msg, _)    => Left(ChessError.InvalidFenFormat(msg))
      case Error(msg, _)      => Left(ChessError.InvalidFenFormat(msg))

  // --- grammar rules ---

  /** One rank token: any non-slash, non-whitespace characters. */
  private def rankToken: Parser[Either[ChessError, Vector[Option[Piece]]]] =
    "[^/\\s]+".r ^^ expandRank

  /** Eight ranks separated by '/'. */
  private def boardP: Parser[Either[ChessError, Board]] =
    rankToken ~ repN(7, "/" ~> rankToken) ^^ { case r0 ~ rest =>
      val allRanks = (r0 :: rest).reverse   // row 0 = rank 1 (bottom)
      val errors = allRanks.collect { case Left(e) => e }
      if errors.nonEmpty then Left(errors.head)
      else Right(Board(allRanks.map(_.getOrElse(Vector.empty)).toVector))
    }

  /** Active color token. */
  private def colorP: Parser[Either[ChessError, Color]] =
    "\\S+".r ^^ parseColorE

  /** Castling availability (raw string; semantics handled by shared util). */
  private def castlingP: Parser[String] = "\\S+".r

  /** En-passant target square (raw string). */
  private def enPassantP: Parser[String] = "\\S+".r

  /** Optional half-move and full-move clocks. */
  private def clocksP: Parser[(Int, Int)] =
    " " ~> "\\d+".r ~ (" " ~> "\\d+".r) ^^ { case hm ~ fm =>
      (hm.toIntOption.getOrElse(0), fm.toIntOption.getOrElse(1))
    }

  /** Full FEN grammar. */
  private def fenP: Parser[Either[ChessError, Game]] =
    boardP ~ " " ~ colorP ~ " " ~ castlingP ~ " " ~ enPassantP ~ opt(clocksP) ^^ {
      case boardE ~ _ ~ colorE ~ _ ~ castling ~ _ ~ ep ~ clocks =>
        for
          board <- boardE
          color <- colorE
        yield
          val movedPieces    = castlingToMovedPieces(castling)
          val lastMove       = parseEnPassant(ep)
          val halfMoveClock  = clocks.map(_._1).getOrElse(0)
          val fullMoveNumber = clocks.map(_._2).getOrElse(1)
          val initial = Game(board, color, GameStatus.Playing, movedPieces, lastMove, halfMoveClock, fullMoveNumber = fullMoveNumber)
          computeInitialStatus(initial)
    }

  // --- helpers ---

  /** Expand a rank string into Option[Piece] cells, preserving specific error types. */
  private def expandRank(rank: String): Either[ChessError, Vector[Option[Piece]]] =
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
