package chess.model

enum Color:
  case White, Black

  def opposite: Color = this match
    case White => Black
    case Black => White

enum Piece(val color: Color, val symbol: Char):
  case King(c: Color)   extends Piece(c, if c == Color.White then 'K' else 'k')
  case Queen(c: Color)  extends Piece(c, if c == Color.White then 'Q' else 'q')
  case Rook(c: Color)   extends Piece(c, if c == Color.White then 'R' else 'r')
  case Bishop(c: Color) extends Piece(c, if c == Color.White then 'B' else 'b')
  case Knight(c: Color) extends Piece(c, if c == Color.White then 'N' else 'n')
  case Pawn(c: Color)   extends Piece(c, if c == Color.White then 'P' else 'p')

  override def toString: String = symbol.toString
