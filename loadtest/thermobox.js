// Load test of the ThermoBox REST server with k6 (https://k6.io): virtual users that log in and
// browse like the Angular client (overview, an element's page, its curves), more and more of them,
// to see how many the server holds. See loadtest/README.md.
//
//   k6 run loadtest/thermobox.js
//   k6 run -e BASE_URL=http://127.0.0.1:8080 -e SCENARIO=ramp -e USERS=usera,userb,userc loadtest/thermobox.js
//
// Options (-e NAME=value):
//   BASE_URL   server, http://127.0.0.1:8080 by default
//   USERS      accounts to log in with, separated by commas ("admin" by default), or a number N for the
//              N first accounts of DatabaseSeeder huge (usera, userb…)
//   PASSWORD   their password, password123 by default (the seeded accounts)
//   SCENARIO   ramp (default): 10 → 400 users by steps, to find the limit;
//              steady: VUS users during DURATION, to measure one load;
//              enterprise: a working day compressed (see below)
//   VUS, DURATION   for steady (50, 5m by default)
//   THINK      seconds a user reads a page before the next one, 2 by default (0: as fast as possible;
//              20 for enterprise)
//
// SCENARIO=enterprise: a whole company, for an hour (loadtest/README.md, « Scénario entreprise »):
//   EMPLOYEES  people with the client open at the busiest moment (1000), reached in RAMP (10m), kept
//              until DURATION (1h); each reads a page every THINK seconds and its client asks for the
//              notifications every 2 minutes, as the apps do
//   LOGINS     logins per second during the first LOGIN_PEAK (5/s for 3m): the morning, when everyone
//              arrives (BCrypt is slow on purpose: this is what it costs)
//   SETPOINTS  setpoints changed per minute by the MANAGER accounts among USERS (20)
//   The boxes that send their measurements are another program: loadtest/devices.mjs (MQTT).
//   MONITOR_USER, MONITOR_PASSWORD   ADMIN account that reads the CPU and memory of the server every
//              MONITOR_EVERY seconds (5) during the test (admin / PASSWORD by default); MONITOR=0: off

import http from 'k6/http';
import { check, group, sleep, fail } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 127.0.0.1 rather than localhost: on Windows, localhost can mean ::1 (IPv6) first, where another
// program may listen on the same port while the browser reaches the server through 127.0.0.1
const BASE = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const PASSWORD = __ENV.PASSWORD || 'password123';
const SCENARIO = __ENV.SCENARIO || 'ramp';
const ENTERPRISE = SCENARIO === 'enterprise';
const THINK = Number(__ENV.THINK ?? (ENTERPRISE ? 20 : 2));
// What the Angular client sends; k6 sends no Accept header by default
const ACCEPT = { Accept: 'application/json, text/plain, */*' };

function accounts() {
  const raw = __ENV.USERS || 'admin';
  if (/^\d+$/.test(raw)) {
    // usera, userb… z, aa, ab… like DatabaseSeeder
    const names = [];
    for (let n = 1; n <= Number(raw); n++) {
      let i = n, s = '';
      while (i > 0) { i--; s = String.fromCharCode(97 + (i % 26)) + s; i = Math.floor(i / 26); }
      names.push('user' + s);
    }
    return names;
  }
  return raw.split(',').map((s) => s.trim()).filter((s) => s);
}

const MONITOR = __ENV.MONITOR !== '0';
const MONITOR_EVERY = Number(__ENV.MONITOR_EVERY || 5);

const scenarios = {
  // Steps of 2 minutes: the step where the errors or the response times jump is the limit
  ramp: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 10 }, { duration: '1m30s', target: 10 },
      { duration: '30s', target: 50 }, { duration: '1m30s', target: 50 },
      { duration: '30s', target: 100 }, { duration: '1m30s', target: 100 },
      { duration: '30s', target: 200 }, { duration: '1m30s', target: 200 },
      { duration: '30s', target: 400 }, { duration: '1m30s', target: 400 },
      { duration: '30s', target: 0 },
    ],
    gracefulRampDown: '30s',
  },
  steady: {
    executor: 'constant-vus',
    vus: Number(__ENV.VUS || 50),
    duration: __ENV.DURATION || '5m',
  },
};

// SCENARIO=enterprise: the people (main scenario), the morning logins, the managers' setpoints
const EMPLOYEES = Number(__ENV.EMPLOYEES || 1000);
const RAMP = __ENV.RAMP || '10m';
const DAY = __ENV.DURATION || '1h';
const LOGINS = Number(__ENV.LOGINS ?? 5);
const LOGIN_PEAK = __ENV.LOGIN_PEAK || '3m';
const SETPOINTS = Number(__ENV.SETPOINTS ?? 20);
/** The clients ask for the setpoint notifications every 2 minutes (NotificationService of the client). */
const NOTIFY_EVERY = 120;
if (ENTERPRISE) {
  scenarios.enterprise = {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [{ duration: RAMP, target: EMPLOYEES }, { duration: `${Math.max(0, seconds(DAY) - seconds(RAMP))}s`, target: EMPLOYEES }],
    gracefulRampDown: '30s',
  };
}

