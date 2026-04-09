package chess.model

enum GameStatus:
  case Playing, Check, Checkmate, Stalemate, Resigned, Draw, TimeOut

  def isTerminal: Boolean = this match
    case Checkmate | Stalemate | Resigned | Draw | TimeOut => true
    case _ => false

case class Game(board: Board, currentPlayer: Color, status: GameStatus, movedPieces: Set[Position] = Set.empty, lastMove: Option[Move] = None, halfMoveClock: Int = 0, moveHistory: Vector[MoveEntry] = Vector.empty, fullMoveNumber: Int = 1):

  def switchPlayer: Game =
    copy(currentPlayer = currentPlayer.opposite)

  /** Apply a move if it passes all validations. Returns Left with error detail on failure.
    * 1. Source has a piece of the current player's color
    * 2. Target is not occupied by a friendly piece
    * 3. Move is valid for the piece type (pattern + path clearance + special rules)
    * 4. Move does not leave own king in check
    * After the move, determines new game status (Check, Checkmate, Stalemate, Draw).
    * Handles special moves: castling rook movement, en passant capture, pawn promotion.
    * Checks draw conditions: 50-move rule, insufficient material. */
  def applyMoveE(m: Move): Either[ChessError, Game] =
    for
      piece    <- board.cell(m.from).toRight(ChessError.NoPieceAtSource(m.from))
      _        <- Either.cond(piece.color == currentPlayer, (),
                    ChessError.WrongColorPiece(m.from, currentPlayer, piece.color))
      _        <- Either.cond(!board.cell(m.to).exists(_.color == currentPlayer), (),
                    ChessError.FriendlyFire(m.from, m.to))
      _        <- Either.cond(MoveValidator.isValidMove(m, board, movedPieces, lastMove), (),
                    ChessError.IllegalMovePattern(m))
      newBoard <- board.move(m).toRight(ChessError.IllegalMovePattern(m))
      fullBoard = MoveValidator.applyMoveEffects(m, board, newBoard)
      _        <- Either.cond(!MoveValidator.isInCheck(fullBoard, currentPlayer), (),
                    ChessError.LeavesKingInCheck(m))
    yield computeUpdatedGame(m, fullBoard, piece)

  /** Apply a move if it passes all validations. Returns None on failure. */
  def applyMove(m: Move): Option[Game] = applyMoveE(m).toOption

  private def computeUpdatedGame(m: Move, fullBoard: Board, piece: Piece): Game =
    val isPawnMove = piece match { case Piece.Pawn(_) => true; case _ => false }
    val isCapture = board.cell(m.to).isDefined ||
      (isPawnMove && m.from.col != m.to.col && board.cell(m.to).isEmpty) // en passant
    val captured: Option[Piece] = board.cell(m.to).orElse(
      piece match
        case Piece.Pawn(_) if m.from.col != m.to.col => board.cell(Position(m.from.row, m.to.col))
        case _ => None
    )
    val opponent = currentPlayer.opposite
    val newMovedPieces = movedPieces + m.from
    val newHalfMoveClock = if isPawnMove || isCapture then 0 else halfMoveClock + 1
    val opponentInCheck = MoveValidator.isInCheck(fullBoard, opponent)
    val opponentHasMoves = MoveValidator.legalMoves(fullBoard, opponent, newMovedPieces, Some(m)).nonEmpty
    val newStatus =
      if opponentInCheck && !opponentHasMoves then GameStatus.Checkmate
      else if !opponentInCheck && !opponentHasMoves then GameStatus.Stalemate
      else if MoveValidator.isInsufficientMaterial(fullBoard) then GameStatus.Draw
      else if newHalfMoveClock >= 100 then GameStatus.Draw
      else if opponentInCheck then GameStatus.Check
      else GameStatus.Playing
    val entry = MoveEntry.create(m, piece, captured, board, newStatus, movedPieces, lastMove)
    val newFullMoveNumber = if currentPlayer == Color.Black then fullMoveNumber + 1 else fullMoveNumber
    copy(board = fullBoard, currentPlayer = opponent, status = newStatus,
      movedPieces = newMovedPieces, lastMove = Some(m), halfMoveClock = newHalfMoveClock,
      moveHistory = moveHistory :+ entry, fullMoveNumber = newFullMoveNumber)

  def resign: Game =
    copy(status = GameStatus.Resigned)

object Game:
  def newGame: Game =
    Game(Board.initial, Color.White, GameStatus.Playing)
