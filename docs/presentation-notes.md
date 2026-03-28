# Presentation Notes – alu-chess

> Wöchentliche Notizen für die Kurspräsentation.

---

## Woche 1–2: Projektsetup, Grundstruktur und erste Zugmechanik

### Was wurde gemacht?

**Infrastruktur & Architektur:**
- sbt-Projekt mit Scala 3.6.4 aufgesetzt
- MVC-Paketstruktur angelegt (`model`, `controller`, `aview`, `util`)
- Observer/Observable-Pattern für MVC-Kommunikation
- GitHub Actions CI-Pipeline (compile → test → coverage)
- Conventional Commits mit `.gitmessage`-Template
- Projekt-Memory (`docs/ai-context.md`) und vier spezialisierte KI-Agenten definiert

**Domain-Model (immutabel, funktional):**
- `Piece` (enum) mit `Color` (White/Black) und Figurtypen (King, Queen, Rook, Bishop, Knight, Pawn)
- `Position` (case class) mit algebraischer Notation (z.B. `"e2"` ↔ `Position(6, 4)`)
- `Move` (case class) mit String-Parsing (z.B. `"e2 e4"` → `Move(from, to)`)
- `Board` (case class, `Vector[Vector[Option[Piece]]]`) mit `cell`, `put`, `clear`, `move`
- `Game` (case class) mit `GameStatus` (Playing/Resigned), `applyMove`, `resign`, Spielerwechsel
- Alle Model-Operationen geben `Option` zurück statt Exceptions zu werfen

**Controller & View:**
- `Controller` koordiniert `Game.applyMove` und benachrichtigt Observer
- `TUI` nimmt Züge als Textinput entgegen (`"e2 e4"`), zeigt Board und Status an
- Status-Anzeige: aktueller Spieler, Resignation

**Tests:**
- 63 Tests in 7 Suites (PieceSpec, BoardSpec, GameSpec, PositionSpec, MoveSpec, ControllerSpec, TUISpec)
- Happy Path + Edge Cases (ungültige Positionen, leere Felder, fremde Figuren)

### Architektonische Entscheidungen
- MVC mit Observer (ADR-001)
- Monolith-first (ADR-002)
- Funktionaler Stil im Domain-Layer (ADR-003)
- ScalaTest als Test-Framework (ADR-004)
- Position und Move als eigene Value Types (ADR-006)
- Option-basierte Zugvalidierung statt Exceptions (ADR-007)

### KI-Nutzung
- Vier spezialisierte Agenten definiert: Architecture, Functional Scala, Test & Coverage, Documentation
- VS Code Prompt Files (`.github/prompts/*.prompt.md`) für schnellen Agentenwechsel
- Projekt-Memory-Dokument als persistenter Kontext für konsistente KI-Arbeit
- KI unterstützt beim Scaffolding, Coding-Stil, Testgenerierung und Doku
- Jeder generierte Code wird manuell gereviewed
- Inkrementelle Commits (8 Commits in Woche 1–2), kein Big-Bang


---

## Woche 3–4: Vollständige Schachregeln und Either-basierte Fehlerbehandlung

### Was wurde gemacht?

**Schachregeln komplett implementiert:**
- Figurspezifische Zugmuster für alle 6 Figurtypen (Bauer, Turm, Läufer, Dame, König, Springer)
- Pfadblockierung für Sliding Pieces (Turm, Läufer, Dame dürfen nicht über Figuren springen)
- Schach-Erkennung (`isInCheck`): Prüft ob der König angegriffen wird
- Legale Züge (`legalMoves`): Filtert Züge, die den eigenen König im Schach lassen
- Schachmatt und Patt als automatische Spielende-Erkennung
- Rochade (kurz und lang) mit allen Voraussetzungen (nicht bewegt, Pfad frei, nicht durch Schach)
- En Passant mit `lastMove`-Tracking
- Bauernumwandlung mit wählbarer Figur (Dame, Turm, Läufer, Springer)
- Remis: 50-Züge-Regel und unzureichendes Material (K vs K, K+B vs K, K+N vs K, K+B vs K+B gleichfarbig)

