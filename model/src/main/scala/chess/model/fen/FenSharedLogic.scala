package chess.model.fen

import chess.model.{Board, ChessError, Color, Game, GameStatus, Move, MoveValidator, Piece, Position}

/** Shared parsing utilities reused by all FEN parser implementations.
  * Accessible only within the chess.model.fen package. */
private[fen] object FenSharedLogic:

  private val DefaultColor      = "w"
  private val DefaultCastling   = "-"
  private val DefaultEnPassant  = "-"
  private val DefaultHalfMove   = "0"

  def normalizeFenParts(parts: Array[String]): Either[ChessError, Array[String]] =
    parts.length match
      case 1 =>
        Right(Array(parts(0), DefaultColor, DefaultCastling, DefaultEnPassant, DefaultHalfMove))
      case n if n >= 4 => Right(parts)
      case _ =>
        Left(ChessError.InvalidFenFormat("FEN requires either 1 field (board only) or at least 4 fields"))

  def parseBoardE(placement: String): Either[ChessError, Board] =
    val ranks = placement.split('/')
    if ranks.length != 8 then Left(ChessError.InvalidFenFormat("Expected 8 ranks"))
    else
      val rows = ranks.reverse.map(parseRankE)
      rows.find(_.isLeft) match
        case Some(Left(err)) => Left(err)
        case _ =>
          val cells = rows.map(_.getOrElse(Vector.empty))
          Right(Board(cells.toVector))

  def parseRankE(rank: String): Either[ChessError, Vector[Option[Piece]]] =
    val result = rank.foldLeft[Either[ChessError, Vector[Option[Piece]]]](Right(Vector.empty)) {
      case (Left(err), _) => Left(err)
      case (Right(acc), ch) if ch.isDigit =>
        val n = ch.asDigit
        if n < 1 || n > 8 then Left(ChessError.InvalidFenBoardRow(rank))
        else Right(acc ++ Vector.fill(n)(None))
      case (Right(acc), ch) =>
        charToPiece(ch) match
          case Some(p) => Right(acc :+ Some(p))
          case None    => Left(ChessError.InvalidFenPieceChar(ch))
    }
    result.filterOrElse(_.length == 8, ChessError.InvalidFenBoardRow(rank))

  def parseColorE(s: String): Either[ChessError, Color] = s match
    case "w" => Right(Color.White)
    case "b" => Right(Color.Black)
    case _   => Left(ChessError.InvalidFenColor(s))

  def charToPiece(c: Char): Option[Piece] = c match
    case 'K' => Some(Piece.King(Color.White))
    case 'Q' => Some(Piece.Queen(Color.White))
    case 'R' => Some(Piece.Rook(Color.White))
    case 'B' => Some(Piece.Bishop(Color.White))
    case 'N' => Some(Piece.Knight(Color.White))
    case 'P' => Some(Piece.Pawn(Color.White))
    case 'k' => Some(Piece.King(Color.Black))
    case 'q' => Some(Piece.Queen(Color.Black))
    case 'r' => Some(Piece.Rook(Color.Black))
    case 'b' => Some(Piece.Bishop(Color.Black))
    case 'n' => Some(Piece.Knight(Color.Black))
    case 'p' => Some(Piece.Pawn(Color.Black))
    case _   => None

  def castlingToMovedPieces(castling: String): Set[Position] =
    val conditions = List(
      (!castling.contains('K'), Position(0, 7)),
      (!castling.contains('Q'), Position(0, 0)),
      (!castling.contains('k'), Position(7, 7)),
      (!castling.contains('q'), Position(7, 0)),
      (!castling.contains('K') && !castling.contains('Q'), Position(0, 4)),
      (!castling.contains('k') && !castling.contains('q'), Position(7, 4))
    )
    conditions.collect { case (true, pos) => pos }.toSet

  def parseEnPassant(ep: String): Option[Move] =
    if ep == "-" then None
    else Position.fromString(ep).flatMap { target =>
      if target.row == 2 then
        Some(Move(Position(1, target.col), Position(3, target.col)))
      else if target.row == 5 then
        Some(Move(Position(6, target.col), Position(4, target.col)))
      else None
    }

  def computeInitialStatus(game: Game): Game =
    val color    = game.currentPlayer
    val board    = game.board
    val inCheck  = MoveValidator.isInCheck(board, color)
    val hasMoves = MoveValidator.legalMoves(board, color, game.movedPieces, game.lastMove).nonEmpty
    val status =
      if inCheck && !hasMoves then GameStatus.Checkmate
      else if !inCheck && !hasMoves then GameStatus.Stalemate
      else if MoveValidator.isInsufficientMaterial(board) then GameStatus.Draw
      else if game.halfMoveClock >= 100 then GameStatus.Draw
      else if inCheck then GameStatus.Check
      else GameStatus.Playing
    game.copy(status = status)
