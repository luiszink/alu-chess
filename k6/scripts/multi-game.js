import http from "k6/http";
import { check, sleep } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:3000";

export default function () { multiGameTest(); }

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

export function multiGameTest() {
  const gameId = `game_${__VU}_${Date.now()}`;

  // ── Active Games ─────────────────────────────────────
  check(http.get(`${BASE}/api/controller/games/active`), {
    "games/active 200":      (r) => r.status === 200,
    "games/active has list": (r) => Array.isArray(r.json("activeGames")),
  });

  // ── Activate Game ────────────────────────────────────
  const activate = http.post(
    `${BASE}/api/controller/game/${gameId}/activate`,
    JSON.stringify({ mode: "HvAI" }),
    { headers: JSON_HEADERS },
  );
  check(activate, {
    "activate 200":         (r) => r.status === 200,
    "activate has gameId":  (r) => r.json("gameId") === gameId,
  });

  // ── Per-Game State ───────────────────────────────────
  const state = http.get(`${BASE}/api/controller/game/${gameId}/state`);
  check(state, {
    "game state 200":     (r) => r.status === 200,
    "game state has fen": (r) => r.json("game.fen") !== undefined,
  });

  // ── Make a Move ──────────────────────────────────────
  const move = http.post(
    `${BASE}/api/controller/game/${gameId}/move`,
    JSON.stringify({ from: "e2", to: "e4" }),
    { headers: JSON_HEADERS, responseCallback: http.expectedStatuses(200, 409, 422) },
  );
  check(move, { "game move accepted": (r) => [200, 409, 422].includes(r.status) });

  // ── Move History ─────────────────────────────────────
  check(http.get(`${BASE}/api/controller/game/${gameId}/move-history`), {
    "game move-history 200":       (r) => r.status === 200,
    "game move-history has moves": (r) => Array.isArray(r.json("moves")),
  });

  // ── Browse ───────────────────────────────────────────
  check(
    http.post(`${BASE}/api/controller/game/${gameId}/browse/back`, null, { headers: JSON_HEADERS }),
    { "game browse/back 200": (r) => r.status === 200 },
  );
  check(
    http.post(`${BASE}/api/controller/game/${gameId}/browse/forward`, null, { headers: JSON_HEADERS }),
    { "game browse/forward 200": (r) => r.status === 200 },
  );
  check(
    http.post(`${BASE}/api/controller/game/${gameId}/browse/to-start`, null, { headers: JSON_HEADERS }),
    { "game browse/to-start 200": (r) => r.status === 200 },
  );
  check(
    http.post(`${BASE}/api/controller/game/${gameId}/browse/to-end`, null, { headers: JSON_HEADERS }),
    { "game browse/to-end 200": (r) => r.status === 200 },
  );
  check(
    http.post(
      `${BASE}/api/controller/game/${gameId}/browse/to-move`,
      JSON.stringify({ index: 0 }),
      { headers: JSON_HEADERS },
    ),
    { "game browse/to-move 200": (r) => r.status === 200 },
  );

  // ── New Game (innerhalb der Session) ─────────────────
  check(
    http.post(`${BASE}/api/controller/game/${gameId}/new-game`, null, { headers: JSON_HEADERS }),
    { "game new-game 200": (r) => r.status === 200 },
  );

  // ── Resign ───────────────────────────────────────────
  check(
    http.post(`${BASE}/api/controller/game/${gameId}/resign`, null, { headers: JSON_HEADERS }),
    { "game resign 200": (r) => r.status === 200 },
  );

  sleep(0.5);
}