**Fehlerbehandlung:**
- `ChessError`-Enum mit typisierten Fehlervarianten (statt Exception oder Option)
- `Either[ChessError, _]`-Rückgabe in `Game.applyMoveE`, `Fen.parseE`, `Move.fromStringE`
- TUI zeigt dem Spieler präzise Fehlermeldungen

**Architektur:**
- `MoveValidator` als separates Objekt (Single Responsibility)
- `movedPieces: Set[Position]` in `Game` für Rochade-Tracking
- `lastMove: Option[Move]` in `Game` für En Passant-Tracking
- `halfMoveClock: Int` in `Game` für 50-Züge-Regel

### Architektonische Entscheidungen
- Either-basierte Fehlerbehandlung (ADR-007)
- MoveValidator als separates Objekt (ADR-008)

---

## Woche 5–6: GUI, Schachuhr, FEN/PGN

### Was wurde gemacht?

**Swing-GUI:**
- Lichess-inspirierte Oberfläche mit Scala Swing
- BoardPanel: Brettdarstellung mit Klick-Interaktion und farbiger Hervorhebung legaler Züge
- SidePanel: FEN-Eingabe, Testpositionen-Auswahl, Aufgabe-Button
- HistoryPanel: Zughistorie im SAN-Format, klickbare Züge für Navigation
- ClockPanel: Schachuhr-Anzeige mit Live-Updates
- NavBar: Navigationsleiste (Anfang/Zurück/Vorwärts/Ende)
- StartPanel: Neues Spiel mit Zeitauswahl (Bullet, Blitz, Rapid, Classical)
- PromotionDialog: Figurauswahl bei Bauernumwandlung

**Schachuhr:**
- Immutable `ChessClock`-Klasse mit `tick`, `press`, `stop`
- `TimeControl`-Presets (1+0 bis 30+0)
- Automatische Spielende-Erkennung bei Zeitablauf

**FEN & PGN:**
- `Fen`-Objekt: Parsing und Serialisierung (alle 6 FEN-Felder)
- `Pgn`-Objekt: PGN-Export mit Metadaten und SAN-Movetext
- `Pgn.replayPgn`: Replay eines PGN-Strings auf neuem Spiel
- `MoveEntry` mit vorberechneter SAN-Notation und Disambiguation

**Controller:**
- History-Navigation (vor/zurück/Anfang/Ende/Zugauswahl)
- `Vector[Game]`-basierte Zustandshistorie im Controller
- `ControllerInterface`-Trait als stabile API für Views

### Architektonische Entscheidungen
- Swing-GUI als zweite View (ADR-009)
- Schachuhr als immutable Domain-Objekt (ADR-010)
- FEN und PGN als Standardformate (ADR-012)

---

## Woche 7–8: Partie-Archiv und Replay

### Was wurde gemacht?

**Partie-Archiv:**
- `GameRepository`-Trait als Abstraktion für Persistenz
- `InMemoryGameRepository` als erste Implementierung
- `GameRecord` bündelt PGN, Ergebnis, Zeitkontrolle, Zustandshistorie
- Auto-Save bei Spielende (Schachmatt, Patt, Aufgabe, Zeitablauf)

**Replay:**
- `Controller.loadReplay` lädt gespeicherte Partien zur Navigation
- Aktuelles Spiel wird beim Replay pausiert und danach wiederhergestellt
- HistoryListPanel in der GUI zeigt das Partie-Archiv

**Testpositionen:**
- 15 vordefinierte Stellungen (Scholar's Mate, En Passant, Rochade, Lucena, Philidor, …)
- In der GUI per Dropdown auswählbar, inklusive freie FEN-Eingabe

### Architektonische Entscheidungen
- Repository-Pattern für Partie-Archiv (ADR-011)

---

## Finale Präsentation: (Platzhalter)

_Zusammenfassung der Architektur-Evolution, Lessons Learned, KI-Reflexion_
