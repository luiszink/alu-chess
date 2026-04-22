package chess.model

import chess.model.pgn.{PgnGame => ParsedPgnGame, PgnSharedLogic}

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** PGN (Portable Game Notation) export and import.
  *
  * Parsing is delegated to the currently active [[PgnParserType]]; switch at any time:
  * {{{
  *   Pgn.activeParser = PgnParserType.Regex
  *   Pgn.activeParser = PgnParserType.Combinator
  *   Pgn.activeParser = PgnParserType.Fast   // default
  * }}}
  */
object Pgn:

  /** Currently active parser. Change to switch between implementations. */
  var activeParser: PgnParserType = PgnParserType.Fast

  /** Parse a PGN string into a PgnGame (tags + SAN tokens). Returns Left with error detail on failure. */
  def parseGameE(pgn: String): Either[ChessError, ParsedPgnGame] = activeParser.instance.parseE(pgn)

  /** Parse a PGN string into a PgnGame. Returns None on invalid input. */
  def parseGame(pgn: String): Option[ParsedPgnGame] = activeParser.instance.parse(pgn)

  /** Parse a PGN string and replay all moves, returning the final Game state. */
  def replayE(pgn: String): Either[ChessError, Game] = activeParser.instance.replayE(pgn)

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
        // $COVERAGE-OFF$ grouped(2) never yields empty Vector
        case _            => ""
        // $COVERAGE-ON$
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
    PgnSharedLogic.parseSAN(san, game)

  /** Parse either coordinate notation or SAN in the context of a game. */
  def parseMoveToken(token: String, game: Game): Either[ChessError, Move] =
    val cleaned = token.trim.replaceAll("[+#?!]+$", "")
    parseCoordinateToken(cleaned).orElse(parseSAN(cleaned, game).toRight(ChessError.InvalidMoveFormat(token)))

  private def parseCoordinateToken(token: String): Either[ChessError, Move] =
    if token.matches("^[a-h][1-8][a-h][1-8]$") then Move.fromStringE(token)
    else if token.matches("^[a-h][1-8][a-h][1-8][qrbnQRBN]$") then
      val from = token.substring(0, 2)
      val to = token.substring(2, 4)
      val promo = token.substring(4, 5).toUpperCase
      Move.fromStringE(s"$from $to $promo")
    else Left(ChessError.InvalidMoveFormat(token))

  /** Replay a PGN movetext (SAN moves) onto a new game. Returns the final game or error. */
  def replayPgn(pgn: String): Either[String, Game] =
    replayE(pgn).left.map(_.message)
