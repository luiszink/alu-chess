package chess.model.benchmark

import chess.model.dao.GameRow

/** Curated set of *real* chess games used as seed data for the DAO
  * benchmark. PGNs are stored verbatim (not re-validated) — for a pure
  * persistence benchmark the only thing that matters is that the payload
  * sizes and shapes are realistic. The list intentionally mixes short,
  * medium and long games so byte-size variance is realistic.
  */
object RealGameSamples:

  /** A small library of historically notable games. */
  val pgns: Vector[String] = Vector(
    // Anderssen vs. Kieseritzky, "Immortal Game", London 1851
    """[Event "Casual Game"]
[Site "London ENG"]
[Date "1851.06.21"]
[White "Adolf Anderssen"]
[Black "Lionel Kieseritzky"]
[Result "1-0"]

1.e4 e5 2.f4 exf4 3.Bc4 Qh4+ 4.Kf1 b5 5.Bxb5 Nf6 6.Nf3 Qh6 7.d3 Nh5
8.Nh4 Qg5 9.Nf5 c6 10.g4 Nf6 11.Rg1 cxb5 12.h4 Qg6 13.h5 Qg5 14.Qf3 Ng8
15.Bxf4 Qf6 16.Nc3 Bc5 17.Nd5 Qxb2 18.Bd6 Bxg1 19.e5 Qxa1+ 20.Ke2 Na6
21.Nxg7+ Kd8 22.Qf6+ Nxf6 23.Be7# 1-0""",

    // Morphy vs. Duke of Brunswick & Count Isouard, "Opera Game", Paris 1858
    """[Event "Casual Game"]
[Site "Paris FRA"]
[Date "1858.??.??"]
[White "Paul Morphy"]
[Black "Duke Karl / Count Isouard"]
[Result "1-0"]

1.e4 e5 2.Nf3 d6 3.d4 Bg4 4.dxe5 Bxf3 5.Qxf3 dxe5 6.Bc4 Nf6 7.Qb3 Qe7
8.Nc3 c6 9.Bg5 b5 10.Nxb5 cxb5 11.Bxb5+ Nbd7 12.O-O-O Rd8 13.Rxd7 Rxd7
14.Rd1 Qe6 15.Bxd7+ Nxd7 16.Qb8+ Nxb8 17.Rd8# 1-0""",

    // Kasparov vs. Topalov, Wijk aan Zee 1999, "Kasparov's Immortal"
    """[Event "Hoogovens A Tournament"]
[Site "Wijk aan Zee NED"]
[Date "1999.01.20"]
[White "Garry Kasparov"]
[Black "Veselin Topalov"]
[Result "1-0"]

1.e4 d6 2.d4 Nf6 3.Nc3 g6 4.Be3 Bg7 5.Qd2 c6 6.f3 b5 7.Nge2 Nbd7 8.Bh6 Bxh6
9.Qxh6 Bb7 10.a3 e5 11.O-O-O Qe7 12.Kb1 a6 13.Nc1 O-O-O 14.Nb3 exd4 15.Rxd4 c5
16.Rd1 Nb6 17.g3 Kb8 18.Na5 Ba8 19.Bh3 d5 20.Qf4+ Ka7 21.Rhe1 d4 22.Nd5 Nbxd5
23.exd5 Qd6 24.Rxd4 cxd4 25.Re7+ Kb6 26.Qxd4+ Kxa5 27.b4+ Ka4 28.Qc3 Qxd5
29.Ra7 Bb7 30.Rxb7 Qc4 31.Qxf6 Kxa3 32.Qxa6+ Kxb4 33.c3+ Kxc3 34.Qa1+ Kd2
35.Qb2+ Kd1 36.Bf1 Rd2 37.Rd7 Rxd7 38.Bxc4 bxc4 39.Qxh8 Rd3 40.Qa8 c3
41.Qa4+ Ke1 42.f4 f5 43.Kc1 Rd2 44.Qa7 1-0""",

    // Fischer vs. Spassky, World Championship 1972, Game 6
    """[Event "World Championship 28th"]
[Site "Reykjavik ISL"]
[Date "1972.07.23"]
[White "Robert James Fischer"]
[Black "Boris Spassky"]
[Result "1-0"]

1.c4 e6 2.Nf3 d5 3.d4 Nf6 4.Nc3 Be7 5.Bg5 O-O 6.e3 h6 7.Bh4 b6 8.cxd5 Nxd5
9.Bxe7 Qxe7 10.Nxd5 exd5 11.Rc1 Be6 12.Qa4 c5 13.Qa3 Rc8 14.Bb5 a6 15.dxc5 bxc5
16.O-O Ra7 17.Be2 Nd7 18.Nd4 Qf8 19.Nxe6 fxe6 20.e4 d4 21.f4 Qe7 22.e5 Rb8
23.Bc4 Kh8 24.Qh3 Nf8 25.b3 a5 26.f5 exf5 27.Rxf5 Nh7 28.Rcf1 Qd8 29.Qg3 Re7
30.h4 Rbb7 31.e6 Rbc7 32.Qe5 Qe8 33.a4 Qd8 34.R1f2 Qe8 35.R2f3 Qd8 36.Bd3 Qe8
37.Qe4 Nf6 38.Rxf6 gxf6 39.Rxf6 Kg8 40.Bc4 Kh8 41.Qf4 1-0""",

    // Karpov vs. Kasparov, World Championship 1985, Game 16
    """[Event "World Championship 32nd"]
[Site "Moscow URS"]
[Date "1985.10.15"]
[White "Anatoly Karpov"]
[Black "Garry Kasparov"]
[Result "0-1"]

1.e4 c5 2.Nf3 e6 3.d4 cxd4 4.Nxd4 Nc6 5.Nb5 d6 6.c4 Nf6 7.N1c3 a6 8.Na3 d5
9.cxd5 exd5 10.exd5 Nb4 11.Be2 Bc5 12.O-O O-O 13.Bf3 Bf5 14.Bg5 Re8 15.Qd2 b5
16.Rad1 Nd3 17.Nab1 h6 18.Bh4 b4 19.Na4 Bd6 20.Bg3 Rc8 21.b3 g5 22.Bxd6 Qxd6
23.g3 Nd7 24.Bg2 Qf6 25.a3 a5 26.axb4 axb4 27.Qa2 Bg6 28.d6 g4 29.Qd2 Kg7
30.f3 Qxd6 31.fxg4 Qd4+ 32.Kh1 Nf6 33.Rf4 Ne4 34.Qxd3 Nf2+ 35.Rxf2 Bxd3
36.Rfd2 Qe3 37.Rxd3 Rc1 38.Nb2 Qf2 39.Nd2 Rxd1+ 40.Nxd1 Re1+ 0-1"""
  )

  private val basePgnBytes: Int = pgns.iterator.map(_.length).sum

  /** Approximate average byte size of one synthetic row (only used for
    * informational metadata, not for measurements). */
  def estimatedRowSize: Int =
    val avgPgn = pgns.map(_.length).sum / pgns.size
    avgPgn + 200 // overhead for FEN, id, etc.

  /** Build a deterministic synthetic row out of the curated PGN list.
    * `index` selects which PGN to base it on; the id is unique. */
  def buildRow(index: Int, idPrefix: String): GameRow =
    val pgn = pgns(math.floorMod(index, pgns.size))
    val whiteWins = pgn.contains("\"1-0\"")
    val blackWins = pgn.contains("\"0-1\"")
    val result =
      if whiteWins then "1-0" else if blackWins then "0-1" else "1/2-1/2"
    GameRow(
      id              = s"$idPrefix-$index",
      datePlayed      = java.time.LocalDateTime.now().toString,
      result          = result,
      pgn             = pgn,
      // We store a generic starting position FEN; a full FEN replay isn't
      // required for a persistence benchmark.
      fen             = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
      moveCount       = pgn.count(_ == '.'),
      timeControlName = Some("Classical"),
      initialTimeMs   = Some(5_400_000L),
      incrementMs     = Some(30_000L),
    )

  /** Generate `n` synthetic rows. */
  def buildBatch(n: Int, idPrefix: String): Vector[GameRow] =
    Vector.tabulate(n)(i => buildRow(i, idPrefix))
