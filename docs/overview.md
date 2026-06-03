# alu-chess — Überblick & Start

Kurzreferenz: Was läuft wo, wie wird es gestartet (Docker **oder** rein lokal).

## Komponenten auf einen Blick

| # | Komponente | Repo / Pfad | Sprache | Port | Aufgabe |
|---|---|---|---|---|---|
| 1 | **Frontend** | `alu-chess-web/` | React + Vite | Dev 5173 / Docker 3000 | UI |
| 2 | **Controller** | `alu-chess/controller/` | Scala (http4s) | 8081 | Spielzustand, Verlauf, SSE, ruft Model auf |
| 3 | **Model** | `alu-chess/model/` | Scala (http4s) | 8082 | Schachregeln, FEN/PGN, Best-Move-Bridge zu Stockfish |
| 4 | **PlayerService** | `alu-chess/playerservice/` | Scala (http4s) | 8083 | Spieler-/Matchmaking-Logik (optional) |
| 5 | **Tournament** | `alu-chess/tournament/` | Scala (http4s) | über Controller 8081 | NowChess-Turnier-Bridge (NDJSON → SSE), eigene KI |
| 6 | **Lichess** | `alu-chess/lichess/` | Scala (http4s) | 8085 | Lichess-Bot-Adapter (NDJSON → SSE), eigene KI |
| 7 | **Stockfish-Engine** | `alu-chess/Dockerfile.stockfish` | Python + FastAPI | 8000 (intern) | UCI-Bridge für Model (nicht für Bots!) |
| 8 | **PostgreSQL** | image `postgres:16` | — | 5432 | Persistenz (optional, `DB_TYPE=postgres`) |
| 9 | **MongoDB** | image `mongo:7` | — | 27017 | Persistenz (optional, `DB_TYPE=mongo`) |

> Tournament- und Lichess-Service haben **keine** Stockfish-Abhängigkeit; sie benutzen die eigene KI aus `chess.model.ai.ChessAI`.

## Kommunikationsdiagramm

```
                       Browser (UI)
                          │  /api/...
                          ▼
           ┌──────────────────────────────┐
           │  Vite-Dev-Server (5173)      │  ← lokal
           │   oder nginx (Frontend 3000) │  ← Docker
           └─────┬────────┬────────┬──────┘
                 │        │        │
   /api/controller     /api/model  /api/tournament    /api/lichess
   /api/player                       /api/perf
                 │        │        │              │
                 ▼        ▼        ▼              ▼
            Controller   Model   Tournament     Lichess
             :8081      :8082  Controller        :8085
                │         │         │              │
                │         │         ▼              ▼
         PlayerService    │     NowChess-Server   lichess.org
            :8083         │     (extern, HTTPS)   (extern, HTTPS)
                          ▼
                    Stockfish-Engine
                       :8000
                (nur für Model nötig)

   Optional:  Postgres :5432   Mongo :27017
```

**Frontend → Backend:** alles über relative Pfade (`/api/...`). Im Docker leitet nginx weiter, im Dev der Vite-Proxy ([vite.config.ts](vite.config.ts)).

**Controller → Model/PlayerService:** HTTP intern (im Docker per Service-Namen, lokal per `localhost`).

**Model → Stockfish:** nur fürs UI-Spielen gegen Engine. Bots brauchen das **nicht**.

**Tournament/Lichess:** halten je einen NDJSON-Stream zum externen Server offen, parsen Events, antworten mit Zügen (über die eigene KI), fanen Events als SSE an die UI aus.

## Variante A — Docker

Ohne `.env` startet der Backend-Stack mit `DB_TYPE=memory`. Das Frontend ist optional, weil es ein separates Repo mit eigenem `Dockerfile.frontend` braucht.

```powershell
cd alu-chess
docker compose up -d --build
# Controller/API: http://localhost:8081
```

Mit Frontend, falls `alu-chess-web` als Nachbarordner existiert:

```powershell
cd alu-chess
docker compose --profile frontend up -d --build
# UI: http://localhost:3000
```

Stoppen + Aufräumen (Disk frei):

```powershell
docker compose down
docker system prune -af --volumes   # gibt am meisten Platz zurück
```

## Variante B — möglichst lokal (minimaler Disk-Bedarf)

Du musst **nur Stockfish** in Docker laufen lassen (Image ~150 MB), alles andere läuft nativ. Wenn du **gar kein** Spiel gegen Stockfish brauchst (z. B. nur Lichess testen), kannst du das auch weglassen.

### 0. Voraussetzungen
- JDK 17, sbt
- Node 18+
- (optional) Docker Desktop — nur für Stockfish

Prüfen:

```powershell
java -version
sbt --version
node --version
```

### 1. Stockfish-Engine in Docker (optional, ~150 MB)

