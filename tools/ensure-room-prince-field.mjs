#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const file = process.argv[2];
if (!file) {
  console.error("Usage: node tools/ensure-room-prince-field.mjs <known-rooms.json>");
  process.exit(2);
}

const legacyPrinceRoomKeys = new Set([
  "Big Red Flag",
  "Bridges",
  "Draw Bridge",
  "Chambers",
  "Doors",
  "Flags",
  "Grass Ruin",
  "Leaves",
  "Market",
  "Pirate",
  "Quartz Knight",
  "Red Blue",
  "Red-Blue",
  "Skull",
  "Sloth",
  "Super Tall",
  "Supertall",
  "Waterfall",
  "Withermancer",
  "Withermancers",
].map(roomNameKey));

const resolved = path.resolve(file);
const database = JSON.parse(fs.readFileSync(resolved, "utf8").replace(/^\uFEFF/, ""));
let updated = 0;

for (const room of database.rooms ?? []) {
  if (typeof room.prince !== "boolean") {
    room.prince = legacyPrinceRoomKeys.has(roomNameKey(room.name));
    updated++;
  }
}

if (updated > 0) {
  fs.writeFileSync(resolved, `${JSON.stringify(database, null, 2)}\n`, "utf8");
}

console.log(`Prince fields present in ${resolved}; added ${updated}.`);

function roomNameKey(name) {
  return String(name).toLowerCase().replace(/[^a-z0-9]/g, "");
}
