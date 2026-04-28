# Performance-Testing für die DAO-Schicht — Plan

> Branch (beide Repos): `feature/performance-testing` (basiert auf `feature/dao`)

## 1. Ausgangslage

Im Branch `feature/dao` wurde eine austauschbare Persistenzschicht über `GameDao`
eingeführt. Drei Implementierungen existieren parallel und werden via `DB_TYPE`
Environment-Variable umgeschaltet:

| `DB_TYPE`  | Implementierung            | Datei                                                   |
|------------|----------------------------|---------------------------------------------------------|
| `postgres` | `SlickGameDao`             | [model/src/main/scala/chess/model/dao/SlickGameDao.scala](model/src/main/scala/chess/model/dao/SlickGameDao.scala) |
| `mongo`    | `MongoGameDao`             | [model/src/main/scala/chess/model/dao/MongoGameDao.scala](model/src/main/scala/chess/model/dao/MongoGameDao.scala) |
| `memory`   | `InMemoryGameRepository`   | [model/src/main/scala/chess/model/InMemoryGameRepository.scala](model/src/main/scala/chess/model/InMemoryGameRepository.scala) |

Da das Interface [`GameDao`](model/src/main/scala/chess/model/dao/GameDao.scala) für
alle Backends identisch ist (`init`, `insert`, `findAll`, `findById`, `delete`,
`clear`), eignet sich die DAO-Ebene perfekt für einen vergleichenden
Performance-Test.

HTTP-Stack im Controller: **http4s + cats-effect IO** (siehe
[ControllerRoutes.scala](controller/src/main/scala/chess/controller/api/ControllerRoutes.scala)).

## 2. Ziel

1. **Backend**: Reproduzierbares Benchmarking der drei DAO-Implementierungen
   über REST-Endpoints, die der Frontend-User per Knopfdruck triggern kann.
2. **Frontend**: Neue Seite `/performance` in `alu-chess-web`, auf der
   - der gewünschte DAO ausgewählt,
   - Parameter (Anzahl Datensätze, Iterationen, Operationen) gesetzt,
   - der Benchmark gestartet und
   - die Ergebnisse als Tabelle + Diagramm visualisiert
   werden können.

## 3. Backend-Plan (`alu-chess`)

### 3.1 Benchmark-Engine (neues Modul)

Neuer Pfad: `model/src/main/scala/chess/model/benchmark/`

- `BenchmarkResult.scala` — Case-Klassen für ein einzelnes Run-Ergebnis und
  aggregierte Statistik (min, max, mean, median, p95, p99, ops/sec, totalMs).
- `DaoBenchmark.scala` — pure cats-effect-Logik:
  ```scala
  final case class BenchmarkConfig(
    operation: BenchmarkOp,    // Insert | FindAll | FindById | Delete | Mixed
    recordCount: Int,          // wie viele Datensätze pro Run
    iterations: Int,           // wie oft die Operation wiederholt wird
    warmupIterations: Int,     // verworfen vor Messung
    seed: Long,
  )
  def run(dao: GameDao, cfg: BenchmarkConfig): IO[BenchmarkResult]
  ```
  - Misst pro Iteration mit `IO.monotonic`.
  - Erzeugt deterministische `GameRow`s aus dem Seed.
  - Führt `dao.clear()` zwischen Runs aus, um Seiteneffekte zu vermeiden.
  - Liefert Latenzen in Nanosekunden + abgeleitete Stats.

### 3.2 DAO-Registry

Neue Datei `controller/src/main/scala/chess/controller/perf/DaoRegistry.scala`:
- Hält Referenzen auf **alle drei** DAOs gleichzeitig (keine Auswahl mehr per
  ENV ausschließlich).
