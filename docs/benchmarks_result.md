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
