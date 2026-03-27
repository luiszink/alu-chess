package chess.model

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** PGN (Portable Game Notation) export and import. */
object Pgn:

  /** Export a game as PGN string. */
  def toPgn(game: Game, white: String = "White", black: String = "Black",
            event: String = "alu-chess Game", timeControl: Option[TimeControl] = None): String =
    val sb = new StringBuilder
    sb.append(s"""[Event "$event"]\n""")
    sb.append(s"""[Site "Local"]\n""")
    sb.append(s"""[Date "${LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))}"]\n""")
    sb.append(s"""[White "$white"]\n""")
    sb.append(s"""[Black "$black"]\n""")
    sb.append(s"""[Result "${resultString(game)}"]\n""")
    timeControl.foreach { tc =>
      val seconds = tc.initialTimeMs / 1000
      val increment = tc.incrementMs / 1000
      sb.append(s"""[TimeControl "$seconds+$increment"]\n""")
    }
    sb.append("\n")

    val entries = game.moveHistory
    val moveTexts = entries.grouped(2).zipWithIndex.map { (pair, idx) =>
      val moveNum = idx + 1
      pair match
        case Vector(w, b) => s"$moveNum. ${w.san} ${b.san}"
        case Vector(w)    => s"$moveNum. ${w.san}"
        case _            => ""
    }.mkString(" ")

    sb.append(moveTexts)
    if moveTexts.nonEmpty then sb.append(" ")
    sb.append(resultString(game))
    sb.toString

  private def resultString(game: Game): String =
    game.status match
      case GameStatus.Checkmate =>
        if game.currentPlayer == Color.White then "0-1" else "1-0"
      case GameStatus.TimeOut =>
        if game.currentPlayer == Color.White then "0-1" else "1-0"
      case GameStatus.Resigned =>
        if game.currentPlayer == Color.White then "0-1" else "1-0"
      case GameStatus.Stalemate | GameStatus.Draw => "1/2-1/2"
      case _ => "*"

  /** Parse a SAN move string in the context of a game. Returns the corresponding Move. */
  def parseSAN(san: String, game: Game): Option[Move] =
    val board = game.board
    val color = game.currentPlayer
    val legal = MoveValidator.legalMoves(board, color, game.movedPieces, game.lastMove)
    val cleaned = san.replaceAll("[+#?!]", "").trim

    // Castling
    if cleaned == "O-O-O" || cleaned == "0-0-0" then
      val row = if color == Color.White then 0 else 7
      return legal.find(m => m.from == Position(row, 4) && m.to == Position(row, 2))
    if cleaned == "O-O" || cleaned == "0-0" then
      val row = if color == Color.White then 0 else 7
      return legal.find(m => m.from == Position(row, 4) && m.to == Position(row, 6))

    // Split promotion
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

  /** Replay a PGN movetext (SAN moves) onto a new game. Returns the final game or error. */
  def replayPgn(pgn: String): Either[String, Game] =
    val movetext = extractMovetext(pgn)
    val tokens = extractSanTokens(movetext)
    tokens.zipWithIndex.foldLeft[Either[String, Game]](Right(Game.newGame)) {
      case (Left(err), _) => Left(err)
      case (Right(game), (token, idx)) =>
        if game.status.isTerminal then Right(game)
        else
          parseSAN(token, game) match
            case Some(move) =>
              game.applyMoveE(move) match
                case Right(updated) => Right(updated)
                case Left(err)      => Left(s"Zug ${idx + 1} ('$token'): ${err.message}")
            case None => Left(s"Zug ${idx + 1}: '$token' nicht erkannt")
    }

  private def extractMovetext(pgn: String): String =
    val lines = pgn.linesIterator.filterNot(_.startsWith("[")).mkString(" ")
    lines.trim

  private def extractSanTokens(movetext: String): Vector[String] =
    movetext
      .replaceAll("\\{[^}]*\\}", " ")
      .replaceAll("\\([^)]*\\)", " ")
      .replaceAll("\\d+\\.", " ")
      .split("\\s+")
      .toVector
      .map(_.trim)
      .filter(_.nonEmpty)
      .filterNot(t => t == "1-0" || t == "0-1" || t == "1/2-1/2" || t == "*")
