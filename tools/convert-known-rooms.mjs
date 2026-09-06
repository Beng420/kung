#!/usr/bin/env node

import fs from "node:fs";
import path from "node:path";

const args = process.argv.slice(2);

if (args.length < 2) {
  console.error(
    "Usage: node tools/convert-known-rooms.mjs <output known-rooms.json> <input file/dir> [more input files/dirs...]"
  );
  process.exit(2);
}

const outputPath = path.resolve(args[0]);
const inputPaths = expandInputs(args.slice(1));
const roomsByKey = new Map();
let sourceIndex = 0;

const CANONICAL_ROOMS = new Map([
  ["Blaze", { type: "PUZZLE", secrets: 1 }],
  ["Ice Path", { type: "PUZZLE", secrets: 1 }],
]);

for (const inputPath of inputPaths) {
  const sourceName = sourceNameFor(inputPath, sourceIndex++);
  if (inputPath.endsWith(".jsonl")) {
    importJsonLines(inputPath, sourceName);
  } else if (inputPath.endsWith(".json")) {
    importRoomDatabase(inputPath, sourceName);
  }
}

const rooms = [...roomsByKey.values()]
  .sort(compareRooms)
  .map((room) => roomToJson(room));

const document = {
  schema: 1,
  generatedAt: new Date().toISOString(),
  rooms,
};

fs.mkdirSync(path.dirname(outputPath), { recursive: true });
fs.writeFileSync(outputPath, `${JSON.stringify(document, null, 2)}\n`, "utf8");

const componentCount = rooms.reduce(
  (total, room) =>
    total + room.variants.reduce((variantTotal, variant) => variantTotal + variant.components.length, 0),
  0
);
const hashCount = rooms.reduce(
  (total, room) =>
    total + room.variants.reduce(
      (variantTotal, variant) =>
        variantTotal + variant.components.reduce((cellTotal, cell) => cellTotal + cell.hashes.length, 0),
      0
    ),
  0
);
console.log(`Wrote ${rooms.length} rooms, ${componentCount} cells, ${hashCount} hashes to ${outputPath}`);

function expandInputs(inputs) {
  const files = [];
  for (const input of inputs) {
    const resolved = path.resolve(input);
    if (!fs.existsSync(resolved)) {
      continue;
    }
    const stat = fs.statSync(resolved);
    if (stat.isDirectory()) {
      for (const name of ["known-rooms.json", "known-rooms.jsonl"]) {
        const file = path.join(resolved, name);
        if (fs.existsSync(file)) {
          files.push(file);
        }
      }
      continue;
    }
    if (stat.isFile() && /^known-rooms\.jsonl?$/.test(path.basename(resolved))) {
      files.push(resolved);
    }
  }
  return [...new Set(files)];
}

function importJsonLines(inputPath, sourceName) {
  const rows = readJsonLines(inputPath).map((row, index) => ({
    ...row,
    sourceName,
    sourceOrder: index,
  }));
  for (const burst of learnBursts(rows)) {
    addBurst(burst, sourceName);
  }
}

function importRoomDatabase(inputPath, fallbackSource) {
  const database = JSON.parse(fs.readFileSync(inputPath, "utf8").replace(/^\uFEFF/, ""));
  for (const roomInput of database.rooms || []) {
    const room = roomFor(roomInput);
    room.crypts = Math.max(room.crypts || 0, numberOrZero(roomInput.crypts));

    for (const variantInput of roomInput.variants || []) {
      if (isExcludedRoomDatabaseVariant(roomInput, variantInput)
          || isBadGrandLibrarySingleCellVariant(roomInput, variantInput)) {
        continue;
      }
      const components = (variantInput.components || [])
        .map((componentInput) => ({
          dx: numberOrZero(componentInput.dx),
          dz: numberOrZero(componentInput.dz),
          hashes: hashesFromComponent(componentInput, fallbackSource),
        }))
        .filter((component) => component.hashes.length > 0);
      if (components.length === 0) {
        continue;
      }

      const variant = variantFor(room, components);
      for (const componentInput of components) {
        const component = componentFor(variant, componentInput.dx, componentInput.dz);
        for (const hash of componentInput.hashes) {
          addHash(component, hash);
        }
        coalesceCoreOnlyHashes(component);
      }
    }
  }
}

