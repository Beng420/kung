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
const catalogPath = path.join(
  root,
  "versions",
  "mc26_1_2",
  "src",
  "main",
  "java",
  "com",
  "github",
  "beng420",
  "kung",
  "feature",
  "dungeon",
  "DungeonKnownRoomCatalog.java"
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
const expectedPrinceAliases = [
  "Draw Bridge",
  "Red-Blue",
  "Super Tall",
  "Withermancers",
];

const key = (name) => name.toLowerCase().replace(/[^a-z0-9]/g, "");
const knownRooms = JSON.parse(fs.readFileSync(roomDataPath, "utf8")).rooms
  .map((room) => room.name.replace(/^"|"$/g, ""))
  .sort((a, b) => a.localeCompare(b));
const knownKeys = new Set(knownRooms.map(key));
const catalog = fs.readFileSync(catalogPath, "utf8");

function readNameSet(constName) {
  const match = catalog.match(new RegExp(`${constName} = canonicalNameSet\\(([\\s\\S]*?)\\);`));
  if (!match) {
    throw new Error(`Missing ${constName} in DungeonKnownRoomCatalog.java`);
  }
  return [...match[1].matchAll(/"([^"]+)"/g)].map((item) => item[1]);
}

const princeRooms = readNameSet("PRINCE_ROOM_NAMES");
const nonPrinceRooms = readNameSet("NON_PRINCE_ROOM_NAMES");
const princeKeys = new Set(princeRooms.map(key));
const nonPrinceKeys = new Set(nonPrinceRooms.map(key));
const expectedPrinceKeys = new Set(expectedPrinceRooms.map(key));
const expectedPrinceWithAliases = new Set([...expectedPrinceRooms, ...expectedPrinceAliases].map(key));

const failures = [];

const actualPrinceKnownRooms = knownRooms
  .filter((room) => princeKeys.has(key(room)) && !nonPrinceKeys.has(key(room)))
  .sort((a, b) => a.localeCompare(b));
const missingExpectedPrince = expectedPrinceRooms.filter((room) => !princeKeys.has(key(room)));
const unexpectedPrince = princeRooms
  .filter((room) => !expectedPrinceWithAliases.has(key(room)))
  .sort((a, b) => a.localeCompare(b));
const missingClassification = knownRooms
  .filter((room) => !princeKeys.has(key(room)) && !nonPrinceKeys.has(key(room)));
const duplicateClassification = knownRooms
  .filter((room) => princeKeys.has(key(room)) && nonPrinceKeys.has(key(room)));
const missingKnownNonPrince = knownRooms
  .filter((room) => !expectedPrinceKeys.has(key(room)) && !nonPrinceKeys.has(key(room)));
const nonPrinceForExpectedPrince = expectedPrinceRooms.filter((room) => nonPrinceKeys.has(key(room)));
const nonPrinceUnknownRooms = nonPrinceRooms
  .filter((room) => !knownKeys.has(key(room)))
  .sort((a, b) => a.localeCompare(b));

if (missingExpectedPrince.length) {
  failures.push(`Missing expected Prince rooms: ${missingExpectedPrince.join(", ")}`);
}
if (unexpectedPrince.length) {
  failures.push(`Unexpected Prince rooms: ${unexpectedPrince.join(", ")}`);
}
if (missingClassification.length) {
  failures.push(`Rooms missing Prince classification: ${missingClassification.join(", ")}`);
}
if (duplicateClassification.length) {
  failures.push(`Rooms listed as both Prince and non-Prince: ${duplicateClassification.join(", ")}`);
}
if (missingKnownNonPrince.length) {
  failures.push(`Known non-Prince rooms missing from NON_PRINCE_ROOM_NAMES: ${missingKnownNonPrince.join(", ")}`);
}
if (nonPrinceForExpectedPrince.length) {
  failures.push(`Expected Prince rooms listed as non-Prince: ${nonPrinceForExpectedPrince.join(", ")}`);
}
if (nonPrinceUnknownRooms.length) {
  failures.push(`Non-Prince entries not found in active room data: ${nonPrinceUnknownRooms.join(", ")}`);
}

console.log(`Known rooms classified: ${knownRooms.length}`);
console.log(`Prince rooms: ${actualPrinceKnownRooms.length}`);
for (const room of actualPrinceKnownRooms) {
  console.log(`  ${room}`);
}
console.log(`Non-Prince rooms: ${knownRooms.length - actualPrinceKnownRooms.length}`);
console.log(`Andesite: ${princeKeys.has(key("Andesite")) && !nonPrinceKeys.has(key("Andesite")) ? "Prince" : "non-Prince"}`);

if (failures.length) {
  console.error("");
  console.error("Prince room audit failed:");
  for (const failure of failures) {
    console.error(`  ${failure}`);
  }
  process.exitCode = 1;
}
