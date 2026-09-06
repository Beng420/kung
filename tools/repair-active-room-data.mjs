#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const profileName = process.argv[2] || "Dungeons 26.1.2";
const dir = path.join(process.env.APPDATA, "ModrinthApp", "profiles", profileName, "kung-dungeon-scans");
const jsonFile = path.join(dir, "known-rooms.json");
const typesFile = path.join(dir, "known-room-types.properties");
const stamp = new Date().toISOString().replace(/[:.]/g, "-");
const sourceJsonFiles = [
  path.resolve("versions/mc26_1_2/src/main/resources/kung-dungeon-scans/known-rooms.json"),
  path.resolve("versions/mc26_2/src/main/resources/kung-dungeon-scans/known-rooms.json"),
];
const canonicalRooms = new Map([
  ["Blaze", { type: "PUZZLE", secrets: 1, crypts: 0 }],
  ["Ice Path", { type: "PUZZLE", secrets: 1, crypts: 0 }],
]);
const maxTemplateComponents = 4;
const maxTemplateSpan = 4;

for (const file of [jsonFile, typesFile]) {
  if (fs.existsSync(file)) {
    fs.copyFileSync(file, `${file}.bak-safe-room-data-${stamp}`);
  }
}

const repairedFiles = [];
for (const file of [...sourceJsonFiles, jsonFile]) {
  if (!fs.existsSync(file)) {
    continue;
  }
  const database = JSON.parse(fs.readFileSync(file, "utf8").replace(/^\uFEFF/, ""));
  const result = repairDatabase(database);
  fs.writeFileSync(file, `${JSON.stringify(database, null, 2)}\n`, "utf8");
  repairedFiles.push({ file, ...result });
}

let removedTypeHints = 0;
if (fs.existsSync(typesFile)) {
  const lines = fs.readFileSync(typesFile, "utf8").split(/\r?\n/);
  const kept = lines.filter((line) => {
    const remove = /^93045045[09]=/.test(line.trim());
    if (remove) {
      removedTypeHints++;
    }
    return !remove;
  });
  fs.writeFileSync(typesFile, `${kept.join("\n").replace(/\n+$/, "")}\n`, "utf8");
}

console.log(`Profile: ${profileName}`);
for (const result of repairedFiles) {
  console.log(`Repaired: ${result.file}`);
  console.log(`  merged canonical rooms: ${result.mergedCanonicalRooms}`);
  console.log(`  removed suspicious variants: ${result.removedSuspiciousVariants}`);
}
console.log(`Removed bad type hints: ${removedTypeHints}`);
console.log(`Backup suffix: bak-safe-room-data-${stamp}`);

function repairDatabase(database) {
  const roomsByKey = new Map();
  let mergedCanonicalRooms = 0;
  let removedSuspiciousVariants = 0;
  for (const inputRoom of database.rooms || []) {
    const room = normalizeRoom(inputRoom);
    if (room !== inputRoom) {
      mergedCanonicalRooms++;
    }
    const key = roomKey(room);
    const existing = roomsByKey.get(key);
    if (existing) {
      existing.crypts = Math.max(Number(existing.crypts || 0), Number(room.crypts || 0));
      existing.variants.push(...(room.variants || []));
    } else {
      roomsByKey.set(key, {
        ...room,
        variants: [...(room.variants || [])],
      });
    }
  }

  for (const room of roomsByKey.values()) {
    const before = (room.variants || []).length;
    dedupeRoomVariants(room);
    removedSuspiciousVariants += before - room.variants.length;
  }

  database.rooms = [...roomsByKey.values()].sort(compareRooms);
  return { mergedCanonicalRooms, removedSuspiciousVariants };
}

function normalizeRoom(room) {
  const canonical = canonicalRooms.get(room.name);
  if (!canonical) {
    return room;
  }
  return {
    ...room,
    type: canonical.type,
    secrets: canonical.secrets,
    crypts: Math.max(Number(room.crypts || 0), canonical.crypts),
  };
}