function hashesFromComponent(componentInput, fallbackSource) {
  const hashes = [];
  for (const hashInput of componentInput.hashes || []) {
    hashes.push({
      core: numberOrZero(hashInput.core),
      stable: numberOrZero(hashInput.stable),
      seen: positiveOrOne(hashInput.seen),
      updatedAt: numberOrZero(hashInput.updatedAt),
      sources: sourceSet(hashInput.source, fallbackSource),
    });
  }
  for (const core of componentInput.coreHashes || []) {
    hashes.push({
      core: numberOrZero(core),
      stable: 0,
      seen: 1,
      updatedAt: 0,
      sources: new Set([fallbackSource]),
    });
  }
  for (const stable of componentInput.stableCoreHashes || []) {
    hashes.push({
      core: 0,
      stable: numberOrZero(stable),
      seen: 1,
      updatedAt: 0,
      sources: new Set([fallbackSource]),
    });
  }
  if (hashes.length === 0 && componentInput.coreHash !== undefined) {
    hashes.push({
      core: numberOrZero(componentInput.coreHash),
      stable: numberOrZero(componentInput.stableCoreHash),
      seen: 1,
      updatedAt: 0,
      sources: new Set([fallbackSource]),
    });
  }
  return hashes.filter((hash) => hash.core !== 0 || hash.stable !== 0);
}

function readJsonLines(inputPath) {
  return fs
    .readFileSync(inputPath, "utf8")
    .replace(/^\uFEFF/, "")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => JSON.parse(line));
}

function* learnBursts(rows) {
  let currentKey = "";
  let currentBurst = [];
  for (const row of rows) {
    const key = roomKey(row);
    if (currentBurst.length > 0 && key !== currentKey) {
      yield currentBurst;
      currentBurst = [];
    }
    currentKey = key;
    currentBurst.push(row);
  }
  if (currentBurst.length > 0) {
    yield currentBurst;
  }
}

function addBurst(burst, sourceName) {
  const first = burst[0];
  const key = roomKey(first);
  if (isExcludedBurst(key, burst)) {
    return;
  }

  const room = roomFor(first);
  room.crypts = Math.max(room.crypts || 0, numberOrZero(first.crypts));

  const minX = Math.min(...burst.map((row) => row.roomGridX));
  const minZ = Math.min(...burst.map((row) => row.roomGridZ));
  const cells = burst.map((row) => ({
    row,
    dx: row.roomGridX - minX,
    dz: row.roomGridZ - minZ,
  }));
  const variant = variantFor(room, cells);

  for (const cellInput of cells) {
    const component = componentFor(variant, cellInput.dx, cellInput.dz);
    addHash(component, {
      core: numberOrZero(cellInput.row.coreHash),
      stable: numberOrZero(cellInput.row.stableCoreHash),
      seen: 1,
      updatedAt: numberOrZero(cellInput.row.sourceOrder),
      sources: new Set([sourceName]),
    });
    coalesceCoreOnlyHashes(component);
  }
}

function roomFor(input) {
  const normalized = normalizeRoom(input);
  const key = roomKey(normalized);
  let room = roomsByKey.get(key);
  if (!room) {
    room = {
      name: normalized.name,
      type: normalized.type,
      secrets: numberOrZero(normalized.secrets),
      crypts: 0,
      variantsBySignature: new Map(),
      variants: [],
    };
    roomsByKey.set(key, room);
  }
  return room;
}

function variantFor(room, cells) {
  const signature = variantSignature(cells);
  let variant = room.variantsBySignature.get(signature);
  if (!variant) {
    variant = {
      signature,
      cellsByPosition: new Map(),
      cells: [],
    };
    room.variantsBySignature.set(signature, variant);
    room.variants.push(variant);
  }
  return variant;
}

function componentFor(variant, dx, dz) {
  const positionKey = `${dx},${dz}`;
  let component = variant.cellsByPosition.get(positionKey);
  if (!component) {
    component = {
      dx,
      dz,
      hashesByKey: new Map(),
      hashes: [],
    };
    variant.cellsByPosition.set(positionKey, component);
    variant.cells.push(component);
  }
  return component;
}

function addHash(component, hash) {
  if (hash.core === 0 && hash.stable === 0) {
    return;
  }
  const key = `${hash.core}|${hash.stable}`;
  const existing = component.hashesByKey.get(key);
  if (existing) {
    existing.seen += positiveOrOne(hash.seen);
    existing.updatedAt = Math.max(existing.updatedAt, numberOrZero(hash.updatedAt));
    for (const source of hash.sources || []) {
      existing.sources.add(source);
    }
    return;
  }
  const copy = {
    core: numberOrZero(hash.core),
    stable: numberOrZero(hash.stable),
    seen: positiveOrOne(hash.seen),
    updatedAt: numberOrZero(hash.updatedAt),
    sources: new Set(hash.sources || []),
  };
  component.hashesByKey.set(key, copy);
  component.hashes.push(copy);
}

