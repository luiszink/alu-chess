# Architecture Decisions – alu-chess

> Dokumentation architektonischer Entscheidungen im ADR-Stil (Architecture Decision Records).

---

## ADR-001: MVC als Grundarchitektur

**Kontext:** Das Projekt ist eine Schach-Applikation im Rahmen eines Softwarearchitektur-Kurses. Es muss verschiedene Architekturmuster demonstrieren können.

**Entscheidung:** MVC (Model-View-Controller) mit Observer-Pattern.

**Begründung:**
- Klare Trennung von Verantwortlichkeiten
- Gut geeignet für inkrementelle Erweiterung (TUI → GUI → Web)
- Standard-Pattern im universitären Kontext, gut präsentierbar
- Observer ermöglicht lose Kopplung zwischen Controller und Views

**Konsequenzen:**
- Model ist unabhängig testbar
- Mehrere Views können parallel existieren
- Controller wird zum zentralen Koordinationspunkt

---

## ADR-002: Monolith-First-Ansatz

**Kontext:** Das Projekt soll am Ende Microservices demonstrieren, aber inkrementell entwickelt werden.

**Entscheidung:** Start als modularer Monolith, spätere Extraktion.

**Begründung:**
- Einfacherer Start, weniger Infrastruktur nötig
- Modulare Paketstruktur erlaubt spätere Aufteilung
- "Monolith first" ist ein anerkanntes Pattern (Martin Fowler)

**Konsequenzen:**
- Keine verteilte Komplexität in der Anfangsphase
- Package-Grenzen müssen sauber gehalten werden
- Refactoring zu Services wird einfacher, wenn Grenzen klar sind

---

## ADR-003: Funktionaler Stil in Scala 3

**Kontext:** Scala erlaubt sowohl OOP als auch FP. Für den Kurs soll ein klarer Stil gelten.

**Entscheidung:** Funktionaler Stil im Domain-Layer, pragmatischer Mix im Rest.

**Begründung:**
- Immutable Domain-Objekte sind einfacher zu testen und zu begründen
- `case class`, `enum`, `Option` statt `null` reduzieren Fehlerquellen
- Reine Funktionen machen Logik vorhersagbar
- Controller/View dürfen pragmatisch `var` für Zustand verwenden (z.B. Observer-Liste)

**Konsequenzen:**
- Model-Layer hat keinen mutable State
- Board-Operationen erzeugen neue Board-Instanzen
- Etwas höherer Speicherverbrauch (akzeptabel für Schach)

---

## ADR-004: ScalaTest als Test-Framework

**Kontext:** Es gibt mehrere Test-Frameworks für Scala (ScalaTest, MUnit, Specs2).

**Entscheidung:** ScalaTest mit WordSpec-Stil und Matchers.

**Begründung:**
- Ausgereift, gut dokumentiert, weit verbreitet
- WordSpec erlaubt lesbare, BDD-ähnliche Tests
- Matchers geben klare Fehlermeldungen
- Gute IDE-Integration

**Konsequenzen:**
- Tests lesen sich als Spezifikation
- sbt-scoverage für Coverage-Reports

---

## ADR-005: Package-Struktur ohne Uni-Prefix

**Kontext:** Typischerweise werden Packages nach der Organisation benannt (z.B. `de.htwg.se.chess`).

**Entscheidung:** Einfaches `chess`-Package ohne Uni-Prefix.

**Begründung:**
- Kürzer, weniger verschachtelt
- Portabler, keine institutionelle Bindung
- Ausreichend für ein Kursprojekt

**Konsequenzen:**
- Kürzere Imports
- Weniger Verzeichnistiefe

---

## ADR-006: Position und Move als eigene Typen

**Kontext:** Züge könnten als Tupel `(Int, Int, Int, Int)` oder als Strings dargestellt werden. Im Domain-Model braucht es eine klare Repräsentation.

**Entscheidung:** `Position` und `Move` als eigene `case class`-Typen mit Parsing aus algebraischer Notation.

