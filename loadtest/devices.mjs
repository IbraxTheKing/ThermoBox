#!/usr/bin/env node
// The boxes of a load test: every box of the server sends a measurement every EVERY seconds over
// MQTT, as real boxes do (thermobox/temperatures/<id>), through the broker the server listens to
// (THERMOBOX_DRIVER=mqtt). k6 does not speak MQTT: this runs next to it, for the « entreprise »
// scenario (loadtest/README.md). Node 18 or later, nothing to install.
//
//   node loadtest/devices.mjs
//   node loadtest/devices.mjs --every 30 --duration 1h --broker mqtt://127.0.0.1:1883
//
// Options (--name value), or the environment variable in capitals (BROKER, EVERY…):
//   --base        the REST server, to list its boxes (http://127.0.0.1:8080)
//   --user, --password   an ADMIN account that sees every box (admin / password123)
//   --broker      mqtt://host:port of the broker (mqtt://127.0.0.1:1883), with --mqtt-user,
//                 --mqtt-password if it asks for them
//   --every       seconds between two measurements of a box (30)
//   --boxes       how many boxes send (all of them by default); more than the server has: the same
//                 boxes send several times per period, to play a larger site
//   --connections MQTT connections the boxes are shared on (50): a real site has one per box
//   --duration    how long (1h); Ctrl+C stops earlier
//
// Every minute it says how many measurements went out, and how long the server took to record
// one (a marked value sent to a box, then read back through the REST API): if that delay grows,
// the server no longer keeps up with the boxes.

import net from 'node:net';
import { setTimeout as wait } from 'node:timers/promises';

const args = process.argv.slice(2);
function option(name, fallback) {
  const i = args.indexOf(`--${name}`);
  if (i >= 0 && args[i + 1] !== undefined) return args[i + 1];
  return process.env[name.toUpperCase().replace(/-/g, '_')] ?? fallback;
}
function seconds(text) {
  if (/^\d+(\.\d+)?$/.test(String(text))) return Number(text);
  let total = 0;
  for (const [, n, unit] of String(text).matchAll(/(\d+(?:\.\d+)?)(h|m|s)/g)) total += Number(n) * { h: 3600, m: 60, s: 1 }[unit];
  return total;
}

const BASE = option('base', 'http://127.0.0.1:8080').replace(/\/$/, '');
const USER = option('user', 'admin');
const PASSWORD = option('password', 'password123');
const BROKER = new URL(option('broker', 'mqtt://127.0.0.1:1883'));
const MQTT_USER = option('mqtt-user', '');
const MQTT_PASSWORD = option('mqtt-password', '');
const EVERY = seconds(option('every', 30));
const BOXES = option('boxes', '') ? Number(option('boxes', '')) : null;
const CONNECTIONS = Number(option('connections', 50));
const DURATION = seconds(option('duration', '1h'));

// ---------------------------------------------------------------- MQTT 3.1.1, publish QoS 0 only

/** Remaining length of a packet (1 to 4 bytes). */
function varint(n) {
  const out = [];
  do {
    let b = n % 128;
    n = Math.floor(n / 128);
    if (n > 0) b |= 0x80;
    out.push(b);
  } while (n > 0);
  return Buffer.from(out);
}
const str = (s) => {
  const b = Buffer.from(s, 'utf8');
  const len = Buffer.alloc(2);
  len.writeUInt16BE(b.length);
  return Buffer.concat([len, b]);
};
const packet = (type, body) => Buffer.concat([Buffer.from([type]), varint(body.length), body]);

/** One MQTT connection; resolves once the broker accepted it (CONNACK). */
function connect(clientId) {
  return new Promise((resolve, reject) => {
    const socket = net.connect(Number(BROKER.port || 1883), BROKER.hostname);
    socket.setNoDelay(true);
    let flags = 0x02; // clean session
    const payload = [str(clientId)];
    if (MQTT_USER) (flags |= 0x80), payload.push(str(MQTT_USER));
    if (MQTT_PASSWORD) (flags |= 0x40), payload.push(str(MQTT_PASSWORD));
    const keepAlive = Buffer.from([0, 60]);
    socket.once('connect', () => socket.write(packet(0x10, Buffer.concat([str('MQTT'), Buffer.from([4, flags]), keepAlive, ...payload]))));
    socket.once('error', reject);
    socket.once('data', (d) => {
      if (d[0] !== 0x20 || d[3] !== 0) return reject(new Error(`${BROKER.host} refused the connection (code ${d[3]})`));
      socket.removeListener('error', reject);
      socket.on('error', (e) => console.warn(`MQTT ${clientId}: ${e.message}`));
      socket.on('data', () => {}); // PINGRESP
      const ping = setInterval(() => socket.write(Buffer.from([0xc0, 0])), 30_000);
      socket.on('close', () => clearInterval(ping));
      resolve(socket);
    });
  });
}

const publish = (socket, topic, value) => socket.write(packet(0x30, Buffer.concat([str(topic), Buffer.from(String(value))])));

