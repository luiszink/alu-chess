# FastAPI REST Summary (Stockfish Engine Service, internal)

Kurzreferenz fuer die Uebernahme in ein anderes Frontend-Repo.
Quelle: `model/src/main/scala/chess/model/api/StockfishEngineAPI.py`

Wichtig: Fuer das eigentliche Frontend-Backend-Contract bitte
`docs/model-routes-rest-summary.md` verwenden (ModelRoutes auf Port 8082).
Diese Datei beschreibt den internen Engine-Service, den der Model-Service aufruft.

## Service

- Name: `stockfish-engine`
- Framework: FastAPI
- Default URL (lokal): `http://localhost:8000`
- OpenAPI JSON: `GET /openapi.json`
- Swagger UI: `GET /docs`
- ReDoc: `GET /redoc`

## Endpoints

### 1) Health Check

`GET /health`

Response `200 OK`:

```json
{
  "status": "ok",
  "service": "stockfish-engine",
  "enginePath": "/usr/games/stockfish"
}
```

Moegliche Fehler:
- `503 Service Unavailable` (Engine konnte nicht gestartet werden)

---

### 2) Best Move berechnen

`POST /best-move`

Request Body:

```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "thinkTimeMs": 1000,
  "skillLevel": 12,
  "threads": 2,
  "hashMb": 128
}
```

Feldregeln:
- `fen` (string, required)
- `thinkTimeMs` (int, optional, default `1000`, range `1..120000`)
- `skillLevel` (int, optional, range `0..20`)
- `threads` (int, optional, range `1..64`)
- `hashMb` (int, optional, range `1..4096`)

Response `200 OK`:

```json
{
  "move": {
    "from": "e2",
    "to": "e4",
    "promotion": null
  },
  "uci": "e2e4",
  "scoreCp": 34,
  "mate": null,
  "depth": 14,
  "nodes": 143233,
  "timeMs": 1000,
  "engine": "stockfish"
}
```

Hinweise:
- `move.from` wird in JSON als `from` ausgegeben (nicht `from_square`).
- `promotion` ist bei Bauernumwandlung z. B. `"Q"`.
- `scoreCp` und `mate` sind alternativ: meist ist eines von beiden `null`.

Moegliche Fehler:
- `400 Bad Request` (ungueltige FEN)
- `422 Unprocessable Entity` (keine legalen Zuege)
- `503 Service Unavailable` (Engine nicht verfuegbar / terminiert)
- `504 Gateway Timeout` (Engine-Timeout)
- `500 Internal Server Error` (unerwarteter Engine-Fehler)

---

### 3) Stellung evaluieren

`POST /evaluate`

Request Body: identisch zu `/best-move`

Response `200 OK`:

```json
{
  "scoreCp": 12,
  "mate": null,
  "depth": 16,
  "nodes": 219333,
  "timeMs": 1000,
  "bestMove": {
    "from": "g1",
    "to": "f3",
    "promotion": null
  },
  "bestMoveUci": "g1f3",
  "engine": "stockfish"
}
```

Hinweise:
- `bestMove` und `bestMoveUci` koennen `null` sein, wenn keine PV/kein Zug vorliegt.
- `scoreCp` = Bewertung in Centipawns relativ zur Seite am Zug.
- `mate` = Distanz bis Matt (wenn Mattlinie erkannt wurde).

Moegliche Fehler:
- `400`, `503`, `504`, `500` analog zu `/best-move`

## Frontend Integration Quick Notes

- Empfohlenes Timeout clientseitig: mind. `thinkTimeMs + 2s` Puffer.
- Bei `503`/`504`: Retry mit niedrigerem `thinkTimeMs` (z. B. `300-700ms`).
- Fuer Move-UI direkt `uci` nutzen (`e2e4`, `e7e8q`), optional `move.from`/`move.to` fuer Board-Highlighting.

## cURL Beispiele

```bash
curl -X GET http://localhost:8000/health
```

```bash
curl -X POST http://localhost:8000/best-move \
  -H "Content-Type: application/json" \
  -d '{"fen":"rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1","thinkTimeMs":700}'
```

```bash
curl -X POST http://localhost:8000/evaluate \
  -H "Content-Type: application/json" \
  -d '{"fen":"r1bqkbnr/pppp1ppp/2n5/4p3/4P3/2N5/PPPP1PPP/R1BQKBNR w KQkq - 2 3","thinkTimeMs":1200}'
```