/** How long a scenario lasts, in seconds ("1m30s" → 90). */
function seconds(text) {
  let total = 0;
  for (const [, n, unit] of String(text).matchAll(/(\d+(?:\.\d+)?)(h|m|s)/g)) total += Number(n) * { h: 3600, m: 60, s: 1 }[unit];
  return total;
}
const main = scenarios[SCENARIO] || fail(`SCENARIO=${SCENARIO}: ramp, steady or enterprise`);
const mainSeconds = main.stages ? main.stages.reduce((t, s) => t + seconds(s.duration), 0) : seconds(main.duration);

export const options = {
  scenarios: {
    [SCENARIO]: main,
    // One more user, apart: reads the load of the server during the whole test
    ...(MONITOR ? { monitor: { executor: 'constant-vus', vus: 1, duration: `${mainSeconds}s`, exec: 'monitor' } } : {}),
    ...(ENTERPRISE && LOGINS > 0
      ? { morning: { executor: 'constant-arrival-rate', rate: LOGINS, timeUnit: '1s', duration: LOGIN_PEAK, preAllocatedVUs: Math.max(5, LOGINS * 4), maxVUs: Math.max(20, LOGINS * 20), exec: 'login' } }
      : {}),
    ...(ENTERPRISE && SETPOINTS > 0
      ? { managers: { executor: 'constant-arrival-rate', rate: SETPOINTS, timeUnit: '1m', duration: `${mainSeconds}s`, preAllocatedVUs: 2, maxVUs: 20, exec: 'manager' } }
      : {}),
  },
  // Hundreds of accounts log in during setup: more than the 60 s k6 gives it by default
  setupTimeout: '15m',
  thresholds: {
    // Not stopping the test: they only mark the run as failed in the summary
    http_req_failed: ['rate<0.01'],
    'http_req_duration{page:overview}': ['p(95)<2000'],
    'http_req_duration{page:element}': ['p(95)<2000'],
    'http_req_duration{page:curves}': ['p(95)<3000'],
    // The two kinds of curves apart, to see which one is slow
    'http_req_duration{name:/api/v2/climate/averages}': ['p(95)<3000'],
    'http_req_duration{name:/api/v2/climate/{id}/history}': ['p(95)<3000'],
    // enterprise: what people wait for in a normal day, not at saturation
    ...(ENTERPRISE
      ? {
          'http_req_duration{page:notifications}': ['p(95)<2000'],
          'http_req_duration{page:login}': ['p(95)<5000'],
          'http_req_duration{page:setpoint}': ['p(95)<1000'],
        }
      : {}),
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
};

const serverErrors = new Counter('server_errors');

// Load of the server process (GET /api/v2/server/metrics), next to the response times in the summary
const cpuProcess = new Trend('server_cpu_process_percent');
const cpuSystem = new Trend('server_cpu_system_percent');
const heapUsed = new Trend('server_heap_used_mb');
const threads = new Trend('server_threads');
const gcPause = new Trend('server_gc_ms_per_interval');

/** Once, before the users start: the server answers, and every account logs in (BCrypt is slow on purpose). */
export function setup() {
  const info = http.get(`${BASE}/api/v2/server`, { headers: ACCEPT });
  if (info.status !== 200) {
    // Say what answers instead: an older ThermoBox, or something else on that port
    const v1 = http.get(`${BASE}/api/server`, { headers: ACCEPT });
    if (v1.status === 200) {
      fail(`${BASE} runs ThermoBox ${v1.json('version')} (APIs: ${v1.json('apiVersions')}), without /api/v2: ` +
        'this test needs 26.12 or later. In Docker, a version installed from the interface wins over the image: ' +
        'see loadtest/README.md, "Le serveur répond 404".');
    }
    if (info.status === 0) fail(`Nothing answers at ${BASE}: is the server started, on this port?`);
    // Who answered: its Server header and the start of its page tell another program apart
    const body = String(info.body || '').replace(/\s+/g, ' ').slice(0, 200);
    fail(`${BASE}/api/v2/server answers ${info.status} (Server: ${info.headers['Server'] || '?'}, ` +
      `address ${info.remote_ip}:${info.remote_port}, body: ${body || 'empty'}), /api/server ${v1.status}: ` +
      'a ThermoBox older than 26.11, or another program on this port? See loadtest/README.md, "Le serveur répond 404".');
  }
  console.log(`ThermoBox ${info.json('version')} at ${BASE}, scenario ${SCENARIO}`);

  // A few at once: one after the other, 1000 accounts would take minutes of BCrypt
  const tokens = [];
  const managers = [];
  const names = accounts();
  for (let i = 0; i < names.length; i += 16) {
    const part = names.slice(i, i + 16);
    const answers = http.batch(part.map((username) => ['POST', `${BASE}/api/v2/auth/login`, JSON.stringify({ username, password: PASSWORD }), {
      headers: { ...ACCEPT, 'Content-Type': 'application/json' }, tags: { page: 'setup' },
    }]));
    answers.forEach((r, j) => {
      if (r.status !== 200) return console.warn(`login ${part[j]}: ${r.status}`);
      tokens.push(r.json('token'));
      if (/^(ADMIN|MANAGER)$/.test(String(r.json('user.type')))) managers.push(r.json('token'));
    });
  }
  if (tokens.length === 0) fail('No account could log in: check USERS and PASSWORD');
  console.log(`${tokens.length} account(s) logged in${ENTERPRISE ? `, ${managers.length} of them MANAGER or ADMIN` : ''}`);
  if (ENTERPRISE) {
    console.log(`Enterprise: ${EMPLOYEES} people in ${RAMP}, for ${DAY}, a page every ${THINK} s; ` +
      `${LOGINS} login(s)/s for ${LOGIN_PEAK}; ${SETPOINTS} setpoint(s)/min. The boxes: node loadtest/devices.mjs`);
    if (SETPOINTS > 0 && managers.length === 0) console.warn('No MANAGER among USERS: no setpoint will be changed (USERS=10 or more for the huge set)');
  }
  return { tokens, managers, monitorToken: MONITOR ? monitorLogin() : null };
}

/** The ADMIN account of the monitor, if it can read /api/v2/server/metrics. */
function monitorLogin() {
  const username = __ENV.MONITOR_USER || 'admin';
  const password = __ENV.MONITOR_PASSWORD || PASSWORD;
  const r = http.post(`${BASE}/api/v2/auth/login`, JSON.stringify({ username, password }), {
    headers: { ...ACCEPT, 'Content-Type': 'application/json' }, tags: { page: 'monitor' },
  });
  const token = r.status === 200 ? r.json('token') : null;
  const m = token ? http.get(`${BASE}/api/v2/server/metrics`, { headers: { ...ACCEPT, Authorization: `Bearer ${token}` }, tags: { page: 'monitor' } }) : null;
  if (!m || m.status !== 200) {
    console.warn(`Server load not measured: ${username} must be an ADMIN of a server 26.12 or later ` +
      `(login ${r.status}${m ? `, metrics ${m.status}` : ''}); MONITOR_USER / MONITOR_PASSWORD to change it`);
    return null;
  }
  console.log(`Server load read every ${MONITOR_EVERY} s (${m.json('processors')} processor(s), heap max ${m.json('heapMaxMb')} MB)`);
  return token;
}

let lastGc = null;

/** The monitor user: the CPU and memory of the server every MONITOR_EVERY seconds. */
export function monitor(data) {
  if (!data.monitorToken) return sleep(mainSeconds);
  const r = http.get(`${BASE}/api/v2/server/metrics`, {
    headers: { ...ACCEPT, Authorization: `Bearer ${data.monitorToken}` }, tags: { page: 'monitor' },
  });
  if (r.status === 200) {
    const m = r.json();
    if (m.processCpu != null) cpuProcess.add(m.processCpu);
    if (m.systemCpu != null) cpuSystem.add(m.systemCpu);
    heapUsed.add(m.heapUsedMb);
    threads.add(m.threads);
    if (lastGc != null) gcPause.add(m.gcTimeMs - lastGc);
    lastGc = m.gcTimeMs;
  }
  sleep(MONITOR_EVERY);
}

function get(path, token, page) {
  const r = http.get(`${BASE}${path}`, {
    headers: { ...ACCEPT, Authorization: `Bearer ${token}`, 'Accept-Encoding': 'gzip' },
    // One series per kind of request, not per URL (ids and dates would make k6 keep thousands)
    tags: { page, name: path.split('?')[0].replace(/\/\d+/g, '/{id}') },
  });
  check(r, { [`${page}: 200`]: (x) => x.status === 200 });
  if (r.status >= 500 || r.status === 0) serverErrors.add(1, { page });
  if (r.status !== 200) reportError(r, page);
  return r;
}

// The first errors of each kind, in full (per simulated user: k6 shares nothing between them)
const reported = {};
function reportError(r, page) {
  const name = r.request.url.split('?')[0].replace(BASE, '').replace(/\/\d+/g, '/{id}');
  const key = `${r.status} ${name}`;
  reported[key] = (reported[key] || 0) + 1;
  if (reported[key] > 1) return;
  const why = r.status === 0 ? r.error : String(r.body || '').slice(0, 300);
  console.warn(`${page}: ${key} -> ${why}`);
}

const iso = (ms) => new Date(ms).toISOString();
/** « 2026-11-04T09:30:00 »: the local time the notifications take (a LocalDateTime of the server). */
const localIso = (ms) => {
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
};
const pick = (list) => list[Math.floor(Math.random() * list.length)];

/** When this simulated user's client last asked for the notifications (enterprise). */
let notifiedAt = 0;

/** One visit: the overview, then the page of an element with its curves, as the client does. */
export default function (data) {
  const token = data.tokens[(__VU - 1) % data.tokens.length];
  const now = Date.now();
  let nodes = [];

  // The client open in the background: its notifications every 2 minutes, since the last time
  if (ENTERPRISE && now - notifiedAt >= NOTIFY_EVERY * 1000) {
    const since = notifiedAt ? `?since=${encodeURIComponent(localIso(notifiedAt))}` : '';
    get(`/api/v2/notifications/setpoints${since}`, token, 'notifications');
    notifiedAt = now;
  }

  group('overview', () => {
    const tree = get('/api/v2/hierarchy/tree', token, 'overview');
    get('/api/v2/favorites', token, 'overview');
    get('/api/v2/settings', token, 'overview');
    nodes = tree.status === 200 ? (tree.json('nodes') || []).filter((n) => n.accessible) : [];
    // Sparklines of the dashboard: 24 h of up to 50 rooms
    const rooms = nodes.filter((n) => n.kind === 'ROOM').slice(0, 50).map((n) => n.id);
    if (rooms.length) {
      get(`/api/v2/climate/averages?ids=${rooms.join(',')}&start=${iso(now - 86400e3)}&end=${iso(now)}&points=60`, token, 'curves');
    }
  });
  sleep(THINK * (0.5 + Math.random()));

  if (nodes.length === 0) return;
  group('element', () => {
    const e = pick(nodes.filter((n) => n.kind !== 'DEVICE').length ? nodes.filter((n) => n.kind !== 'DEVICE') : nodes);
    get(`/api/v2/hierarchy/elements/${e.id}`, token, 'element');
    // Its curves over 7 days, as wide as a screen
    get(`/api/v2/climate/${e.id}/history?start=${iso(now - 7 * 86400e3)}&end=${iso(now)}&points=1000`, token, 'curves');
  });
  sleep(THINK * (0.5 + Math.random()));
}

// ---------------------------------------------------------------- enterprise

const everyone = ENTERPRISE ? accounts() : [];

/** The morning: someone arrives and logs in (scenario morning, LOGINS per second). */
export function login() {
  const username = pick(everyone);
  const r = http.post(`${BASE}/api/v2/auth/login`, JSON.stringify({ username, password: PASSWORD }), {
    headers: { ...ACCEPT, 'Content-Type': 'application/json' }, tags: { page: 'login', name: '/api/v2/auth/login' },
  });
  check(r, { 'login: 200': (x) => x.status === 200 });
  if (r.status >= 500 || r.status === 0) serverErrors.add(1, { page: 'login' });
  if (r.status !== 200) reportError(r, 'login');
}

/** Rooms each manager may change (read once per simulated user). */
let managed = null;

/** A manager changes the setpoint of one of its rooms (scenario managers, SETPOINTS per minute). */
export function manager(data) {
  if (!data.managers.length) return;
  const token = data.managers[(__VU - 1) % data.managers.length];
  if (managed == null) {
    const tree = get('/api/v2/hierarchy/tree', token, 'setpoint');
    managed = tree.status === 200 ? (tree.json('nodes') || []).filter((n) => n.accessible && n.kind === 'ROOM').map((n) => n.id) : [];
  }
  if (!managed.length) return;
  // 19 to 23 °C by half degrees: inside the default limits
  const value = 19 + Math.floor(Math.random() * 9) / 2;
  const r = http.put(`${BASE}/api/v2/roomtemps/room/${pick(managed)}/setpoint?value=${value}`, null, {
    headers: { ...ACCEPT, Authorization: `Bearer ${token}` }, tags: { page: 'setpoint', name: '/api/v2/roomtemps/room/{id}/setpoint' },
  });
  check(r, { 'setpoint: 200': (x) => x.status === 200 });
  if (r.status >= 500 || r.status === 0) serverErrors.add(1, { page: 'setpoint' });
  if (r.status !== 200) reportError(r, 'setpoint');
}
