#!/usr/bin/env node
import http from "node:http";
import fs from "node:fs/promises";
import path from "node:path";

const port = Number(process.env.PORT || process.env.KUNG_ROOM_SYNC_PORT || 8765);
const dataFile = path.resolve(process.env.KUNG_ROOM_DATA_PATH || "room-sync-data/known-rooms.json");
const token = process.env.KUNG_ROOM_SYNC_TOKEN || "";
const maxBodyBytes = 512 * 1024;
const liveRunTtlMillis = Number(process.env.KUNG_LIVE_ROOM_SYNC_TTL_MS || 180_000);
const liveRuns = new Map();

const server = http.createServer(async (request, response) => {
  try {
    if (request.method === "GET" && request.url === "/health") {
      return json(response, 200, { ok: true });
    }
    if (request.method === "GET" && request.url === "/rooms") {
      if (!authorized(request)) {
        return json(response, 401, { error: "unauthorized" });
      }
      return json(response, 200, await readDatabase());
    }
    if (request.method === "POST" && request.url === "/rooms/report") {
      if (!authorized(request)) {
        return json(response, 401, { error: "unauthorized" });
      }
      const report = JSON.parse(await readBody(request));
      const result = await mergeRoomReport(report);
      return json(response, 200, result);
    }
    if (request.method === "POST" && request.url === "/runs/live/report") {
      if (!authorized(request)) {
        return json(response, 401, { error: "unauthorized" });
      }
      const report = JSON.parse(await readBody(request));
      const result = mergeLiveRunReport(report);
      return json(response, 200, result);
    }
    if (request.method === "GET" && request.url.startsWith("/runs/live?")) {
      if (!authorized(request)) {
        return json(response, 401, { error: "unauthorized" });
      }
      const url = new URL(request.url, "http://localhost");
      return json(response, 200, liveRunSnapshot(
        String(url.searchParams.get("runKey") || ""),
        String(url.searchParams.get("player") || "")
      ));
    }
    return json(response, 404, { error: "not found" });
  } catch (error) {
    console.error(error);
    return json(response, 500, { error: error.message || "server error" });
  }
});

server.listen(port, () => {
  console.log(`Kung room sync server listening on :${port}`);
  console.log(`Data file: ${dataFile}`);
  console.log(token ? "Auth: bearer token required" : "Auth: disabled");
});

function authorized(request) {
  if (!token) {
    return true;
  }
  return request.headers.authorization === `Bearer ${token}`;
}

function json(response, status, value) {
  response.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
  });
  response.end(`${JSON.stringify(value, null, 2)}\n`);
}

async function readBody(request) {
  const chunks = [];
  let bytes = 0;
  for await (const chunk of request) {
    bytes += chunk.length;
    if (bytes > maxBodyBytes) {
      throw new Error("request body too large");
    }
    chunks.push(chunk);
  }
  return Buffer.concat(chunks).toString("utf8");
}

async function readDatabase() {
  try {
    const text = await fs.readFile(dataFile, "utf8");
    const database = JSON.parse(text.replace(/^\uFEFF/, ""));
    if (!database || !Array.isArray(database.rooms)) {
      throw new Error("known-rooms.json must contain a rooms array");
    }
    return database;
  } catch (error) {
    if (error.code === "ENOENT") {
      return { schema: 1, rooms: [] };
    }
    throw error;
  }
}

async function writeDatabase(database) {
  await fs.mkdir(path.dirname(dataFile), { recursive: true });
  await fs.writeFile(dataFile, `${JSON.stringify(database, null, 2)}\n`, "utf8");
}

async function mergeRoomReport(report) {
  validateReport(report);
  const database = await readDatabase();
  database.schema = 1;
  database.rooms ||= [];

  const room = findOrCreateRoom(database, report);
  room.crypts = Math.max(Number(room.crypts || 0), Number(report.crypts || 0));

  const incoming = variantFromReport(report);
  const existing = room.variants.find((variant) => sameShape(variant, incoming));
  if (existing) {
    mergeVariant(existing, incoming);
  } else {
    room.variants.push(incoming);
  }

  sortDatabase(database);
  await writeDatabase(database);
  return {
    ok: true,
    name: report.name,
    type: report.type,
    secrets: report.secrets,
    cells: incoming.components.length,
  };
}

