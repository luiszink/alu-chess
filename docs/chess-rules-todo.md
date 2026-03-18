# Schachregeln – Implementierungs-Roadmap

> Übersicht aller fehlenden Schachregeln und Spielabläufe.
> Reihenfolge: inkrementell, jede Stufe baut auf der vorherigen auf und ist einzeln testbar.

---

## Ist-Zustand

| Feature | Status |
|---|---|
| Board: Figur von A nach B verschieben | ✅ |
| Game: Prüfung ob Figur dem aktiven Spieler gehört | ✅ |
| Game: Spielerwechsel nach Zug | ✅ |
| Game: Resignation | ✅ |
| Figurspezifische Zugregeln | ✅ |
| Schlangregeln (eigene Figuren geschützt) | ✅ |
| Pfadblockierung (Sliding Pieces) | ✅ |
| Schach-Erkennung | ✅ |
| Legale Züge (Schach-Constraint) | ✅ |
| Schachmatt / Patt | ✅ |
| Spezialzüge (Rochade, En Passant, Promotion) | ✅ |
| Remis-Bedingungen | ✅ |

---

## Implementierungsreihenfolge

### Stufe 1: Eigene Figuren nicht schlagen ✅
**Aufwand:** Klein
**Dateien:** `Board.scala` oder `Game.scala`

- `applyMove` verweigert Züge, bei denen die Zielfigur dieselbe Farbe hat
- Einfachste sinnvolle Validierung, sofort testbar

---

### Stufe 2: Figurspezifische Zugmuster ✅
**Aufwand:** Mittel–Groß (Kernstück)
**Dateien:** Neues Modul `MoveValidator.scala` (oder `MoveRules.scala`)

Jede Figur bekommt eine Funktion: `(Move, Board) → Boolean`

| Figur | Regeln |
|---|---|
| **Pawn** | 1 Feld vorwärts (Richtung je Farbe), 2 Felder von Startposition, diagonal schlagen, kein Rückwärts |
| **Rook** | Horizontal/Vertikal, beliebig weit |
| **Bishop** | Diagonal, beliebig weit |
| **Queen** | Kombination Rook + Bishop |
| **King** | 1 Feld in jede Richtung |
| **Knight** | L-Form (2+1), darf über Figuren springen |

**Wichtig:** Pawn ist die komplexeste Grundfigur (Richtung, Startposition, Schlagrichtung ≠ Zugrichtung).

---

### Stufe 3: Pfadblockierung (Sliding Pieces) ✅
**Aufwand:** Klein–Mittel
**Dateien:** `MoveValidator.scala`

- ✅ Rook, Bishop und Queen dürfen nicht über andere Figuren hinwegziehen
- ✅ Knight ist davon ausgenommen (springt)
- ✅ Pfad zwischen `from` und `to` muss frei sein
- In MoveValidator.isPathClear implementiert, gemeinsam mit Stufe 2

---

### Stufe 4: Schach-Erkennung ✅
**Aufwand:** Mittel
**Dateien:** `MoveValidator.scala`

- ✅ `isInCheck(board, color): Boolean`
- ✅ `findKing` und `isAttackedBy` als Hilfsfunktionen
- Prüft alle gegnerischen Figuren: Kann eine davon den König erreichen?
- Nutzt die Zugmuster aus Stufe 2 + Pfadlogik aus Stufe 3

---

### Stufe 5: Legale Züge filtern (Schach-Constraint) ✅
**Aufwand:** Mittel
**Dateien:** `Game.scala`, `MoveValidator.scala`

- ✅ Ein Zug ist nur legal, wenn der eigene König danach **nicht** im Schach steht
- ✅ `legalMoves(board, color): List[Move]` — alle legalen Züge eines Spielers
- ✅ `applyMove` nutzt diesen Filter zusätzlich zu den Zugmustern
- Deckt ab: Fesselung (gepinnte Figuren), König darf nicht ins Schach ziehen

---

### Stufe 6: Schachmatt und Patt ✅
**Aufwand:** Klein (baut auf Stufe 4+5 auf)
**Dateien:** `Game.scala`, `GameStatus` Enum erweitert

