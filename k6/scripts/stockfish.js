import http from "k6/http";
import { check, sleep } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:3000";

export default function () { stockfishTest(); }

export const options = {
  scenarios: {
    smoke: {
      executor: "constant-vus",
      vus: 1,
      duration: "10s",
      tags: { phase: "smoke", service: "stockfish" },
    },
    load: {
      executor: "constant-vus",
      vus: 5,
      duration: "30s",
      startTime: "12s",
      tags: { phase: "load", service: "stockfish" },
    },
  },
  thresholds: {
    http_req_failed:                            ["rate<0.01"],
    "http_req_duration{phase:smoke}":           ["p(95)<2000"],
    "http_req_duration{service:stockfish,phase:load}": ["p(95)<5000"],
  },
};

const JSON_HEADERS = { "Content-Type": "application/json" };
const STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

export function stockfishTest() {
  // ── Health ───────────────────────────────────────────
  check(http.get(`${BASE}/api/model/stockfish/health`), {
    "stockfish/health 200": (r) => r.status === 200,
  });

  // ── Best Move ────────────────────────────────────────
  check(
    http.post(
      `${BASE}/api/model/stockfish/best-move`,
      JSON.stringify({ fen: STARTING_FEN, thinkTimeMs: 50 }),
      { headers: JSON_HEADERS },
    ),
    {
      "stockfish/best-move 200":      (r) => r.status === 200,
      "stockfish/best-move has move": (r) => r.json("move") !== undefined,
    },
  );

  // ── Evaluate ─────────────────────────────────────────
  check(
    http.post(
      `${BASE}/api/model/stockfish/evaluate`,
      JSON.stringify({ fen: STARTING_FEN, thinkTimeMs: 50 }),
      { headers: JSON_HEADERS },
    ),
    { "stockfish/evaluate 200": (r) => r.status === 200 },
  );

  sleep(1);
}
