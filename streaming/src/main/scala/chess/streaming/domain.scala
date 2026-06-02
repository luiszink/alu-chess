package chess.streaming

import chess.model.{Game, GameStatus, Move, Piece}

/** Ein Zug aus der DSL-Datei, noch nicht gegen das Domain-Modell validiert. */
case class ParsedMove(from: String, to: String, promo: Option[Char]):

  def toMove: Option[Move] =
    for
      f <- chess.model.Position.fromString(from)
      t <- chess.model.Position.fromString(to)
    yield Move(f, t, promo)

/** Ein erfolgreich angewandter Zug mit Zustand vor und nach dem Zug. */
case class GameEvent(
  moveNumber: Int,
  raw: ParsedMove,
  gameBefore: Game,
  gameAfter: Game,
):
  def isCapture: Boolean =
    gameBefore.board.cell(chess.model.Position.fromString(raw.to).get).isDefined ||
      (gameBefore.board.cell(chess.model.Position.fromString(raw.from).get).exists {
        case Piece.Pawn(_) => raw.from.head != raw.to.head
        case _             => false
      })

  def isCheck: Boolean = gameAfter.status == GameStatus.Check || gameAfter.status == GameStatus.Checkmate

/** Ein GameEvent angereichert mit dem Evaluierungsscore (in Centipawns, aus Weiß-Perspektive). */
case class EnrichedEvent(event: GameEvent, evalScore: Int)

/** Aggregierte Statistik einer verarbeiteten Partie. */
case class GameStats(
  totalMoves: Int,
  captures: Int,
  checks: Int,
  invalidMoves: Int,
  finalEval: Int,
  finalStatus: GameStatus,
):
  override def toString: String =
    s"""=== Partie-Statistik ===
       |  Züge gesamt : $totalMoves
       |  Schlagzüge  : $captures
       |  Schachgebote: $checks
       |  Ungültige   : $invalidMoves
       |  Eval (cP)   : ${if finalEval >= 0 then s"+$finalEval" else s"$finalEval"}
       |  Endstatus   : $finalStatus""".stripMargin

object GameStats:
  val empty: GameStats = GameStats(0, 0, 0, 0, 0, GameStatus.Playing)
