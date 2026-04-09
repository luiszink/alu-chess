# Web UI – REST API Spezifikation

> Vollständige API-Referenz für das alu-chess Web-Frontend.
> Basiert auf der tatsächlichen Http4s-Implementierung (Controller-Service + Model-Service).

---

## Architektur-Überblick

Das Backend besteht aus **zwei unabhängigen Microservices**:

| Service | Port | Pfad-Präfix | Aufgabe |
|---------|------|-------------|---------|
| **Controller-Service** | `8081` | `/api/controller/...` | Spielzustand verwalten, Züge ausführen, History, Replay, SSE |
| **Model-Service** | `8082` | `/api/model/...` | Stateless Schachlogik: Zugvalidierung, legale Züge, FEN/PGN |

Das Frontend spricht primär mit dem **Controller-Service** (Spielfluss). Für stateless Operationen wie "legale Züge für ein Feld" oder FEN/PGN-Konvertierung wird der **Model-Service** direkt angesprochen.

### Basis-Konfiguration

| Eigenschaft | Wert |
|-------------|------|
| **Controller URL** | `http://localhost:8081` |
| **Model URL** | `http://localhost:8082` |
| **Content-Type** | `application/json` |
| **Encoding** | UTF-8 |
| **CORS** | Allow All Origins (Dev) |

---

## Datenmodelle (JSON)

### ControllerState (Hauptantwort des Controller-Service)

Wird von fast allen Controller-Endpoints zurückgegeben:

```json
{
  "game": {
    "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
    "status": "Playing",
    "currentPlayer": "White",
    "halfMoveClock": 0,
    "fullMoveNumber": 1,
    "isTerminal": false
  },
  "browseIndex": 0,
  "totalStates": 1,
  "isAtLatest": true,
  "isInReplay": false,
  "statusText": "White am Zug",
  "clock": null
}
```

**Feld-Erklärung:**

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `game` | `GameJson` | Das aktuell angezeigte Spiel (kann Browse-Position sein) |
| `game.fen` | `string` | Position als FEN-String – **Board-Darstellung muss clientseitig aus dem FEN geparst werden** |
| `game.status` | `string` | `Playing`, `Check`, `Checkmate`, `Stalemate`, `Resigned`, `Draw`, `TimeOut` |
| `game.currentPlayer` | `string` | `"White"` oder `"Black"` |
| `game.halfMoveClock` | `number` | Halbzüge seit letztem Bauernzug/Schlagen |
| `game.fullMoveNumber` | `number` | Aktuelle Zugnummer (startet bei 1) |
| `game.isTerminal` | `boolean` | Spiel beendet? |
| `browseIndex` | `number` | 0-basierter Index im Spielverlauf |
| `totalStates` | `number` | Gesamtzahl der Spielzustände (Anfang + jeder Zug) |
| `isAtLatest` | `boolean` | Wird der neueste Zustand angezeigt? (false = History-Browsing) |
| `isInReplay` | `boolean` | Wird ein gespeichertes Spiel nachgespielt? |
| `statusText` | `string` | Deutschsprachiger Status (z.B. `"White am Zug"`, `"Schachmatt! Black gewinnt"`) |
| `clock` | `ClockState \| null` | Uhr-Info (null wenn ohne Uhr) |

### ClockState

```json
{
  "whiteTimeMs": 300000,
  "blackTimeMs": 295000
}
```

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `whiteTimeMs` | `number` | Verbleibende Zeit Weiß in Millisekunden |
| `blackTimeMs` | `number` | Verbleibende Zeit Schwarz in Millisekunden |

> **Hinweis:** Formatierung (`"5:00"`) und aktive Farbe muss clientseitig bestimmt werden (aus `game.currentPlayer`).

### GameJson (Model-Service-Antwort)

Wird von den Model-Endpoints zurückgegeben (gleiche Struktur wie `game` im ControllerState):

```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "status": "Playing",
  "currentPlayer": "Black",
  "halfMoveClock": 0,
  "fullMoveNumber": 1,
  "isTerminal": false
}
```

### MoveHistoryEntry

Vom Endpoint `GET /api/controller/move-history` zurückgegeben:

