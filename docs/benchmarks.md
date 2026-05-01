# JMH Benchmarks

## Überblick

Das `benchmark/`-Modul enthält Scala-JMH-Mikrobenchmarks für die performance-kritischen Teile des `model`-Moduls. Es ist bewusst **nicht** im Root-Aggregate registriert — `sbt test` und `sbt compile` bleiben dadurch unverändert.

---

## Benchmarks

| Datei | Was gemessen wird | Zeiteinheit |
|---|---|---|
| `FenBenchmark` | FEN-Parser (Fast / Regex / Combinator) + `toFen` Serialisierung | µs |
| `MoveGenerationBenchmark` | `legalMoves`, `isValidMove`, `isInCheck`, `applyMove` | µs |
| `EvaluatorBenchmark` | `Evaluator.evaluate`, Board-Primitives (put/clear/move/cell) | ns |
| `AlphaBetaBenchmark` | Alpha-Beta-Suche bei Tiefe 1, 2, 3 + Matt-in-1-Erkennung | ms |

### Teststellungen

- **Startstellung** — symmetrisch, alle Figuren, ~20 legale Züge
- **Mittelspiel** — offene Linien, rochierte Könige, ~35+ Züge
- **Rochade** — beide Seiten rochadebereit (testet Sonder-Zugpfad)
- **En-Passant** — En-passant-Schlag möglich
- **Endspiel** — spärliches Brett, wenige Züge

---

## Benchmarks ausführen

```bash
# Alle Benchmarks (dauert ~10–20 min für AlphaBeta-Tiefe 3)
sbt "benchmark/Jmh/run"

# Einzelne Klasse
sbt "benchmark/Jmh/run chess.benchmark.FenBenchmark"

# Schneller Smoke-Test (1 Warmup, 1 Messung, 1 Fork — für CI oder schnelle Überprüfung)
sbt "benchmark/Jmh/run -wi 1 -i 1 -f 1"

# Nur kompilieren (ohne ausführen)
sbt benchmark/Jmh/compile

# JSON-Output für Visualisierung mit jmh.morethan.io
sbt "benchmark/Jmh/run -rf json -rff benchmark-results.json"
```

---

## Erwartete Größenordnungen

| Benchmark | Erwarteter Bereich |
|---|---|
| `parse_Fast_*` | 10–50 µs |
| `parse_Regex_*` | 20–100 µs |
| `parse_Combinator_*` | 50–300 µs |
| `toFen_*` | 5–30 µs |
| `legalMoves_Start` | 50–200 µs |
| `legalMoves_Midgame` | 100–500 µs |
| `evaluate_*` | 200–2000 ns |
| `board_put` / `board_clear` | 50–200 ns |
| `search_Depth1_*` | 1–10 ms |
| `search_Depth2_*` | 10–100 ms |
| `search_Depth3_*` | 50–500 ms |

---

## Architektur

```
benchmark/
└── src/main/scala/chess/benchmark/
    ├── FenBenchmark.scala
    ├── MoveGenerationBenchmark.scala
    ├── EvaluatorBenchmark.scala
    └── AlphaBetaBenchmark.scala
```

- Abhängigkeit: nur `model` (keine Abhängigkeit auf `controller` oder `playerservice`)
- `coverageEnabled := false` — verhindert Instrumentierung, die Timings verfälscht
- Nicht im Root-Aggregate — beeinträchtigt `sbt test` / `sbt assembly` nicht