function coalesceCoreOnlyHashes(component) {
  const stableByCore = new Map();
  for (const hash of component.hashes) {
    if (hash.core !== 0 && hash.stable !== 0) {
      stableByCore.set(hash.core, hash);
    }
  }
  if (stableByCore.size === 0) {
    return;
  }

  component.hashes = component.hashes.filter((hash) => {
    if (hash.core === 0 || hash.stable !== 0 || !stableByCore.has(hash.core)) {
      return true;
    }
    const stableHash = stableByCore.get(hash.core);
    stableHash.seen += hash.seen;
    stableHash.updatedAt = Math.max(stableHash.updatedAt, hash.updatedAt);
    for (const source of hash.sources) {
      stableHash.sources.add(source);
    }
    component.hashesByKey.delete(`${hash.core}|${hash.stable}`);
    return false;
  });
}

function roomToJson(room) {
  return {
    name: room.name,
    type: room.type,
    secrets: room.secrets,
    crypts: room.crypts || 0,
    variants: room.variants.sort(compareVariants).map((variant, index) => ({
      id: `variant-${index + 1}`,
      components: variant.cells.sort(compareCells).map((cell) => ({
        dx: cell.dx,
        dz: cell.dz,
        hashes: cell.hashes.sort(compareHashes).map((hash) => ({
          core: hash.core,
          stable: hash.stable,
          seen: hash.seen,
          updatedAt: hash.updatedAt,
          source: [...hash.sources].sort().join(","),
        })),
      })),
    })),
  };
}

function isExcludedBurst(key, burst) {
  // Lava Ravine is an L-shaped 3-component room; 2-cell bursts were incomplete learns.
  if (key === "Lava Ravine|NORMAL|6" && burst.length < 3) {
    return true;
  }
  // One old local 1.21 import mislabeled the Steps hash as a one-cell Diagonal variant.
  return key === "Diagonal|NORMAL|4"
    && burst.length === 1
    && numberOrZero(burst[0].coreHash) === -195230825;
}

function isExcludedRoomDatabaseVariant(room, variant) {
  if (roomKey(room) !== "Diagonal|NORMAL|4") {
    return false;
  }
  const components = variant.components || [];
  return components.length === 1
    && components.some((component) =>
      hashesFromComponent(component, "exclude-check")
        .some((hash) => hash.core === -195230825 || hash.stable === 1108899508)
    );
}

function isBadGrandLibrarySingleCellVariant(room, variant) {
  if (roomKey(room) !== "Grand Library|NORMAL|4") {
    return false;
  }
  const components = variant.components || [];
  if (components.length !== 1) {
    return false;
  }
  const cores = new Set(hashesFromComponent(components[0], "exclude-check").map((hash) => hash.core));
  return cores.has(1309677363) && cores.has(-1303935381);
}

function roomKey(row) {
  const normalized = normalizeRoom(row);
  return `${normalized.name}|${normalized.type}|${numberOrZero(normalized.secrets)}`;
}

function normalizeRoom(row) {
  const canonical = CANONICAL_ROOMS.get(row.name);
  if (!canonical) {
    return row;
  }
  return {
    ...row,
    type: canonical.type,
    secrets: canonical.secrets,
  };
}

function variantSignature(cells) {
  return cells
    .map((cell) => `${numberOrZero(cell.dx)},${numberOrZero(cell.dz)}`)
    .sort()
    .join(";");
}

function numberOrZero(value) {
  return Number.isFinite(Number(value)) ? Number(value) : 0;
}

function positiveOrOne(value) {
  return Number.isInteger(Number(value)) && Number(value) > 0 ? Number(value) : 1;
}

function sourceSet(source, fallbackSource) {
  const sources = new Set();
  for (const part of String(source || fallbackSource).split(",")) {
    const trimmed = part.trim();
    if (trimmed) {
      sources.add(trimmed);
    }
  }
  if (sources.size === 0) {
    sources.add(fallbackSource);
  }
  return sources;
}

function sourceNameFor(inputPath, index) {
  const lower = inputPath.toLowerCase();
  if (lower.includes("here we go again (2)") || lower.includes("mc26_1_2")) {
    return "learned-26.1.2";
  }
  if (lower.includes("mc26_2")) {
    return "bundled-26.2";
  }
  if (lower.includes("here we go again") || lower.includes("mc1_21_11")) {
    return "learned-1.21.11";
  }
  return `input-${index + 1}`;
}

function compareRooms(a, b) {
  return (
    a.name.localeCompare(b.name) ||
    a.type.localeCompare(b.type) ||
    Number(a.secrets) - Number(b.secrets)
  );
}

function compareVariants(a, b) {
  return a.signature.localeCompare(b.signature);
}

function compareCells(a, b) {
  return a.dz - b.dz || a.dx - b.dx;
}

function compareHashes(a, b) {
  const sourceA = [...a.sources].sort().join(",");
  const sourceB = [...b.sources].sort().join(",");
  return (
    b.seen - a.seen ||
    sourceA.localeCompare(sourceB) ||
    a.core - b.core ||
    a.stable - b.stable
  );
}