```json
{
  "move": "e2→e4",
  "san": "e4",
  "status": "Playing"
}
```

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `move` | `string` | Zug als String (`"e2→e4"`, `"e7→e8=Q"`) |
| `san` | `string` | Standard Algebraic Notation (`"e4"`, `"Nxe5+"`, `"O-O"`, `"e8=Q#"`) |
| `status` | `string` | Spielstatus nach dem Zug |

### LegalMovesForSquare (Model-Service)

```json
{
  "square": "e2",
  "piece": {
    "type": "Pawn",
    "color": "White",
    "symbol": "♙"
  },
  "moves": [
    {"to": "e3", "isCapture": false, "promotion": null},
    {"to": "e4", "isCapture": false, "promotion": null}
  ]
}
```

Wenn kein Stein auf dem Feld: `{ "square": "e5", "piece": null, "moves": [] }`

### LegalMoves (Model-Service)

```json
{
  "moves": [
    {"from": "a2", "to": "a3", "promotion": null},
    {"from": "a2", "to": "a4", "promotion": null},
    {"from": "b1", "to": "a3", "promotion": null}
  ]
}
```

### GameRecordSummary

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "datePlayed": "2026-04-08T14:30:00",
  "result": "1-0",
  "moveCount": 42
}
```

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `id` | `string` | UUID des gespeicherten Spiels |
| `datePlayed` | `string` | ISO-Datetime |
| `result` | `string` | `"1-0"`, `"0-1"`, `"½-½"`, `"*"` |
| `moveCount` | `number` | Anzahl der Züge |

### ErrorResponse

```json
{
  "error": "IllegalMovePattern",
  "message": "Illegal move: e2→e5"
}
```

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `error` | `string` | Fehler-Typ (mappt auf `ChessError` Enum) |
| `message` | `string` | Menschenlesbare Fehlermeldung |

**Mögliche Error-Typen:**

| error | HTTP Status | Beschreibung |
|-------|-------------|--------------|
| `InvalidMoveFormat` | 400 | Zug-String nicht parsbar |
| `InvalidPositionString` | 400 | Position nicht parsbar |
| `InvalidPromotionPiece` | 400 | Ungültige Umwandlungsfigur |
| `NoPieceAtSource` | 422 | Kein Stein auf dem Startfeld |
| `WrongColorPiece` | 422 | Stein gehört dem Gegner |
| `FriendlyFire` | 422 | Zielfeld von eigener Figur besetzt |
| `IllegalMovePattern` | 422 | Ungültiges Zugmuster für Figurtyp |
| `LeavesKingInCheck` | 422 | Zug lässt eigenen König im Schach |
| `GameAlreadyOver` | 409 | Spiel ist bereits beendet |
| `InvalidFenFormat` | 400 | FEN-String ungültig |
| `InvalidPgnFormat` | 400 | PGN-Format ungültig |
| `InvalidPgnMove` | 400 | PGN-Zug nicht erkannt |

---

## Controller-Service Endpoints (Port 8081)

---

### 1. Spielzustand abrufen

```
GET /api/controller/state
```

**Response:** `200 OK` → `ControllerState`

---

### 2. Neues Spiel starten

```
POST /api/controller/new-game
```

**Request Body:** keiner (leeres JSON `{}` oder kein Body)

**Response:** `200 OK` → `ControllerState` (Anfangsposition)

---

### 3. Zug ausführen

```
POST /api/controller/move
```

**Request Body:**
```json
{
  "from": "e2",
  "to": "e4"
}
```

Für Bauernumwandlung:
```json
{
  "from": "e7",
  "to": "e8",
  "promotion": "Q"
}
```

**Response:**
- `200 OK` → `ControllerState` (neuer Zustand nach Zug)
- `400 Bad Request` → `ErrorResponse` (Parse-Fehler)
- `422 Unprocessable Entity` → `ErrorResponse` (illegaler Zug)
- `409 Conflict` → `ErrorResponse` (Spiel bereits beendet)

---

### 4. FEN-Position laden

```
POST /api/controller/load-fen
```

**Request Body:**
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
}
```

**Response:**
- `200 OK` → `ControllerState`
- `400 Bad Request` → `ErrorResponse`

---

### 5. Aufgeben

```
POST /api/controller/resign
```

**Response:** `200 OK` → `ControllerState` (mit `game.status: "Resigned"`)

---

### 6. History Navigation

