import http from "k6/http";
import { check, sleep } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:3000";

export default function () { modelTest(); }

export const options = {
  scenarios: {
    smoke: {
      executor: "constant-vus",
      vus: 1,
      duration: "10s",
      tags: { phase: "smoke" },
    },
    load: {
      executor: "constant-vus",
      vus: 10,
      duration: "30s",
      startTime: "12s",
      tags: { phase: "load" },
    },
  },
  thresholds: {
    http_req_failed:                  ["rate<0.01"],
    "http_req_duration{phase:smoke}": ["p(95)<300"],
    "http_req_duration{phase:load}":  ["p(95)<500"],
  },
};

const STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
const JSON_HEADERS  = { "Content-Type": "application/json" };

export function modelTest() {
  // ── New Game ─────────────────────────────────────────
  check(http.get(`${BASE}/api/model/new-game`), {
    "new-game 200":     (r) => r.status === 200,
    "new-game has fen": (r) => typeof r.json("fen") === "string",
  });

  // ── Parse FEN ────────────────────────────────────────
  check(
    http.post(`${BASE}/api/model/parse-fen`, JSON.stringify({ fen: STARTING_FEN }), { headers: JSON_HEADERS }),
    {
      "parse-fen 200":     (r) => r.status === 200,
      "parse-fen has fen": (r) => typeof r.json("fen") === "string",
    },
  );

  // ── Legal Moves ──────────────────────────────────────
  check(
    http.post(`${BASE}/api/model/legal-moves`, JSON.stringify({ fen: STARTING_FEN }), { headers: JSON_HEADERS }),
    {
      "legal-moves 200":       (r) => r.status === 200,
      "legal-moves not empty": (r) => r.json("moves").length > 0,
    },
  );

  // ── Legal Moves for Square ───────────────────────────
  check(
    http.post(
      `${BASE}/api/model/legal-moves-for-square`,
      JSON.stringify({ fen: STARTING_FEN, square: "e2" }),
      { headers: JSON_HEADERS },
    ),
    {
      "legal-moves-for-square 200": (r) => r.status === 200,
      "legal-moves-for-square e2":  (r) => r.json("square") === "e2",
    },
  );

  // ── Validate Move ────────────────────────────────────
  check(
    http.post(
      `${BASE}/api/model/validate-move`,
      JSON.stringify({ fen: STARTING_FEN, from: "e2", to: "e4" }),
      { headers: JSON_HEADERS },
    ),
    {
      "validate-move 200":     (r) => r.status === 200,
      "validate-move has fen": (r) => typeof r.json("fen") === "string",
    },
  );

  // ── To FEN ───────────────────────────────────────────
  check(
    http.post(`${BASE}/api/model/to-fen`, JSON.stringify({ fen: STARTING_FEN }), { headers: JSON_HEADERS }),
    {
      "to-fen 200":     (r) => r.status === 200,
      "to-fen has fen": (r) => typeof r.json("fen") === "string",
    },
  );

  // ── Parse PGN ────────────────────────────────────────
  check(
    http.post(
      `${BASE}/api/model/parse-pgn`,
      JSON.stringify({ pgn: "1. e4 e5 2. Nf3 Nc6 3. Bb5" }),
      { headers: JSON_HEADERS },
    ),
    {
      "parse-pgn 200":     (r) => r.status === 200,
      "parse-pgn has fen": (r) => typeof r.json("fen") === "string",
    },
  );

  // ── To PGN ───────────────────────────────────────────
  check(
    http.post(`${BASE}/api/model/to-pgn`, JSON.stringify({ fen: STARTING_FEN }), { headers: JSON_HEADERS }),
    {
      "to-pgn 200":     (r) => r.status === 200,
      "to-pgn has pgn": (r) => typeof r.json("pgn") === "string",
    },
  );

  // ── Test Positions ───────────────────────────────────
  check(http.get(`${BASE}/api/model/test-positions`), {
    "test-positions 200":       (r) => r.status === 200,
    "test-positions not empty": (r) => r.json("positions").length > 0,
  });

  sleep(0.5);
}
