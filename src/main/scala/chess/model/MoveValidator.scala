package chess.model

/** Validates whether a move is legal according to piece-specific movement rules.
  * Checks movement patterns and path clearance (sliding pieces). */
object MoveValidator:

  /** Check if a move is valid for the piece at move.from on the given board.
    * Assumes: piece exists at from, color check done elsewhere. */
  def isValidMove(move: Move, board: Board): Boolean =
    board.cell(move.from) match
      case Some(piece) => isValidForPiece(piece, move, board)
      case None        => false

  private def isValidForPiece(piece: Piece, move: Move, board: Board): Boolean =
    piece match
      case Piece.Pawn(_)   => isValidPawnMove(piece.color, move, board)
      case Piece.Rook(_)   => isValidRookMove(move, board)
      case Piece.Bishop(_) => isValidBishopMove(move, board)
      case Piece.Queen(_)  => isValidQueenMove(move, board)
      case Piece.King(_)   => isValidKingMove(move)
      case Piece.Knight(_) => isValidKnightMove(move)

  // --- Pawn ---

  private def isValidPawnMove(color: Color, move: Move, board: Board): Boolean =
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

    isOneForward || isTwoForward || isDiagonalCapture

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

  private def isValidKingMove(move: Move): Boolean =
    val dr = math.abs(move.to.row - move.from.row)
    val dc = math.abs(move.to.col - move.from.col)
    dr <= 1 && dc <= 1 && (dr + dc) > 0

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

  /** Can any piece of `attackerColor` reach `target`? */
  private def isAttackedBy(board: Board, target: Position, attackerColor: Color): Boolean =
    val attackers = for
      r <- 0 until 8
      c <- 0 until 8
      pos = Position(r, c)
      piece <- board.cell(pos)
      if piece.color == attackerColor
    yield pos

    attackers.exists { from =>
      isValidMove(Move(from, target), board)
    }

  // --- Legal move generation ---

  /** All legal moves for a player: piece moves that don't leave own king in check. */
  def legalMoves(board: Board, color: Color): List[Move] =
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
      if isValidMove(move, board)
    yield move

    allMoves.filter { move =>
      board.move(move) match
        case Some(newBoard) => !isInCheck(newBoard, color)
        case None           => false
    }.toList
