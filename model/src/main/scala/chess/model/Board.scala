package chess.model

case class Board(cells: Vector[Vector[Option[Piece]]]):

  val size: Int = 8

  def cell(row: Int, col: Int): Option[Piece] =
    if isValid(row, col) then cells(row)(col) else None

  def cell(pos: Position): Option[Piece] =
    cell(pos.row, pos.col)

  def put(row: Int, col: Int, piece: Piece): Board =
    if isValid(row, col) then
      copy(cells = cells.updated(row, cells(row).updated(col, Some(piece))))
    else this

  def put(pos: Position, piece: Piece): Board =
    put(pos.row, pos.col, piece)

  def clear(row: Int, col: Int): Board =
    if isValid(row, col) then
      copy(cells = cells.updated(row, cells(row).updated(col, None)))
    else this

  def clear(pos: Position): Board =
    clear(pos.row, pos.col)

  /** Apply a move: pick up the piece at 'from' and place it at 'to'. No rule validation. */
  def move(m: Move): Option[Board] =
    cell(m.from).map { piece =>
      this.clear(m.from).put(m.to, piece)
    }

  def isValid(row: Int, col: Int): Boolean =
    row >= 0 && row < size && col >= 0 && col < size

  override def toString: String =
    val separator = "  +" + ("---+" * size)
    val header = "    " + ('a' to 'h').map(c => s" $c ").mkString(" ")
    val rows = cells.zipWithIndex.reverse.map { (row, r) =>
      val rowStr = row.map {
        case Some(piece) => s" $piece "
        case None        => "   "
      }.mkString(s"${r + 1} |", "|", "|")
      s"$separator\n$rowStr"
    }
    s"$header\n${rows.mkString("\n")}\n$separator"

object Board:
  def empty: Board =
    Board(Vector.fill(8)(Vector.fill(8)(None)))

  def initial: Board =
    val backRow = Vector(
      Piece.Rook.apply, Piece.Knight.apply, Piece.Bishop.apply, Piece.Queen.apply,
      Piece.King.apply, Piece.Bishop.apply, Piece.Knight.apply, Piece.Rook.apply
    )
    val emptyRow = Vector.fill(8)(Option.empty[Piece])

    val whiteBackRow = backRow.map(f => Some(f(Color.White)))
    val whitePawnRow = Vector.fill(8)(Some(Piece.Pawn(Color.White)))
    val blackPawnRow = Vector.fill(8)(Some(Piece.Pawn(Color.Black)))
    val blackBackRow = backRow.map(f => Some(f(Color.Black)))

    Board(Vector(
      whiteBackRow,
      whitePawnRow,
      emptyRow,
      emptyRow,
      emptyRow,
      emptyRow,
      blackPawnRow,
      blackBackRow
    ))