- Wird in `Chess.scala` / Server-Bootstrap initialisiert (Postgres/Mongo nur
  wenn die jeweilige Verbindung erfolgreich aufgebaut wurde — sonst als
  „unavailable" markiert, damit der Endpoint einen sauberen 503 zurückgibt).
- Methode `lookup(name: String): Option[GameDao]`.

> ⚠️ Konsequenz: `docker-compose.yml` wird so angepasst, dass im
> `performance`-Profil **beide** Datenbanken parallel laufen, der Controller
> URLs für beide kennt und im Default-Profil das Verhalten unverändert bleibt.

### 3.3 REST-Endpoints (neue Routen-Datei)

Neuer Pfad: `controller/src/main/scala/chess/controller/api/PerfRoutes.scala`
(eingehängt in `ControllerServer.scala`).

| Methode | Pfad                                 | Beschreibung                                                  |
|---------|--------------------------------------|---------------------------------------------------------------|
| `GET`   | `/api/perf/dao/list`                 | Verfügbare DAOs + Health-Status                               |
| `POST`  | `/api/perf/dao/{name}/benchmark`     | Startet einen Benchmark, Body = `BenchmarkConfig`             |
| `POST`  | `/api/perf/dao/compare`              | Führt denselben Benchmark gegen mehrere DAOs aus              |
| `GET`   | `/api/perf/runs`                     | Liste aller bisher gelaufenen Benchmarks (in-memory Ringpuffer) |
| `GET`   | `/api/perf/runs/{id}`                | Detail eines Runs (alle Latenzen für Histogramm)              |

Alle Routen geben JSON via `circe` zurück (gleicher Stil wie bestehende Routen).

### 3.4 Tests

- `model/src/test/scala/chess/model/benchmark/DaoBenchmarkSpec.scala`
  - Nutzt `InMemoryGameRepository` als DAO-Adapter.
  - Verifiziert: `recordCount` Datensätze nach Insert vorhanden, Stats sind
    monoton, ops/sec > 0.
- `controller/src/test/scala/chess/controller/api/PerfRoutesSpec.scala`
  - http4s `Router`-Tests mit Mock-DAOs.

## 4. Frontend-Plan (`alu-chess-web`)

### 4.1 Neue Abhängigkeit

`recharts` (klein, React-19-kompatibel, ausreichend für Bar/Line-Charts).
Alternative: `chart.js` + `react-chartjs-2` — falls schon Erfahrung
vorhanden, sonst `recharts`.

### 4.2 API-Client

Neue Datei `src/api/perfApi.ts` mit Pendants zu obigen Endpoints. Folgt dem
existierenden `get`/`post`-Pattern aus
[`controllerApi.ts`](../alu-chess-web/src/api/controllerApi.ts).

### 4.3 Typen

`src/types/perf.ts`:
```ts
export type DaoName = 'postgres' | 'mongo' | 'memory';
export type BenchmarkOp = 'insert' | 'findAll' | 'findById' | 'delete' | 'mixed';
export interface BenchmarkConfig {
  operation: BenchmarkOp;
  recordCount: number;
  iterations: number;
  warmupIterations: number;
  seed?: number;
}
export interface BenchmarkStats {
  minMs: number; maxMs: number; meanMs: number;
  medianMs: number; p95Ms: number; p99Ms: number;
  opsPerSec: number; totalMs: number;
}
export interface BenchmarkResult {
  id: string; dao: DaoName; config: BenchmarkConfig;
  stats: BenchmarkStats; startedAt: string; latenciesMs: number[];
}
```

### 4.4 Neue Seite

`src/pages/PerformancePage.tsx`:

Layout (Top → Bottom):
1. **Konfigurator** — Form mit:
   - DAO-Multiselect (Checkboxen `postgres`, `mongo`, `memory`),
   - Operation-Dropdown,
   - Numeric Inputs `recordCount`, `iterations`, `warmupIterations`,
   - Button „Benchmark starten" (POST `/api/perf/dao/compare`).
2. **Live-Status** — Spinner + zuletzt empfangener Run.
3. **Vergleichstabelle** — Spalten: DAO, Operation, mean, median, p95, p99, ops/sec.
4. **Bar-Chart** — Latenz (mean/p95/p99) je DAO pro Operation.
5. **Verlauf** — Liste der letzten N Runs (vom `/api/perf/runs`-Endpoint),
   Klick lädt Detailansicht (Histogramm-Chart aus `latenciesMs`).

Komponentenstruktur:
```
src/
  pages/PerformancePage.tsx
  components/Performance/
    BenchmarkForm.tsx
    ResultsTable.tsx
    LatencyBarChart.tsx
    LatencyHistogram.tsx
    RunHistory.tsx
```

### 4.5 Routing & Navigation

- `App.tsx`: neuer Route `<Route path="/performance" element={<PerformancePage />} />`.
- `NavBar.tsx`: neuer Eintrag in `NAV_LINKS`:
  `{ to: '/performance', label: 'Performance' }`.

### 4.6 Tests

- Komponententests sind im aktuellen Setup nicht vorhanden — daher
  vorerst nur manuelles Smoke-Testing über die UI.

## 5. Docker / Compose

`docker-compose.yml`:
- Neues Profil `perf`, das `postgres`, `mongo`, `controller`, `model`,
  `playerservice`, `frontend`, `stockfish` zusammen startet.
- Controller bekommt zusätzlich `MONGO_URI`, `MONGO_DB`, `DB_URL`,
  `DB_USER`, `DB_PASSWORD` **immer** gesetzt; `DB_TYPE` bleibt für die
  „normale" Persistenz, das Perf-Modul nutzt unabhängig davon alle drei
  DAOs.

Aufruf:
```bash
docker compose --profile perf up --build
```

## 6. Lieferschnitte (Pull-Request-Reihenfolge)

1. **PR 1 — Backend Benchmark Engine + Tests**
   `feature/performance-testing` in `alu-chess`:
   - `BenchmarkConfig`, `BenchmarkResult`, `DaoBenchmark`, Specs.
2. **PR 2 — Backend REST-Endpoints + DaoRegistry + Compose-Profil**
   - `PerfRoutes`, `DaoRegistry`, Bootstrap-Integration, RoutesSpec.
3. **PR 3 — Frontend Performance-Seite**
   `feature/performance-testing` in `alu-chess-web`:
   - `perfApi.ts`, Typen, Page + Komponenten, Routing, NavBar, `recharts`.
4. **PR 4 — Doku-Update** (`README.md` beider Repos, Screenshot der Seite).

## 7. Offene Fragen / Bestätigung benötigt

- [ ] Charting-Bibliothek: **recharts** ok, oder lieber chart.js / Plotly?
- [ ] Sollen Run-Historien persistiert werden (eigene Tabelle / Collection)
      oder reicht ein In-Memory-Ringpuffer (z. B. letzte 50 Runs)?
- [ ] Soll der Controller selbst zwischen DAOs gleichzeitig laufen können
      (Registry-Ansatz aus 3.2), oder soll der Benchmark-Endpoint die DAOs
      bei Bedarf transient öffnen und wieder schließen?
- [ ] Mock-/Stress-Daten: realistische `GameRow`s aus echten PGNs erzeugen,
      oder synthetische generische Bytes-Payload?

Sobald diese Punkte geklärt sind, beginne ich mit **PR 1**.
