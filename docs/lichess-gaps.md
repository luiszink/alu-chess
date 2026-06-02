# Lichess Integration — Gap Tracking

Stand: 2026-05-28. Branches: `alu-chess` / `alu-chess-web` → `feature/lichess`.

| ID | Thema | Status | Notiz |
|---|---|---|---|
| G-L01 | BOT-Account anlegen + Upgrade durchführen | offen | Manuell vor Deploy, siehe `lichess-setup.md` |
| G-L02 | OAuth-Token (Scope `bot:play`) generieren + sicher ablegen | offen | Staging-Secret |
| G-L03 | Challenge-Policy (Casual, Standard, 3+0…15+10) | **bestätigt** | ENV-konfigurierbar |
| G-L04 | Multi-Game-Handling (max 2 parallel) | **bestätigt** | Per `Semaphore` im `LichessBotSession` |
| G-L05 | Reconnect & Backoff für NDJSON | geplant | Exp. Backoff 1s→60s, Keep-Alive-Leerzeilen verwerfen |
| G-L06 | Rate-Limit-Handling (`429` + `Retry-After`) | geplant | Generischer Retry-Middleware im Client |
| G-L07 | Zeit-Management (`AI_TIME_LIMIT_MS` vs. `wtime/btime`) | geplant | `min(staticBudget, remaining * 1/40)` |
| G-L08 | Abort/Resign/Chat aus UI | **out of MVP** | Erst in v2 |
| G-L09 | Challenge-Versand aus UI | **out of MVP** | Erst in v2 |
| G-L10 | Spectator-View (live Brett) | **out of MVP** | Erstmal Lichess-Deeplink |

## MVP-Scope (fix)

- Auto-Accept Casual-Standard-Challenges 3+0 bis 15+10
- Max 2 parallele Games
- KI = bestehender `chess.model.ai.ChessAI`
- Status-UI: aktive Games, Event-Log, Bot-Online-Status

## Bewusst NICHT im MVP

- Challenge-Versand aus UI
- Chat
- Manuelles Resign/Abort
- Spectator-Brett (stattdessen Lichess-Deeplink)
- Variant-Support außer `standard`
- Rated Games
