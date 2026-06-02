package chess.benchmark

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import chess.model.{Fen, Board, Color, Move, Position, Piece}
import chess.model.ai.Evaluator

/** Board-Evaluierung und Board-Primitives.
  *
  * Evaluator.evaluate wird an jedem Blattknoten der Suche aufgerufen.
  * Board.put / clear / move messen die Kosten der persistenten
  * Vector-Datenstruktur (copy-on-write) die Board intern verwendet.
  *
  * Zeiteinheit: Nanoseconds — diese Operationen sind sehr schnell (~100–2000 ns).
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = Array("-Xms256m", "-Xmx256m"))
@State(Scope.Benchmark)
class EvaluatorBenchmark:

  val startFen: String =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  val midgameFen: String =
    "r1bq1rk1/pp2ppbp/2np1np1/8/3NP3/2N1BP2/PPPQ2PP/R3KB1R w KQ - 0 9"

  val endgameFen: String =
    "8/8/4k3/8/8/4K3/3B4/8 w - - 0 1"

  var startBoard: Board   = _
  var midgameBoard: Board = _
  var endgameBoard: Board = _
  var e2: Position        = _
  var e4: Position        = _
  var e2e4: Move          = _

  @Setup(Level.Trial)
  def setup(): Unit =
    startBoard   = Fen.parse(startFen).get.board
    midgameBoard = Fen.parse(midgameFen).get.board
    endgameBoard = Fen.parse(endgameFen).get.board
    e2   = Position.fromString("e2").get
    e4   = Position.fromString("e4").get
    e2e4 = Move(e2, e4)

  // ── Evaluator ─────────────────────────────────────────────────

  @Benchmark
  def evaluate_Start(bh: Blackhole): Unit =
    bh.consume(Evaluator.evaluate(startBoard))

  @Benchmark
  def evaluate_Midgame(bh: Blackhole): Unit =
    bh.consume(Evaluator.evaluate(midgameBoard))

  @Benchmark
  def evaluate_Endgame(bh: Blackhole): Unit =
    bh.consume(Evaluator.evaluate(endgameBoard))

  // ── Board-Primitives ──────────────────────────────────────────
  // Messen den Overhead von Vector.updated (persistente Datenstruktur).

  @Benchmark
  def board_put(bh: Blackhole): Unit =
    bh.consume(startBoard.put(e4, Piece.Pawn(Color.White)))

  @Benchmark
  def board_clear(bh: Blackhole): Unit =
    bh.consume(startBoard.clear(e2))

  @Benchmark
  def board_move(bh: Blackhole): Unit =
    bh.consume(startBoard.move(e2e4))

  @Benchmark
  def board_cell_lookup(bh: Blackhole): Unit =
    bh.consume(startBoard.cell(e2))
