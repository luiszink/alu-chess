First run of benchmarks:

[info] Benchmark                                         Mode  Cnt      Score      Error  Units
[info] AlphaBetaBenchmark.search_Depth1_Midgame          avgt    5   4309,492   130,224  ms/op
[info] AlphaBetaBenchmark.search_Depth1_Start            avgt    5     11,423     0,431  ms/op
[info] AlphaBetaBenchmark.search_Depth2_Midgame          avgt    5  54921,456  1939,538  ms/op
[info] AlphaBetaBenchmark.search_Depth2_Start            avgt    5    851,857    14,948  ms/op
[info] AlphaBetaBenchmark.search_Depth3_Midgame          avgt    5  60007,944   13,978  ms/op
[info] AlphaBetaBenchmark.search_Depth3_Start            avgt    5   1766,692   60,071  ms/op
[info] AlphaBetaBenchmark.search_MateIn1                 avgt    5      1,177    0,009  ms/op
[info] EvaluatorBenchmark.board_cell_lookup              avgt   20      2,268    0,012  ns/op
[info] EvaluatorBenchmark.board_clear                    avgt   20     24,993    0,259  ns/op
[info] EvaluatorBenchmark.board_move                     avgt   20     55,249    0,775  ns/op
[info] EvaluatorBenchmark.board_put                      avgt   20     30,442    0,631  ns/op
[info] EvaluatorBenchmark.evaluate_Endgame               avgt   20    554,091   98,551  ns/op
[info] EvaluatorBenchmark.evaluate_Midgame               avgt   20    572,725   22,399  ns/op
[info] EvaluatorBenchmark.evaluate_Start                 avgt   20    533,336   29,974  ns/op
[info] FenBenchmark.parse_Combinator_Midgame             avgt   20    231,586    4,957  us/op
[info] FenBenchmark.parse_Combinator_Start               avgt   20    144,149    4,718  us/op
[info] FenBenchmark.parse_Fast_EnPassant                 avgt   20    117,250    2,958  us/op
[info] FenBenchmark.parse_Fast_Midgame                   avgt   20    229,791   13,192  us/op
[info] FenBenchmark.parse_Fast_Start                     avgt   20    141,697    9,170  us/op
[info] FenBenchmark.parse_Regex_Midgame                  avgt   20    206,541    5,286  us/op
[info] FenBenchmark.parse_Regex_Start                    avgt   20    118,385    1,814  us/op
[info] FenBenchmark.toFen_Midgame                        avgt   20      0,599    0,010  us/op
[info] FenBenchmark.toFen_Start                          avgt   20      0,578    0,013  us/op
[info] MoveGenerationBenchmark.applyMove_e2e4            avgt   20    120,467    2,016  us/op
[info] MoveGenerationBenchmark.isInCheck_Midgame         avgt   20      3,113    0,070  us/op
[info] MoveGenerationBenchmark.isInCheck_StartPosition   avgt   20      3,364    0,060  us/op
[info] MoveGenerationBenchmark.isValidMove_e2e4          avgt   20      0,013    0,001  us/op
[info] MoveGenerationBenchmark.legalMoves_Endgame        avgt   20     40,448    1,094  us/op
[info] MoveGenerationBenchmark.legalMoves_Midgame        avgt   20    189,347    2,030  us/op
[info] MoveGenerationBenchmark.legalMoves_Start          avgt   20    105,939    1,368  us/op
[info] MoveGenerationBenchmark.legalMoves_WithCastling   avgt   20    117,478    2,128  us/op
[info] MoveGenerationBenchmark.legalMoves_WithEnPassant  avgt   20    111,853    3,280  us/op
[success] Total time: 1907 s (31:47), completed 01.05.2026, 12:08:43


After optimizations:
[info] Benchmark                                         Mode  Cnt    Score    Error  Units
[info] AlphaBetaBenchmark.search_Depth1_Midgame          avgt    5   25,697 ┬  1,019  ms/op
[info] AlphaBetaBenchmark.search_Depth1_Start            avgt    5    4,158 ┬  0,417  ms/op
[info] AlphaBetaBenchmark.search_Depth2_Midgame          avgt    5  123,448 ┬  7,208  ms/op
[info] AlphaBetaBenchmark.search_Depth2_Start            avgt    5   30,648 ┬  3,065  ms/op
[info] AlphaBetaBenchmark.search_Depth3_Midgame          avgt    5  361,855 ┬ 30,883  ms/op
[info] AlphaBetaBenchmark.search_Depth3_Start            avgt    5   77,365 ┬  1,895  ms/op
[info] AlphaBetaBenchmark.search_MateIn1                 avgt    5    1,219 ┬  0,122  ms/op
[info] EvaluatorBenchmark.board_cell_lookup              avgt   20    2,375 ┬  0,049  ns/op
[info] EvaluatorBenchmark.board_clear                    avgt   20   25,508 ┬  0,303  ns/op
[info] EvaluatorBenchmark.board_move                     avgt   20   56,983 ┬  0,635  ns/op
[info] EvaluatorBenchmark.board_put                      avgt   20   27,833 ┬  0,344  ns/op
[info] EvaluatorBenchmark.evaluate_Endgame               avgt   20  262,813 ┬  4,328  ns/op
[info] EvaluatorBenchmark.evaluate_Midgame               avgt   20  482,867 ┬ 11,171  ns/op
[info] EvaluatorBenchmark.evaluate_Start                 avgt   20  464,912 ┬ 28,278  ns/op
[info] FenBenchmark.parse_Combinator_Midgame             avgt   20  204,554 ┬  4,392  us/op
[info] FenBenchmark.parse_Combinator_Start               avgt   20  124,666 ┬  1,374  us/op
[info] FenBenchmark.parse_Fast_EnPassant                 avgt   20  105,238 ┬  1,222  us/op
[info] FenBenchmark.parse_Fast_Midgame                   avgt   20  179,357 ┬  1,718  us/op
[info] FenBenchmark.parse_Fast_Start                     avgt   20  102,742 ┬  1,237  us/op
[info] FenBenchmark.parse_Regex_Midgame                  avgt   20  179,462 ┬  1,745  us/op
[info] FenBenchmark.parse_Regex_Start                    avgt   20  103,347 ┬  1,939  us/op
[info] FenBenchmark.toFen_Midgame                        avgt   20    0,567 ┬  0,023  us/op
[info] FenBenchmark.toFen_Start                          avgt   20    0,581 ┬  0,026  us/op
[info] MoveGenerationBenchmark.applyMove_e2e4            avgt   20  106,273 ┬  1,743  us/op
[info] MoveGenerationBenchmark.isInCheck_Midgame         avgt   20    2,748 ┬  0,044  us/op
[info] MoveGenerationBenchmark.isInCheck_StartPosition   avgt   20    3,026 ┬  0,311  us/op
[info] MoveGenerationBenchmark.isValidMove_e2e4          avgt   20    0,012 ┬  0,001  us/op
[info] MoveGenerationBenchmark.legalMoves_Endgame        avgt   20   36,083 ┬  0,373  us/op
[info] MoveGenerationBenchmark.legalMoves_Midgame        avgt   20  172,260 ┬  1,930  us/op
[info] MoveGenerationBenchmark.legalMoves_Start          avgt   20   96,317 ┬  0,954  us/op
[info] MoveGenerationBenchmark.legalMoves_WithCastling   avgt   20  104,859 ┬  1,089  us/op
[info] MoveGenerationBenchmark.legalMoves_WithEnPassant  avgt   20   97,919 ┬  1,069  us/op
[success] Total time: 1006 s (16:46), completed 01.05.2026, 13:20:12