Alle Browse-Endpoints antworten mit `200 OK` → `ControllerState`.

#### Einen Zug zurück
```
POST /api/controller/browse/back
```

#### Einen Zug vorwärts
```
POST /api/controller/browse/forward
```

#### Zum Anfang
```
POST /api/controller/browse/to-start
```

#### Zum aktuellen Zustand
```
POST /api/controller/browse/to-end
```

#### Zu bestimmtem Zug springen
```
POST /api/controller/browse/to-move
```

**Request Body:**
```json
{
  "index": 5
}
```

---

### 7. Zughistorie abrufen

```
GET /api/controller/move-history
```

**Response:** `200 OK`
```json
{
  "moves": [
    {"move": "e2→e4", "san": "e4", "status": "Playing"},
    {"move": "e7→e5", "san": "e5", "status": "Playing"}
  ]
}
```

---

### 8. Gespeicherte Spiele auflisten

```
GET /api/controller/games
```

**Response:** `200 OK`
```json
{
  "games": [
    {
      "id": "550e8400-...",
      "datePlayed": "2026-04-08T14:30:00",
      "result": "1-0",
      "moveCount": 42
    }
  ]
}
```

---

### 9. Gespeichertes Spiel laden (Replay)

```
POST /api/controller/replay/load
```

**Request Body:**
```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Response:**
- `200 OK` → `ControllerState` (mit `isInReplay: true`)
- `404 Not Found` → `{ "error": "Game not found" }`

---

### 10. Replay verlassen

```
POST /api/controller/replay/exit
```

**Response:** `200 OK` → `ControllerState` (zurück zum Live-Spiel)

---

### 11. Spielzustand als JSON exportieren

```
GET /api/controller/export
```

**Response:** `200 OK` → Vollständiges GameRecord-JSON (alle Spielzustände + Metadaten, Format des internen `GameJson.toRecordJsonString`)

---

### 12. Spielzustand aus JSON importieren

```
POST /api/controller/import
```

**Content-Type:** `text/plain` (Raw JSON-String als Body)

**Request Body:** Der JSON-String vom Export-Endpoint.

**Response:**
- `200 OK` → `ControllerState`
- `400 Bad Request` → `ErrorResponse`

---

### 13. Live-Updates (Server-Sent Events)

```
GET /api/controller/events
Accept: text/event-stream
```

**Event-Format:** Es gibt einen einzigen Event-Typ `"state"`:

```
event: state
data: {"game":{"fen":"...","status":"Playing","currentPlayer":"Black",...},"browseIndex":1,"totalStates":2,"isAtLatest":true,"isInReplay":false,"statusText":"Black am Zug","clock":null}
```

**Beschreibung:** Offener Stream. Der Server sendet ein `state`-Event bei **jeder** Zustandsänderung (Zug, neues Spiel, FEN laden, Resign, Browse, Replay, etc.). Das `data`-Feld enthält das gleiche JSON wie der `ControllerState`.

```typescript
const events = new EventSource('http://localhost:8081/api/controller/events');

events.addEventListener('state', (e) => {
  const state: ControllerState = JSON.parse(e.data);
  updateUI(state);
});

events.onerror = () => {
  setTimeout(() => reconnect(), 3000);
};
```

---

### 14. Health Check

```
GET /health
```

**Response:** `200 OK`
```json
{"status": "ok", "service": "controller"}
```

---

## Model-Service Endpoints (Port 8082)

Alle Model-Endpoints sind **stateless** — sie arbeiten nur mit den übergebenen Daten.

---

### 15. Neues Spiel (Startstellung)

```
GET /api/model/new-game
```

**Response:** `200 OK` → `GameJson`

---

### 16. Zug validieren und anwenden

```
POST /api/model/validate-move
```

**Request Body:**
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "from": "e2",
  "to": "e4",
  "promotion": null
}
```

**Response:**
- `200 OK` → `GameJson` (neuer Zustand nach Zug)
- `400 Bad Request` → `ErrorResponse`
- `422 Unprocessable Entity` → `ErrorResponse`
- `409 Conflict` → `ErrorResponse` (Spiel beendet)

---

### 17. Alle legalen Züge

```
POST /api/model/legal-moves
```

