# Copilot Instructions for alu-chess

Dieses Dokument gibt Copilot einen stabilen, projektspezifischen Rahmen.

## Projektkontext
- Scala Chess App im Softwarearchitektur-Kurs
- Monolith first
- MVC-Architektur
- Text-UI zuerst
- Inkrementelle Entwicklung, keine Big-Bang-Implementierung

## Architekturregeln
- MVC strikt trennen
- Model ist unabhängig von View, HTTP, Persistenz, externen APIs
- Domain-Logik bleibt im Model-Layer
- Controller koordiniert Use Cases
- View macht Ein-/Ausgabe, keine Domain-Regeln
- Overengineering vermeiden

## Scala- und Stilregeln
- Functional first
- val statt var (insbesondere im Model)
- Immutable Datenstrukturen bevorzugen
- null vermeiden, Option verwenden
- case class/enums für Domainmodell
- Kleine, lesbare Methoden
- Keine cleveren Lösungen auf Kosten der Verständlichkeit
- vermeide try-catch, benutze Try-Monade statt dessen

## Testing-Regeln
- ScalaTest für Unit-Tests
- Neue Domain-Funktion nur mit passenden Tests
- Happy Path + Edge Cases
- Tests sollen Verhalten dokumentieren
- Coverage sinnvoll erhöhen (nicht künstlich)

## Scope-Regel
- Nur den aktuell angefragten Umfang umsetzen
- Keine vorgezogene Implementierung von HTTP, Persistenz, Bot oder Microservices
- Bei Unklarheit: minimalste sinnvolle Interpretation wählen

## Commit-Disziplin
- Kleine, thematisch saubere Änderungen
- Häufig committen, wenn ein klarer Zwischenstand stabil ist
- Jeder Commit muss bauen und Tests dürfen nicht verschlechtern
- Commit-Nachrichten nach Conventional Commits
- Keine gemischten Commits (z.B. Refactor + Feature + Docs in einem)

## Commit-Format
<type>(<scope>): <kurze beschreibung>

Typen:
- feat: neue Funktion
- fix: Fehlerbehebung
- refactor: Umstrukturierung ohne Verhaltensänderung
- test: Tests
- docs: Dokumentation
- chore: Build/Config/Tooling

Beispiele:
- chore(setup): add sbt and ScalaTest baseline
- docs(architecture): add initial ADRs
- test(model): add board and piece unit tests

## Arbeitsmodus für Copilot
- Erst lesen, dann minimal ändern
- Bestehende Struktur respektieren
- Änderungen kurz begründen
- Bei größeren Änderungen immer Dateien nennen
- Keine unnötigen neuen Abstraktionen einführen
