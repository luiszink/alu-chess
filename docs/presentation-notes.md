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

## Woche 3–4: (Platzhalter)

_Geplant: Schachregeln (legale Züge pro Figurtyp, Schach-Erkennung), erweiterte Zugvalidierung, Command-Pattern (Undo/Redo)_

---

## Woche 5–6: (Platzhalter)

_Geplant: Command-Pattern (Undo/Redo), GUI oder HTTP-API_

---

## Woche 7–8: (Platzhalter)

_Geplant: Persistenz, Bot-Integration, Architektur-Refactoring_

---

## Finale Präsentation: (Platzhalter)

_Zusammenfassung der Architektur-Evolution, Lessons Learned, KI-Reflexion_
