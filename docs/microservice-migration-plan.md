# Microservice Migration Plan – alu-chess

## Überblick

Dieses Dokument beschreibt den Migrationsplan vom aktuellen Monolithen zu einer microservice-fähigen Architektur mit Web UI, REST API und Docker-Deployment.

**Aufgabenstruktur laut Kurs:**
- **Task 4:** REST API mit Akka HTTP als weitere View-Schicht + REST API für Module (Vorbereitung Interprozesskommunikation)
- **Task 5:** Projekt in Module aufteilen, REST API mit Http4S, Web UI über den REST-Service

> **Empfehlung:** Die Folien beschreiben den Weg. Task 4 (Akka HTTP) ist eine Zwischenstufe – die finale Architektur ist Task 5 (Http4S + Module + Web UI). Du kannst Task 4 als Lern-/Vorbereitungsschritt nutzen oder direkt mit Task 5 (Http4S) beginnen, je nach Kursanforderung. Im Zweifel die Specs/Aufgabenblätter des Dozenten prüfen.

---

## Phase 1: REST API im Monolith (Task 4)

### Ziel
REST API als zusätzlicher View-Layer neben TUI und SwingGUI. Der Controller bleibt gleich – die REST API ist nur eine weitere "View", die HTTP-Requests entgegennimmt und den Controller aufruft.

### Architektur

```
┌──────────────────────────────────────────────────────────────┐
│  Monolith (ein Prozess)                                      │
│                                                              │
│  ┌─────────┐  ┌──────────┐  ┌───────────┐  ┌─────────────┐ │
│  │  TUI    │  │ SwingGUI │  │ REST API  │  │  Web UI     │ │
│  │ (stdin) │  │ (Swing)  │  │ (Http4s)  │  │ (Browser)   │ │
│  └────┬────┘  └────┬─────┘  └─────┬─────┘  └──────┬──────┘ │
│       │            │               │                │        │
│       └────────────┴───────┬───────┘                │        │
│                            │                        │        │
│                    ┌───────▼────────┐               │        │
│                    │  Controller    │◄──────────────┘        │
│                    │ (Observable)   │   (HTTP calls)          │
│                    └───────┬────────┘                         │
│                            │                                 │
│                    ┌───────▼────────┐                        │
│                    │    Model       │                        │
│                    │ (Game, Board,  │                        │
│                    │  MoveValidator)│                        │
│                    └────────────────┘                        │
└──────────────────────────────────────────────────────────────┘
```

### Umsetzungsschritte

1. **Http4s-Dependency hinzufügen** (in `build.sbt`)
2. **`ChessRoutes`-Klasse** erstellen (neuer View-Layer unter `chess.aview.api`)
3. **JSON-Serialisierung** erweitern (Anfrage/Antwort-DTOs)
4. **Server starten** in `Chess.scala` (parallel zu TUI/GUI)
5. **Observer-Pattern für SSE** (Server-Sent Events für Live-Updates)

---

## Phase 2: Modularisierung (Task 5)

### Ziel
Projekt in unabhängige Module aufteilen, die über REST miteinander kommunizieren (Docker-Container).

### Zielarchitektur

```
┌─────────────────────────────────────────────────────────────────┐
│  Docker Compose                                                  │
│                                                                  │
│  ┌───────────────┐     ┌──────────────────┐    ┌─────────────┐ │
│  │   Web UI      │     │  Controller-     │    │   Model-    │ │
│  │   (Vite +     │────▶│  Service         │───▶│   Service   │ │
│  │    React/     │ API │  (Http4s)        │REST│  (Http4s)   │ │
│  │    Svelte)    │     │  Port: 8081      │    │  Port: 8082 │ │
│  │   Port: 3000  │     └──────────────────┘    └─────────────┘ │
│  └───────────────┘                                              │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Modul-Aufteilung

| Modul | Inhalt | Verantwortung |
|-------|--------|---------------|
| **model-service** | `chess.model.*` | Spiellogik, Validierung, FEN/PGN |
| **controller-service** | `chess.controller.*` | Spielzustand, Orchestrierung, Clock |
| **web-ui** | Frontend (separates Repo) | Benutzeroberfläche im Browser |

### sbt Multi-Project Build

```scala
// build.sbt
lazy val model = (project in file("model"))
  .settings(
    name := "alu-chess-model",
    libraryDependencies ++= Seq(
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "io.circe" %% "circe-generic" % circeVersion,
    )
  )

lazy val controller = (project in file("controller"))
  .dependsOn(model) // Für lokale Entwicklung
  .settings(
    name := "alu-chess-controller",
  )

lazy val root = (project in file("."))
  .aggregate(model, controller)
