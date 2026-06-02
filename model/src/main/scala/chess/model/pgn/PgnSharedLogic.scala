package chess.model.pgn

import chess.model.{Board, ChessError, Color, Game, GameStatus, Move, MoveValidator, Piece, Position}

/** Shared parsing utilities reused by all PGN parser implementations.
  * Accessible only within the chess.model.pgn package. */
private[model] object PgnSharedLogic:

  val Results: Set[String] = Set("1-0", "0-1", "1/2-1/2", "*")

  /** Extract SAN move tokens from raw movetext (no tags, no result). */
  def extractSanTokens(movetext: String): Vector[String] =
    movetext
      .replaceAll("\\{[^}]*\\}", " ")
      .replaceAll("\\([^)]*\\)", " ")
      .replaceAll("\\d+\\.+", " ")
      .split("\\s+")
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .filterNot(Results.contains)

  /** Parse a SAN move string in the context of a game. Returns the corresponding Move. */
  def parseSAN(san: String, game: Game): Option[Move] =
    val board = game.board
    val color = game.currentPlayer
    val legal = MoveValidator.legalMoves(board, color, game.movedPieces, game.lastMove)
    val cleaned = san.replaceAll("[+#?!]", "").trim

    if cleaned == "O-O-O" || cleaned == "0-0-0" then
      val row = if color == Color.White then 0 else 7
      return legal.find(m => m.from == Position(row, 4) && m.to == Position(row, 2))
    if cleaned == "O-O" || cleaned == "0-0" then
      val row = if color == Color.White then 0 else 7
      return legal.find(m => m.from == Position(row, 4) && m.to == Position(row, 6))

    val (moveStr, promotion) =
      if cleaned.contains("=") then
        val parts = cleaned.split("=")
        (parts(0), Some(parts(1).charAt(0).toUpper))
      else (cleaned, None)

    val pieceChars = "KQRBN"
    if moveStr.nonEmpty && pieceChars.contains(moveStr(0)) then
      parsePieceMove(moveStr, legal, board, color, promotion)
    else
      parsePawnMove(moveStr, cleaned, legal, board, color, promotion)

  private def parsePieceMove(
    moveStr: String, legal: List[Move], board: Board, color: Color, promotion: Option[Char]
  ): Option[Move] =
    val pieceChar = moveStr(0)
    val rest = moveStr.drop(1).replace("x", "")
    if rest.length < 2 then return None

    val destStr = rest.takeRight(2)
    val disambig = rest.dropRight(2)

    Position.fromString(destStr).flatMap { to =>
      val candidates = legal.filter { m =>
        m.to == to && board.cell(m.from).exists(p => p.symbol.toUpper == pieceChar && p.color == color)
      }

      val filtered = disambig.length match
        case 0 => candidates
        case 1 if disambig(0).isLetter => candidates.filter(_.from.col == disambig(0) - 'a')
        case 1 if disambig(0).isDigit  => candidates.filter(_.from.row == disambig(0).asDigit - 1)
        case 2 => Position.fromString(disambig).map(p => candidates.filter(_.from == p)).getOrElse(Nil)
        case _ => Nil

      filtered.headOption.map(m => m.copy(promotion = promotion.orElse(m.promotion)))
    }

  private def parsePawnMove(
    moveStr: String, originalStr: String, legal: List[Move], board: Board, color: Color, promotion: Option[Char]
  ): Option[Move] =
    val str = moveStr.replace("x", "")
    if str.length < 2 then return None

    val destStr = str.takeRight(2)
    val fileHint =
      if str.length > 2 then Some(str(0) - 'a')
      else if originalStr.contains("x") && originalStr.nonEmpty && originalStr(0).isLetter then
        Some(originalStr(0) - 'a')
      else None

    Position.fromString(destStr).flatMap { to =>
      val candidates = legal.filter { m =>
        m.to == to && board.cell(m.from).exists {
          case Piece.Pawn(c) => c == color
          case _ => false
        }
      }

      val filtered = fileHint match
        case Some(col) => candidates.filter(_.from.col == col)
        case None      => candidates

      filtered.headOption.map(m => m.copy(promotion = promotion.orElse(m.promotion)))
    }

  /** Replay parsed SAN tokens on a new game, returning the final game state. */
  def replayMoves(pgnGame: PgnGame): Either[ChessError, Game] =
    pgnGame.moves.zipWithIndex.foldLeft[Either[ChessError, Game]](Right(Game.newGame)) {
      case (Left(err), _) => Left(err)
      case (Right(game), _) if game.status.isTerminal => Right(game)
      case (Right(game), (token, idx)) =>
        parseSAN(token, game) match
          case Some(move) =>
            game.applyMoveE(move) match
              case Right(updated) => Right(updated)
              case Left(err)      => Left(ChessError.InvalidPgnMove(idx + 1, token))
          case None => Left(ChessError.InvalidPgnMove(idx + 1, token))
    }

  /** Replay parsed SAN tokens on a new game, returning all intermediate game states. */
  def replayAllStates(pgnGame: PgnGame): Either[ChessError, Vector[Game]] =
    pgnGame.moves.zipWithIndex.foldLeft[Either[ChessError, Vector[Game]]](Right(Vector(Game.newGame))) {
      case (Left(err), _) => Left(err)
      case (Right(states), _) if states.last.status.isTerminal => Right(states)
      case (Right(states), (token, idx)) =>
        parseSAN(token, states.last) match
          case Some(move) =>
            states.last.applyMoveE(move) match
              case Right(updated) => Right(states :+ updated)
              case Left(_)        => Left(ChessError.InvalidPgnMove(idx + 1, token))
          case None => Left(ChessError.InvalidPgnMove(idx + 1, token))
    }

  /** Detect the result token from the end of movetext. */
  def extractResult(movetext: String): Option[String] =
    val trimmed = movetext.trim
    Results.find(r => trimmed.endsWith(r))
