# Spark Analytics in alu-chess

## Zweck

Spark wird als nachgelagerte Analytics-Schicht eingesetzt. Die bestehende
Pekko-Streams-Pipeline verarbeitet weiterhin einzelne Zuege zustandsbehaftet;
Spark ersetzt diese Pipeline nicht.

Der Service `spark-analytics` liest die Kafka-Persistenz-Events aus
`game-persistence-requests` und schreibt analytische Auswertungen nach MongoDB.

```text
Controller
  -> Kafka topic: game-persistence-requests
  -> kafka-scala-worker -> MongoDB collection: games
  -> spark-analytics    -> MongoDB collections: game_analytics, game_analytics_summary
```

## Verarbeitung

Der Spark-Service nutzt Structured Streaming mit Kafka als Source.

Spark uebernimmt dabei zwei verteilte Aufgaben:

1. **Ingestion**: Structured Streaming liest `game-persistence-requests` als
   Kafka-Source. `maxOffsetsPerTrigger` begrenzt die Groesse jedes Micro-Batches,
   damit der initiale Backlog-Read den Treiber nicht ueberlaeuft.
2. **Aggregation**: Die globale Zusammenfassung wird ueber den offiziellen
   **MongoDB Spark Connector** berechnet. `game_analytics` wird als verteilter
   DataFrame gelesen und per Spark SQL aggregiert; nur das kleine Ergebnis
   (`totalGames`, Durchschnitt, Count-Maps) landet auf dem Treiber. Die Collection
   wird nicht mehr komplett in den Treiberspeicher geladen.

Die Anwendung der Events (Upsert/Delete/Clear) auf `game_analytics` ist
zustandsbehaftete Steuerlogik und laeuft weiterhin pro Event ueber den
MongoDB-Sync-Treiber – idempotent, sodass ein Reprocessing nach Crash unkritisch
ist.

Unterstuetzte Events:

| Event Type | Wirkung |
| --- | --- |
| `game-record-upsert-requested` | Schreibt/aktualisiert ein Dokument in `game_analytics` |
| `game-record-delete-requested` | Entfernt das Analytics-Dokument zur `recordId` |
| `game-records-clear-requested` | Leert `game_analytics` und `game_analytics_summary` |

Nach Upsert/Delete berechnet Spark die globale Zusammenfassung neu und schreibt
ein Dokument mit `_id = "current"` in `game_analytics_summary`.

## Collections

`game_analytics` enthaelt ein Dokument pro Partie:

- `recordId`
- `datePlayed`
- `result`
- `moveCount`
- `timeControlName`
- `initialTimeMs`
- `incrementMs`
- `finalFen`
- `pgn`

`game_analytics_summary` enthaelt die aggregierte Sicht:

- `totalGames`
- `averageMoveCount`
- `resultCounts`
- `timeControlCounts`
- `generatedAt`

## Anzeige im Web-UI

Die Spark-Zusammenfassung wird im Frontend unter **Statistik** (`/analytics`)
dargestellt (Kennzahlen + Verteilungsbalken fuer Ergebnis und Zeitkontrolle).

Datenweg:

```text
spark-analytics -> MongoDB: game_analytics_summary (_id = "current")
  -> Controller GET /api/controller/analytics/summary
  -> Web-UI Seite /analytics
```

Der Controller liest die Summary ueber `MongoAnalyticsSummaryDao` aus Mongo –
bewusst unabhaengig von `DB_TYPE`, da Spark Analytics immer nach Mongo schreibt.
Solange noch keine Auswertung existiert (oder Mongo nicht erreichbar ist),
liefert der Endpoint `available = false`, und das UI zeigt einen Empty-State.

## Konfiguration

| Variable | Default |
| --- | --- |
| `SPARK_KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` |
| `SPARK_INPUT_TOPIC` | `game-persistence-requests` |
| `SPARK_MONGO_URI` | `mongodb://mongo:27017` |
| `SPARK_MONGO_DB` | `chess` |
| `SPARK_CHECKPOINT_DIR` | `/tmp/spark-checkpoints/chess-analytics` |
| `SPARK_MASTER` | `local[*]` |
| `SPARK_MAX_OFFSETS_PER_TRIGGER` | `5000` |
| `SPARK_LOG_LEVEL` | `WARN` |

## Start

```bash
docker compose up --build spark-analytics
```

Im normalen Compose-Setup startet `spark-analytics` zusammen mit Kafka und
MongoDB. Der Checkpoint liegt im Volume `spark_checkpoints`, damit Spark die
Kafka-Offsets stabil fortsetzen kann.

## Tests

```bash
sbt "sparkAnalytics/test"
```

