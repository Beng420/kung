#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const inputPath = process.argv[2];

if (!inputPath) {
  console.error("Usage: node tools/analyze-room-data.mjs <known-rooms.json>");
  process.exit(2);
}

const file = path.resolve(inputPath);
const database = JSON.parse(fs.readFileSync(file, "utf8"));
const rooms = Array.isArray(database.rooms) ? database.rooms : [];
const byHash = new Map();
const sourceCounts = new Map();
let variantCount = 0;
let componentCount = 0;
let hashCount = 0;

for (const room of rooms) {
  const roomKey = `${room.name}|${room.type}|${room.secrets}`;
  const variants = Array.isArray(room.variants) ? room.variants : [];
  variantCount += variants.length;
  for (const variant of variants) {
    const components = Array.isArray(variant.components) ? variant.components : [];
    componentCount += components.length;
    for (const component of components) {
      const hashes = Array.isArray(component.hashes) ? component.hashes : [];
      for (const hash of hashes) {
        hashCount++;
        count(sourceCounts, hash.source || "unknown");
        addHash(byHash, "core", hash.core, roomKey, room, variant, component, hash);
        addHash(byHash, "stable", hash.stable, roomKey, room, variant, component, hash);
      }
    }
  }
}

const conflicts = [...byHash.entries()]
  .map(([key, entries]) => ({ key, entries, roomKeys: unique(entries.map((entry) => entry.roomKey)) }))
  .filter((entry) => entry.roomKeys.length > 1)
  .sort((a, b) => b.entries.length - a.entries.length || a.key.localeCompare(b.key));

const unstableRooms = rooms
  .map((room) => ({ room, stableHashes: stableHashCount(room), hashTotal: totalHashCount(room) }))
  .filter((entry) => entry.hashTotal > 0 && entry.stableHashes === 0)
  .sort((a, b) => roomName(a.room).localeCompare(roomName(b.room)));

const oneObservationRooms = rooms
  .map((room) => ({ room, oneSeen: oneSeenHashCount(room), hashTotal: totalHashCount(room) }))
  .filter((entry) => entry.hashTotal > 0 && entry.oneSeen === entry.hashTotal)
  .sort((a, b) => roomName(a.room).localeCompare(roomName(b.room)));

console.log(`File: ${file}`);
console.log(`Rooms: ${rooms.length}`);
console.log(`Variants: ${variantCount}`);
console.log(`Components: ${componentCount}`);
console.log(`Hashes: ${hashCount}`);
console.log(`Sources: ${[...sourceCounts.entries()].map(([source, total]) => `${source}=${total}`).join(", ") || "none"}`);
console.log("");
printSection("Hash conflicts across room keys", conflicts, (entry) =>
  `${entry.key}: ${entry.roomKeys.join(" ; ")}`
);
printSection("Rooms with only unstable hashes", unstableRooms, (entry) =>
  `${roomName(entry.room)} hashes=${entry.hashTotal}`
);
printSection("Rooms where every hash is seen once", oneObservationRooms, (entry) =>
  `${roomName(entry.room)} hashes=${entry.hashTotal}`
);

if (conflicts.length > 0) {
  process.exitCode = 1;
}

function addHash(map, kind, value, roomKey, room, variant, component, hash) {
  if (!Number.isInteger(value) || value === 0) {
    return;
  }
  const key = `${kind}:${value}`;
  const entries = map.get(key) || [];
  entries.push({ roomKey, room, variant, component, hash });
  map.set(key, entries);
}

function stableHashCount(room) {
  return allHashes(room).filter((hash) => Number.isInteger(hash.stable) && hash.stable !== 0).length;
}

function oneSeenHashCount(room) {
  return allHashes(room).filter((hash) => !Number.isInteger(hash.seen) || hash.seen <= 1).length;
}

function totalHashCount(room) {
  return allHashes(room).length;
}

function allHashes(room) {
  const hashes = [];
  for (const variant of room.variants || []) {
    for (const component of variant.components || []) {
      hashes.push(...(component.hashes || []));
    }
  }
  return hashes;
}

function printSection(title, rows, format) {
  console.log(`${title}: ${rows.length}`);
  for (const row of rows.slice(0, 40)) {
    console.log(`  ${format(row)}`);
  }
  if (rows.length > 40) {
    console.log(`  ... ${rows.length - 40} more`);
  }
  console.log("");
}

function count(map, key) {
  map.set(key, (map.get(key) || 0) + 1);
}

function unique(values) {
  return [...new Set(values)];
}

function roomName(room) {
  return `${room.name}|${room.type}|${room.secrets}`;
}
