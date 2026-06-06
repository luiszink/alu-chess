# Kafka-gesteuerter KI-Gegner

## Kurzidee

Kafka wird aktiv fuer den Web-Modus Human-vs-AI verwendet. Nach einem menschlichen Zug sendet der Controller einen KI-Zugauftrag an Kafka. Ein eigener AI-Worker konsumiert diesen Auftrag, berechnet mit `ChessAI` den Antwortzug und sendet das Ergebnis ueber Kafka zurueck.

Damit ist Kafka nicht nur Event-Logging, sondern ein verpflichtender Teil der Funktion: Ohne Kafka oder AI-Worker macht die KI keinen Antwortzug.

## Ablauf

```text
Spieler macht Zug
  -> Controller validiert und speichert den Zug
  -> Controller publiziert AiMoveRequest nach Kafka

AI-Worker konsumiert AiMoveRequest
  -> parst FEN
  -> berechnet besten Zug mit ChessAI
  -> publiziert AiMoveResponse nach Kafka

Controller konsumiert AiMoveResponse
  -> prueft gameId/requestId
  -> wendet KI-Zug an
  -> UI erhaelt Update ueber SSE
```

## Kafka Topics

- `chess-ai-requests`
- `chess-ai-responses`

## Event: AiMoveRequest

```json
{
  "requestId": "abc",
  "gameId": "game-123",
  "fen": "...",
  "aiColor": "Black",
  "timeLimitMs": 2000,
  "maxDepth": 4
}
```

## Event: AiMoveResponse

```json
{
  "requestId": "abc",
  "gameId": "game-123",
  "from": "e7",
  "to": "e5",
  "promotion": null,
  "error": null
}
```

Bei Fehlern kann die Antwort statt eines Zuges ein Fehlerfeld enthalten:

```json
{
  "requestId": "abc",
  "gameId": "game-123",
  "from": null,
  "to": null,
  "promotion": null,
  "error": "Invalid FEN"
}
```

## Umgesetzte Aenderungen

- Neues sbt-Modul `aiworker`, abhaengig von `model`.
- Neuer Service-Einstiegspunkt `chess.aiworker.KafkaAiWorker`.
- Neuer `Dockerfile.aiworker`.
- `docker-compose.yml` ergaenzt den Service `aiworker`.
- Controller bekommt einen Kafka-AI-Coordinator.
- `MultiGameRoutes` loest nach einem erfolgreichen menschlichen HvAI-Zug einen Kafka-Request aus.
- `.env.example` ergaenzt Kafka-AI-Konfiguration.

## Neue Konfiguration

```env
KAFKA_TOPIC_AI_REQUESTS=chess-ai-requests
KAFKA_TOPIC_AI_RESPONSES=chess-ai-responses
KAFKA_AI_WORKER_GROUP_ID=alu-chess-ai-workers
AI_TIME_LIMIT_MS=2000
AI_MAX_DEPTH=4
```

## Warum Kafka hier sinnvoll ist

Kafka entkoppelt die KI-Berechnung vom Controller. Der Controller muss nicht waehrend der KI-Suche blockieren, und die KI kann als eigener Worker-Service skaliert oder ausgetauscht werden.

Der Use Case ist aktiv und sichtbar: Im Human-vs-AI-Spiel kommt der KI-Antwortzug ueber Kafka. Ohne Kafka oder AI-Worker bleibt das Spiel nach dem menschlichen Zug stehen, weil kein KI-Zug berechnet und zurueckgemeldet wird.

## Abgrenzung

- Gilt nur fuer Web Human-vs-AI im Multi-Game-Modus.
- TUI, Swing und Legacy-Single-Game bleiben unveraendert.
- REST bleibt fuer menschliche Zuege synchron.
- Kafka wird nur fuer die asynchrone KI-Antwort verwendet.

## Testplan

- JSON-Encoding und JSON-Decoding der Kafka-Events testen.
- AI-Worker mit gueltiger FEN testen.
- AI-Worker mit ungueltiger FEN testen.
- AI-Worker mit terminaler Stellung testen.
- Controller testen: HvAI loest nach menschlichem Zug einen Kafka-Request aus.
- Controller testen: HvH loest keinen Kafka-Request aus.
- Controller testen: unbekannte oder veraltete AI-Responses werden ignoriert.
- Manuelle Pruefung mit `docker compose up --build`.

## Praesentationssatz

Kafka wird in `alu-chess` als asynchrone Command/Response-Infrastruktur fuer den KI-Gegner eingesetzt. Der Controller sendet KI-Zugauftraege an Kafka, ein separater AI-Worker berechnet die Antwort und sendet den KI-Zug ueber Kafka zurueck. Dadurch ist Kafka ein aktiver Bestandteil der Human-vs-AI-Funktion und nicht nur ein Event-Log.
