import http from "k6/http";
import { check, sleep } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:3000";

export default function () { controllerTest(); }

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

const JSON_HEADERS = { "Content-Type": "application/json" };
const STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

export function controllerTest() {
  // ── New Game ─────────────────────────────────────────
  const newGame = http.post(`${BASE}/api/controller/new-game`, null, { headers: JSON_HEADERS });
  check(newGame, {
    "new-game 200":     (r) => r.status === 200,
    "new-game has fen": (r) => r.json("game.fen") !== undefined,
  });

  // ── State ────────────────────────────────────────────
  const state = http.get(`${BASE}/api/controller/state`);
  check(state, {
    "state 200":     (r) => r.status === 200,
    "state has fen": (r) => r.json("game.fen") !== undefined,
  });

  // ── Load FEN ─────────────────────────────────────────
  const loadFen = http.post(
    `${BASE}/api/controller/load-fen`,
    JSON.stringify({ fen: STARTING_FEN }),
    { headers: JSON_HEADERS },
  );
  check(loadFen, { "load-fen 200": (r) => r.status === 200 });

  // ── Make a Move (e2→e4) ──────────────────────────────
  // 422 = illegaler Zug, 409 = Spiel bereits beendet
  const move = http.post(
    `${BASE}/api/controller/move`,
    JSON.stringify({ from: "e2", to: "e4" }),
    { headers: JSON_HEADERS, responseCallback: http.expectedStatuses(200, 409, 422) },
  );
  check(move, { "move accepted": (r) => [200, 409, 422].includes(r.status) });

  // ── Move History ─────────────────────────────────────
  const history = http.get(`${BASE}/api/controller/move-history`);
  check(history, {
    "history 200":       (r) => r.status === 200,
    "history has moves": (r) => Array.isArray(r.json("moves")),
  });

  // ── Browse ───────────────────────────────────────────
  check(
    http.post(`${BASE}/api/controller/browse/back`, null, { headers: JSON_HEADERS }),
    { "browse/back 200": (r) => r.status === 200 },
  );
  check(
    http.post(`${BASE}/api/controller/browse/forward`, null, { headers: JSON_HEADERS }),
    { "browse/forward 200": (r) => r.status === 200 },
  );
  check(
    http.post(`${BASE}/api/controller/browse/to-start`, null, { headers: JSON_HEADERS }),
    { "browse/to-start 200": (r) => r.status === 200 },
  );
  check(
    http.post(`${BASE}/api/controller/browse/to-end`, null, { headers: JSON_HEADERS }),
    { "browse/to-end 200": (r) => r.status === 200 },
  );
  check(
    http.post(
      `${BASE}/api/controller/browse/to-move`,
      JSON.stringify({ index: 0 }),
      { headers: JSON_HEADERS },
    ),
    { "browse/to-move 200": (r) => r.status === 200 },
  );

  // ── Game Records ─────────────────────────────────────
  const games = http.get(`${BASE}/api/controller/games`);
  check(games, {
    "games 200":      (r) => r.status === 200,
    "games has list": (r) => Array.isArray(r.json("games")),
  });

  // ── Export ───────────────────────────────────────────
  const exp = http.get(`${BASE}/api/controller/export`);
  check(exp, { "export 200": (r) => r.status === 200 });

  // ── Resign (beendet das aktuelle Spiel) ──────────────
  const resign = http.post(`${BASE}/api/controller/resign`, null, { headers: JSON_HEADERS });
  check(resign, { "resign 200": (r) => r.status === 200 });

  // ── Replay exit (kein aktiver Replay – 200 erwartet) ─
  const replayExit = http.post(`${BASE}/api/controller/replay/exit`, null, { headers: JSON_HEADERS });
  check(replayExit, { "replay/exit 200": (r) => r.status === 200 });

  sleep(0.5);
}
