package chess.model

/** Forsyth-Edwards Notation (FEN) parser.
  * Converts a FEN string to a Game and vice versa. */
object Fen:

  private val defaultColor = "w"
  private val defaultCastling = "-"
  private val defaultEnPassant = "-"
  private val defaultHalfMoveClock = "0"

  private def normalizeFenParts(parts: Array[String]): Either[ChessError, Array[String]] =
    parts.length match
      case 1 => Right(Array(parts(0), defaultColor, defaultCastling, defaultEnPassant, defaultHalfMoveClock))
      case n if n >= 4 => Right(parts)
      case _ => Left(ChessError.InvalidFenFormat("FEN requires either 1 field (board only) or at least 4 fields"))

  /** Parse a FEN string into a Game. Returns Left with error detail on failure. */
  def parseE(fen: String): Either[ChessError, Game] =
    val rawParts = fen.trim.split("\\s+")
    normalizeFenParts(rawParts).flatMap { parts =>
      for
        board <- parseBoardE(parts(0))
        color <- parseColorE(parts(1))
      yield
        val castling = parts(2)
        val movedPieces = castlingToMovedPieces(castling)
        val enPassantStr = if parts.length > 3 then parts(3) else "-"
        val lastMove = parseEnPassant(enPassantStr)
        val halfMoveClock = if parts.length > 4 then parts(4).toIntOption.getOrElse(0) else 0
        val fullMoveNumber = if parts.length > 5 then parts(5).toIntOption.getOrElse(1) else 1
        val initial = Game(board, color, GameStatus.Playing, movedPieces, lastMove, halfMoveClock, fullMoveNumber = fullMoveNumber)
        computeInitialStatus(initial)
      }

  /** Parse a FEN string into a Game. Returns Failure with exception on invalid input. */
  def parseT(fen: String): scala.util.Try[Game] =
    parseE(fen).left.map(e => new Exception(e.message)).toTry

  /** Parse a FEN string into a Game. Returns None on invalid input. */
  def parse(fen: String): Option[Game] = parseE(fen).toOption

  /** Convert a Game to a FEN string. */
  def toFen(game: Game): String =
    val boardStr = boardToFen(game.board)
    val colorStr = if game.currentPlayer == Color.White then "w" else "b"
    val castlingStr = movedPiecesToCastling(game.board, game.movedPieces)
    val epStr = lastMoveToEnPassant(game.lastMove, game.board)
    val halfMove = game.halfMoveClock
    val fullMove = game.fullMoveNumber
    s"$boardStr $colorStr $castlingStr $epStr $halfMove $fullMove"

  private def computeInitialStatus(game: Game): Game =
    val color   = game.currentPlayer
    val board   = game.board
    val inCheck = MoveValidator.isInCheck(board, color)
    val hasMoves = MoveValidator.legalMoves(board, color, game.movedPieces, game.lastMove).nonEmpty
    val status =
      if inCheck && !hasMoves then GameStatus.Checkmate
      else if !inCheck && !hasMoves then GameStatus.Stalemate
      else if MoveValidator.isInsufficientMaterial(board) then GameStatus.Draw
      else if game.halfMoveClock >= 100 then GameStatus.Draw
      else if inCheck then GameStatus.Check
      else GameStatus.Playing
    game.copy(status = status)

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

  private def parseColorE(s: String): Either[ChessError, Color] = s match
    case "w" => Right(Color.White)
    case "b" => Right(Color.Black)
    case _   => Left(ChessError.InvalidFenColor(s))

  private def charToPiece(c: Char): Option[Piece] = c match
    case 'K' => Some(Piece.King(Color.White))
    case 'Q' => Some(Piece.Queen(Color.White))
    case 'R' => Some(Piece.Rook(Color.White))
    case 'B' => Some(Piece.Bishop(Color.White))
    case 'N' => Some(Piece.Knight(Color.White))
    case 'P' => Some(Piece.Pawn(Color.White))
    case 'k' => Some(Piece.King(Color.Black))
    case 'q' => Some(Piece.Queen(Color.Black))
    case 'r' => Some(Piece.Rook(Color.Black))
    case 'b' => Some(Piece.Bishop(Color.Black))
    case 'n' => Some(Piece.Knight(Color.Black))
    case 'p' => Some(Piece.Pawn(Color.Black))
    case _   => None

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

  private def castlingToMovedPieces(castling: String): Set[Position] =
    var moved = Set.empty[Position]
    if !castling.contains('K') then moved += Position(0, 7) // h1 rook
    if !castling.contains('Q') then moved += Position(0, 0) // a1 rook
    if !castling.contains('k') then moved += Position(7, 7) // h8 rook
    if !castling.contains('q') then moved += Position(7, 0) // a8 rook
    if !castling.contains('K') && !castling.contains('Q') then moved += Position(0, 4) // white king
    if !castling.contains('k') && !castling.contains('q') then moved += Position(7, 4) // black king
    moved

  private def parseEnPassant(ep: String): Option[Move] =
    if ep == "-" then None
    else Position.fromString(ep).flatMap { target =>
      if target.row == 2 then // white pawn pushed e2→e4, target is e3
        Some(Move(Position(1, target.col), Position(3, target.col)))
      else if target.row == 5 then // black pawn pushed e7→e5, target is e6
        Some(Move(Position(6, target.col), Position(4, target.col)))
      else None
    }

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