function mergeLiveRunReport(report) {
  validateLiveRunReport(report);
  pruneLiveRuns();

  const runKey = sourceKey(report.runKey, 120);
  const player = sourceKey(report.player, 32);
  let run = liveRuns.get(runKey);
  if (!run) {
    run = { runKey, updatedAt: 0, clients: new Map() };
    liveRuns.set(runKey, run);
  }

  const now = Date.now();
  const rooms = report.rooms
    ? report.rooms
    .map(liveRoomFromReport)
    .filter(Boolean)
    .slice(0, 36)
    : [];
  const doors = Array.isArray(report.doors)
    ? report.doors
      .map(liveDoorFromReport)
      .filter(Boolean)
      .slice(0, 60)
    : [];
  const players = Array.isArray(report.players)
    ? report.players
      .map(livePlayerFromReport)
      .filter(Boolean)
      .slice(0, 5)
    : [];
  run.clients.set(player.toLowerCase(), {
    player,
    updatedAt: Number(report.createdAt || now),
    rooms,
    doors,
    players,
  });
  run.updatedAt = now;
  return { ok: true, runKey, player, rooms: rooms.length, doors: doors.length, players: players.length };
}

function liveRunSnapshot(runKeyValue, playerValue) {
  pruneLiveRuns();
  const runKey = sourceKey(runKeyValue, 120);
  const requester = sourceKey(playerValue, 32).toLowerCase();
  const run = liveRuns.get(runKey);
  if (!run) {
    return { schema: 1, runKey, clients: [] };
  }
  return {
    schema: 1,
    runKey,
    updatedAt: run.updatedAt,
    clients: [...run.clients.values()]
      .filter(client => client.player.toLowerCase() !== requester)
      .map(client => ({
        player: client.player,
        updatedAt: client.updatedAt,
        rooms: client.rooms,
        doors: client.doors || [],
        players: client.players || [],
      })),
  };
}

function validateLiveRunReport(report) {
  if (!report || report.kind !== "kung-live-room-sync") {
    throw new Error("expected kind=kung-live-room-sync");
  }
  if (!report.runKey || typeof report.runKey !== "string") {
    throw new Error("live report needs a runKey");
  }
  if (!report.player || typeof report.player !== "string") {
    throw new Error("live report needs a player");
  }
  if (report.rooms !== undefined && !Array.isArray(report.rooms)) {
    throw new Error("live report rooms must be an array");
  }
  if (report.doors !== undefined && !Array.isArray(report.doors)) {
    throw new Error("live report doors must be an array");
  }
  if (report.players !== undefined && !Array.isArray(report.players)) {
    throw new Error("live report players must be an array");
  }
  if (!Array.isArray(report.rooms) && !Array.isArray(report.doors) && !Array.isArray(report.players)) {
    throw new Error("live report needs rooms, doors, or players");
  }
}

function liveRoomFromReport(room) {
  if (!room || !Number.isInteger(Number(room.roomGridX)) || !Number.isInteger(Number(room.roomGridZ))) {
    return null;
  }
  const roomGridX = Number(room.roomGridX);
  const roomGridZ = Number(room.roomGridZ);
  if (roomGridX < 0 || roomGridZ < 0 || roomGridX > 5 || roomGridZ > 5) {
    return null;
  }
  const name = String(room.name || "").slice(0, 80);
  const type = String(room.type || "UNKNOWN").replace(/[^A-Z_]/g, "").slice(0, 20) || "UNKNOWN";
  return {
    roomGridX,
    roomGridZ,
    name,
    type,
    secrets: boundedInteger(room.secrets, 0, 99),
    crypts: boundedInteger(room.crypts, 0, 99),
    roomSecretsFound: boundedInteger(room.roomSecretsFound, 0, 99),
    roomSecretsMax: boundedInteger(room.roomSecretsMax, 0, 99),
    visited: Boolean(room.visited),
    cleared: Boolean(room.cleared),
    completed: Boolean(room.completed),
  };
}