**Begründung:**
- Type Safety: Der Compiler verhindert, dass Zeile und Spalte verwechselt werden
- Algebraische Notation (`"e2"`, `"e2 e4"`) ist die Sprache des Schachs – direkte Übersetzung in Domain-Typen
- Parsing und Validierung zentral in Companion Objects (`Position.fromString`, `Move.fromString`)
- Trennung von Darstellung (String) und Semantik (Position/Move) folgt dem DDD-Prinzip

**Konsequenzen:**
- Board-API wird ausdrucksstärker: `board.move(Move(...))` statt `board.move(r1, c1, r2, c2)`
- Neue Regeln (legale Züge, Schach) können direkt auf `Position`/`Move` arbeiten
- Geringer Overhead (zwei kleine case classes), kein Overengineering

---

## ADR-007: Either-basierte Fehlerbehandlung mit ChessError

**Kontext:** Ungültige Züge sind erwartbares Verhalten, keine Ausnahmesituation. Der Benutzer (TUI oder GUI) braucht aber aussagekräftige Fehlermeldungen.

**Entscheidung:** `Game.applyMoveE`, `Fen.parseE`, `Move.fromStringE` etc. geben `Either[ChessError, _]` zurück. `ChessError` ist ein Scala-3-Enum mit typisierten Fehlervarianten.

**Begründung:**
- `Either` macht im Typsystem sichtbar, dass eine Operation fehlschlagen kann
- `ChessError`-Varianten (z.B. `NoPieceAtSource`, `LeavesKingInCheck`, `InvalidFenFormat`) liefern präzise Fehlermeldungen
- Aufrufer (Controller, TUI, GUI) können per Pattern Matching gezielt reagieren
- Kein try/catch im Domain-Layer, passt zum funktionalen Stil
- Erweiterung um neue Fehlertypen ist einfach (neuen Enum-Case hinzufügen)

**Konsequenzen:**
- Controller bietet zwei API-Varianten: `doMove` (Boolean) und `doMoveResult` (Either) – Views wählen passende Variante
- TUI zeigt `err.message` direkt an, GUI kann differenziert reagieren
- Convenience-Wrapper (`Option`-basiert, `Try`-basiert) existieren für einfachere Anwendungsfälle

---

## ADR-008: MoveValidator als separates Objekt

**Kontext:** Zugvalidierung ist die komplexeste Logik im Schachspiel (Figurmuster, Pfadblockierung, Schach-Erkennung, Spezialzüge). Diese Logik muss irgendwo leben.

**Entscheidung:** Eigenes `MoveValidator`-Objekt im Model-Layer. Nicht in `Board` oder `Game` integriert.

**Begründung:**
- Single Responsibility: `Board` verwaltet das Spielfeld, `Game` den Spielzustand, `MoveValidator` die Regeln
- Testbarkeit: Jede Komponente kann isoliert getestet werden
- Erweiterbarkeit: Neue Regeln (z.B. Rochade, En Passant) können als private Methoden ergänzt werden, ohne `Board` oder `Game` zu verändern
- Kein Overengineering: Ein Objekt mit privaten Methoden, kein Interface oder Strategy-Pattern

**Konsequenzen:**
- `Game.applyMoveE` delegiert Regelprüfung an `MoveValidator`
- `MoveValidator.legalMoves` liefert alle legalen Züge (für Schachmatt/Patt-Erkennung und SAN-Disambiguation)
- Spezialzüge (Rochade, En Passant, Promotion) werden in `applyMoveEffects` zentral behandelt

---

## ADR-009: Swing-GUI als zweite View

**Kontext:** Nach der TUI sollte eine grafische Oberfläche ergänzt werden. Das MVC-Pattern erlaubt mehrere Views auf denselben Controller.

**Entscheidung:** Scala Swing (`scala-swing` 3.0.0) als GUI-Framework.

