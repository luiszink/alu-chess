# Lichess Bot Setup

End-to-End-Anleitung zur Inbetriebnahme des Lichess-Bots für `alu-chess`.

## 1. BOT-Account anlegen (einmalig, irreversibel!)

> ⚠️ Der Upgrade auf BOT kann **nicht rückgängig** gemacht werden. Der Account darf vor dem Upgrade **keine einzige Partie gespielt** haben.

1. Auf <https://lichess.org/signup> einen **neuen** Account registrieren
   (z. B. `aluchess-bot`).
2. E-Mail bestätigen, einloggen.
3. **Keine** Partie spielen, **keine** Puzzles lösen.

## 2. OAuth Personal Access Token erzeugen

1. Eingeloggt als der neue Bot-Account: <https://lichess.org/account/oauth/token/create>
2. Beschreibung: `alu-chess bot`
3. Scopes anhaken:
   - ✅ `bot:play` (Pflicht)
   - ✅ `challenge:read`
   - ✅ `challenge:write` (für späteren Challenge-Versand)
4. Token erzeugen und **sofort kopieren** — er wird nur einmal angezeigt.

## 3. Account auf BOT upgraden

Einmalig per `curl`:

```bash
curl -X POST https://lichess.org/api/bot/account/upgrade \
  -H "Authorization: Bearer <DEIN_TOKEN>"
```

Erwartete Antwort: `{"ok":true}`. Ab jetzt ist der Account ein BOT — er kann keine normalen Partien mehr spielen, nur über die Bot-API.

Verifizieren:

```bash
curl https://lichess.org/api/account -H "Authorization: Bearer <TOKEN>" | jq .title
# erwartet: "BOT"
```

## 4. Token in die Umgebung legen

**Lokal (`.env`, nicht committen):**

```env
LICHESS_BOT_TOKEN=lip_xxxxxxxxxxxxxxxxxx
LICHESS_BASE_URL=https://lichess.org
LICHESS_AUTO_ACCEPT=true
LICHESS_ACCEPT_RATED=false
LICHESS_ACCEPT_VARIANTS=standard
LICHESS_MIN_INITIAL_SECONDS=180
LICHESS_MAX_INITIAL_SECONDS=900
LICHESS_MAX_GAMES_CONCURRENT=2
AI_TIME_LIMIT_MS=2000
AI_MAX_DEPTH=4
```

**Staging/Production:** als Secret in Docker/CI hinterlegen, niemals in Git.

## 5. Service starten

```bash
docker compose up -d lichess
docker compose logs -f lichess
```

Erfolgs-Log:

```
[lichess] connected as BOT <username>
[lichess] event stream open
```

## 6. Smoke-Test

Von einem Test-Account (normalem User) eine Casual-Challenge senden:
<https://lichess.org/?user=ALU_CHESS_BOT_NAME#friend>

Bot sollte automatisch annehmen und ziehen.

## 7. Frontend

Aufrufen: `http://localhost:8080/lichess`

Erwartet:
- Bot-Status: `online (<username>)`
- Aktive Spiele mit Lichess-Deeplink
- Live-Event-Log via SSE

## Sicherheitshinweise

- Token niemals in Logs ausgeben.
- Token-Datei in `.gitignore` aufnehmen (`.env`).
- Bei Token-Leak: <https://lichess.org/account/oauth/token> → Token revoken und neu erzeugen. Kein Re-Upgrade nötig.

## Wichtige API-Limits

- `429 Too Many Requests` → exponentielles Backoff (Start 1s, max 60s).
- NDJSON-Streams senden alle ~6s eine leere Keep-Alive-Zeile — vom Parser ignorieren.
- Bei Verbindungsabbruch automatisch reconnecten (besonders `/api/stream/event`).

## Konfigurations-Referenz

| ENV | Default | Bedeutung |
|---|---|---|
| `LICHESS_BOT_TOKEN` | — | OAuth-Token, **Pflicht** |
| `LICHESS_BASE_URL` | `https://lichess.org` | API-Endpoint |
| `LICHESS_AUTO_ACCEPT` | `true` | Eingehende Challenges automatisch annehmen |
| `LICHESS_ACCEPT_RATED` | `false` | Auch Rated-Games annehmen |
| `LICHESS_ACCEPT_VARIANTS` | `standard` | Komma-Liste erlaubter Varianten |
| `LICHESS_MIN_INITIAL_SECONDS` | `180` | Mindest-Hauptzeit (3 min) |
| `LICHESS_MAX_INITIAL_SECONDS` | `900` | Maximal-Hauptzeit (15 min) |
| `LICHESS_MAX_GAMES_CONCURRENT` | `2` | Parallel-Spiele |
| `AI_TIME_LIMIT_MS` | `2000` | Engine-Budget pro Zug (ms) |
| `AI_MAX_DEPTH` | `4` | Engine-Tiefe |
