# AI Context – alu-chess

> Projekt-Memory-Dokument für KI-Agenten im IDE-Kontext.
> Bitte konsistent mit diesem Dokument arbeiten.

---

## 1. Projektziel

Entwicklung einer Schach-Applikation in Scala als Lehrprojekt im Kurs **Softwarearchitektur**.
Inspiration: Lichess, aber wesentlich einfacher.
Ziel: Inkrementelle Weiterentwicklung vom Monolith zu einer skalierbaren Architektur.

---

## 2. Aktuelle Phase und Scope

**Phase:** Schachregeln vollständig, GUI fertig, Uhr und Partie-Archiv implementiert.

**Implementiert:**
- Monolith mit MVC-Architektur und Observer-Pattern
- Vollständige Schachregeln (alle Figurzüge, Rochade, En Passant, Bauernumwandlung)
- Schach-, Schachmatt- und Patt-Erkennung
- Remis-Bedingungen (50-Züge-Regel, unzureichendes Material)
- Typisierte Fehlerbehandlung mit `Either[ChessError, _]` statt Exceptions
- FEN-Parser und -Serializer (beliebige Stellungen laden/exportieren)
- PGN-Export und -Replay (Standardformat für Partien)
- SAN-Notation (Standard Algebraic Notation) für Zughistorie
- Swing-GUI im Lichess-Stil (Board, Uhr, Historie, FEN-Eingabe, Testpositionen)
- Text UI (TUI) mit algebraischer Notation (`e2 e4`, `e7 e8 Q`)
- Schachuhr mit konfigurierbaren Zeitkontrollen (Bullet bis Classical)
- History-Navigation im Controller (vor/zurück/Anfang/Ende/Zugauswahl)
- Partie-Archiv mit In-Memory-Repository und Replay-Funktion
- Testpositionen für schnelles Ausprobieren (Scholar's Mate, En Passant, Rochade, …)
- 16 Test-Suites, CI-Pipeline auf GitHub Actions
- Conventional Commits mit `.gitmessage`-Template

**Nicht im Scope (geplant für spätere Phasen):**
- Command-Pattern (Undo/Redo)
- HTTP-API / REST
- Persistenz (Datenbank, Dateisystem)
- Bot / KI-Gegner
- Microservices
- Dreifache Stellungswiederholung (Remis-Bedingung)

---

## 3. Architekturregeln

- **MVC** als Grundstruktur
- Model darf **nicht** von View abhängen
- Model darf **nicht** von Persistenz abhängen
- Controller koordiniert Use Cases
- View kümmert sich nur um Ein-/Ausgabe
- Domain-Logik gehört in `model/`, nicht in `aview/` oder `controller/`
- Monolith modular halten
- Struktur so vorbereiten, dass spätere Extraktion von Adaptern möglich ist
- Observer-Pattern für MVC-Kommunikation
- Fehlerbehandlung über `Either[ChessError, _]` – keine Exceptions im Domain-Layer
- Repository-Trait als Abstraktion für Persistenz (aktuell In-Memory, später austauschbar)

---

## 4. Scala-Coding-Regeln

- `val` statt `var` bevorzugen
- `null` vermeiden, `Option` verwenden
- `case class` für immutable Domain-Objekte
- Kein mutable State im Model-Layer
- Pure Functions bevorzugen
- Kleine Methoden bevorzugen
- Kein Overengineering
- Lesbarkeit vor Cleverness
- Scala-3-Features nutzen (enum, @main, Extension Methods wo sinnvoll)
- `Either` für fehlbare Operationen, `Try`-Monade statt try-catch

---

## 5. Testregeln

- ScalaTest (WordSpec + Matchers)
- Jedes neue Domain-Feature bekommt Tests
- Happy Path **und** Edge Cases testen
- Tests sollen Verhalten beschreiben
- Sinnvolle Coverage priorisieren, nicht künstliche Coverage
- Coverage-Thresholds: ≥98% Statements, ≥85% Branches
- GUI-Klassen (`chess.aview.gui.*`) und `Chess.scala` sind von Coverage ausgenommen

---

## 6. KI-Nutzungsregeln

- KI unterstützt, ersetzt aber kein architektonisches Denken
- Kleine Inkremente generieren, keine riesigen Systeme
- Generierten Code **immer** reviewen
- KI nach Begründungen fragen, nicht nur nach Implementierung
- Separate Agenten für Architektur, funktionalen Stil, Testing und Dokumentation
- Generierter Code muss in der Präsentation erklärbar bleiben

---

## 7. Tech Stack

| Komponente | Version / Tool |
|---|---|
| Sprache | Scala 3.6.4 |
| Build Tool | sbt 1.10.7 |
| Testing | ScalaTest 3.2.19 |
| Coverage | sbt-scoverage 2.2.2 |
| GUI | Scala Swing 3.0.0 |
| UI (alternativ) | TUI (Text) |
| Architektur | MVC + Observer |

---

## 8. Geplante Erweiterungen (Roadmap)

1. ~~Vollständige Schachregeln~~ ✅
2. Command-Pattern (Undo/Redo)
3. ~~GUI~~ ✅ (Scala Swing)
4. HTTP-API (z.B. http4s oder Play)
5. Persistenz (Datei/DB)
6. Bot-API
7. Microservice-Extraktion
8. Performance / Concurrency