- ✅ **Schachmatt:** König im Schach + keine legalen Züge → Gewinn für Gegner
- ✅ **Patt:** König nicht im Schach + keine legalen Züge → Remis
- ✅ `GameStatus` erweitert: `Check`, `Checkmate`, `Stalemate`
- ✅ Nach jedem Zug automatische Prüfung

---

### Stufe 7: Rochade (Castling) ✅
**Aufwand:** Mittel
**Dateien:** `Game.scala`, `MoveValidator.scala`, evtl. `Game` erweitern um Zustandstracking

- Königsseite (O-O) und Damenseite (O-O-O)
- **Voraussetzungen:**
  - König und Turm dürfen sich noch nicht bewegt haben → **Zustand tracken**
  - Felder zwischen König und Turm müssen frei sein
  - König darf nicht im Schach stehen
  - König darf nicht durch Schach ziehen
  - König darf nicht ins Schach ziehen
- Erfordert `hasMoved`-Tracking in `Game` (z.B. `Set[Position]` für bewegte Figuren)

---

### Stufe 8: En Passant ✅
**Aufwand:** Mittel
**Dateien:** `Game.scala`, `MoveValidator.scala`

- Bauer schlägt gegnerischen Bauer, der gerade 2 Felder vorgezogen hat
- **Voraussetzung:** Letzter Zug war ein Doppelschritt des gegnerischen Bauern
- Erfordert Tracking des letzten Zugs in `Game` (z.B. `lastMove: Option[Move]`)
- Geschlagener Bauer steht nicht auf dem Zielfeld → Sonderbehandlung beim Entfernen

---

### Stufe 9: Bauernumwandlung (Promotion) ✅
**Aufwand:** Klein–Mittel
**Dateien:** `Move.scala` (erweitern), `Game.scala`, `MoveValidator.scala`

- Bauer erreicht letzte Reihe → wird zu Dame, Turm, Läufer oder Springer
- `Move` um optionale `promotion: Option[Piece]` erweitern
- TUI-Input erweitern: z.B. `"e7 e8 Q"` für Umwandlung in Dame
- Default: Dame (häufigster Fall)

---

### Stufe 10: Remis-Bedingungen ✅
**Aufwand:** Mittel
**Dateien:** `Game.scala`, `GameStatus` erweitern

| Regel | Beschreibung | Tracking |
|---|---|---|
| **Patt** | Bereits in Stufe 6 | – |
| **50-Züge-Regel** | 50 Züge ohne Bauernzug oder Schlag → Remis | Halbzug-Zähler |
| **Ungenügendes Material** | K vs K, K+B vs K, K+N vs K → Remis | Figurenzählung |
| **Dreifache Stellungswiederholung** | Selbe Position + selber Spieler 3× → Remis | Positionshistorie |

**Empfehlung:** 50-Züge-Regel und ungenügendes Material zuerst (einfacher). Dreifache Wiederholung ist komplexer (benötigt Stellungs-Hash).

---

## Zusammenfassung: Abhängigkeitsgraph

```
Stufe 1: Eigene Figuren nicht schlagen
    │
Stufe 2: Figurspezifische Zugmuster
    │
Stufe 3: Pfadblockierung
    │
Stufe 4: Schach-Erkennung
    │
Stufe 5: Legale Züge (Schach-Constraint)
    │
Stufe 6: Schachmatt / Patt
    │
    ├── Stufe 7: Rochade (braucht hasMoved-Tracking)
    ├── Stufe 8: En Passant (braucht lastMove-Tracking)
    ├── Stufe 9: Bauernumwandlung
    └── Stufe 10: Remis-Bedingungen
```

Stufen 7–10 sind untereinander unabhängig und können in beliebiger Reihenfolge nach Stufe 6 integriert werden.

---

## Architektur-Hinweise

- **MoveValidator als eigenes Objekt:** Hält die Zugregeln aus `Board` und `Game` heraus → Single Responsibility
- **Kein mutable State im Model:** Tracking-Daten (hasMoved, lastMove, halfMoveClock) werden als Felder in `Game` gehalten (immutable, kopiert bei jedem Zug)
- **Jede Stufe einzeln committen und testen:** Conventional Commits, z.B. `feat(model): add pawn movement rules`
- **Option/Either beibehalten:** Weiterhin `Option[Game]` für fehlbare Operationen, später evtl. `Either[MoveError, Game]` für bessere Fehlermeldungen
