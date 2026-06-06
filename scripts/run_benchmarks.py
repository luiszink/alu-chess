#!/usr/bin/env python3
"""
Benchmark-Runner für alu-chess DAO-Performance-Tests.

Liest Konfiguration aus Umgebungsvariablen:
  CONTROLLER_URL  — Basis-URL des Controllers  (default: http://localhost:8081)
  RECORD_COUNTS   — kommagetrennte Integer      (default: 10,100,1000)
  OPERATIONS      — kommagetrennte Strings      (default: Insert,FindAll,FindById,Delete,Mixed)
  ITERATIONS      — gemessene Iterationen       (default: 20)
  WARMUP          — Warmup-Iterationen          (default: 3)
  OUTPUT_DIR      — Ausgabe-Verzeichnis         (default: benchmark-results)

Ausgabe:
  OUTPUT_DIR/<Op>_<records>.json   — Rohergebnis je Kombination
  OUTPUT_DIR/all_results.json      — alle Ergebnisse zusammen
  OUTPUT_DIR/run_meta.json         — Laufzeit-Metadaten
  OUTPUT_DIR/errors.json           — fehlgeschlagene Kombinationen (falls vorhanden)
"""

import datetime
import json
import os
import pathlib
import platform
import sys
import urllib.error
import urllib.request

# ── Konfiguration aus Umgebung ────────────────────────────────────────────────
BASE_URL      = os.environ.get("CONTROLLER_URL", "http://localhost:8081")
OUTPUT_DIR    = pathlib.Path(os.environ.get("OUTPUT_DIR", "benchmark-results"))
RECORD_COUNTS = [int(x.strip()) for x in os.environ.get("RECORD_COUNTS", "10,100,1000").split(",")]
OPERATIONS    = [x.strip() for x in os.environ.get("OPERATIONS", "Insert,FindAll,FindById,Delete,Mixed").split(",")]
ITERATIONS    = int(os.environ.get("ITERATIONS", "20"))
WARMUP        = int(os.environ.get("WARMUP", "3"))

OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


# ── HTTP-Hilfsfunktionen ──────────────────────────────────────────────────────
def post_json(url: str, data: dict) -> object:
    body = json.dumps(data).encode()
    req  = urllib.request.Request(
        url, data=body,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=600) as resp:
        return json.loads(resp.read())


def get_json(url: str) -> object:
    with urllib.request.urlopen(url, timeout=30) as resp:
        return json.loads(resp.read())


# ── Laufzeit-Metadaten ────────────────────────────────────────────────────────
RUN_META = {
    "startedAt":    datetime.datetime.utcnow().isoformat() + "Z",
    "host":         os.environ.get("RUNNER_NAME", platform.node()),
    "runnerOs":     os.environ.get("RUNNER_OS", platform.system()),
    "operations":   OPERATIONS,
    "recordCounts": RECORD_COUNTS,
    "iterations":   ITERATIONS,
    "warmup":       WARMUP,
    "githubRunId":  os.environ.get("GITHUB_RUN_ID", "local"),
    "githubSha":    os.environ.get("GITHUB_SHA", "local"),
}

# ── Verfügbare DAOs ermitteln ─────────────────────────────────────────────────
print(f"Controller: {BASE_URL}")
print(f"Operationen: {OPERATIONS}")
print(f"Record-Counts: {RECORD_COUNTS}")
print(f"Iterationen: {ITERATIONS}  Warmup: {WARMUP}")
print()

try:
    daos_info      = get_json(f"{BASE_URL}/api/perf/dao/list")
    available_daos = [d["name"] for d in daos_info["daos"] if d["available"]]
    unavailable    = [d for d in daos_info["daos"] if not d["available"]]
    print(f"Verfügbare DAOs  : {available_daos}")
    if unavailable:
        print(f"Nicht verfügbar  : {[d['name'] for d in unavailable]}")
        for d in unavailable:
            print(f"  {d['name']}: {d.get('error', '—')}")
    print()
except Exception as exc:
    print(f"FATAL: DAO-Liste konnte nicht abgefragt werden: {exc}", file=sys.stderr)
    sys.exit(1)

if not available_daos:
    print("FATAL: Keine DAOs verfügbar", file=sys.stderr)
    sys.exit(1)

RUN_META["availableDaos"] = available_daos

# ── Benchmark-Matrix durchlaufen ──────────────────────────────────────────────
all_results: list = []
errors:      list = []
total = len(OPERATIONS) * len(RECORD_COUNTS)
done  = 0

for op in OPERATIONS:
    for records in RECORD_COUNTS:
        done += 1
        label = f"[{done:>2}/{total}]  {op:<10}  records={records:>5}"
        sep   = "─" * 60
        print(sep)
        print(label)
        print(sep)

        payload = {
            "daos": available_daos,
            "config": {
                "operation":        op,
                "recordCount":      records,
                "iterations":       ITERATIONS,
                "warmupIterations": WARMUP,
                "seed":             42,
            },
        }

        try:
            results     = post_json(f"{BASE_URL}/api/perf/dao/compare", payload)
            result_list = results if isinstance(results, list) else [results]
            all_results.extend(result_list)

            for r in result_list:
                dao   = r.get("dao", "?")
                stats = r.get("stats", {})
                print(
                    f"  {dao:<10}  "
                    f"mean={stats.get('meanMs', 0):10.3f} ms  "
                    f"p95={stats.get('p95Ms', 0):10.3f} ms  "
                    f"ops/s={stats.get('opsPerSec', 0):>12.0f}"
                )

            # Zwischenergebnis speichern
            out_file = OUTPUT_DIR / f"{op}_{records}.json"
            out_file.write_text(json.dumps(result_list, indent=2))
            print(f"  → gespeichert: {out_file}")

        except urllib.error.HTTPError as exc:
            body = exc.read().decode(errors="replace")
            msg  = f"HTTP {exc.code}: {body}"
            print(f"  ERROR: {msg}")
            errors.append({"op": op, "records": records, "error": msg})

        except Exception as exc:
            print(f"  ERROR: {exc}")
            errors.append({"op": op, "records": records, "error": str(exc)})

        print()

# ── Gesamtergebnis speichern ──────────────────────────────────────────────────
RUN_META["finishedAt"]    = datetime.datetime.utcnow().isoformat() + "Z"
RUN_META["totalResults"]  = len(all_results)
RUN_META["totalErrors"]   = len(errors)

(OUTPUT_DIR / "all_results.json").write_text(json.dumps(all_results, indent=2))
(OUTPUT_DIR / "run_meta.json").write_text(json.dumps(RUN_META, indent=2))

if errors:
    (OUTPUT_DIR / "errors.json").write_text(json.dumps(errors, indent=2))
    print(f"⚠️  {len(errors)} Fehler — siehe {OUTPUT_DIR}/errors.json")

print("═" * 60)
print(f"Fertig.  {len(all_results)} Ergebnisse,  {len(errors)} Fehler.")
print(f"Ausgabe: {OUTPUT_DIR.resolve()}")
