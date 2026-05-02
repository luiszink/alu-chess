package chess.model

enum GameStatus:
  case Playing, Check, Checkmate, Stalemate, Resigned, Draw, TimeOut

  def isTerminal: Boolean = this match
    case Checkmate | Stalemate | Resigned | Draw | TimeOut => true
    case _ => false

case class Game(board: Board, currentPlayer: Color, status: GameStatus, movedPieces: Set[Position] = Set.empty, lastMove: Option[Move] = None, halfMoveClock: Int = 0, moveHistory: Vector[MoveEntry] = Vector.empty, fullMoveNumber: Int = 1, repetitionKeys: Vector[Long] = Vector.empty):

  def switchPlayer: Game =
    copy(currentPlayer = currentPlayer.opposite)

  /** Apply a move if it passes all validations. Returns Left with error detail on failure.
    * 1. Source has a piece of the current player's color
    * 2. Target is not occupied by a friendly piece
    * 3. Move is valid for the piece type (pattern + path clearance + special rules)
    * 4. Move does not leave own king in check
    * After the move, determines new game status (Check, Checkmate, Stalemate, Draw).
    * Handles special moves: castling rook movement, en passant capture, pawn promotion.
    * Checks draw conditions: threefold repetition, 50-move rule, insufficient material. */
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

  /** Lightweight move application for use inside the AI search tree.
    *
    * Identical to applyMove but skips the MoveValidator.legalMoves call that
    * computeUpdatedGame uses to detect checkmate/stalemate. The AI's negamax
    * already calls legalMoves for move generation; calling it again inside
    * applyMove doubles the cost per node. Instead:
    *   - status is set to Check/Playing/Draw only (never Checkmate/Stalemate)
    *   - checkmate and stalemate are detected by negamax via moves.isEmpty + isInCheck
    *   - moveHistory is not updated (AI does not need it)
    */
  private[model] def applyMoveSearch(m: Move): Option[Game] =
    for
      piece    <- board.cell(m.from)
      if piece.color == currentPlayer
      if !board.cell(m.to).exists(_.color == currentPlayer)
      if MoveValidator.isValidMove(m, board, movedPieces, lastMove)
      newBoard <- board.move(m)
      fullBoard = MoveValidator.applyMoveEffects(m, board, newBoard)
      if !MoveValidator.isInCheck(fullBoard, currentPlayer)
    yield
      val isPawnMove      = piece match { case Piece.Pawn(_) => true; case _ => false }
      val isCap           = board.cell(m.to).isDefined ||
                              (isPawnMove && m.from.col != m.to.col && board.cell(m.to).isEmpty)
      val opponent        = currentPlayer.opposite
      val newMovedPieces  = movedPieces + m.from
      val newHalfMoveClock = if isPawnMove || isCap then 0 else halfMoveClock + 1
      val currentKey      = repetitionKey(board, currentPlayer, movedPieces, lastMove)
      val histKeys        = if repetitionKeys.nonEmpty then repetitionKeys else Vector(currentKey)
      val nextKey         = repetitionKey(fullBoard, opponent, newMovedPieces, Some(m))
      val newRepetitionKeys = histKeys :+ nextKey
      val repetitionCount = newRepetitionKeys.count(_ == nextKey)
      val opponentInCheck = MoveValidator.isInCheck(fullBoard, opponent)
      val newStatus =
        if repetitionCount >= 3                              then GameStatus.Draw
        else if MoveValidator.isInsufficientMaterial(fullBoard) then GameStatus.Draw
        else if newHalfMoveClock >= 100                      then GameStatus.Draw
        else if opponentInCheck                              then GameStatus.Check
        else GameStatus.Playing
      copy(
        board          = fullBoard,
        currentPlayer  = opponent,
        status         = newStatus,
        movedPieces    = newMovedPieces,
        lastMove       = Some(m),
        halfMoveClock  = newHalfMoveClock,
        repetitionKeys = newRepetitionKeys
      )

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
    val currentKey = repetitionKey(board, currentPlayer, movedPieces, lastMove)
    val historyKeys = if repetitionKeys.nonEmpty then repetitionKeys else Vector(currentKey)
    val nextKey = repetitionKey(fullBoard, opponent, newMovedPieces, Some(m))
    val newRepetitionKeys = historyKeys :+ nextKey
    val repetitionCount = newRepetitionKeys.count(_ == nextKey)
    val opponentInCheck = MoveValidator.isInCheck(fullBoard, opponent)
    val opponentHasMoves = MoveValidator.legalMoves(fullBoard, opponent, newMovedPieces, Some(m)).nonEmpty
    val newStatus =
      if opponentInCheck && !opponentHasMoves then GameStatus.Checkmate
      else if !opponentInCheck && !opponentHasMoves then GameStatus.Stalemate
      else if repetitionCount >= 3 then GameStatus.Draw
      else if MoveValidator.isInsufficientMaterial(fullBoard) then GameStatus.Draw
      else if newHalfMoveClock >= 100 then GameStatus.Draw
      else if opponentInCheck then GameStatus.Check
      else GameStatus.Playing
    val entry = MoveEntry.create(m, piece, captured, board, newStatus, movedPieces, lastMove)
    val newFullMoveNumber = if currentPlayer == Color.Black then fullMoveNumber + 1 else fullMoveNumber
    copy(board = fullBoard, currentPlayer = opponent, status = newStatus,
      movedPieces = newMovedPieces, lastMove = Some(m), halfMoveClock = newHalfMoveClock,
      moveHistory = moveHistory :+ entry, fullMoveNumber = newFullMoveNumber,
      repetitionKeys = newRepetitionKeys)

  private def repetitionKey(
    board: Board,
    currentPlayer: Color,
    movedPieces: Set[Position],
    lastMove: Option[Move]
  ): Long =
    chess.model.ai.Zobrist.hashRaw(board, currentPlayer, movedPieces, lastMove)

  def resign: Game =
    copy(status = GameStatus.Resigned)

object Game:
  def newGame: Game =
    Game(Board.initial, Color.White, GameStatus.Playing)
