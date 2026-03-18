package chess.model

enum GameStatus:
  case Playing, Resigned

case class Game(board: Board, currentPlayer: Color, status: GameStatus):

  def switchPlayer: Game =
    copy(currentPlayer = currentPlayer.opposite)

  /** Apply a move if the source has a piece of the current player's color
    * and the target is not occupied by a friendly piece. Returns None on failure. */
  def applyMove(m: Move): Option[Game] =
    board.cell(m.from) match
      case Some(piece) if piece.color == currentPlayer =>
        val targetFriendly = board.cell(m.to).exists(_.color == currentPlayer)
        if targetFriendly then None
        else board.move(m).map(newBoard => copy(board = newBoard).switchPlayer)
      case _ => None

  def resign: Game =
    copy(status = GameStatus.Resigned)

object Game:
  def newGame: Game =
    Game(Board.initial, Color.White, GameStatus.Playing)