**Request Body:**
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
}
```

**Response:** `200 OK`
```json
{
  "moves": [
    {"from": "a2", "to": "a3", "promotion": null},
    {"from": "a2", "to": "a4", "promotion": null}
  ]
}
```

> In der Startposition sind es 20 legale Züge (16 Bauern + 4 Springer).

---

### 18. Legale Züge für ein Feld

```
POST /api/model/legal-moves-for-square
```

**Request Body:**
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "square": "e2"
}
```

**Response:** `200 OK` → `LegalMovesForSquare` (siehe Datenmodell oben)

---

### 19. FEN parsen

```
POST /api/model/parse-fen
```

**Request Body:**
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
}
```

**Response:**
- `200 OK` → `GameJson`
- `400 Bad Request` → `ErrorResponse`

---

### 20. Game zu FEN

```
POST /api/model/to-fen
```

**Request Body:** Ein GameJson-Objekt (mindestens `"fen"` Feld)
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1",
  "status": "Playing",
  "currentPlayer": "Black",
  "halfMoveClock": 0,
  "fullMoveNumber": 1,
  "isTerminal": false
}
```

**Response:** `200 OK`
```json
{
  "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq e3 0 1"
}
```

---

### 21. PGN parsen (Replay)

```
POST /api/model/parse-pgn
```

**Request Body:**
```json
{
  "pgn": "1. e4 e5 2. Nf3 Nc6"
}
```

**Response:**
- `200 OK` → `GameJson` (Position nach allen PGN-Zügen)
- `400 Bad Request` → `ErrorResponse`

---

### 22. Game zu PGN

```
POST /api/model/to-pgn
```

**Request Body:** Ein GameJson-Objekt (mit `"fen"`)

**Response:** `200 OK`
```json
{
  "pgn": "[Event \"alu-chess Game\"]\n[Site \"Local\"]\n[Date \"2026.04.08\"]\n[White \"White\"]\n[Black \"Black\"]\n[Result \"*\"]\n\n1. e4 e5 *"
}
```

---

### 23. Test-Positionen

```
GET /api/model/test-positions
```

**Response:** `200 OK`
```json
{
  "positions": [
    {"name": "Startstellung", "fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1", "description": "Standard Anfangsposition"},
    {"name": "Scholar's Mate (1 Zug)", "fen": "...", "description": "..."},
    {"name": "En Passant Test", "fen": "...", "description": "..."}
  ]
}
```

---

### 24. Health Check

```
GET /health
```

**Response:** `200 OK`
```json
{"status": "ok", "service": "model"}
```

---

## Zusammenfassung aller Endpoints

### Controller-Service (Port 8081)

| Methode | Pfad | Beschreibung | Response |
|---------|------|--------------|----------|
| `GET` | `/api/controller/state` | Aktueller Spielzustand | `ControllerState` |
| `POST` | `/api/controller/new-game` | Neues Spiel starten | `ControllerState` |
| `POST` | `/api/controller/move` | Zug ausführen | `ControllerState` |
| `POST` | `/api/controller/load-fen` | FEN laden | `ControllerState` |
| `POST` | `/api/controller/resign` | Aufgeben | `ControllerState` |
| `POST` | `/api/controller/browse/back` | Einen Zug zurück | `ControllerState` |
| `POST` | `/api/controller/browse/forward` | Einen Zug vor | `ControllerState` |
| `POST` | `/api/controller/browse/to-start` | Zum Anfang | `ControllerState` |
| `POST` | `/api/controller/browse/to-end` | Zum aktuellen Zustand | `ControllerState` |
| `POST` | `/api/controller/browse/to-move` | Zu bestimmtem Zug | `ControllerState` |
| `GET` | `/api/controller/move-history` | Zughistorie | `{moves: MoveHistoryEntry[]}` |
| `GET` | `/api/controller/games` | Gespeicherte Spiele | `{games: GameRecordSummary[]}` |
| `POST` | `/api/controller/replay/load` | Replay starten | `ControllerState` |
| `POST` | `/api/controller/replay/exit` | Replay verlassen | `ControllerState` |
| `GET` | `/api/controller/export` | JSON exportieren | Raw JSON |
| `POST` | `/api/controller/import` | JSON importieren | `ControllerState` |
| `GET` | `/api/controller/events` | SSE Live-Updates | `text/event-stream` |
| `GET` | `/health` | Health Check | `{status, service}` |

