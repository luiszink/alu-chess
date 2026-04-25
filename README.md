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
| Container | Docker (Stockfish-Service) |
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
| `DB_PASSWORD` | DB-Passwort | `chess` |
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

> Hinweis: Beide Services müssen parallel laufen, bevor das Web-Frontend
> ([alu-chess-web](../SoftwarearchitekturWeb/alu-chess-web)) Anfragen an
> Port 8081 (Controller) bzw. 8082 (Model) senden kann.

### Coverage-Report erzeugen

```bash
sbt clean coverage test coverageReport
```

Der Report liegt anschließend unter:
- `model/target/scala-3.6.4/scoverage-report/index.html`
- `controller/target/scala-3.6.4/scoverage-report/index.html`

---

## Projektstruktur

Das Projekt ist als **sbt Multi-Module Build** organisiert:

```
alu-chess/
├── build.sbt                          # Root-Build mit Modul-Definitionen
├── Dockerfile                         # Container-Build für FastAPI + Stockfish
├── requirements.txt                   # Python-Abhängigkeiten für Engine-Service
├── model/                             # Model-Modul (reine Domain-Logik)
│   └── src/main/scala/chess/
│       ├── model/
│       │   ├── Board.scala            # Spielfeld (8×8, immutable)
│       │   ├── Game.scala             # Spielzustand, Zugausführung, Statusprüfung
│       │   ├── Piece.scala            # Figurtypen und Farben (enum)
│       │   ├── Position.scala         # Feld auf dem Brett (algebraische Notation)
│       │   ├── Move.scala             # Zug-Datentyp mit Promotion, String-Parsing
│       │   ├── MoveValidator.scala    # Zugvalidierung, Schach-Erkennung, legale Züge
│       │   ├── Fen.scala              # FEN-Parser und -Serializer
│       │   ├── MoveEntry.scala        # Zugeintrag mit SAN-Notation
│       │   ├── ChessClock.scala       # Schachuhr mit Zeitkontrollen
│       │   ├── ChessError.scala       # Typisierte Fehlermeldungen (enum)
│       │   ├── GameJson.scala         # JSON-Serialisierung (Circe)
│       │   ├── GameRecord.scala       # Abgeschlossene Partie (PGN, Züge, Ergebnis)
│       │   ├── GameRepository.scala   # Repository-Trait für Partie-Archiv
│       │   ├── InMemoryGameRepository.scala
│       │   ├── TestPositions.scala    # Vordefinierte Teststellungen
│       │   ├── fen/                   # FEN-Parser-Strategien (Regex, Combinator, Fast)
│       │   ├── pgn/                   # PGN-Parser-Strategien (Regex, Combinator, Fast)
│       │   └── api/
│       │       ├── ModelRoutes.scala      # REST-Endpoints inkl. Stockfish-Proxy (Port 8082)
│       │       ├── ModelServer.scala      # Http4s-Server (IOApp)
│       │       └── StockfishEngineAPI.py  # FastAPI-Engine-Service (Port 8000)
│       └── util/
│           ├── Observable.scala       # Observer-Pattern (Trait)
│           └── Observer.scala         # Observer-Interface
├── controller/                        # Controller-Modul (hängt von model ab)
│   └── src/main/scala/chess/
│       ├── Chess.scala                # Entry Point (@main aluChess)
│       ├── controller/
│       │   ├── Controller.scala       # Use-Case-Koordination, Observable
│       │   ├── ControllerInterface.scala
│       │   └── api/
│       │       ├── ControllerRoutes.scala  # REST-Endpoints (Port 8081)
│       │       └── ControllerServer.scala  # Http4s-Server (IOApp)
│       └── aview/
│           ├── TUI.scala              # Text User Interface (Observer)
│           └── gui/                   # Swing-GUI im Lichess-Stil
│               ├── SwingGUI.scala
│               ├── BoardPanel.scala
│               ├── SidePanel.scala
│               ├── NavBar.scala
│               ├── HistoryPanel.scala
│               ├── HistoryListPanel.scala
│               ├── ClockPanel.scala
│               ├── StartPanel.scala
│               └── PromotionDialog.scala
└── docs/                              # Dokumentation
```

### Modul-Abhängigkeiten

```
controller ──dependsOn──▶ model
```

Das Model-Modul hat **keine** Abhängigkeit zum Controller. Diese Trennung wird auf Build-Ebene erzwungen.

---

## Architektur

Das Projekt folgt dem **MVC-Pattern** mit **Observer** für lose Kopplung und ist als **sbt Multi-Module Build** organisiert (Vorbereitung für Microservice-Migration mit Docker):

- **Model-Modul** (`model/`) – Immutable Domain-Objekte (`Board`, `Piece`, `Game`, `Move`, `Position`, `MoveValidator`, …). Kein `var`, kein `null`, keine Abhängigkeiten zu View oder Controller. Fehlerbehandlung über `Either[ChessError, _]`. Eigener REST-Service via Http4s (Port 8082).
- **Controller-Modul** (`controller/`) – Koordiniert Use Cases (Zug ausführen, FEN laden, Aufgeben, Uhr, Replay). Implementiert `Observable` und hält den Spielzustand. Abhängig vom Model-Modul. Eigener REST-Service via Http4s (Port 8081).
- **View (aview)** – Swing-GUI und TUI als `Observer` im Controller-Modul. Beide reagieren auf dieselben Controller-Events.
- **REST APIs** – Beide Module exponieren Http4s-Endpoints. Der Controller-Service kommuniziert mit dem Model-Service per HTTP (Microservice-fähig).
- **Stockfish-Service** – Separater Python/FastAPI-Service im Docker-Container. Der Model-Service leitet Engine-Requests dorthin weiter (`ENGINE_BASE_URL`, Default: `http://localhost:8000`).

### REST API Endpoints

| Service | Endpoint | Beschreibung |
|---------|----------|-------------|
| Model (8082) | `GET /api/model/new-game` | Initiales Spielfeld |
| Model (8082) | `POST /api/model/validate-move` | Zug validieren |
| Model (8082) | `POST /api/model/legal-moves` | Legale Züge für Position |
| Model (8082) | `GET /api/model/stockfish/health` | Healthcheck des Engine-Service |
| Model (8082) | `POST /api/model/stockfish/best-move` | Besten Zug von Stockfish holen |
| Model (8082) | `POST /api/model/stockfish/evaluate` | Stellung von Stockfish bewerten |
| Controller (8081) | `GET /api/controller/state` | Spielzustand |
| Controller (8081) | `POST /api/controller/move` | Zug ausführen |
| Controller (8081) | `POST /api/controller/new-game` | Neues Spiel |
| Controller (8081) | `GET /api/controller/events` | SSE Live-Updates |
| Controller (8081) | … | +12 weitere Endpoints |

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