```

---

## Phase 3: Web UI (Separates Repo)

### Empfohlener Tech-Stack

| Komponente | Empfehlung | Begründung |
|-----------|------------|------------|
| **Framework** | React + TypeScript | Beliebteste Wahl, gute Ökosystem |
| **Alternative** | Svelte oder Vue | Leichterer Einstieg |
| **Build Tool** | Vite | Schnell, modern |
| **Styling** | Tailwind CSS | Utility-first, schnell entwickelbar |
| **Schach-Board** | `react-chessboard` + `chess.js` | Standard-Bibliothek für Schach-UIs |
| **State** | Zustand oder React Context | Leichtgewichtig |
| **HTTP Client** | fetch API oder axios | Kommunikation mit Backend |
| **Live Updates** | Server-Sent Events (SSE) | Echtzeit-Updates vom Server |

> **Details zur Web UI API und deren Funktionen findest du im separaten Dokument: `docs/web-ui-api-spec.md`**

---

## Implementierungsreihenfolge

### Empfohlene Reihenfolge (inkrementell)

```
Schritt 1: REST API im bestehenden Monolith
    ├─ Http4s-Dependency + Server-Setup
    ├─ GET /api/game (Spielzustand lesen)
    ├─ POST /api/game/move (Zug machen)
    ├─ POST /api/game/new (Neues Spiel)
    └─ Tests für alle Endpoints

Schritt 2: Vollständige REST API
    ├─ Alle Endpoints (FEN, PGN, History, Clock, ...)
    ├─ SSE für Live-Updates
    ├─ Error-Handling über HTTP Status Codes
    └─ API-Tests

Schritt 3: Web UI (neues Repo)
    ├─ Projekt-Setup (Vite + React + TypeScript)
    ├─ Schach-Board Komponente
    ├─ API-Integration
    ├─ Live-Updates via SSE
    └─ Alle Features (History, Clock, FEN/PGN)

Schritt 4: Modularisierung
    ├─ sbt Multi-Project aufsetzen
    ├─ Model-Service extrahieren mit eigener REST API
    ├─ Controller-Service ruft Model-Service per HTTP auf
    ├─ Docker-Files erstellen
    └─ Docker Compose für alle Services

Schritt 5: Docker & Deployment
    ├─ Dockerfile pro Service
    ├─ docker-compose.yml
    ├─ Health-Checks
    └─ Performance-Testing
```

---

## Technische Entscheidungen

### Warum Http4s statt Akka HTTP?

| Aspekt | Http4s | Akka HTTP |
|--------|--------|-----------|
| **Scala 3** | Volle Unterstützung | Eingeschränkt (Pekko Fork) |
| **FP-Stil** | Cats Effect, IO-Monade | Actor-basiert |
| **Passt zum Projekt** | Funktionaler Stil (wie Model) | Anderer Paradigma |
| **Task 5 Vorgabe** | ✅ Explizit gefordert | Task 4 nur |
| **Lightweight** | Kleiner Footprint | Größeres Framework |

> Falls Task 4 explizit Akka HTTP verlangt: Akka HTTP als Zwischenschritt implementieren, dann für Task 5 auf Http4s migrieren.

### JSON-Library: Circe statt Play JSON

Für Http4s ist **Circe** die natürlichere Wahl (automatische Codec-Ableitung, Integration mit Http4s). Die bestehende Play-JSON-Serialisierung in `GameJson.scala` kann als Referenz dienen, muss aber auf Circe portiert werden.

```scala
// Circe-Derivation (automatisch für case classes)
import io.circe.generic.auto.*
import io.circe.syntax.*

// Beispiel: Game → JSON automatisch
game.asJson
```

### Server-Sent Events (SSE) für Live-Updates

Statt Polling nutzt die Web UI SSE, um Zustandsänderungen in Echtzeit zu empfangen:

```
Client                          Server
  │                               │
  │  GET /api/game/events         │
  │  Accept: text/event-stream    │
  │──────────────────────────────▶│
  │                               │
  │  data: {"type":"move",...}    │
  │◁ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │
  │                               │
  │  data: {"type":"clock",...}   │
  │◁ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ ─ │
  │                               │
```

---

## Risiken und Offene Punkte

| Risiko | Mitigation |
|--------|------------|
| Task 4 vs. Task 5 Konflikt | Specs des Dozenten prüfen – ggf. erst Akka HTTP, dann Http4s |
| Kein Web UI Spec vom Kurs | Lichess als Referenz, minimale Features zuerst |
| Observer-Pattern über HTTP | SSE als Ersatz, evtl. WebSocket für Bidirektional |
| Clock-Ticking über Netzwerk | Server tickt, Client zeigt nur an + Client-seitige Interpolation |
| Latenz bei Modultrennung | Für lokale Docker-Container vernachlässigbar |

---

## Nächste Schritte

1. **Specs des Dozenten klären:** Ist Task 4 (Akka HTTP) Pflicht oder optional?
2. **Http4s Dependency + minimalen Server** im Monolith einbauen
3. **`GET /api/game`** als ersten Endpoint implementieren
4. **Web-UI-Repo** aufsetzen mit Vite + React + TypeScript
5. **Schach-Board-Komponente** mit `react-chessboard` integrieren