### Model-Service (Port 8082)

| Methode | Pfad | Beschreibung | Response |
|---------|------|--------------|----------|
| `GET` | `/api/model/new-game` | Startstellungs-Game | `GameJson` |
| `POST` | `/api/model/validate-move` | Zug validieren + anwenden | `GameJson` |
| `POST` | `/api/model/legal-moves` | Alle legalen Züge | `{moves: MoveJson[]}` |
| `POST` | `/api/model/legal-moves-for-square` | Legale Züge für ein Feld | `LegalMovesForSquare` |
| `POST` | `/api/model/parse-fen` | FEN → Game | `GameJson` |
| `POST` | `/api/model/to-fen` | Game → FEN | `{fen: string}` |
| `POST` | `/api/model/parse-pgn` | PGN → Game | `GameJson` |
| `POST` | `/api/model/to-pgn` | Game → PGN | `{pgn: string}` |
| `GET` | `/api/model/test-positions` | Test-Positionen | `{positions: TestPosition[]}` |
| `GET` | `/health` | Health Check | `{status, service}` |

---

## Web UI Komponenten & Funktionen

### Empfohlene Komponentenstruktur

```
src/
├── App.tsx                     # Router, Layout
├── api/
│   ├── controllerApi.ts        # Controller-Service API-Calls (Port 8081)
│   ├── modelApi.ts             # Model-Service API-Calls (Port 8082)
│   └── eventSource.ts          # SSE-Verbindung
├── store/
│   └── gameStore.ts            # Zustand (Zustand/Context)
├── components/
│   ├── Board/
│   │   ├── ChessBoard.tsx      # react-chessboard Wrapper
│   │   ├── SquareHighlight.tsx  # Zug-Highlights
│   │   └── PromotionDialog.tsx  # Bauernumwandlung
│   ├── Clock/
│   │   └── ChessClock.tsx      # Uhren-Anzeige
│   ├── History/
│   │   ├── MoveList.tsx        # Zugliste
│   │   └── NavigationBar.tsx   # ⏮ ◀ ▶ ⏭
│   ├── Controls/
│   │   ├── NewGameDialog.tsx   # Neues Spiel
│   │   ├── GameStatus.tsx      # Status-Anzeige
│   │   └── FenPgnTools.tsx     # Import/Export
│   ├── GameHistory/
│   │   └── SavedGames.tsx      # Liste alter Spiele
│   └── Layout/
│       ├── NavBar.tsx          # Navigation
│       └── SidePanel.tsx       # Seitenpanel
└── pages/
    ├── PlayPage.tsx            # Hauptseite (Board + Controls)
    └── HistoryPage.tsx         # Gespeicherte Spiele
```

### Kern-Funktionen pro Komponente

#### `controllerApi.ts` – Controller-Service Client

```typescript
const CTRL_URL = 'http://localhost:8081';

const json = (r: Response) => {
  if (!r.ok) return r.json().then(e => Promise.reject(e));
  return r.json();
};

const post = (path: string, body?: object) =>
  fetch(`${CTRL_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: body ? JSON.stringify(body) : undefined,
  }).then(json);

export const controllerApi = {
  getState: () => fetch(`${CTRL_URL}/api/controller/state`).then(json),
  newGame: () => post('/api/controller/new-game'),
  makeMove: (from: string, to: string, promotion?: string) =>
    post('/api/controller/move', { from, to, promotion: promotion ?? null }),
  loadFen: (fen: string) => post('/api/controller/load-fen', { fen }),
  resign: () => post('/api/controller/resign'),

  // History Navigation
  browseBack: () => post('/api/controller/browse/back'),
  browseForward: () => post('/api/controller/browse/forward'),
  browseToStart: () => post('/api/controller/browse/to-start'),
  browseToEnd: () => post('/api/controller/browse/to-end'),
  browseToMove: (index: number) => post('/api/controller/browse/to-move', { index }),

  // Move History
  getMoveHistory: () => fetch(`${CTRL_URL}/api/controller/move-history`).then(json),

  // Game Records
  getGames: () => fetch(`${CTRL_URL}/api/controller/games`).then(json),
  loadReplay: (id: string) => post('/api/controller/replay/load', { id }),
  exitReplay: () => post('/api/controller/replay/exit'),

  // Import/Export
  exportGame: () => fetch(`${CTRL_URL}/api/controller/export`).then(json),
  importGame: (jsonStr: string) =>
    fetch(`${CTRL_URL}/api/controller/import`, {
      method: 'POST',
      headers: { 'Content-Type': 'text/plain' },
      body: jsonStr,
    }).then(json),
};
```

#### `modelApi.ts` – Model-Service Client

```typescript
const MODEL_URL = 'http://localhost:8082';

