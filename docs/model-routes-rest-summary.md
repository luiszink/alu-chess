# Model Service REST Summary (Frontend-relevant)

Diese Zusammenfassung beschreibt die REST-API, die dein Frontend direkt nutzen sollte:

- Service: Model-Service (Http4s)
- Base URL (lokal): `http://localhost:8082`
- Route-Basis: `/api/model/...`

Hinweis: Der FastAPI-Stockfish-Service auf Port 8000 ist ein internes Backend-Detail.
Das Frontend spricht fuer Engine-Funktionen ueber den Model-Service mit `/api/model/stockfish/...`.

## Endpoints

### Health

`GET /health`

Response `200`:

```json
{
  "status": "ok",
  "service": "model"
}
```

---

### Neues Spiel

`GET /api/model/new-game`

Response `200` (`GameJson`):

```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "status": "Playing",
  "currentPlayer": "White",
  "halfMoveClock": 0,
  "fullMoveNumber": 1,
  "isTerminal": false
}
```

---

### Zug validieren und anwenden

`POST /api/model/validate-move`

Request:

```json
{
  "fen": "...",
  "from": "e2",
  "to": "e4",
  "promotion": null
}
```

Response `200`: `GameJson` (neuer Spielzustand)

Fehler:
- `400` (z. B. fehlende/ungueltige Felder)
- `422` (illegaler Zug)
- `409` (Spiel bereits beendet)

---

### Alle legalen Zuege

`POST /api/model/legal-moves`

Request:

```json
{
  "fen": "..."
}
```

Response `200`:

```json
{
  "moves": [
    { "from": "a2", "to": "a3", "promotion": null },
    { "from": "a2", "to": "a4", "promotion": null }
  ]
}
```

Fehler: `400`

---

### Legale Zuege fuer ein Feld

`POST /api/model/legal-moves-for-square`

Request:

```json
{
  "fen": "...",
  "square": "e2"
}
```

Response `200`:

```json
{
  "square": "e2",
  "piece": {
    "type": "Pawn",
    "color": "White",
    "symbol": "♙"
  },
  "moves": [
    { "to": "e3", "isCapture": false, "promotion": null },
    { "to": "e4", "isCapture": false, "promotion": null }
  ]
}
```

Wenn kein Stein auf dem Feld: `"piece": null`, `"moves": []`.

Fehler: `400`

---

### FEN parsen

`POST /api/model/parse-fen`

Request:

```json
{ "fen": "..." }
```

Response `200`: `GameJson`

Fehler: `400`

---

### Game-JSON zu FEN

`POST /api/model/to-fen`

Request: Game-JSON (mindestens mit gueltigem `fen`)

Response `200`:

```json
{ "fen": "..." }
```

Fehler: `400`

---

### PGN parsen

`POST /api/model/parse-pgn`

Request:

```json
{ "pgn": "1. e4 e5 2. Nf3 Nc6" }
```

Response `200`: `GameJson`

Fehler: `400`

---

### Game-JSON zu PGN

`POST /api/model/to-pgn`

Request: Game-JSON

Response `200`:

```json
{ "pgn": "1. e4 e5 2. Nf3 Nc6" }
```

Fehler: `400`

---

### Testpositionen

`GET /api/model/test-positions`

Response `200`:

```json
{
  "positions": [
    {
      "name": "...",
      "fen": "...",
      "description": "..."
    }
  ]
}
```

---

## Stockfish ueber ModelRoutes (Frontend soll diese nutzen)

### Engine Health

`GET /api/model/stockfish/health`

Leitet auf Engine-Service `/health` weiter.

### Best Move

`POST /api/model/stockfish/best-move`

Request:

```json
{
  "fen": "...",
  "thinkTimeMs": 1000,
  "skillLevel": 12,
  "threads": 2,
  "hashMb": 128
}
```

Response `200` (vom Engine-Service durchgereicht, validiert):

```json
{
  "move": { "from": "e2", "to": "e4", "promotion": null },
  "uci": "e2e4",
  "scoreCp": 34,
  "mate": null,
  "depth": 14,
  "nodes": 143233,
  "timeMs": 1000,
  "engine": "stockfish"
}
```

Moegliche Fehler:
- `400` bei ungueltigem/missing `fen`
- `502` falls Engine-Response fachlich ungueltig ist
- `503`, `504`, `500` als Engine-/Proxy-Fehler

### Evaluate

`POST /api/model/stockfish/evaluate`

Request: wie `best-move`

Response `200`:

```json
{
  "scoreCp": 12,
  "mate": null,
  "depth": 16,
  "nodes": 219333,
  "timeMs": 1000,
  "bestMove": { "from": "g1", "to": "f3", "promotion": null },
  "bestMoveUci": "g1f3",
  "engine": "stockfish"
}
```

Moegliche Fehler: analog zu `best-move`.

---

## Error-Format

Typisches Fehlerobjekt:

```json
{
  "error": "IllegalMovePattern",
  "message": "Illegal move: e2→e5"
}
```

Engine/Proxy-Fehler verwenden ebenfalls JSON mit `error` + `message`.

## Frontend Quick Guidance

- Verwende fuer den normalen Spielfluss primaer den Controller-Service (`/api/controller/...`).
- Verwende fuer stateless Schachlogik den Model-Service (`/api/model/...`).
- Fuer KI immer `/api/model/stockfish/...` statt direkte Calls auf Port 8000.
