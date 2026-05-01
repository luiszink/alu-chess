package chess.benchmark

import org.openjdk.jmh.annotations.*
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import chess.model.{Fen, FenParserType, Game}

/** FEN-Parser und Serialisierung im Vergleich.
  *
  * Drei Implementierungen existieren: Fast (Standard), Regex, Combinator.
  * Fen.activeParser ist ein var im Companion Object — thread-safe solange
  * JMH single-threaded läuft (Standard) und @Fork separate JVM-Prozesse startet.
  */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgs = Array("-Xms256m", "-Xmx256m"))
@State(Scope.Benchmark)
class FenBenchmark:

  val startFen: String =
    "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

  val midgameFen: String =
    "r1bq1rk1/pp2ppbp/2np1np1/8/3NP3/2N1BP2/PPPQ2PP/R3KB1R w KQ - 0 9"

  val enPassantFen: String =
    "rnbqkbnr/pppp1ppp/8/4pP2/8/8/PPPPP1PP/RNBQKBNR w KQkq e6 0 3"

  var startGame: Game   = _
  var midgameGame: Game = _

  @Setup(Level.Trial)
  def setup(): Unit =
    startGame   = Fen.parse(startFen).get
    midgameGame = Fen.parse(midgameFen).get

  // ── Fast Parser (Standard) ────────────────────────────────────

  @Benchmark
  def parse_Fast_Start(bh: Blackhole): Unit =
    Fen.activeParser = FenParserType.Fast
    bh.consume(Fen.parse(startFen))

  @Benchmark
  def parse_Fast_Midgame(bh: Blackhole): Unit =
    Fen.activeParser = FenParserType.Fast
    bh.consume(Fen.parse(midgameFen))

  @Benchmark
  def parse_Fast_EnPassant(bh: Blackhole): Unit =
    Fen.activeParser = FenParserType.Fast
    bh.consume(Fen.parse(enPassantFen))

  // ── Regex Parser ──────────────────────────────────────────────

  @Benchmark
  def parse_Regex_Start(bh: Blackhole): Unit =
    Fen.activeParser = FenParserType.Regex
    bh.consume(Fen.parse(startFen))

  @Benchmark
  def parse_Regex_Midgame(bh: Blackhole): Unit =
    Fen.activeParser = FenParserType.Regex
    bh.consume(Fen.parse(midgameFen))

  // ── Combinator Parser ─────────────────────────────────────────

  @Benchmark
  def parse_Combinator_Start(bh: Blackhole): Unit =
    Fen.activeParser = FenParserType.Combinator
    bh.consume(Fen.parse(startFen))

  @Benchmark
  def parse_Combinator_Midgame(bh: Blackhole): Unit =
    Fen.activeParser = FenParserType.Combinator
    bh.consume(Fen.parse(midgameFen))

  // ── Serialisierung ────────────────────────────────────────────
  // toFen wird bei jedem Zug für repetitionKey aufgerufen.

  @Benchmark
  def toFen_Start(bh: Blackhole): Unit =
    bh.consume(Fen.toFen(startGame))

  @Benchmark
  def toFen_Midgame(bh: Blackhole): Unit =
    bh.consume(Fen.toFen(midgameGame))