```powershell
cd alu-chess
docker compose up -d stockfish
# läuft intern auf Port 8000, ohne Port-Mapping nicht von Windows aus erreichbar
```

Damit dein lokal laufender Model-Service den Container erreicht, eine lokale Override-Datei anlegen (nicht committen):

```yaml
# alu-chess/docker-compose.override.yml
services:
  stockfish:
    ports:
      - "127.0.0.1:8000:8000"
```

…oder Stockfish komplett überspringen und `ENGINE_BASE_URL` weglassen (dann sind die Engine-Endpoints des Model-Service deaktiviert, KI gegen Mensch geht trotzdem).

### 2. Backend lokal starten

In **jeweils einem eigenen PowerShell-Fenster**. ENV-Variablen gelten in PowerShell nur in der **aktuellen Session** — am einfachsten zuerst setzen, dann `sbt` starten.

```powershell
# Terminal 1 — Model
cd alu-chess
$env:PORT = "8082"
$env:ENGINE_BASE_URL = "http://localhost:8000"
sbt "model/run"
```

```powershell
# Terminal 2 — PlayerService
cd alu-chess
$env:PORT = "8083"
sbt "playerservice/run"
```

```powershell
# Terminal 3 — Controller
cd alu-chess
$env:PORT = "8081"
$env:PLAYER_SERVICE_URL = "http://localhost:8083"
$env:DB_TYPE = "memory"
$env:TOURNAMENT_SERVER_URL = "https://tournament.maichess.berger-software.com"
$env:TOURNAMENT_BOT_NAME = "alu-chess-bot"
$env:TOURNAMENT_DIRECTOR_NAME = "alu-chess-director"
sbt "controller/run"
```

```powershell
# Tournament läuft nicht mehr als eigener Server.
# Die Routen /api/tournament/... werden vom Controller bereitgestellt.
```

```powershell
# Terminal 5 — Lichess (optional)
cd alu-chess
$env:PORT = "8085"
$env:LICHESS_BOT_TOKEN = "lip_xxx"
sbt "lichess/run"
```

> Tipp: Beim Branch-Wechsel zwischen `feature/lichess` und `feature/tournament` einmal `sbt clean` ausführen, falls alte `.class`-Dateien gemeldet werden.

> Falls `sbt "lichess/run"` mit „project not found" abbricht, bist du auf einem Branch, der das Modul nicht hat — wechsle auf `feature/lichess` (analog `feature/tournament`).

### 3. Frontend lokal starten

```powershell
cd alu-chess-web
npm install        # einmalig
npm run dev
# UI: http://localhost:5173
```

Die [vite.config.ts](../../alu-chess-web/vite.config.ts) proxied automatisch:

| Pfad | Ziel |
|---|---|
| `/api/controller`, `/api/player`, `/api/perf` | `localhost:8081` |
| `/api/model` | `localhost:8082` |
| `/api/tournament` | `localhost:8081` |
| `/api/lichess` | `localhost:8085` |

### 4. Was kann man weglassen?

| Use-Case | Mindest-Services |
|---|---|
| Nur lokal Schach spielen (kein Engine-Gegner) | Frontend + Controller + Model + PlayerService |
| + gegen Stockfish spielen | + Stockfish-Container |
| + Turnier (NowChess) | Controller mit `TOURNAMENT_SERVER_URL` |
| + Lichess-Bot | + Lichess |

Du musst **nicht alles** gleichzeitig starten.

### 5. Speicherplatz-Tipps (PowerShell)

```powershell
# alle Scala-Build-Ordner löschen (mehrere GB) — neu kompilieren danach
cd alu-chess
sbt clean

# Vite-Cache wegwerfen
Remove-Item -Recurse -Force alu-chess-web\node_modules\.vite -ErrorAction SilentlyContinue

# Docker-Aufräumen
docker system prune -af --volumes
```

`~/.ivy2` und `~/.sbt/boot` **nicht** löschen — sonst lädt sbt beim nächsten Start alle Abhängigkeiten erneut.

## Branches & Merge-Reihenfolge

- `feature/performance-testing` (web) / `streams` (backend) — Hauptarbeitsstand
- `feature/lichess` — Lichess-Bot-Integration, unabhängig
- `feature/tournament` — NowChess-Turnier-Integration, unabhängig

Empfohlen: **Lichess zuerst** nach `main` mergen (öffentliche API stabil), danach Tournament (Server noch in Entwicklung).

## Wichtige Dokumente

- [docs/lichess-setup.md](docs/lichess-setup.md) — Bot-Account + Token Schritt für Schritt
- [docs/lichess-gaps.md](docs/lichess-gaps.md) — Offene Punkte Lichess
- [docs/tournament-gaps.md](docs/tournament-gaps.md) — Offene Punkte Turnier
- [docs/architecture-decisions.md](docs/architecture-decisions.md) — ADRs