const json = (r: Response) => {
  if (!r.ok) return r.json().then(e => Promise.reject(e));
  return r.json();
};

const post = (path: string, body: object) =>
  fetch(`${MODEL_URL}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }).then(json);

export const modelApi = {
  newGame: () => fetch(`${MODEL_URL}/api/model/new-game`).then(json),
  validateMove: (fen: string, from: string, to: string, promotion?: string) =>
    post('/api/model/validate-move', { fen, from, to, promotion: promotion ?? null }),
  legalMoves: (fen: string) => post('/api/model/legal-moves', { fen }),
  legalMovesForSquare: (fen: string, square: string) =>
    post('/api/model/legal-moves-for-square', { fen, square }),
  parseFen: (fen: string) => post('/api/model/parse-fen', { fen }),
  toFen: (game: GameJson) => post('/api/model/to-fen', game),
  parsePgn: (pgn: string) => post('/api/model/parse-pgn', { pgn }),
  toPgn: (game: GameJson) => post('/api/model/to-pgn', game),
  getTestPositions: () => fetch(`${MODEL_URL}/api/model/test-positions`).then(json),
};
```

#### `eventSource.ts` – Live-Updates

```typescript
export function connectToGameEvents(
  onStateChange: (state: ControllerState) => void
): () => void {
  const events = new EventSource('http://localhost:8081/api/controller/events');

  events.addEventListener('state', (e) => {
    const state: ControllerState = JSON.parse(e.data);
    onStateChange(state);
  });

  events.onerror = () => {
    events.close();
    setTimeout(() => connectToGameEvents(onStateChange), 3000);
  };

  return () => events.close();
}
```

#### `ChessBoard.tsx` – Board-Interaktion (Konzept)

```tsx
import { Chessboard } from 'react-chessboard';

function ChessBoard({ fen, onMove }: { fen: string; onMove: (from: string, to: string) => void }) {
  const [selectedSquare, setSelectedSquare] = useState<string | null>(null);
  const [legalMoves, setLegalMoves] = useState<Array<{to: string; isCapture: boolean}>>([]);

  const onSquareClick = async (square: string) => {
    if (selectedSquare) {
      await onMove(selectedSquare, square);
      setSelectedSquare(null);
      setLegalMoves([]);
    } else {
      const result = await modelApi.legalMovesForSquare(fen, square);
      if (result.moves.length > 0) {
        setSelectedSquare(square);
        setLegalMoves(result.moves);
      }
    }
  };

  const onPieceDrop = (source: string, target: string) => {
    onMove(source, target);
    return true;
  };

  return (
    <Chessboard
      position={fen}
      onSquareClick={onSquareClick}
      onPieceDrop={onPieceDrop}
      customSquareStyles={highlightStyles(selectedSquare, legalMoves)}
    />
  );
}
```

---

## TypeScript Types (für Web UI)

```typescript
// types/chess.ts

type Color = 'White' | 'Black';
type PieceType = 'King' | 'Queen' | 'Rook' | 'Bishop' | 'Knight' | 'Pawn';
type GameStatusType = 'Playing' | 'Check' | 'Checkmate' | 'Stalemate' | 'Resigned' | 'Draw' | 'TimeOut';

interface GameJson {
  fen: string;
  status: GameStatusType;
  currentPlayer: Color;
  halfMoveClock: number;
  fullMoveNumber: number;
  isTerminal: boolean;
}

interface ClockState {
  whiteTimeMs: number;
  blackTimeMs: number;
}

interface ControllerState {
  game: GameJson;
  browseIndex: number;
  totalStates: number;
  isAtLatest: boolean;
  isInReplay: boolean;
  statusText: string;
  clock: ClockState | null;
}

interface MoveHistoryEntry {
  move: string;
  san: string;
  status: GameStatusType;
}

interface PieceInfo {
  type: PieceType;
  color: Color;
  symbol: string;
}

interface MoveJson {
  from: string;
  to: string;
  promotion: string | null;
}

interface LegalMovesForSquare {
  square: string;
  piece: PieceInfo | null;
  moves: Array<{
    to: string;
    isCapture: boolean;
    promotion: string | null;
  }>;
}

interface GameRecordSummary {
  id: string;
  datePlayed: string;
  result: string;
  moveCount: number;
}

interface TestPosition {
  name: string;
  fen: string;
  description: string;
}

interface ErrorResponse {
  error: string;
  message: string;
}
```

---

## Mapping: Controller → REST API

| Controller-Methode | HTTP Endpoint | Anmerkung |
|-------------------|---------------|-----------|
| `game` | `GET /api/controller/state` | Gesamtzustand |
| `newGame()` | `POST /api/controller/new-game` | |
| `doMoveResult(move)` | `POST /api/controller/move` | Either → 200/4xx |
| `resign()` | `POST /api/controller/resign` | |
| `loadFenResult(fen)` | `POST /api/controller/load-fen` | |
| `statusText` | Teil von `/api/controller/state` | Als `statusText` Feld |
| `browseBack()` | `POST /api/controller/browse/back` | |
| `browseForward()` | `POST /api/controller/browse/forward` | |
| `browseToStart()` | `POST /api/controller/browse/to-start` | |
| `browseToEnd()` | `POST /api/controller/browse/to-end` | |
| `browseToMove(i)` | `POST /api/controller/browse/to-move` | Body: `{"index": i}` |
| `isAtLatest` / `browseIndex` / `gameStatesCount` | Teil von `/api/controller/state` | |
| `latestMoveHistory` | `GET /api/controller/move-history` | Separater Endpoint |
| `clock` | Teil von `/api/controller/state` | `clock` Feld |
| `gameHistory` | `GET /api/controller/games` | |
| `loadReplay(id)` | `POST /api/controller/replay/load` | Body: `{"id": "..."}` |
| `isInReplay` | Teil von `/api/controller/state` | |
| `exitReplay()` | `POST /api/controller/replay/exit` | |
| `exportCurrentGameAsJson` | `GET /api/controller/export` | |
| `importGameFromJson(j)` | `POST /api/controller/import` | Body: Raw JSON-String |
| Observer (`update()`) | `GET /api/controller/events` (SSE) | Event-Typ: `"state"` |

---

## Quick-Start: Web UI Repo aufsetzen

```bash
# 1. Neues Repo erstellen
npm create vite@latest alu-chess-web -- --template react-ts
cd alu-chess-web

# 2. Dependencies installieren
npm install react-chessboard chess.js
npm install -D tailwindcss @tailwindcss/vite

# 3. Projektstruktur erstellen
mkdir -p src/{api,store,components/{Board,Clock,History,Controls,GameHistory,Layout},pages,types}

# 4. Types-Datei erstellen (siehe TypeScript Types oben)
# 5. API-Client erstellen (siehe chessApi.ts oben)
# 6. EventSource erstellen (siehe eventSource.ts oben)
# 7. Los geht's mit der ersten Komponente!
```

---

## Hinweise für die Umsetzung

1. **FEN ist dein Freund:** `react-chessboard` akzeptiert FEN direkt als `position`-Prop. Du brauchst das Board-Array nur für eigene Darstellung.

2. **chess.js nur für Client-Highlighting:** Verwende `chess.js` NICHT für Validierung (das macht der Server). Nutze es nur, um auf dem Client schnell legale Züge zu markieren. Alternativ: `GET /api/game/moves/{square}` aufrufen.

3. **Clock-Thread:** Der Server tickt die Clock intern (z.B. 100ms Timer). SSE liefert regelmäßige Clock-Updates. Der Client interpoliert zwischen Updates für flüssige Anzeige.

4. **Promotion-Handling:** Wenn ein Bauer die letzte Reihe erreicht, muss die Web UI einen Dialog anzeigen. Erst wenn der Benutzer wählt (Q/R/B/N), wird der Zug mit `promotion`-Feld an den Server gesendet.

5. **Error-Handling:** Bei 4xx-Responses den `message`-Text dem Benutzer anzeigen (z.B. Toast-Notification).