function liveDoorFromReport(door) {
  if (!door || !Number.isInteger(Number(door.scanGridX)) || !Number.isInteger(Number(door.scanGridZ))) {
    return null;
  }
  const scanGridX = Number(door.scanGridX);
  const scanGridZ = Number(door.scanGridZ);
  if (scanGridX < 0 || scanGridZ < 0 || scanGridX > 10 || scanGridZ > 10 || (scanGridX % 2) === (scanGridZ % 2)) {
    return null;
  }
  const kind = String(door.kind || "NONE").replace(/[^A-Z_]/g, "").slice(0, 20) || "NONE";
  const targetType = String(door.targetType || "UNKNOWN").replace(/[^A-Z_]/g, "").slice(0, 20) || "UNKNOWN";
  return {
    scanGridX,
    scanGridZ,
    kind,
    targetType,
    targetVisited: Boolean(door.targetVisited),
  };
}

function livePlayerFromReport(player) {
  if (!player) {
    return null;
  }
  const name = String(player.name || "").replace(/[^A-Za-z0-9_]/g, "").slice(0, 16);
  if (name.length < 3) {
    return null;
  }
  return {
    name,
    secretsFound: boundedInteger(player.secretsFound, 0, 250),
    deaths: boundedInteger(player.deaths, 0, 99),
  };
}

function pruneLiveRuns() {
  const cutoff = Date.now() - liveRunTtlMillis;
  for (const [runKey, run] of liveRuns) {
    for (const [clientKey, client] of run.clients) {
      if (Number(client.updatedAt || 0) < cutoff) {
        run.clients.delete(clientKey);
      }
    }
    if (run.clients.size === 0 || Number(run.updatedAt || 0) < cutoff) {
      liveRuns.delete(runKey);
    }
  }
}

function validateReport(report) {
  if (!report || report.kind !== "kung-room-export") {
    throw new Error("expected kind=kung-room-export");
  }
  if (!report.name || typeof report.name !== "string") {
    throw new Error("report needs a room name");
  }
  if (!report.type || typeof report.type !== "string") {
    throw new Error("report needs a room type");
  }
  if (!Number.isInteger(report.secrets) || report.secrets < 0) {
    throw new Error("report needs a non-negative secrets value");
  }
  if (!Array.isArray(report.cells) || report.cells.length < 1 || report.cells.length > 4) {
    throw new Error("report must contain 1-4 cells");
  }
}

function findOrCreateRoom(database, report) {
  let room = database.rooms.find((candidate) =>
    eq(candidate.name, report.name)
      && candidate.type === report.type
      && Number(candidate.secrets) === Number(report.secrets)
  );
  if (!room) {
    room = {
      name: report.name,
      type: report.type,
      secrets: report.secrets,
      crypts: Number(report.crypts || 0),
      variants: [],
    };
    database.rooms.push(room);
  }
  room.variants ||= [];
  return room;
}

function variantFromReport(report) {
  const minDx = Math.min(...report.cells.map((cell) => Number(cell.dx)));
  const minDz = Math.min(...report.cells.map((cell) => Number(cell.dz)));
  const components = report.cells.map((cell) => ({
    dx: Number(cell.dx) - minDx,
    dz: Number(cell.dz) - minDz,
    hashes: hashesFromReportCell(cell, report),
  }));
  components.sort((a, b) => a.dz - b.dz || a.dx - b.dx);
  if (!connected(components)) {
    throw new Error("room report cells must be connected");
  }
  return { components };
}

