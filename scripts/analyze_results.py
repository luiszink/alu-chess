#!/usr/bin/env python3
"""
Analysiert benchmark-results/all_results.json und erzeugt:
  results.csv    — eine Zeile je (dao, operation, recordCount)
  summary.md     — GitHub-Markdown-Tabelle (wird auch in GITHUB_STEP_SUMMARY geschrieben)

Aufruf: python3 scripts/analyze_results.py [benchmark-results/]
"""

import csv
import json
import os
import pathlib
import sys
from collections import defaultdict

# ── Eingabe ───────────────────────────────────────────────────────────────────
output_dir   = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "benchmark-results")
results_file = output_dir / "all_results.json"

if not results_file.exists():
    print(f"ERROR: {results_file} nicht gefunden", file=sys.stderr)
    sys.exit(1)

with results_file.open() as f:
    results = json.load(f)

if not results:
    print("Keine Ergebnisse vorhanden.")
    sys.exit(0)

# Optionale Metadaten
meta = {}
meta_file = output_dir / "run_meta.json"
if meta_file.exists():
    with meta_file.open() as f:
        meta = json.load(f)

# ── CSV ───────────────────────────────────────────────────────────────────────
FIELDS = [
    "dao", "operation", "recordCount", "iterations",
    "meanMs", "medianMs", "p95Ms", "p99Ms", "minMs", "maxMs",
    "opsPerSec", "totalMs",
]

csv_rows = []
for r in results:
    stats = r.get("stats", {})
    cfg   = r.get("config", {})
    csv_rows.append({
        "dao":         r.get("dao", "?"),
        "operation":   cfg.get("operation", "?"),
        "recordCount": cfg.get("recordCount", 0),
        "iterations":  stats.get("iterations", 0),
        "meanMs":      round(stats.get("meanMs",   0), 4),
        "medianMs":    round(stats.get("medianMs", 0), 4),
        "p95Ms":       round(stats.get("p95Ms",    0), 4),
        "p99Ms":       round(stats.get("p99Ms",    0), 4),
        "minMs":       round(stats.get("minMs",    0), 4),
        "maxMs":       round(stats.get("maxMs",    0), 4),
        "opsPerSec":   round(stats.get("opsPerSec", 0), 2),
        "totalMs":     round(stats.get("totalMs",  0), 2),
    })

csv_path = output_dir / "results.csv"
with csv_path.open("w", newline="") as f:
    writer = csv.DictWriter(f, fieldnames=FIELDS)
    writer.writeheader()
    writer.writerows(csv_rows)

print(f"CSV gespeichert: {csv_path}  ({len(csv_rows)} Zeilen)")

# ── Markdown-Tabelle ──────────────────────────────────────────────────────────
OP_ORDER  = ["Insert", "FindAll", "FindById", "Delete", "Mixed"]
DAO_ORDER = ["memory", "postgres", "mongo"]

by_op: dict = defaultdict(list)
for row in csv_rows:
    by_op[row["operation"]].append(row)

lines = []
lines.append("# DAO Performance Benchmark — Ergebnisse\n")

if meta:
    lines.append(f"| | |")
    lines.append(f"|---|---|")
    lines.append(f"| **Gestartet** | {meta.get('startedAt', '?')} |")
    lines.append(f"| **Beendet** | {meta.get('finishedAt', '?')} |")
    lines.append(f"| **Host** | {meta.get('host', '?')} ({meta.get('runnerOs', '?')}) |")
    lines.append(f"| **Runner-OS** | Ubuntu (GitHub Actions) |")
    lines.append(f"| **Iterationen** | {meta.get('iterations', '?')} gemessen + {meta.get('warmup', '?')} Warmup |")
    lines.append(f"| **DAOs** | {', '.join(meta.get('availableDaos', []))} |")
    lines.append(f"| **Commit** | `{meta.get('githubSha', 'local')}` |")
    lines.append("")

for op in OP_ORDER:
    rows = by_op.get(op, [])
    if not rows:
        continue

    lines.append(f"## {op}\n")
    lines.append("| DAO | Records | Mean (ms) | Median (ms) | p95 (ms) | p99 (ms) | ops/sec |")
    lines.append("|:----|--------:|----------:|------------:|---------:|---------:|--------:|")

    rows_sorted = sorted(
        rows,
        key=lambda r: (
            r["recordCount"],
            DAO_ORDER.index(r["dao"]) if r["dao"] in DAO_ORDER else 99,
        ),
    )

    prev_rc = None
    for row in rows_sorted:
        if prev_rc is not None and row["recordCount"] != prev_rc:
            lines.append("|  |  |  |  |  |  |  |")   # optische Trennung
        prev_rc = row["recordCount"]
        lines.append(
            f"| {row['dao']:<8} "
            f"| {row['recordCount']:>7,} "
            f"| {row['meanMs']:>9.3f} "
            f"| {row['medianMs']:>11.3f} "
            f"| {row['p95Ms']:>8.3f} "
            f"| {row['p99Ms']:>8.3f} "
            f"| {row['opsPerSec']:>7.0f} |"
        )
    lines.append("")

# Schnellvergleich: mean bei 100 Records
lines.append("## Schnellvergleich — Mean-Latenz bei 100 Records\n")
lines.append("| Operation | memory (ms) | postgres (ms) | mongo (ms) | Faktor postgres/memory | Faktor mongo/memory |")
lines.append("|:----------|------------:|--------------:|-----------:|----------------------:|--------------------:|")

rc100 = {(r["dao"], r["operation"]): r for r in csv_rows if r["recordCount"] == 100}
for op in OP_ORDER:
    mem  = rc100.get(("memory",   op), {}).get("meanMs")
    pg   = rc100.get(("postgres", op), {}).get("meanMs")
    mg   = rc100.get(("mongo",    op), {}).get("meanMs")
    if mem is None:
        continue
    pg_factor = f"{pg / mem:.0f}×"  if (pg  and mem) else "—"
    mg_factor = f"{mg / mem:.0f}×"  if (mg  and mem) else "—"
    mem_s = f"{mem:.3f}" if mem is not None else "—"
    pg_s  = f"{pg:.3f}"  if pg  is not None else "—"
    mg_s  = f"{mg:.3f}"  if mg  is not None else "—"
    lines.append(
        f"| {op:<10} | {mem_s:>11} | {pg_s:>13} | {mg_s:>10} "
        f"| {pg_factor:>22} | {mg_factor:>19} |"
    )

lines.append("")

md = "\n".join(lines)

# In Datei schreiben
md_path = output_dir / "summary.md"
md_path.write_text(md, encoding="utf-8")
print(f"Markdown gespeichert: {md_path}")

# In GITHUB_STEP_SUMMARY (falls vorhanden)
github_summary = os.environ.get("GITHUB_STEP_SUMMARY")
if github_summary:
    with open(github_summary, "a", encoding="utf-8") as f:
        f.write(md)
    print("Job-Summary geschrieben.")

print()
print(md)
