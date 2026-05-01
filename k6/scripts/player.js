import http from "k6/http";
import { check, sleep } from "k6";

const BASE = __ENV.BASE_URL || "http://localhost:3000";

export default function () { playerTest(); }

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

function registerPlayer(name) {
  const r = http.post(
    `${BASE}/api/player/register`,
    JSON.stringify({ name }),
    { headers: JSON_HEADERS },
  );
  check(r, {
    "register 201":    (res) => res.status === 201,
    "register has id": (res) => typeof res.json("id") === "string",
  });
  return r.status === 201 ? r.json("id") : null;
}

export function playerTest() {
  const ts = Date.now();

  // ── Waiting Sessions (vor Registrierung) ─────────────
  check(http.get(`${BASE}/api/player/sessions/waiting`), {
    "waiting sessions 200":      (r) => r.status === 200,
    "waiting sessions is array": (r) => Array.isArray(r.json("sessions")),
  });

  // ── HvAI Flow ────────────────────────────────────────
  const p1 = registerPlayer(`HvAI_${__VU}_${ts}`);
  if (!p1) { sleep(0.5); return; }

  check(http.get(`${BASE}/api/player/${p1}/status`), {
    "player status 200":        (r) => r.status === 200,
    "player status has player": (r) => r.json("player.id") === p1,
  });

  const hvai = http.post(
    `${BASE}/api/player/session/hvai`,
    JSON.stringify({ playerId: p1 }),
    { headers: JSON_HEADERS, responseCallback: http.expectedStatuses(201, 409) },
  );
  check(hvai, { "hvai session 201 or 409": (r) => [201, 409].includes(r.status) });

  if (hvai.status === 201) {
    const sid = hvai.json("id");

    check(http.get(`${BASE}/api/player/session/${sid}`), {
      "get session 200":    (r) => r.status === 200,
      "get session has id": (r) => r.json("id") === sid,
    });
  }

  // ── HvH Flow (zwei Spieler, einer erstellt, einer joint) ─
  const p2 = registerPlayer(`HvH_A_${__VU}_${ts}`);
  const p3 = registerPlayer(`HvH_B_${__VU}_${ts}`);
  if (!p2 || !p3) { sleep(0.5); return; }

  const hvh = http.post(
    `${BASE}/api/player/session/hvh`,
    JSON.stringify({ playerId: p2 }),
    { headers: JSON_HEADERS, responseCallback: http.expectedStatuses(201, 409) },
  );
  check(hvh, { "hvh session 201 or 409": (r) => [201, 409].includes(r.status) });

  if (hvh.status === 201) {
    const hvhId = hvh.json("id");

    const join = http.post(
      `${BASE}/api/player/session/${hvhId}/join`,
      JSON.stringify({ playerId: p3 }),
      { headers: JSON_HEADERS, responseCallback: http.expectedStatuses(200, 409) },
    );
    check(join, { "join session 200 or 409": (r) => [200, 409].includes(r.status) });
  }

  sleep(0.5);
}
