package chess.benchmark

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import chess.model.{Fen, Game}
import chess.model.ai.AlphaBeta

/** Alpha-Beta-Suche bei fixer Tiefe.
  *
  * Tiefe 1–3 decken den praxisrelevanten Bereich ab. Tiefe 4+ dauert mehrere
  * Sekunden pro Aufruf — zu langsam für reproduzierbare Messungen.
  *
  * timeLimitMs = 60 s stellt sicher, dass die Tiefe (nicht der Timer) die
  * Suche beendet → reproduzierbare Knotenanzahl auf verschiedenen Maschinen.
  *
  * Zeiteinheit: Milliseconds — KI-Suche dauert 1–500 ms je nach Tiefe/Stellung.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 5, timeUnit = TimeUnit.SECONDS)
@Fork(value = 1, jvmArgs = Array("-Xms256m", "-Xmx512m"))
@State(Scope.Benchmark)
class AlphaBetaBenchmark:

  val startFen: String =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  val midgameFen: String =
    "r1bq1rk1/pp2ppbp/2np1np1/8/3NP3/2N1BP2/PPPQ2PP/R3KB1R w KQ - 0 9"

  val mateIn1Fen: String =
    "7k/8/6K1/8/8/8/8/R7 w - - 0 1"

  val timeLimitMs: Long = 60_000L

  var startGame: Game   = _
  var midgameGame: Game = _
  var mateIn1Game: Game = _

  @Setup(Level.Trial)
  def setup(): Unit =
    startGame   = Fen.parse(startFen).get
    midgameGame = Fen.parse(midgameFen).get
    mateIn1Game = Fen.parse(mateIn1Fen).get

  // ── Tiefe 1: reine Zugaufzählung + Evaluation ─────────────────

  @Benchmark
  def search_Depth1_Start(bh: Blackhole): Unit =
    bh.consume(AlphaBeta.bestMove(startGame, timeLimitMs, maxDepth = 1))

  @Benchmark
  def search_Depth1_Midgame(bh: Blackhole): Unit =
    bh.consume(AlphaBeta.bestMove(midgameGame, timeLimitMs, maxDepth = 1))

  // ── Tiefe 2: Root + eine Gegner-Antwort ───────────────────────

  @Benchmark
  def search_Depth2_Start(bh: Blackhole): Unit =
    bh.consume(AlphaBeta.bestMove(startGame, timeLimitMs, maxDepth = 2))

  @Benchmark
  def search_Depth2_Midgame(bh: Blackhole): Unit =
    bh.consume(AlphaBeta.bestMove(midgameGame, timeLimitMs, maxDepth = 2))

  // ── Tiefe 3: Alpha-Beta-Pruning beginnt zu wirken ─────────────

  @Benchmark
  def search_Depth3_Start(bh: Blackhole): Unit =
    bh.consume(AlphaBeta.bestMove(startGame, timeLimitMs, maxDepth = 3))

  @Benchmark
  def search_Depth3_Midgame(bh: Blackhole): Unit =
    bh.consume(AlphaBeta.bestMove(midgameGame, timeLimitMs, maxDepth = 3))

  // ── Matt-in-1: Fast-Path-Erkennung ───────────────────────────

  @Benchmark
  def search_MateIn1(bh: Blackhole): Unit =
    bh.consume(AlphaBeta.bestMove(mateIn1Game, timeLimitMs, maxDepth = 3))
