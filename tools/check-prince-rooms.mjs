#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const roomDataPath = path.join(
  root,
  "versions",
  "mc26_1_2",
  "src",
  "main",
  "resources",
  "kung-dungeon-scans",
  "known-rooms.json"
);

const expectedPrinceRooms = [
  "Big Red Flag",
  "Bridges",
  "Chambers",
  "Doors",
  "Flags",
  "Grass Ruin",
  "Leaves",
  "Market",
  "Pirate",
  "Quartz Knight",
  "Red Blue",
  "Skull",
  "Sloth",
  "Supertall",
  "Waterfall",
  "Withermancer",
];

const key = (name) => name.toLowerCase().replace(/[^a-z0-9]/g, "");
const database = JSON.parse(fs.readFileSync(roomDataPath, "utf8"));
const rooms = (database.rooms ?? []).slice().sort((a, b) => a.name.localeCompare(b.name));
const roomsByKey = new Map(rooms.map((room) => [key(room.name), room]));
const expectedPrinceKeys = new Set(expectedPrinceRooms.map(key));
const failures = [];

for (const room of rooms) {
  if (typeof room.prince !== "boolean") {
    failures.push(`Room is missing boolean prince field: ${room.name}`);
  }
}

for (const expected of expectedPrinceRooms) {
  const room = roomsByKey.get(key(expected));
  if (!room) {
    failures.push(`Expected Prince room is missing from known room data: ${expected}`);
  } else if (room.prince !== true) {
    failures.push(`Expected Prince room is marked prince=false: ${room.name}`);
  }
}

const unexpectedPrince = rooms
  .filter((room) => room.prince === true && !expectedPrinceKeys.has(key(room.name)))
  .map((room) => room.name);
if (unexpectedPrince.length) {
  failures.push(`Unexpected Prince rooms: ${unexpectedPrince.join(", ")}`);
}

const princeRooms = rooms.filter((room) => room.prince === true).map((room) => room.name);

console.log(`Known rooms classified: ${rooms.length}`);
console.log(`Prince rooms: ${princeRooms.length}`);
for (const room of princeRooms) {
  console.log(`  ${room}`);
}
console.log(`Non-Prince rooms: ${rooms.length - princeRooms.length}`);
console.log(`Andesite: ${roomsByKey.get(key("Andesite"))?.prince ? "Prince" : "non-Prince"}`);

if (failures.length) {
  console.error("");
  console.error("Prince room audit failed:");
  for (const failure of failures) {
    console.error(`  ${failure}`);
  }
  process.exitCode = 1;
}
