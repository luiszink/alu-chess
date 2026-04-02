package chess.model

/** Common interface for all FEN parser implementations. */
trait FenParser:
  def parseE(fen: String): Either[ChessError, Game]

  def parseT(fen: String): scala.util.Try[Game] =
    parseE(fen).left.map(e => new Exception(e.message)).toTry

  def parse(fen: String): Option[Game] = parseE(fen).toOption

/** Shared utilities used by all FenParser implementations. */
private[model] object FenParserUtils:

  val DefaultColor        = "w"
  val DefaultCastling     = "-"
  val DefaultEnPassant    = "-"
  val DefaultHalfMoveClock = "0"

  def normalizeFenParts(parts: Array[String]): Either[ChessError, Array[String]] =
    parts.length match
      case 1 => Right(Array(parts(0), DefaultColor, DefaultCastling, DefaultEnPassant, DefaultHalfMoveClock))
      case n if n >= 4 => Right(parts)
      case _ => Left(ChessError.InvalidFenFormat("FEN requires either 1 field (board only) or at least 4 fields"))

  def parseColorE(s: String): Either[ChessError, Color] = s match
    case "w" => Right(Color.White)
    case "b" => Right(Color.Black)
    case _   => Left(ChessError.InvalidFenColor(s))

  def charToPiece(c: Char): Option[Piece] = c match
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

  def castlingToMovedPieces(castling: String): Set[Position] =
    var moved = Set.empty[Position]
    if !castling.contains('K') then moved += Position(0, 7)
    if !castling.contains('Q') then moved += Position(0, 0)
    if !castling.contains('k') then moved += Position(7, 7)
    if !castling.contains('q') then moved += Position(7, 0)
    if !castling.contains('K') && !castling.contains('Q') then moved += Position(0, 4)
    if !castling.contains('k') && !castling.contains('q') then moved += Position(7, 4)
    moved

  def parseEnPassant(ep: String): Option[Move] =
    if ep == "-" then None
    else Position.fromString(ep).flatMap { target =>
      if target.row == 2 then
        Some(Move(Position(1, target.col), Position(3, target.col)))
      else if target.row == 5 then
        Some(Move(Position(6, target.col), Position(4, target.col)))
      else None
    }

  def computeInitialStatus(game: Game): Game =
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