// ---------------------------------------------------------------- the server

async function api(path, token, init = {}) {
  const r = await fetch(`${BASE}${path}`, { ...init, headers: { Accept: 'application/json', 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...init.headers } });
  if (!r.ok) throw new Error(`${init.method ?? 'GET'} ${path}: ${r.status} ${(await r.text()).slice(0, 200)}`);
  return r.status === 204 ? null : r.json();
}

const login = await api('/api/v2/auth/login', null, { method: 'POST', body: JSON.stringify({ username: USER, password: PASSWORD }) }).catch((e) => {
  console.error(`Login ${USER} at ${BASE}: ${e.message}`);
  process.exit(1);
});
const token = login.token;
const tree = await api('/api/v2/hierarchy/tree', token);
const devices = (tree.nodes ?? []).filter((n) => n.kind === 'DEVICE').map((n) => n.id);
if (!devices.length) {
  console.error(`No box on ${BASE} for ${USER}: an ADMIN of a seeded base (DatabaseSeeder huge)?`);
  process.exit(1);
}
const count = BOXES ?? devices.length;
/** The boxes that send, in order: past the real ones, the same again (a larger site). */
const boxes = Array.from({ length: count }, (_, i) => devices[i % devices.length]);

console.log(`${count} box(es) (${devices.length} on the server), a measurement every ${EVERY} s each: ` +
  `${(count / EVERY).toFixed(1)} measurement(s)/s, through ${BROKER.host} on ${Math.min(CONNECTIONS, count)} connection(s), for ${DURATION} s`);

const sockets = [];
for (let i = 0; i < Math.min(CONNECTIONS, count); i++) {
  sockets.push(await connect(`thermobox-loadtest-${process.pid}-${i}`).catch((e) => {
    console.error(`MQTT ${BROKER.host}: ${e.message}. Broker started (docker compose --profile mqtt up -d)?`);
    process.exit(1);
  }));
}

// ---------------------------------------------------------------- sending

/** Last value of each box: it drifts slowly around 21 °C, as a room does. */
const values = new Map();
const nextValue = (id) => {
  const v = values.get(id) ?? 19 + Math.random() * 4;
  const next = Math.min(26, Math.max(16, v + (Math.random() - 0.5) * 0.3));
  values.set(id, next);
  return Math.round(next * 100) / 100;
};

let sent = 0;
let sentThisMinute = 0;
const started = Date.now();
const end = started + DURATION * 1000;

// Each box at its own moment in the period, as real boxes are not in step
const tick = 250;
const perTick = count / ((EVERY * 1000) / tick);
let carry = 0;
let cursor = 0;
const sender = setInterval(() => {
  if (Date.now() >= end) return;
  carry += perTick;
  for (; carry >= 1; carry--) {
    const id = boxes[cursor % count];
    // The box being probed keeps its marked value until it is read back
    if (probing === id) { cursor++; continue; }
    publish(sockets[cursor % sockets.length], `thermobox/temperatures/${id}`, nextValue(id));
    cursor++;
    sent++;
    sentThisMinute++;
  }
}, tick);

// ---------------------------------------------------------------- does the server keep up?

/** The box whose marked value is awaited: the others go on sending, not this one. */
let probing = null;

/** A marked value (three decimals, the others have two) sent to one box, then read back. */
async function probe() {
  const id = devices[Math.floor(Math.random() * devices.length)];
  const value = Math.round((20 + Math.random()) * 100) / 100 + 0.005;
  probing = id;
  const sentAt = Date.now();
  publish(sockets[0], `thermobox/temperatures/${id}`, value.toFixed(3));
  try {
    while (Date.now() - sentAt < 60_000) {
      await wait(250);
      const latest = await api(`/api/v2/roomtemps/room/${id}/measured/latest`, token).catch(() => null);
      // Stored as a float: 21.375 may come back as 21.3750007
      if (latest && Math.abs((latest.value ?? 0) - value) < 0.002) return Date.now() - sentAt;
    }
    return null;
  } finally {
    probing = null;
  }
}

const report = setInterval(async () => {
  const minutes = ((Date.now() - started) / 60000).toFixed(0);
  const delay = await probe();
  console.log(`${minutes} min: ${sentThisMinute} measurement(s) in the last minute (${(sentThisMinute / 60).toFixed(1)}/s), ${sent} in all; ` +
    (delay == null ? 'a measurement sent was not recorded after 60 s: the server does not keep up (or does not listen: THERMOBOX_DRIVER=mqtt?)' : `recorded by the server in ${delay} ms`));
  sentThisMinute = 0;
}, 60_000);

async function stop() {
  clearInterval(sender);
  clearInterval(report);
  for (const s of sockets) s.end(Buffer.from([0xe0, 0])); // DISCONNECT
  console.log(`${sent} measurement(s) sent in ${((Date.now() - started) / 1000).toFixed(0)} s`);
  process.exit(0);
}
process.on('SIGINT', stop);
setTimeout(stop, DURATION * 1000);
