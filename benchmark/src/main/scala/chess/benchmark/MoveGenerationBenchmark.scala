package chess.benchmark

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import chess.model.{Fen, Game, Board, Color, Move, Position, MoveValidator}

/** Zuggenierung und Validierung.
  *
  * legalMoves ist die innerste Schleife der Alpha-Beta-Suche — sie wird an
  * jedem Knoten aufgerufen. Verschiedene Stellungen üben verschiedene Code-Pfade
  * (Rochade, En passant, Schachfilterung) aus.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = Array("-Xms256m", "-Xmx256m"))
@State(Scope.Benchmark)
class MoveGenerationBenchmark:

  val startFen: String =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  val midgameFen: String =
    "r1bq1rk1/pp2ppbp/2np1np1/8/3NP3/2N1BP2/PPPQ2PP/R3KB1R w KQ - 0 9"

  val castlingFen: String =
    "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1"

  val enPassantFen: String =
    "rnbqkbnr/pppp1ppp/8/4pP2/8/8/PPPPP1PP/RNBQKBNR w KQkq e6 0 3"

  val endgameFen: String =
    "8/8/4k3/8/8/4K3/3B4/8 w - - 0 1"

  var startGame: Game    = _
  var midgameGame: Game  = _
  var castlingGame: Game = _
  var enPassantGame: Game = _
  var endgameGame: Game  = _
  var e2e4: Move         = _

  @Setup(Level.Trial)
  def setup(): Unit =
    startGame    = Fen.parse(startFen).get
    midgameGame  = Fen.parse(midgameFen).get
    castlingGame = Fen.parse(castlingFen).get
    enPassantGame = Fen.parse(enPassantFen).get
    endgameGame  = Fen.parse(endgameFen).get
    e2e4 = Move(Position.fromString("e2").get, Position.fromString("e4").get)

  // ── legalMoves ───────────────────────────────────────────────

  @Benchmark
  def legalMoves_Start(bh: Blackhole): Unit =
    bh.consume(MoveValidator.legalMoves(
      startGame.board, startGame.currentPlayer,
      startGame.movedPieces, startGame.lastMove
    ))

  @Benchmark
  def legalMoves_Midgame(bh: Blackhole): Unit =
    bh.consume(MoveValidator.legalMoves(
      midgameGame.board, midgameGame.currentPlayer,
      midgameGame.movedPieces, midgameGame.lastMove
    ))

  @Benchmark
  def legalMoves_WithCastling(bh: Blackhole): Unit =
    bh.consume(MoveValidator.legalMoves(
      castlingGame.board, castlingGame.currentPlayer,
      castlingGame.movedPieces, castlingGame.lastMove
    ))

  @Benchmark
  def legalMoves_WithEnPassant(bh: Blackhole): Unit =
    bh.consume(MoveValidator.legalMoves(
      enPassantGame.board, enPassantGame.currentPlayer,
      enPassantGame.movedPieces, enPassantGame.lastMove
    ))

  @Benchmark
  def legalMoves_Endgame(bh: Blackhole): Unit =
    bh.consume(MoveValidator.legalMoves(
      endgameGame.board, endgameGame.currentPlayer,
      endgameGame.movedPieces, endgameGame.lastMove
    ))

  // ── isValidMove ───────────────────────────────────────────────

  @Benchmark
  def isValidMove_e2e4(bh: Blackhole): Unit =
    bh.consume(MoveValidator.isValidMove(
      e2e4, startGame.board,
      startGame.movedPieces, startGame.lastMove
    ))

  // ── isInCheck ────────────────────────────────────────────────

  @Benchmark
  def isInCheck_StartPosition(bh: Blackhole): Unit =
    bh.consume(MoveValidator.isInCheck(startGame.board, Color.White))

  @Benchmark
  def isInCheck_Midgame(bh: Blackhole): Unit =
    bh.consume(MoveValidator.isInCheck(midgameGame.board, Color.White))

  // ── applyMove ────────────────────────────────────────────────

  @Benchmark
  def applyMove_e2e4(bh: Blackhole): Unit =
    bh.consume(startGame.applyMove(e2e4))
