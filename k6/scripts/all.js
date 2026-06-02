import { controllerTest } from "./controller.js";
import { modelTest }      from "./model.js";
import { playerTest }     from "./player.js";
import { multiGameTest }  from "./multi-game.js";
import { stockfishTest }  from "./stockfish.js";

export const options = {
  scenarios: {
    // ── Smoke (1 VU, 10s) ──────────────────────────────
    controller_smoke: { executor: "constant-vus", exec: "controller", vus: 1, duration: "10s", startTime: "0s",  tags: { phase: "smoke", service: "controller"    } },
    model_smoke:      { executor: "constant-vus", exec: "model",      vus: 1, duration: "10s", startTime: "0s",  tags: { phase: "smoke", service: "model"         } },
    player_smoke:     { executor: "constant-vus", exec: "player",     vus: 1, duration: "10s", startTime: "0s",  tags: { phase: "smoke", service: "playerservice" } },
    multigame_smoke:  { executor: "constant-vus", exec: "multigame",  vus: 1, duration: "10s", startTime: "0s",  tags: { phase: "smoke", service: "multigame"     } },
    stockfish_smoke:  { executor: "constant-vus", exec: "stockfish",  vus: 1, duration: "10s", startTime: "0s",  tags: { phase: "smoke", service: "stockfish"     } },

    // ── Load (konstant, 10 VUs, 30s) ───────────────────
    controller_load:  { executor: "constant-vus", exec: "controller", vus: 10, duration: "30s", startTime: "12s", tags: { phase: "load", service: "controller"    } },
    model_load:       { executor: "constant-vus", exec: "model",      vus: 10, duration: "30s", startTime: "12s", tags: { phase: "load", service: "model"         } },
    player_load:      { executor: "constant-vus", exec: "player",     vus: 10, duration: "30s", startTime: "12s", tags: { phase: "load", service: "playerservice" } },
    multigame_load:   { executor: "constant-vus", exec: "multigame",  vus: 10, duration: "30s", startTime: "12s", tags: { phase: "load", service: "multigame"     } },
    // Stockfish ist Single-threaded → 5 VUs genügen
    stockfish_load:   { executor: "constant-vus", exec: "stockfish",  vus: 5,  duration: "30s", startTime: "12s", tags: { phase: "load", service: "stockfish"     } },

    // ── Ramping (Controller: 0→10→20→0 VUs) ────────────
    // Zeigt ab welcher Last die Latenz ansteigt
    controller_ramp: {
      executor: "ramping-vus",
      exec:     "controller",
      startVUs: 0,
      stages: [
        { duration: "10s", target: 10 },
        { duration: "20s", target: 10 },
        { duration: "10s", target: 20 },
        { duration: "20s", target: 20 },
        { duration:  "5s", target:  0 },
      ],
      startTime: "45s",
      tags: { phase: "ramp", service: "controller" },
    },
  },

  thresholds: {
    "http_req_failed":                                      ["rate<0.01"],
    "http_req_duration{service:controller,phase:load}":     ["p(95)<500"],
    "http_req_duration{service:model,phase:load}":          ["p(95)<500"],
    "http_req_duration{service:playerservice,phase:load}":  ["p(95)<500"],
    "http_req_duration{service:multigame,phase:load}":      ["p(95)<500"],
    "http_req_duration{service:stockfish,phase:load}":      ["p(95)<5000"],
    "http_req_duration{service:controller,phase:ramp}":     ["p(95)<800"],
  },
};

export function controller() { controllerTest(); }
export function model()      { modelTest(); }
export function player()     { playerTest(); }
export function multigame()  { multiGameTest(); }
export function stockfish()  { stockfishTest(); }
