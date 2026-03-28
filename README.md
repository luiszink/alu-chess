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
| Testing | ScalaTest 3.2.19 |
| Coverage | sbt-scoverage 2.2.2 |
| GUI | Scala Swing 3.0.0 |
| Architektur | MVC + Observer |

---

## Setup

### Voraussetzungen

- JDK 17+ (empfohlen: JDK 21)
- sbt 1.10.x

### Kompilieren

```bash
sbt compile
```

### Tests ausführen

```bash
sbt test
```

### Anwendung starten

```bash
sbt run
```

### Coverage-Report erzeugen

```bash
sbt clean coverage test coverageReport
```

Der Report liegt anschließend unter `target/scala-3.6.4/scoverage-report/index.html`.

---

## Projektstruktur

```
src/main/scala/chess/
├── Chess.scala                    # Entry Point (@main)
├── model/
│   ├── Piece.scala                # Figurtypen und Farben (enum)
│   ├── Board.scala                # Spielfeld (8×8, immutable)
│   ├── Game.scala                 # Spielzustand, Zugausführung, Statusprüfung
│   ├── Move.scala                 # Zug-Datentyp mit Promotion, String-Parsing
│   ├── Position.scala             # Feld auf dem Brett (algebraische Notation)
│   ├── MoveValidator.scala        # Zugvalidierung, Schach-Erkennung, legale Züge
│   ├── Fen.scala                  # FEN-Parser und -Serializer
│   ├── Pgn.scala                  # PGN-Export und -Replay
│   ├── MoveEntry.scala            # Zugeintrag mit SAN-Notation
│   ├── ChessClock.scala           # Schachuhr mit Zeitkontrollen
│   ├── ChessError.scala           # Typisierte Fehlermeldungen (enum)
│   ├── GameRecord.scala           # Abgeschlossene Partie (PGN, Züge, Ergebnis)
│   ├── GameRepository.scala       # Repository-Trait für Partie-Archiv
│   ├── InMemoryGameRepository.scala # In-Memory-Implementierung
│   └── TestPositions.scala        # Vordefinierte Teststellungen für GUI
├── controller/
│   ├── Controller.scala           # Use-Case-Koordination, Observable, Zustandsverwaltung
│   └── ControllerInterface.scala  # Trait für Controller-API
├── aview/
│   ├── TUI.scala                  # Text User Interface (Observer)
│   └── gui/
│       ├── SwingGUI.scala         # Hauptfenster (Swing Frame)
│       ├── BoardPanel.scala       # Brettdarstellung und Zuginteraktion
│       ├── SidePanel.scala        # Seitenpanel (FEN-Eingabe, Spielstart)
│       ├── NavBar.scala           # Navigationsleiste
│       ├── HistoryPanel.scala     # Zughistorie-Anzeige
│       ├── HistoryListPanel.scala # Partie-Archiv-Liste
│       ├── ClockPanel.scala       # Schachuhr-Anzeige
│       ├── StartPanel.scala       # Startbildschirm mit Zeitauswahl
│       └── PromotionDialog.scala  # Dialog für Bauernumwandlung
└── util/
    ├── Observable.scala           # Observer-Pattern (Trait)
    └── Observer.scala             # Observer-Interface

src/test/scala/chess/              # 16 Test-Suites (Spiegel der Hauptstruktur)

docs/
├── ai-context.md                  # Projekt-Memory für KI-Agenten
├── architecture-decisions.md      # Architekturentscheidungen (ADR)
├── presentation-notes.md          # Wöchentliche Präsentationsnotizen
├── chess-rules-todo.md            # Implementierungs-Roadmap (alle Stufen abgeschlossen)
├── agent-prompts.md               # Copy/Paste-Prompts für spezialisierte KI-Rollen
└── commit-rules.md                # Commit-Disziplin und Message-Format
```

---

## Architektur

Das Projekt folgt dem **MVC-Pattern** mit **Observer** für lose Kopplung:

- **Model** – Immutable Domain-Objekte (`Board`, `Piece`, `Game`, `Move`, `Position`, `MoveValidator`, …). Kein `var`, kein `null`, keine Abhängigkeiten zu View oder Persistenz. Fehlerbehandlung über `Either[ChessError, _]`.
- **Controller** – Koordiniert Use Cases (Zug ausführen, FEN laden, Aufgeben, Uhr, Replay). Implementiert `Observable` und hält den Spielzustand als `Vector[Game]` für History-Navigation.
- **View (aview)** – Swing-GUI und TUI als `Observer`. Beide reagieren auf dieselben Controller-Events.
- **Util** – Observer/Observable-Pattern als Infrastruktur für MVC.

Detaillierte ADRs: [`docs/architecture-decisions.md`](docs/architecture-decisions.md)

---

## Dokumentation

- [`docs/ai-context.md`](docs/ai-context.md) – Projekt-Memory für KI-gestützte Entwicklung
- [`docs/architecture-decisions.md`](docs/architecture-decisions.md) – Architekturentscheidungen im ADR-Format
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
