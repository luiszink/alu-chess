package chess.model

/** Forsyth-Edwards Notation (FEN) parser and serialiser.
  *
  * Parsing is delegated to the currently active [[FenParserType]]; switch at any time:
  * {{{
  *   Fen.activeParser = FenParserType.Regex
  *   Fen.activeParser = FenParserType.Combinator
  *   Fen.activeParser = FenParserType.Fast   // default
  * }}}
  */
object Fen:

  /** Currently active parser. Change to switch between implementations.
    *
    * Note: this is intentional shared mutable state for interactive, single-threaded
    * use (CLI / TUI / GUI). Do not modify concurrently from multiple threads. */
  var activeParser: FenParserType = FenParserType.Fast

  /** Parse a FEN string into a Game. Returns Left with error detail on failure. */
  def parseE(fen: String): Either[ChessError, Game] = activeParser.instance.parseE(fen)

  /** Parse a FEN string into a Game. Returns Failure with exception on invalid input. */
  def parseT(fen: String): scala.util.Try[Game] = activeParser.instance.parseT(fen)

  /** Parse a FEN string into a Game. Returns None on invalid input. */
  def parse(fen: String): Option[Game] = activeParser.instance.parse(fen)

  /** Convert a Game to a FEN string. */
  def toFen(game: Game): String =
    val boardStr    = boardToFen(game.board)
    val colorStr    = if game.currentPlayer == Color.White then "w" else "b"
    val castlingStr = movedPiecesToCastling(game.board, game.movedPieces)
    val epStr       = lastMoveToEnPassant(game.lastMove, game.board)
    s"$boardStr $colorStr $castlingStr $epStr ${game.halfMoveClock} ${game.fullMoveNumber}"

  // ── Serialisation helpers (only used by toFen) ────────────────────────────

  private def pieceToChar(p: Piece): Char = p match
    case Piece.King(Color.White)   => 'K'
    case Piece.Queen(Color.White)  => 'Q'
    case Piece.Rook(Color.White)   => 'R'
    case Piece.Bishop(Color.White) => 'B'
    case Piece.Knight(Color.White) => 'N'
    case Piece.Pawn(Color.White)   => 'P'
    case Piece.King(Color.Black)   => 'k'
    case Piece.Queen(Color.Black)  => 'q'
    case Piece.Rook(Color.Black)   => 'r'
    case Piece.Bishop(Color.Black) => 'b'
    case Piece.Knight(Color.Black) => 'n'
    case Piece.Pawn(Color.Black)   => 'p'

  private def boardToFen(board: Board): String =
    (7 to 0 by -1).map { row =>
      val cells = board.cells(row)
      val sb = new StringBuilder
      var emptyCount = 0
      cells.foreach {
        case None =>
          emptyCount += 1
        case Some(piece) =>
          if emptyCount > 0 then
            sb.append(emptyCount)
            emptyCount = 0
          sb.append(pieceToChar(piece))
      }
      if emptyCount > 0 then sb.append(emptyCount)
      sb.toString
    }.mkString("/")

  private def movedPiecesToCastling(board: Board, movedPieces: Set[Position]): String =
    val sb = new StringBuilder
    val whiteKing = Position(0, 4)
    val blackKing = Position(7, 4)
    if !movedPieces.contains(whiteKing) then
      if !movedPieces.contains(Position(0, 7)) && board.cell(Position(0, 7)).contains(Piece.Rook(Color.White)) then sb.append('K')
      if !movedPieces.contains(Position(0, 0)) && board.cell(Position(0, 0)).contains(Piece.Rook(Color.White)) then sb.append('Q')
    if !movedPieces.contains(blackKing) then
      if !movedPieces.contains(Position(7, 7)) && board.cell(Position(7, 7)).contains(Piece.Rook(Color.Black)) then sb.append('k')
      if !movedPieces.contains(Position(7, 0)) && board.cell(Position(7, 0)).contains(Piece.Rook(Color.Black)) then sb.append('q')
    if sb.isEmpty then "-" else sb.toString

  private def lastMoveToEnPassant(lastMove: Option[Move], board: Board): String =
    lastMove match
      case Some(m) if math.abs(m.to.row - m.from.row) == 2 =>
        board.cell(m.to) match
          case Some(Piece.Pawn(_)) =>
            val epRow = (m.from.row + m.to.row) / 2
            Position(epRow, m.to.col).toString
          case _ => "-"
      case _ => "-"