function dedupeRoomVariants(room) {
  const variantsBySignature = new Map();
  for (const variant of room.variants || []) {
    const components = (variant.components || [])
      .map((component) => ({
        dx: Number(component.dx || 0),
        dz: Number(component.dz || 0),
        hashes: dedupeHashes(component.hashes || []),
      }))
      .filter((component) => component.hashes.length > 0)
      .sort(compareComponents);
    if (components.length === 0) {
      continue;
    }
    if (!isValidTemplateShape(components)) {
      continue;
    }
    const signature = components.map((component) => `${component.dx},${component.dz}`).join("|");
    const existing = variantsBySignature.get(signature);
    if (existing) {
      mergeVariant(existing, components);
    } else {
      variantsBySignature.set(signature, { components });
    }
  }
  room.variants = [...variantsBySignature.values()]
    .sort((a, b) => b.components.length - a.components.length || variantSignature(a).localeCompare(variantSignature(b)))
    .map((variant, index) => ({
      id: `variant-${index + 1}`,
      components: variant.components,
    }));
}

function isValidTemplateShape(components) {
  if (components.length === 0 || components.length > maxTemplateComponents) {
    return false;
  }
  const xs = components.map((component) => component.dx);
  const zs = components.map((component) => component.dz);
  if (Math.max(...xs) - Math.min(...xs) + 1 > maxTemplateSpan
      || Math.max(...zs) - Math.min(...zs) + 1 > maxTemplateSpan) {
    return false;
  }

  const cells = new Set(components.map(cellKey));
  const visited = new Set();
  const queue = [cellKey(components[0])];
  visited.add(queue[0]);
  for (let index = 0; index < queue.length; index++) {
    const [x, z] = queue[index].split(",").map(Number);
    for (const neighbor of [`${x + 1},${z}`, `${x - 1},${z}`, `${x},${z + 1}`, `${x},${z - 1}`]) {
      if (cells.has(neighbor) && !visited.has(neighbor)) {
        visited.add(neighbor);
        queue.push(neighbor);
      }
    }
  }
  return visited.size === cells.size;
}

function mergeVariant(existing, incomingComponents) {
  const componentsByCell = new Map(existing.components.map((component) => [cellKey(component), component]));
  for (const component of incomingComponents) {
    const existingComponent = componentsByCell.get(cellKey(component));
    if (existingComponent) {
      existingComponent.hashes = dedupeHashes([...existingComponent.hashes, ...component.hashes]);
    } else {
      existing.components.push(component);
      componentsByCell.set(cellKey(component), component);
    }
  }
  existing.components.sort(compareComponents);
}

function dedupeHashes(hashes) {
  const byHash = new Map();
  for (const hash of hashes) {
    const core = Number(hash.core || 0);
    const stable = Number(hash.stable || 0);
    if (core === 0 && stable === 0) {
      continue;
    }
    const key = `${core}|${stable}`;
    const existing = byHash.get(key);
    if (!existing) {
      byHash.set(key, {
        core,
        stable,
        seen: positiveOrOne(hash.seen),
        updatedAt: Number(hash.updatedAt || 0),
        source: sourceString(hash.source),
      });
      continue;
    }
    existing.seen = Math.max(existing.seen, positiveOrOne(hash.seen));
    existing.updatedAt = Math.max(existing.updatedAt, Number(hash.updatedAt || 0));
    existing.source = sourceString([existing.source, hash.source].filter(Boolean).join(","));
  }
  return [...byHash.values()].sort(compareHashes);
}

function compareRooms(a, b) {
  return a.name.localeCompare(b.name)
    || a.type.localeCompare(b.type)
    || Number(a.secrets || 0) - Number(b.secrets || 0)
    || Number(a.crypts || 0) - Number(b.crypts || 0);
}

function compareComponents(a, b) {
  return Number(a.dx || 0) - Number(b.dx || 0) || Number(a.dz || 0) - Number(b.dz || 0);
}

function compareHashes(a, b) {
  return Number(a.core || 0) - Number(b.core || 0)
    || Number(a.stable || 0) - Number(b.stable || 0)
    || Number(a.updatedAt || 0) - Number(b.updatedAt || 0);
}

function roomKey(room) {
  return `${room.name}|${room.type}|${Number(room.secrets || 0)}|${Number(room.crypts || 0)}`;
}

function cellKey(component) {
  return `${component.dx},${component.dz}`;
}

function variantSignature(variant) {
  return variant.components.map(cellKey).join("|");
}

function positiveOrOne(value) {
  const number = Number(value || 0);
  return number > 0 ? number : 1;
}

function sourceString(value) {
  const sources = String(value || "unknown")
    .split(",")
    .map((source) => source.trim())
    .filter(Boolean);
  return [...new Set(sources)].sort().join(",");
}
