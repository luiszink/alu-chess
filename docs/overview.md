# alu-chess — Überblick & Start

Kurzreferenz: Was läuft wo, wie wird es gestartet (Docker **oder** rein lokal).

## Komponenten auf einen Blick

| # | Komponente | Repo / Pfad | Sprache | Port | Aufgabe |
|---|---|---|---|---|---|
| 1 | **Frontend** | `alu-chess-web/` | React + Vite | Dev 5173 / Docker 3000 | UI |
| 2 | **Controller** | `alu-chess/controller/` | Scala (http4s) | 8081 | Spielzustand, Verlauf, SSE, ruft Model auf |
| 3 | **Model** | `alu-chess/model/` | Scala (http4s) | 8082 | Schachregeln, FEN/PGN, Best-Move-Bridge zu Stockfish |
| 4 | **PlayerService** | `alu-chess/playerservice/` | Scala (http4s) | 8083 | Spieler-/Matchmaking-Logik (optional) |
| 5 | **Tournament** | `alu-chess/tournament/` | Scala (http4s) | 8084 | NowChess-Turnier-Bridge (NDJSON → SSE), eigene KI |
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
             :8081      :8082     :8084          :8085
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

## Variante A — Docker (vollständig, einfach)

Voraussetzung: `.env` im `alu-chess/`-Verzeichnis mit `FRONTEND_CONTEXT=../alu-chess-web` und ggf. `DB_TYPE=memory`.

```bash
cd alu-chess
docker compose up -d --build
# UI: http://localhost:3000
```

Stoppen + Aufräumen (Disk frei):

```bash
docker compose down
docker system prune -af --volumes   # ← gibt am meisten Platz zurück
```

## Variante B — möglichst lokal (minimaler Disk-Bedarf)

Du musst **nur Stockfish** in Docker laufen lassen (Image ~150 MB), alles andere läuft nativ. Wenn du **gar kein** Spiel gegen Stockfish brauchst (z. B. nur Lichess testen), kannst du das auch weglassen.

### 0. Voraussetzungen
- JDK 17, sbt
- Node 18+
- (optional) Docker — nur für Stockfish

### 1. Stockfish-Engine in Docker (optional, ~150 MB)

```bash
cd alu-chess
docker compose up -d stockfish
# → http://localhost:8000  (nur intern aus dem Compose-Netz erreichbar)
```

Damit dein lokal laufender Model-Service den Container erreicht, im nächsten Schritt Port mappen — entweder vorübergehend per Override:

```yaml
# alu-chess/docker-compose.override.yml  (nur lokal, nicht committen)
services:
  stockfish:
    ports:
      - "127.0.0.1:8000:8000"
```

…oder Stockfish komplett überspringen und `ENGINE_BASE_URL` leer lassen (dann sind die Engine-Endpoints des Model-Service deaktiviert, KI gegen Mensch geht trotzdem).

### 2. Backend lokal starten

In **jeweils eigenem Terminal**:

```bash
# Terminal 1 — Model
cd alu-chess
PORT=8082 ENGINE_BASE_URL=http://localhost:8000 sbt "model/run"

# Terminal 2 — PlayerService
cd alu-chess
PORT=8083 sbt "playerservice/run"

# Terminal 3 — Controller
cd alu-chess
PORT=8081 \
  PLAYER_SERVICE_URL=http://localhost:8083 \
  DB_TYPE=memory \
  sbt "controller/run"

# Terminal 4 — Tournament (optional)
cd alu-chess
PORT=8084 \
  TOURNAMENT_BASE_URL=https://st.nowchess.janis-eccarius.de \
  TOURNAMENT_BOT_TOKEN=... \
  sbt "tournament/run"

# Terminal 5 — Lichess (optional)
cd alu-chess
PORT=8085 \
  LICHESS_BOT_TOKEN=lip_xxx \
  sbt "lichess/run"
```

> Windows / Git-Bash: ENV inline gleich, z. B. `PORT=8082 ENGINE_BASE_URL=http://localhost:8000 sbt "model/run"`. PowerShell: `$env:PORT=8082; sbt "model/run"`.

### 3. Frontend lokal starten

```bash
cd alu-chess-web
npm install                # einmalig
npm run dev
# → http://localhost:5173
```

Die `vite.config.ts` proxied automatisch:

| Pfad | Ziel |
|---|---|
| `/api/controller`, `/api/player`, `/api/perf` | `localhost:8081` |
| `/api/model` | `localhost:8082` |
| `/api/tournament` | `localhost:8084` |
| `/api/lichess` | `localhost:8085` |

### 4. Was kann man weglassen?

| Use-Case | Mindest-Services |
|---|---|
| Nur lokal Schach spielen (kein Engine-Gegner) | Frontend + Controller + Model + PlayerService |
| + gegen Stockfish spielen | + Stockfish-Container |
| + Turnier (NowChess) | + Tournament |
| + Lichess-Bot | + Lichess |

Du musst **nicht alles** gleichzeitig starten.

### 5. Speicherplatz-Tipps

- `sbt clean` in `alu-chess/` löscht alle `target/`-Ordner (mehrere GB).
- `~/.ivy2` und `~/.sbt/boot` nicht löschen — sonst lädt sbt alles neu.
- Frontend: `rm -rf alu-chess-web/node_modules/.vite` falls Vite-Cache zu groß.
- Docker: `docker system prune -af --volumes` räumt unbenutzte Images + Volumes.

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
