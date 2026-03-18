package chess.model

/** Validates whether a move is legal according to piece-specific movement rules.
  * Checks movement patterns, path clearance (sliding pieces), and special moves (castling, en passant). */
object MoveValidator:

  /** Check if a move is valid for the piece at move.from on the given board.
    * Assumes: piece exists at from, color check done elsewhere. */
  def isValidMove(move: Move, board: Board,
                  movedPieces: Set[Position] = Set.empty,
                  lastMove: Option[Move] = None): Boolean =
    board.cell(move.from) match
      case Some(piece) => isValidForPiece(piece, move, board, movedPieces, lastMove)
      case None        => false

  private def isValidForPiece(piece: Piece, move: Move, board: Board,
                              movedPieces: Set[Position], lastMove: Option[Move]): Boolean =
    piece match
      case Piece.Pawn(_)   => isValidPawnMove(piece.color, move, board, lastMove)
      case Piece.Rook(_)   => isValidRookMove(move, board)
      case Piece.Bishop(_) => isValidBishopMove(move, board)
      case Piece.Queen(_)  => isValidQueenMove(move, board)
      case Piece.King(_)   => isValidKingMove(move, board, movedPieces)
      case Piece.Knight(_) => isValidKnightMove(move)

  // --- Pawn ---

  private def isValidPawnMove(color: Color, move: Move, board: Board, lastMove: Option[Move] = None): Boolean =
    val direction = if color == Color.White then 1 else -1
    val startRow  = if color == Color.White then 1 else 6
    val dr = move.to.row - move.from.row
    val dc = move.to.col - move.from.col

    val isOneForward = dr == direction && dc == 0 && board.cell(move.to).isEmpty
    val isTwoForward = dr == 2 * direction && dc == 0 &&
      move.from.row == startRow &&
      board.cell(move.to).isEmpty &&
      board.cell(Position(move.from.row + direction, move.from.col)).isEmpty
    val isDiagonalCapture = dr == direction && math.abs(dc) == 1 &&
      board.cell(move.to).exists(_.color != color)
    val isEnPassant = dr == direction && math.abs(dc) == 1 &&
      board.cell(move.to).isEmpty &&
      lastMove.exists { last =>
        val capturedPos = Position(move.from.row, move.to.col)
        board.cell(capturedPos).exists {
          case Piece.Pawn(c) => c != color
          case _ => false
        } &&
        math.abs(last.to.row - last.from.row) == 2 &&
        last.to == capturedPos
      }

    isOneForward || isTwoForward || isDiagonalCapture || isEnPassant

  // --- Rook ---

  private def isValidRookMove(move: Move, board: Board): Boolean =
    val dr = move.to.row - move.from.row
    val dc = move.to.col - move.from.col
    (dr == 0 || dc == 0) && isPathClear(move, board)

  // --- Bishop ---

  private def isValidBishopMove(move: Move, board: Board): Boolean =
    val dr = math.abs(move.to.row - move.from.row)
    val dc = math.abs(move.to.col - move.from.col)
    dr == dc && dr > 0 && isPathClear(move, board)

  // --- Queen ---

  private def isValidQueenMove(move: Move, board: Board): Boolean =
    isValidRookMove(move, board) || isValidBishopMove(move, board)

  // --- King ---

  private def isValidKingMove(move: Move, board: Board, movedPieces: Set[Position]): Boolean =
    val dr = math.abs(move.to.row - move.from.row)
    val dc = math.abs(move.to.col - move.from.col)
    val isNormalMove = dr <= 1 && dc <= 1 && (dr + dc) > 0
    val isCastling = dr == 0 && dc == 2 && isCastlingValid(move, board, movedPieces)
    isNormalMove || isCastling

  // --- Knight ---

  private def isValidKnightMove(move: Move): Boolean =
    val dr = math.abs(move.to.row - move.from.row)
    val dc = math.abs(move.to.col - move.from.col)
    (dr == 2 && dc == 1) || (dr == 1 && dc == 2)

  // --- Path clearance for sliding pieces (Rook, Bishop, Queen) ---

  private def isPathClear(move: Move, board: Board): Boolean =
    val dr = Integer.signum(move.to.row - move.from.row)
    val dc = Integer.signum(move.to.col - move.from.col)
    val steps = math.max(
      math.abs(move.to.row - move.from.row),
      math.abs(move.to.col - move.from.col)
    )

    (1 until steps).forall { i =>
      val pos = Position(move.from.row + i * dr, move.from.col + i * dc)
      board.cell(pos).isEmpty
    }

  // --- Castling ---

  /** Validate castling: king moves 2 squares towards rook.
    * Requirements: king + rook not moved, path clear, king not in/through/into check. */
  private def isCastlingValid(move: Move, board: Board, movedPieces: Set[Position]): Boolean =
    board.cell(move.from) match
      case Some(Piece.King(color)) =>
        val row = if color == Color.White then 0 else 7
        val kingStart = Position(row, 4)
        move.from == kingStart &&
        !movedPieces.contains(kingStart) && {
          val isKingSide = move.to.col == 6
          val isQueenSide = move.to.col == 2
          (isKingSide || isQueenSide) && {
            val rookCol = if isKingSide then 7 else 0
            val rookPos = Position(row, rookCol)
            board.cell(rookPos).contains(Piece.Rook(color)) &&
            !movedPieces.contains(rookPos) && {
              val pathCols = if isKingSide then (5 to 6) else (1 to 3)
              pathCols.forall(c => board.cell(Position(row, c)).isEmpty) &&
              !isInCheck(board, color) && {
                val throughCol = if isKingSide then 5 else 3
                val throughBoard = board.clear(kingStart).put(Position(row, throughCol), Piece.King(color))
                !isInCheck(throughBoard, color)
              }
            }
          }
        }
      case _ => false

  /** Apply special move side-effects to the board after basic piece movement.
    * Handles: castling rook movement, en passant capture. */
  def applyMoveEffects(move: Move, boardBefore: Board, boardAfterMove: Board): Board =
    // Castling: move the rook
    val afterCastling = boardBefore.cell(move.from) match
      case Some(Piece.King(color)) if math.abs(move.to.col - move.from.col) == 2 =>
        val row = move.from.row
        if move.to.col == 6 then
          boardAfterMove.clear(Position(row, 7)).put(Position(row, 5), Piece.Rook(color))
        else
          boardAfterMove.clear(Position(row, 0)).put(Position(row, 3), Piece.Rook(color))
      case _ => boardAfterMove

    // En passant: remove captured pawn (diagonal pawn move to empty square)
    boardBefore.cell(move.from) match
      case Some(Piece.Pawn(_)) if move.from.col != move.to.col && boardBefore.cell(move.to).isEmpty =>
        afterCastling.clear(Position(move.from.row, move.to.col))
      case _ => afterCastling

  // --- Check detection ---

  /** Is the king of the given color under attack? */
  def isInCheck(board: Board, color: Color): Boolean =
    findKing(board, color) match
      case Some(kingPos) => isAttackedBy(board, kingPos, color.opposite)
      case None          => false // no king on board (shouldn't happen in normal play)

  /** Find the position of the king of a given color. */
  private def findKing(board: Board, color: Color): Option[Position] =
    val positions = for
      r <- 0 until 8
      c <- 0 until 8
      if board.cell(r, c).contains(Piece.King(color))
    yield Position(r, c)
    positions.headOption

  /** Can any piece of `attackerColor` reach `target`?
    * Uses basic attack patterns (no castling or en passant). */
  private def isAttackedBy(board: Board, target: Position, attackerColor: Color): Boolean =
    val attackers = for
      r <- 0 until 8
      c <- 0 until 8
      pos = Position(r, c)
      piece <- board.cell(pos)
      if piece.color == attackerColor
    yield (pos, piece)

    attackers.exists { (from, piece) =>
      canAttackSquare(piece, Move(from, target), board)
    }

  /** Check if a piece can attack a target square (basic attack patterns, no special moves). */
  private def canAttackSquare(piece: Piece, move: Move, board: Board): Boolean =
    piece match
      case Piece.Pawn(_)   => isValidPawnMove(piece.color, move, board)
      case Piece.Rook(_)   => isValidRookMove(move, board)
      case Piece.Bishop(_) => isValidBishopMove(move, board)
      case Piece.Queen(_)  => isValidQueenMove(move, board)
      case Piece.King(_)   =>
        val dr = math.abs(move.to.row - move.from.row)
        val dc = math.abs(move.to.col - move.from.col)
        dr <= 1 && dc <= 1 && (dr + dc) > 0
      case Piece.Knight(_) => isValidKnightMove(move)

  // --- Legal move generation ---

  /** All legal moves for a player: piece moves that don't leave own king in check. */
  def legalMoves(board: Board, color: Color,
                 movedPieces: Set[Position] = Set.empty,
                 lastMove: Option[Move] = None): List[Move] =
    val ownPieces = for
      r <- 0 until 8
      c <- 0 until 8
      pos = Position(r, c)
      piece <- board.cell(pos)
      if piece.color == color
    yield pos

    val allMoves = for
      from <- ownPieces
      r <- 0 until 8
      c <- 0 until 8
      to = Position(r, c)
      move = Move(from, to)
      if to != from
      if !board.cell(to).exists(_.color == color) // not capturing own piece
      if isValidMove(move, board, movedPieces, lastMove)
    yield move

    allMoves.filter { move =>
      board.move(move) match
        case Some(newBoard) =>
          val fullBoard = applyMoveEffects(move, board, newBoard)
          !isInCheck(fullBoard, color)
        case None => false
    }.toList