function hashesFromReportCell(cell, report) {
  if (!Array.isArray(cell.hashes) || cell.hashes.length < 1) {
    throw new Error("each cell needs at least one hash");
  }
  const hashes = [];
  for (const hash of cell.hashes) {
    const core = Number(hash.coreHash ?? hash.core ?? 0);
    const stable = Number(hash.stableCoreHash ?? hash.stable ?? 0);
    if (!core && !stable) {
      continue;
    }
    const existing = hashes.find((candidate) => candidate.core === core && candidate.stable === stable);
    if (existing) {
      existing.seen += 1;
      existing.updatedAt = Math.max(existing.updatedAt, Number(report.createdAt || Date.now()));
      continue;
    }
    hashes.push({
      core,
      stable,
      seen: 1,
      updatedAt: Number(report.createdAt || Date.now()),
      source: sourceName(hash.source),
    });
  }
  if (hashes.length < 1) {
    throw new Error("each cell needs at least one non-zero hash");
  }
  return hashes;
}

function sourceName(source) {
  const value = String(source || "sync").replace(/[^a-zA-Z0-9_.-]/g, "-");
  return `homeserver-${value}`.slice(0, 80);
}

function sourceKey(source, maxLength) {
  return String(source || "")
    .replace(/[^a-zA-Z0-9_.:-]/g, "-")
    .slice(0, maxLength || 80);
}

function boundedInteger(value, min, max) {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return min;
  }
  return Math.max(min, Math.min(max, Math.trunc(number)));
}

function sameShape(first, second) {
  const firstCells = first.components.map(cellKey).sort();
  const secondCells = second.components.map(cellKey).sort();
  return firstCells.length === secondCells.length
    && firstCells.every((value, index) => value === secondCells[index]);
}

function mergeVariant(target, incoming) {
  for (const incomingComponent of incoming.components) {
    const targetComponent = target.components.find((component) => cellKey(component) === cellKey(incomingComponent));
    if (!targetComponent) {
      target.components.push(incomingComponent);
      continue;
    }
    targetComponent.hashes ||= [];
    for (const incomingHash of incomingComponent.hashes) {
      const targetHash = targetComponent.hashes.find((hash) =>
        Number(hash.core || 0) === incomingHash.core
          && Number(hash.stable || 0) === incomingHash.stable
      );
      if (targetHash) {
        targetHash.seen = Number(targetHash.seen || 1) + incomingHash.seen;
        targetHash.updatedAt = Math.max(Number(targetHash.updatedAt || 0), incomingHash.updatedAt);
      } else {
        targetComponent.hashes.push(incomingHash);
      }
    }
    targetComponent.hashes.sort((a, b) =>
      Number(b.stable || 0) - Number(a.stable || 0)
        || Number(a.core || 0) - Number(b.core || 0)
    );
  }
}

function sortDatabase(database) {
  for (const room of database.rooms) {
    room.variants.sort((a, b) => a.components.length - b.components.length);
    for (const variant of room.variants) {
      variant.components.sort((a, b) => a.dz - b.dz || a.dx - b.dx);
    }
  }
  database.rooms.sort((a, b) =>
    a.name.localeCompare(b.name)
      || a.type.localeCompare(b.type)
      || Number(a.secrets) - Number(b.secrets)
  );
}

function connected(components) {
  const cells = new Set(components.map(cellKey));
  const queue = [components[0]];
  const seen = new Set([cellKey(components[0])]);
  for (let index = 0; index < queue.length; index += 1) {
    const cell = queue[index];
    for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
      const key = `${cell.dx + dx},${cell.dz + dz}`;
      if (!cells.has(key) || seen.has(key)) {
        continue;
      }
      seen.add(key);
      queue.push({ dx: cell.dx + dx, dz: cell.dz + dz });
    }
  }
  return seen.size === cells.size;
}

function cellKey(cell) {
  return `${Number(cell.dx)},${Number(cell.dz)}`;
}

function eq(first, second) {
  return String(first).toLowerCase() === String(second).toLowerCase();
}
