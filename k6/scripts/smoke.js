/**
 * Smoke-Test: prueft alle drei Services in einem Lauf durch nginx.
 * Ideal als schneller Sanity-Check nach dem Start.
 *
 * Ausfuehren:
 *   docker compose --profile k6 run --rm k6 run /scripts/smoke.js
 */
import http from "k6/http";
import { check, group } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:3000";

export const options = {
  vus:        1,
  iterations: 1,
  thresholds: {
    http_req_failed:   ["rate==0"],
    http_req_duration: ["p(95)<1000"],
  },
};

const JSON_HEADERS = { "Content-Type": "application/json" };
const STARTING_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

export default function () {

  group("Controller", () => {
    http.post(`${BASE}/api/controller/new-game`, null, { headers: JSON_HEADERS });

    const state = http.get(`${BASE}/api/controller/state`);
    check(state, {
      "state 200":        (r) => r.status === 200,
      "state has fen":    (r) => typeof r.json("game.fen") === "string",
    });

    const move = http.post(
      `${BASE}/api/controller/move`,
      JSON.stringify({ from: "e2", to: "e4" }),
      { headers: JSON_HEADERS, responseCallback: http.expectedStatuses(200, 409, 422) },
    );
    check(move, { "move accepted": (r) => [200, 409, 422].includes(r.status) });
  });

  group("Model", () => {
    const lm = http.post(
      `${BASE}/api/model/legal-moves`,
      JSON.stringify({ fen: STARTING_FEN }),
      { headers: JSON_HEADERS },
    );
    check(lm, {
      "legal-moves 200":        (r) => r.status === 200,
      "legal-moves count > 0":  (r) => r.json("moves").length > 0,
    });

    const vm = http.post(
      `${BASE}/api/model/validate-move`,
      JSON.stringify({ fen: STARTING_FEN, from: "e2", to: "e4" }),
      { headers: JSON_HEADERS },
    );
    check(vm, { "validate-move 200": (r) => r.status === 200 });
  });

  group("PlayerService", () => {
    const reg = http.post(
      `${BASE}/api/player/register`,
      JSON.stringify({ name: `SmokeTestPlayer_${Date.now()}` }),
      { headers: JSON_HEADERS },
    );
    check(reg, {
      "register 201":    (r) => r.status === 201,
      "register has id": (r) => typeof r.json("id") === "string",
    });
  });
}
