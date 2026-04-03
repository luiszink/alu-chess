package chess.model

enum ChessError:
  case InvalidMoveFormat(input: String)
  case InvalidPositionString(input: String)
  case InvalidPromotionPiece(char: Char)
  case NoPieceAtSource(pos: Position)
  case WrongColorPiece(pos: Position, expected: Color, found: Color)
  case FriendlyFire(from: Position, to: Position)
  case IllegalMovePattern(move: Move)
  case LeavesKingInCheck(move: Move)
  case InvalidFenFormat(detail: String)
  case InvalidFenBoardRow(rank: String)
  case InvalidFenPieceChar(char: Char)
  case InvalidFenColor(token: String)
  case GameAlreadyOver(status: GameStatus)
  case InvalidPgnFormat(detail: String)
  case InvalidPgnMove(moveNumber: Int, san: String)

  def message: String = this match
    case InvalidMoveFormat(i)          => s"Cannot parse move: '$i'"
    case InvalidPositionString(i)      => s"Invalid square: '$i'"
    case InvalidPromotionPiece(c)      => s"Invalid promotion piece: '$c' (use Q, R, B, N)"
    case NoPieceAtSource(p)            => s"No piece at $p"
    case WrongColorPiece(p, exp, fnd)  => s"$fnd piece at $p, but it is $exp's turn"
    case FriendlyFire(f, t)            => s"Cannot capture own piece: $f -> $t"
    case IllegalMovePattern(m)         => s"Illegal move: $m"
    case LeavesKingInCheck(m)          => s"Move $m leaves king in check"
    case InvalidFenFormat(d)           => s"Invalid FEN: $d"
    case InvalidFenBoardRow(r)         => s"Invalid FEN rank: '$r'"
    case InvalidFenPieceChar(c)        => s"Unknown FEN piece: '$c'"
    case InvalidFenColor(t)            => s"Invalid active color token: '$t'"
    case GameAlreadyOver(s)            => s"Game is over: $s"
    case InvalidPgnFormat(d)           => s"Invalid PGN: $d"
    case InvalidPgnMove(n, san)        => s"Zug $n: '$san' nicht erkannt"
