#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const modules = ["mc26_1_2", "mc26_2"];

// Source: https://hypixelskyblock.minecraft.wiki/w/Catacombs_Rooms
// Princes are included in the crypt total on the wiki and are not added again.
const wikiCrypts = new Map(Object.entries({
  "Entrance": 0,
  "Mines": 11,
  "Doors": 7,
  "Locked Away": 1,
  "Beams": 2,
  "Hall": 0,
  "Banners": 1,
  "Andesite": 0,
  "Painting": 0,
  "Multicolored": 0,
  "Black Flag": 1,
  "Overgrown Chains": 1,
  "Chains": 0,
  "Golden Oasis": 1,
  "Redstone Warrior": 4,
  "Silvers Sword": 0,
  "Pedestal": 1,
  "Temple": 0,
  "Big Red Flag": 1,
  "Mural": 0,
  "Perch": 1,
  "End": 1,
  "Cage": 0,
  "Sloth": 1,
  "Steps": 1,
  "Granite": 0,
  "Crypt": 2,
  "Dueces": 6,
  "Red Green": 2,
  "Long Hall": 3,
  "Mirror": 0,
  "Quad Lava": 0,
  "Small Waterfall": 5,
  "Basement": 0,
  "Dip": 3,
  "Water": 0,
  "Dome": 2,
  "Scaffolding": 0,
  "Small Stairs": 1,
  "Tomioka": 0,
  "Mushroom": 0,
  "Slabs": 2,
  "Duncan": 0,
  "Logs": 0,
  "Cages": 0,
  "Skull": 1,
  "Balcony": 0,
  "Knight": 0,
  "Sarcophagus": 1,
  "Drop": 6,
  "Raccoon": 2,
  "Double Diamond": 0,
  "Blue Skulls": 4,
  "Leaves": 1,
  "Withermancer": 6,
  "Redstone Key": 4,
  "Prison Cell": 1,
  "Cell": 0,
  "Arrow Trap": 1,
  "Bridges": 6,
  "Cobble Wall Pillar": 1,
  "Spikes": 2,
  "Gold": 0,
  "Chambers": 6,
  "Lava Ravine": 4,
  "Wizard": 8,
  "Overgrown": 0,
  "Jumping Skulls": 0,
  "Lots Of Floors": 1,
  "Purple Flags": 7,
  "Archway": 4,
  "Mage": 0,
  "Grass Ruin": 4,
  "Grand Library": 2,
  "Pit": 4,
  "Atlas": 5,
  "Layers": 3,
  "Waterfall": 3,
  "Pressure Plates": 6,
  "Carpets": 3,
  "Supertall": 6,
  "Gravel": 2,
  "Cathedral": 5,
  "Red Blue": 1,
  "Spider": 3,
  "Deathmite": 4,
  "Museum": 4,
  "Market": 4,
  "Melon": 4,
  "Mossy": 2,
  "Flags": 8,
  "Well": 5,
  "Hallway": 1,
  "Diagonal": 3,
  "Catwalk": 5,
  "Dino Site": 4,
  "Stairs": 1,
  "Quartz Knight": 9,
  "Rails": 1,
  "Vinny 8 Ball": 10,
  "Pillars": 0,
  "Sand Dragon": 1,
  "Tombstone": 0,
  "Stone Window": 1,
  "Lava Pit": 1,
  "Mini Rail Track": 3,
  "Trinity": 0,
  "Hanging Vines": 0,
  "Miniboss": 0,
  "King Midas": 1,
  "Shadow Assassin": 2,
  "Three Weirdos": 0,
  "Water Board": 0,
  "Ice Fill": 0,
  "Creeper Beams": 0,
  "Teleport Maze": 0,
  "Blaze": 0,
  "Quiz": 0,
  "Tic Tac Toe": 0,
  "Boulder": 0,
  "Ice Path": 0,
  "New Trap": 1,
  "Old Trap": 2,
  "Blood": 0,
  "Fairy": 0,
  "Altar": 3,
  "Pirate": 2,
  "Criss Cross": 6,
  "Ritual": 1,
  "Pipes": 9,
  "Slime": 1,
  "Redstone Crypt": 0,
  "Staircase": 2,
  "Zodd": 0,
}));

const confirmationNeeded = new Map(Object.entries({
  "Admin": 34,
  "Buttons": 21,
}));

let failed = false;
for (const moduleName of modules) {
  const file = path.join(
    root,
    "versions",
    moduleName,
    "src",
    "main",
    "resources",
    "kung-dungeon-scans",
    "known-rooms.json"
  );
  const rooms = JSON.parse(fs.readFileSync(file, "utf8")).rooms;
  const byName = new Map(rooms.map((room) => [room.name, room]));
  const mismatches = [];
  const missing = [];

  for (const [name, crypts] of wikiCrypts) {
    const room = byName.get(name);
    if (!room) {
      missing.push(name);
    } else if (room.crypts !== crypts) {
      mismatches.push(`${name}: db=${room.crypts} wiki=${crypts}`);
    }
  }

  const uncertain = [];
  for (const [name, wikiValue] of confirmationNeeded) {
    const room = byName.get(name);
    uncertain.push(`${name}: db=${room?.crypts ?? "missing"} wiki-confirm-needed=${wikiValue}`);
  }

  const extras = rooms
    .filter((room) => !wikiCrypts.has(room.name) && !confirmationNeeded.has(room.name))
    .map((room) => `${room.name}|${room.type}|${room.secrets}/${room.crypts}`);

  console.log(`${moduleName}:`);
  console.log(`  mismatches=${mismatches.length}`);
  for (const line of mismatches) {
    console.log(`    ${line}`);
  }
  console.log(`  missing=${missing.length}`);
  for (const line of missing) {
    console.log(`    ${line}`);
  }
  console.log("  confirmationNeeded:");
  for (const line of uncertain) {
    console.log(`    ${line}`);
  }
  console.log(`  nonWikiExtras=${extras.length}`);
  for (const line of extras) {
    console.log(`    ${line}`);
  }

  failed ||= mismatches.length > 0 || missing.length > 0;
}

if (failed) {
  process.exitCode = 1;
}
