# alu-chess

Eine Schach-Applikation in Scala – Lehrprojekt für den Kurs **Softwarearchitektur**.

Inspiration: [Lichess](https://lichess.org/), aber wesentlich einfacher.

---

## Features

- Vollständige Schachregeln (alle Figurzüge, Rochade, En Passant, Bauernumwandlung)
- Schach-, Schachmatt- und Patt-Erkennung
- Remis-Bedingungen (50-Züge-Regel, unzureichendes Material)
- Swing-GUI im Lichess-Stil mit Drag-and-Click-Bedienung
- Text UI (TUI) mit algebraischer Notation (`e2 e4`, `e7 e8 Q`)
- Schachuhr mit konfigurierbaren Zeitkontrollen (Bullet, Blitz, Rapid, Classical)
- Interne KI (Alpha-Beta) sowie Stockfish-Anbindung über separaten Engine-Service
- FEN-Import/-Export (beliebige Stellungen laden)
- PGN-Export und -Replay (Partien im Standardformat speichern)
- Zughistorie mit SAN-Notation und Navigation (vor/zurück/Anfang/Ende)
- Partie-Archiv mit Replay-Funktion (In-Memory)
- Testpositionen für schnelles Ausprobieren (Scholar's Mate, En Passant, Rochade, …)

---

## Tech Stack

| Komponente | Detail |
|---|---|
| Sprache | Scala 3.6.4 |
| Build Tool | sbt 1.10.7 |
| Python Engine Service | FastAPI 0.115 + Uvicorn + python-chess |
| Testing | ScalaTest 3.2.19 |
| Coverage | sbt-scoverage 2.2.2 |
| GUI | Scala Swing 3.0.0 |
| REST API | Http4s 0.23 + Circe |
| Container | Docker + Docker Compose |
| Reverse Proxy | nginx (API Gateway für alle Services) |
| Architektur | MVC + Observer, Multi-Module (sbt) |

---

## Setup

### Voraussetzungen

- JDK 17+ (empfohlen: JDK 21)
- sbt 1.10.x
- Docker Desktop (für den Stockfish-Engine-Service und Docker Compose)

### Kompilieren

```bash
sbt compile
```

### Tests ausführen

```bash
sbt test
```

### Anwendung starten (TUI + GUI)

```bash
sbt "controller/run"
```

### REST API starten

```bash
# 1. Stockfish-Engine-Service (Port 8000, FastAPI + Stockfish im Container)
docker build -t alu-stockfish-engine:dev .
docker run --name alu-stockfish -d -p 8000:8000 alu-stockfish-engine:dev

# 2. Model- und Controller-Service in einem sbt-Aufruf starten
#    (Model läuft im Hintergrund auf Port 8082, Controller im Vordergrund auf Port 8081)
sbt runAll
```

### Docker Compose (alle Services + Frontend)

Alle Services (Postgres/MongoDB, Stockfish, Model, Controller, PlayerService, Frontend) lassen sich
per Docker Compose starten. Vorher muss `.env` konfiguriert sein:

```bash
# 1. Umgebungsvariablen einrichten
cp .env.example .env
# .env öffnen und mindestens FRONTEND_CONTEXT sowie DB_TYPE setzen
```

Wichtige Variablen in `.env`:

| Variable | Beschreibung | Beispielwert |
|---|---|---|
| `DB_TYPE` | Datenbank-Backend | `memory` \| `postgres` \| `mongo` |
| `FRONTEND_CONTEXT` | Pfad zum `alu-chess-web`-Repo (relativ oder absolut) | `../../SoftwarearchitekturWeb/alu-chess-web` |
| `DB_URL` | JDBC-URL für PostgreSQL | `jdbc:postgresql://postgres:5432/chess` |
| `DB_USER` | DB-Benutzer | `chess` |
| `DB_PASSWORD` | DB-Passwort | `Password` |
| `MONGO_URI` | MongoDB-Verbindungs-URI | `mongodb://mongo:27017` |
| `MONGO_DB` | MongoDB-Datenbankname | `chess` |

```bash
# 2. Alle Services starten (in-memory, kein Datenbank-Profil nötig)
docker compose up --build

# Mit PostgreSQL
docker compose --profile postgres up --build

# Mit MongoDB
docker compose --profile mongo up --build
```

> **Hinweis zu `FRONTEND_CONTEXT`:** Der Pfad wird relativ zur `docker-compose.yml` aufgelöst.
> Falls das `alu-chess-web`-Repo an einem anderen Ort liegt, absoluten Pfad angeben, z. B.
> `FRONTEND_CONTEXT=C:/Users/name/projects/alu-chess-web`.

Nach dem Start ist die gesamte Applikation unter **`http://localhost:3000`** erreichbar.
nginx fungiert als API Gateway und leitet alle `/api/`-Requests intern weiter — die Backend-Ports
8081, 8082 und 8083 sind vom Host aus **nicht** direkt erreichbar.

Alternativ einzeln in separaten Terminals starten:

```bash
# Model-Service (Port 8082)
# Optional: ENGINE_BASE_URL anpassen, falls der Engine-Service nicht auf localhost:8000 läuft
# Windows PowerShell: $env:ENGINE_BASE_URL = "http://localhost:8000"
sbt "model/runMain chess.model.api.ModelServer"

# Controller-Service (Port 8081)
sbt "controller/runMain chess.controller.api.ControllerServer"
```

Stockfish-Container stoppen/entfernen:

```bash
docker stop alu-stockfish
docker rm alu-stockfish
```

> Hinweis: Bei lokalem sbt-Start (ohne Docker Compose) sendet das Web-Frontend
> ([alu-chess-web](../SoftwarearchitekturWeb/alu-chess-web)) Anfragen direkt an
> Port 8081 (Controller) bzw. 8082 (Model). Im Docker-Compose-Setup übernimmt
> nginx das Routing — das Frontend spricht dann ausschließlich mit `localhost:3000`.

### Coverage-Report erzeugen

```bash
sbt clean coverage test coverageReport
```

Der Report liegt anschließend unter:
- `model/target/scala-3.6.4/scoverage-report/index.html`
- `controller/target/scala-3.6.4/scoverage-report/index.html`

---

## Performancetests

Alle Performancetests setzen voraus, dass die Services laufen (Docker Compose oder sbt).
Schnellstart: `docker compose up -d` — nginx ist dann unter `http://localhost:3000` erreichbar.

### JMH

Micro-Benchmarks für reine Scala-Logik (kein laufender Service nötig).
Benchmarks liegen in [`benchmark/src/main/scala/chess/benchmark/`](benchmark/src/main/scala/chess/benchmark/).

```bash
# Alle Benchmarks ausführen
sbt "benchmark/Jmh/run"

# Einzelne Benchmark-Klassen
sbt "benchmark/Jmh/run .*FenBenchmark.*"
sbt "benchmark/Jmh/run .*MoveGenerationBenchmark.*"
sbt "benchmark/Jmh/run .*EvaluatorBenchmark.*"
sbt "benchmark/Jmh/run .*AlphaBetaBenchmark.*"

# Mit Ausgabe-Format (z. B. JSON für externe Auswertung)
sbt "benchmark/Jmh/run -rf json -rff results.json"
```

| Klasse | Testet |
|---|---|
| `FenBenchmark` | FEN-Parser (Fast / Regex / Combinator) |
| `MoveGenerationBenchmark` | `legalMoves`, `isValidMove`, `applyMove` |
| `EvaluatorBenchmark` | `Evaluator.evaluate`, Board-Operationen |
| `AlphaBetaBenchmark` | Alpha-Beta-Suche auf Tiefe 1–3 |

---

### k6

HTTP-Lasttests gegen die laufenden Services. k6 muss installiert sein (`winget install k6` / `brew install k6`)
oder per Docker gestartet werden.

Skripte liegen in [`k6/scripts/`](k6/scripts/).

Der Standard-`BASE_URL` ist bereits `http://localhost:3000` — `--env` ist nur nötig, wenn ein anderer Host verwendet wird.

```bash
# Alle Services testen (Smoke + Load + Ramp, inkl. Multi-Game)
k6 run k6/scripts/all.js

# Einzelne Services
k6 run k6/scripts/model.js
k6 run k6/scripts/controller.js
k6 run k6/scripts/player.js
k6 run k6/scripts/stockfish.js
k6 run k6/scripts/multi-game.js

# Schneller Smoke-Test
k6 run k6/scripts/smoke.js

# Andere BASE_URL (optional)
k6 run k6/scripts/all.js --env BASE_URL=http://meinserver:3000

# Über Docker Compose (kein lokales k6 nötig)
docker compose --profile k6 run --rm k6
docker compose --profile k6 run --rm k6 run /scripts/controller.js
```

| Skript | Testet | Szenario | VUs | Dauer |
|---|---|---|---|---|
| `smoke.js` | alle Services | Smoke | 1 | 10 s |
| `model.js` | Model-Service | Smoke + Load | 1 / 10 | 10 s + 30 s |
| `controller.js` | Controller-Service | Smoke + Load | 1 / 10 | 10 s + 30 s |
| `player.js` | Player-Service | Smoke + Load | 1 / 10 | 10 s + 30 s |
| `stockfish.js` | Stockfish via Model | Smoke + Load | 1 / 5 | 10 s + 30 s |
| `multi-game.js` | Multi-Game Endpoints (`/game/{id}/…`) | Smoke + Load | 1 / 10 | 10 s + 30 s |
| `all.js` | alle 5 Services + Controller-Ramp | Smoke + Load + Ramp | alle | ~2 min |

**Thresholds:** Fehlerrate < 1 %, Smoke p95 < 300 ms, Load p95 < 500 ms, Stockfish p95 < 5000 ms, Controller-Ramp p95 < 800 ms.

---

### Gatling

HTTP-Lasttests mit automatisch generiertem HTML-Report.
Simulationen liegen in [`gatling/src/test/scala/chess/gatling/simulations/`](gatling/src/test/scala/chess/gatling/simulations/).

**Voraussetzung:** Docker Compose muss laufen (`docker compose up -d`).

```bash
# Alle Simulationen nacheinander ausführen
sbt gatlingAll

# Einzelne Simulationen
sbt "gatling/Gatling/testOnly chess.gatling.simulations.ModelSimulation"
sbt "gatling/Gatling/testOnly chess.gatling.simulations.ControllerSimulation"
sbt "gatling/Gatling/testOnly chess.gatling.simulations.PlayerSimulation"
sbt "gatling/Gatling/testOnly chess.gatling.simulations.StockfishSimulation"
sbt "gatling/Gatling/testOnly chess.gatling.simulations.AllServicesSimulation"

# Nur kompilieren (ohne Ausführung — zum Prüfen auf Typfehler)
sbt "gatling/compile"

# Andere BASE_URL (Standard: http://localhost:3000)
$env:BASE_URL = "http://localhost:3000"; sbt gatlingAll
```

| Simulation | Testet | Szenarien |
|---|---|---|
| `ModelSimulation` | Model-Service | Smoke + Load |
| `ControllerSimulation` | Controller-Service | Smoke + Load |
| `PlayerSimulation` | Player-Service | Smoke + Load |
| `StockfishSimulation` | Stockfish via Model | Smoke + Load (p95 < 5000 ms) |
| `AllServicesSimulation` | Alle Services | Smoke + Load + Controller-Ramp |

**HTML-Report** — wird nach jedem Lauf automatisch erzeugt:

```
gatling/target/gatling/<SimulationsName>-<Timestamp>/index.html
```

---

## Projektstruktur

Das Projekt ist als **sbt Multi-Module Build** organisiert:

```
alu-chess/
├── build.sbt                          # Root-Build (alle Module + Command Aliases)
├── docker-compose.yml                 # Alle Services + Profile (postgres, mongo, k6)
├── Dockerfile.controller              # Controller-Service Image
├── Dockerfile.model                   # Model-Service Image
├── Dockerfile.playerservice           # PlayerService Image
├── Dockerfile.stockfish               # Stockfish/FastAPI Image
├── requirements.txt                   # Python-Abhängigkeiten für Stockfish-Service
├── .env.example                       # Vorlage für Umgebungsvariablen
├── project/
│   ├── plugins.sbt                    # sbt-Plugins (scoverage, assembly, jmh, gatling)
│   └── build.properties               # sbt-Version
│
├── model/                             # Model-Modul (reine Domain-Logik, Port 8082)
│   └── src/
│       ├── main/scala/chess/
│       │   ├── model/
│       │   │   ├── Board.scala            # Spielfeld (8×8, immutable)
│       │   │   ├── Game.scala             # Spielzustand, Zugausführung, Statusprüfung
│       │   │   ├── Piece.scala            # Figurtypen und Farben (enum)
│       │   │   ├── Position.scala         # Feld auf dem Brett (algebraische Notation)
│       │   │   ├── Move.scala             # Zug-Datentyp mit Promotion, String-Parsing
│       │   │   ├── MoveValidator.scala    # Zugvalidierung, Schach-Erkennung, legale Züge
│       │   │   ├── Fen.scala              # FEN-Serializer (wählt Parser-Strategie)
│       │   │   ├── FenParserType.scala    # Enum: Regex | Combinator | Fast
│       │   │   ├── Pgn.scala              # PGN-Serializer (wählt Parser-Strategie)
│       │   │   ├── PgnParserType.scala    # Enum: Regex | Combinator | Fast
│       │   │   ├── MoveEntry.scala        # Zugeintrag mit SAN-Notation
│       │   │   ├── ChessClock.scala       # Schachuhr mit Zeitkontrollen
│       │   │   ├── ChessError.scala       # Typisierte Fehlermeldungen (enum)
│       │   │   ├── GameJson.scala         # JSON-Serialisierung (Circe)
│       │   │   ├── GameRecord.scala       # Abgeschlossene Partie (PGN, Züge, Ergebnis)
│       │   │   ├── GameScript.scala       # Skript-basierte Partie-Ausführung
│       │   │   ├── GameRepository.scala   # Repository-Trait für Partie-Archiv
│       │   │   ├── InMemoryGameRepository.scala
│       │   │   ├── PersistentGameRepository.scala  # DB-Implementierung (Slick/Mongo)
│       │   │   ├── TestPositions.scala    # Vordefinierte Teststellungen
│       │   │   ├── ai/                    # Interne Alpha-Beta-KI
│       │   │   │   ├── ChessAI.scala      # Einstiegspunkt (wählt Modus)
│       │   │   │   ├── AlphaBeta.scala    # Alpha-Beta-Suche mit Transpositionstabelle
│       │   │   │   ├── Evaluator.scala    # Statische Stellungsbewertung
│       │   │   │   ├── MoveOrderer.scala  # Zugordnung (MVV-LVA, killer moves)
│       │   │   │   ├── TranspositionTable.scala
│       │   │   │   ├── Zobrist.scala      # Zobrist-Hashing für Board-States
│       │   │   │   └── AIMode.scala       # Enum: Internal | Stockfish
│       │   │   ├── fen/                   # FEN-Parser-Strategien
│       │   │   │   ├── FenParser.scala    # Trait
│       │   │   │   ├── RegexFenParser.scala
│       │   │   │   ├── CombinatorFenParser.scala
│       │   │   │   ├── FastFenParser.scala
│       │   │   │   └── FenSharedLogic.scala
│       │   │   ├── pgn/                   # PGN-Parser-Strategien
│       │   │   │   ├── PgnParser.scala    # Trait
│       │   │   │   ├── RegexPgnParser.scala
│       │   │   │   ├── CombinatorPgnParser.scala
│       │   │   │   ├── FastPgnParser.scala
│       │   │   │   ├── PgnSharedLogic.scala
│       │   │   │   └── PgnGame.scala
│       │   │   ├── dao/                   # Datenbankzugriff
│       │   │   │   ├── GameDao.scala      # Trait
│       │   │   │   ├── SlickGameDao.scala # PostgreSQL (Slick)
│       │   │   │   ├── MongoGameDao.scala # MongoDB (mongo4cats)
│       │   │   │   ├── GameRow.scala
│       │   │   │   └── GameTable.scala
│       │   │   └── api/
│       │   │       ├── ModelRoutes.scala      # REST-Endpoints inkl. Stockfish-Proxy
│       │   │       ├── ModelServer.scala      # Http4s-Server (IOApp)
│       │   │       └── StockfishEngineAPI.py  # FastAPI-Engine-Service (Port 8000)
│       │   └── util/
│       │       ├── Observable.scala       # Observer-Pattern (Trait)
│       │       └── Observer.scala
│       └── test/scala/chess/model/        # ScalaTest-Specs (je Klasse eine Spec)
│
├── controller/                        # Controller-Modul (hängt von model ab, Port 8081)
│   └── src/
│       ├── main/scala/chess/
│       │   ├── Chess.scala                # Entry Point (@main aluChess)
│       │   ├── controller/
│       │   │   ├── Controller.scala       # Use-Case-Koordination, Observable
│       │   │   ├── ControllerInterface.scala
│       │   │   ├── GameRegistry.scala     # Multi-Game-Verwaltung (concurrent Map)
│       │   │   └── api/
│       │   │       ├── ControllerRoutes.scala   # Einzelspiel-Endpoints
│       │   │       ├── MultiGameRoutes.scala    # Multi-Game-Endpoints (/game/{id}/…)
│       │   │       ├── PlayerServiceClient.scala # HTTP-Client zum PlayerService
│       │   │       └── ControllerServer.scala
│       │   └── aview/
│       │       ├── TUI.scala              # Text User Interface (Observer)
│       │       └── gui/                   # Swing-GUI im Lichess-Stil
│       │           ├── SwingGUI.scala
│       │           ├── BoardPanel.scala
│       │           ├── SidePanel.scala
│       │           ├── NavBar.scala
│       │           ├── HistoryPanel.scala
│       │           ├── HistoryListPanel.scala
│       │           ├── ClockPanel.scala
│       │           ├── StartPanel.scala
│       │           └── PromotionDialog.scala
│       └── test/scala/chess/             # TUISpec, ControllerSpec, ControllerRoutesSpec
│
├── playerservice/                     # Eigenständiger Player-Service (Port 8083)
│   └── src/main/scala/chess/playerservice/
│       ├── PlayerRegistry.scala       # In-Memory-Spieler- und Sitzungsverwaltung
│       └── api/
│           ├── PlayerRoutes.scala     # REST-Endpoints
│           └── PlayerServer.scala     # Http4s-Server (IOApp)
│
├── benchmark/                         # JMH Micro-Benchmarks (sbt-jmh)
│   └── src/main/scala/chess/benchmark/
│       ├── FenBenchmark.scala         # FEN-Parser: Regex vs. Combinator vs. Fast
│       ├── MoveGenerationBenchmark.scala
│       ├── EvaluatorBenchmark.scala
│       └── AlphaBetaBenchmark.scala   # Alpha-Beta-Suche auf Tiefe 1–3
│
├── k6/                                # k6 HTTP-Lasttests
│   └── scripts/
│       ├── all.js                     # Alle Services: Smoke + Load + Controller-Ramp
│       ├── model.js
│       ├── controller.js
│       ├── player.js
│       ├── stockfish.js
│       ├── multi-game.js              # Multi-Game-Endpoints (/game/{id}/…)
│       └── smoke.js
│
├── gatling/                           # Gatling HTTP-Lasttests (Scala-DSL, HTML-Reports)
│   └── src/test/
│       ├── resources/
│       │   ├── gatling.conf
│       │   └── logback-test.xml
│       └── scala/chess/gatling/
│           ├── config/
│           │   └── GatlingConfig.scala        # BASE_URL, Thresholds, VU-Counts
│           ├── scenarios/
│           │   ├── ModelScenarios.scala
│           │   ├── ControllerScenarios.scala
│           │   ├── PlayerScenarios.scala
│           │   └── StockfishScenarios.scala
│           └── simulations/
│               ├── ModelSimulation.scala
│               ├── ControllerSimulation.scala
│               ├── PlayerSimulation.scala
│               ├── StockfishSimulation.scala
│               └── AllServicesSimulation.scala
│
└── docs/                              # Dokumentation (Markdown)
```

### Modul-Abhängigkeiten

```
controller    ──dependsOn──▶ model
benchmark     ──dependsOn──▶ model
playerservice                (unabhängig, eigener Service)
gatling                      (kein compile-Abhängigkeit — HTTP-Tests gegen laufende Services)
k6                           (kein compile-Abhängigkeit — HTTP-Tests gegen laufende Services)
```

Das Model-Modul hat **keine** Abhängigkeit zum Controller. Diese Trennung wird auf Build-Ebene erzwungen.

---

## Architektur

Das Projekt folgt dem **MVC-Pattern** mit **Observer** für lose Kopplung und ist als **sbt Multi-Module Build** organisiert (Vorbereitung für Microservice-Migration mit Docker):

- **Model-Modul** (`model/`) – Immutable Domain-Objekte (`Board`, `Piece`, `Game`, `Move`, `Position`, `MoveValidator`, …). Kein `var`, kein `null`, keine Abhängigkeiten zu View oder Controller. Fehlerbehandlung über `Either[ChessError, _]`. Eigener REST-Service via Http4s (Port 8082).
- **Controller-Modul** (`controller/`) – Koordiniert Use Cases (Zug ausführen, FEN laden, Aufgeben, Uhr, Replay). Implementiert `Observable` und hält den Spielzustand. Abhängig vom Model-Modul. Eigener REST-Service via Http4s (Port 8081).
- **View (aview)** – Swing-GUI und TUI als `Observer` im Controller-Modul. Beide reagieren auf dieselben Controller-Events.
- **REST APIs** – Beide Module exponieren Http4s-Endpoints. Der Controller-Service kommuniziert mit dem Model-Service per HTTP (Microservice-fähig).
- **nginx (API Gateway)** – Im Docker-Compose-Setup fungiert nginx als einziger Einstiegspunkt (`localhost:3000`). Es leitet `/api/controller/`-Requests an den Controller-Service und `/api/model/`-Requests an den Model-Service weiter. Die Backend-Ports sind vom Host aus nicht direkt erreichbar. SSE-Verbindungen werden mit `proxy_buffering off` ungepuffert durchgeleitet.
- **Stockfish-Service** – Separater Python/FastAPI-Service im Docker-Container. Der Model-Service leitet Engine-Requests dorthin weiter (`ENGINE_BASE_URL`, Default: `http://localhost:8000`).

### REST API Endpoints

Im Docker-Compose-Setup werden alle Endpoints über nginx unter `http://localhost:3000` erreichbar.
Bei lokalem sbt-Start sind die Ports direkt verfügbar (Controller: 8081, Model: 8082).

| Service | Pfad | Beschreibung |
|---------|------|-------------|
| Model | `GET /api/model/new-game` | Initiales Spielfeld |
| Model | `POST /api/model/validate-move` | Zug validieren |
| Model | `POST /api/model/legal-moves` | Legale Züge für Position |
| Model | `GET /api/model/stockfish/health` | Healthcheck des Engine-Service |
| Model | `POST /api/model/stockfish/best-move` | Besten Zug von Stockfish holen |
| Model | `POST /api/model/stockfish/evaluate` | Stellung von Stockfish bewerten |
| Controller | `GET /api/controller/state` | Spielzustand |
| Controller | `POST /api/controller/move` | Zug ausführen |
| Controller | `POST /api/controller/new-game` | Neues Spiel |
| Controller | `GET /api/controller/events` | SSE Live-Updates |
| Controller | … | +12 weitere Endpoints |

Detaillierte ADRs: [`docs/architecture-decisions.md`](docs/architecture-decisions.md)

---

## Dokumentation

- [`docs/ai-context.md`](docs/ai-context.md) – Projekt-Memory für KI-gestützte Entwicklung
- [`docs/architecture-decisions.md`](docs/architecture-decisions.md) – Architekturentscheidungen im ADR-Format
- [`docs/microservice-migration-plan.md`](docs/microservice-migration-plan.md) – Migrationsplan Monolith → Microservices (Docker)
- [`docs/web-ui-api-spec.md`](docs/web-ui-api-spec.md) – Web UI & REST API Spezifikation
- [`docs/presentation-notes.md`](docs/presentation-notes.md) – Präsentationsnotizen pro Woche
- [`docs/chess-rules-todo.md`](docs/chess-rules-todo.md) – Schachregeln-Roadmap (alle Stufen abgeschlossen)
- [`docs/agent-prompts.md`](docs/agent-prompts.md) – Copy/Paste-Prompts für spezialisierte KI-Rollen
- [`docs/commit-rules.md`](docs/commit-rules.md) – Commit-Disziplin und Message-Format

---

## Teamregeln (Repo)

- [`.github/copilot-instructions.md`](.github/copilot-instructions.md) – persistente Copilot-Regeln für dieses Repository
- [`.github/pull_request_template.md`](.github/pull_request_template.md) – PR-Checkliste für konsistente Reviews

---

## Lizenz

Kursprojekt – keine öffentliche Lizenz.
