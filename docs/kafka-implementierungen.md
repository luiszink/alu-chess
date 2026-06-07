# Kafka-Implementierungen in alu-chess

Diese Datei fasst die drei fachlichen Kafka-Integrationen im Projekt zusammen. Alle drei nutzen denselben Kafka-Broker, aber unterschiedliche Topics und Consumer Groups. Das Routing passiert also nicht durch eine zentrale Weiche, sondern durch Kafka-Topics.

## 1. Asynchrone KI-Zuege

**Ziel:** Der Controller soll einen KI-Zug nicht direkt berechnen muessen. Stattdessen wird die Anfrage asynchron an einen Scala-Worker ausgelagert.

```text
Controller
  -> Topic: chess-ai-requests
  -> kafka-scala-worker
  -> Topic: chess-ai-responses
  -> Controller
```

**Producer:** `KafkaAiCoordinator` im Controller.

**Consumer:** `KafkaAiWorker`, gestartet ueber `KafkaWorkerApp` im Container `kafka-scala-worker`.

**Event-Typen:** `AiMoveRequest` und `AiMoveResponse`.

**Topics und Gruppen:**

| Zweck | Default |
| --- | --- |
| Request Topic | `chess-ai-requests` |
| Response Topic | `chess-ai-responses` |
| Consumer Group | `alu-chess-ai-workers` |

**Warum Kafka hier sinnvoll ist:**

- KI-Berechnung kann laenger dauern und blockiert nicht den Controller.
- Mehrere Scala-Worker koennen spaeter parallel laufen.
- Der Controller bleibt fuer Spielkoordination zustaendig, die KI-Logik liegt im Worker.

## 2. Stockfish Engine ueber Kafka

**Ziel:** Die REST-Kommunikation zwischen Model-Service und Stockfish-Microservice wird durch Kafka ersetzt.

```text
Model-Service
  -> Topic: stockfish-engine-requests
  -> stockfishworker
  -> Topic: stockfish-engine-responses
  -> Model-Service
```

**Producer/Requester:** `KafkaEngineProxy` im Model-Service.

**Consumer:** `StockfishKafkaWorker.py` im Container `stockfishworker`.

**Event-Typen:** `StockfishEngineRequest` und `StockfishEngineResponse`.

**Unterstuetzte Operationen:**

| Operation | Bedeutung |
| --- | --- |
| `health` | Prueft, ob der Worker erreichbar ist |
| `best-move` | Fragt den besten Zug fuer eine FEN-Stellung ab |
| `evaluate` | Fragt eine Bewertung fuer eine FEN-Stellung ab |

**Topics und Gruppen:**

| Zweck | Default |
| --- | --- |
| Request Topic | `stockfish-engine-requests` |
| Response Topic | `stockfish-engine-responses` |
| Consumer Group | `alu-chess-stockfish-workers` |

**Warum Kafka hier sinnvoll ist:**

- Der Model-Service haengt nicht mehr direkt an einer REST-Schnittstelle des Python-Workers.
- Engine-Anfragen werden ueber `requestId` und `clientId` korreliert.
- Stockfish-Worker koennen spaeter horizontal skaliert werden.
- Der Python/Stockfish-Teil bleibt getrennt vom Scala-System.

## 3. Persistenz als Kafka-Consumer

**Ziel:** Der Controller schreibt abgeschlossene/importierte Partien nicht direkt in die Datenbank. Stattdessen publiziert er Persistenz-Events, die vom Scala-Worker konsumiert und in MongoDB gespeichert werden.

```text
Controller
  -> Topic: game-persistence-requests
  -> kafka-scala-worker
  -> MongoDB
```

**Producer:** `KafkaPublishingGameRepository` im Controller.

**Consumer:** `KafkaGamePersistenceConsumer`, gestartet ueber `KafkaWorkerApp` im Container `kafka-scala-worker`.

**Event-Typ:** `GamePersistenceEvent`.

**Event-Arten:**

| Event Type | Bedeutung |
| --- | --- |
| `game-record-upsert-requested` | Partie speichern oder aktualisieren |
| `game-record-delete-requested` | Partie loeschen |
| `game-records-clear-requested` | Alle gespeicherten Partien loeschen |

**Topics und Gruppen:**

| Zweck | Default |
| --- | --- |
| Persistence Topic | `game-persistence-requests` |
| Consumer Group | `alu-chess-persistence-workers` |

**Datenbank-Default:**

| Variable | Default |
| --- | --- |
| `DB_TYPE` | `mongo` |
| `PERSISTENCE_TRANSPORT` | `kafka` |
| `MONGO_URI` | `mongodb://mongo:27017` |
| `MONGO_DB` | `chess` |

**Warum Kafka hier sinnvoll ist:**

- Kafka wird nicht nur als Event-Log verwendet, sondern steuert aktiv den DB-Write.
- Der Controller bleibt fuer Spielablauf und API zustaendig, nicht fuer dauerhafte Speicherung.
- Persistenz ist asynchron und entkoppelt.
- Der Consumer committed Kafka-Offsets erst nach erfolgreichem DB-Write.
- MongoDB und Postgres koennen ueber die vorhandenen DAOs genutzt werden; Standard ist MongoDB.

## Gemeinsame Architektur

```text
                 +------------------+
                 |      Kafka       |
                 +------------------+
                    ^      ^      ^
                    |      |      |
     AI Requests ---+      |      +--- Persistence Events
                           |
          Stockfish Requests/Responses
```

**Wichtige Container:**

| Container | Aufgabe |
| --- | --- |
| `kafka` | Ein Kafka-Broker fuer alle Topics |
| `controller` | Produziert AI- und Persistenz-Requests, konsumiert AI-Responses |
| `model` | Produziert Stockfish-Requests und konsumiert Stockfish-Responses |
| `kafka-scala-worker` | Kombinierter Scala-Worker fuer AI und Persistenz |
| `stockfishworker` | Python-Worker mit Stockfish |
| `mongo` | Standarddatenbank fuer gespeicherte Partien |

**Wichtig:** Es gibt nur einen Kafka-Broker-Container. Mehrere Worker-Container sind keine weiteren Kafka-Instanzen, sondern Consumer/Producer, die mit dem Broker sprechen.

## Start

Standardstart mit MongoDB, Kafka, Controller, Model, Scala-Worker und Stockfish-Worker:

```powershell
docker compose up --build
```

Nach der Umbenennung von `aiworker` zu `kafka-scala-worker` kann ein alter Container als Orphan uebrig bleiben. In diesem Fall einmalig aufraeumen:

```powershell
docker compose down --remove-orphans
```

Danach reicht wieder der normale Startbefehl.