**Begründung:**
- Scala Swing ist die einzige Scala-native GUI-Bibliothek mit guter Scala-3-Unterstützung
- Keine zusätzliche Infrastruktur (kein Browser, kein Server) nötig
- GUI und TUI laufen parallel als Observer auf demselben Controller
- Bewährt das Observer-Pattern aus ADR-001 in der Praxis
- GUI-Klassen sind sauber von der Domain-Logik getrennt (eigenes Package `aview.gui`)

**Konsequenzen:**
- GUI-Klassen sind von Coverage ausgenommen (headless testing nicht möglich)
- Controller-Interface wächst um GUI-relevante Methoden (History-Navigation, Clock, Replay)
- Das MVC-Pattern funktioniert mit mehreren Views ohne Codeänderung im Model

---

## ADR-010: Schachuhr als immutable Domain-Objekt

**Kontext:** Zeitkontrolle ist ein zentrales Feature in Schach-Apps. Die Uhr muss ticken, pausieren und zwischen Spielern wechseln.

**Entscheidung:** `ChessClock` als immutable Case Class mit `tick`, `press`, `stop`-Methoden. Zeitkonfiguration über `TimeControl`.

**Begründung:**
- Passt zum funktionalen Stil: jede Operation erzeugt eine neue Clock-Instanz
- `System.nanoTime()` wird als Parameter übergeben, nicht intern abgefragt → testbar
- `TimeControl` als separater Datentyp mit Presets (Bullet, Blitz, Rapid, Classical)
- Controller verwaltet `Option[ChessClock]` – Spiele ohne Uhr sind möglich

**Konsequenzen:**
- GUI ruft `tickClock()` periodisch auf (Timer-basiert)
- Controller prüft nach jedem Tick, ob Zeit abgelaufen ist
- Uhrzustand wird nicht in `Game` gespeichert (separate Concern)

---

## ADR-011: Repository-Pattern für Partie-Archiv

**Kontext:** Abgeschlossene Partien sollen gespeichert und als Replay geladen werden können. Aktuell nur In-Memory, später austauschbar.

**Entscheidung:** `GameRepository`-Trait mit `InMemoryGameRepository`-Implementierung. Controller erhält Repository per Constructor Injection.

**Begründung:**
- Trait als Abstraktion ermöglicht spätere Implementierung (Dateisystem, Datenbank) ohne Controller-Änderung
- Constructor Injection macht die Abhängigkeit explizit und testbar
- `GameRecord` bündelt alle relevanten Daten einer Partie (PGN, Züge, Ergebnis, Zeitkontrolle)
- Auto-Save bei Spielende: Controller speichert automatisch, ohne dass die View sich darum kümmert

**Konsequenzen:**
- Model definiert Repository-Interface, Controller benutzt es, konkrete Implementierung ist austauschbar
- Replay-Funktion im Controller nutzt gespeicherte `gameStates` für Navigation
- Kein Persistenz-Framework nötig – erst bei tatsächlichem Bedarf einführen

---

## ADR-012: FEN und PGN als Standardformate

**Kontext:** Schach hat etablierte Standardformate für Stellungen (FEN) und Partien (PGN). Diese zu unterstützen macht die App interoperabel.

**Entscheidung:** `Fen`-Objekt für Parsing und Serialisierung, `Pgn`-Objekt für Export und Replay. Beide im Model-Layer.

**Begründung:**
- FEN ermöglicht das Laden beliebiger Stellungen (Test, Analyse, Debugging)
- PGN ermöglicht den Export von Partien in einem universell lesbaren Format
- SAN-Notation (Standard Algebraic Notation) für menschenlesbare Zughistorie
- Alles im Model-Layer: kein I/O, reine Daten-Transformation

**Konsequenzen:**
- GUI bietet FEN-Eingabefeld und Testpositionen-Auswahl
- PGN-Export enthält Metadaten (Datum, Spieler, Zeitkontrolle, Ergebnis)
- Replay parst PGN-Movetext und spielt Züge auf neuem Spiel nach
