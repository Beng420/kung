#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";

const root = process.cwd();
const firstRoot = path.join(root, "versions", "mc26_1_2");
const secondRoot = path.join(root, "versions", "mc26_2");
const ignored = new Set([
  "build.gradle",
  "src/main/resources/fabric.mod.json",
]);
const knownVersionSpecific = new Set([
  "src/main/java/com/github/beng420/kung/command/KungCommands.java",
  "src/main/java/com/github/beng420/kung/config/KungHudEditorScreen.java",
  "src/main/java/com/github/beng420/kung/feature/dungeon/DungeonRunStats.java",
  "src/main/java/com/github/beng420/kung/feature/misc/LobbyHopHelperFeature.java",
  "src/main/java/com/github/beng420/kung/feature/misc/SuperpairsHelperFeature.java",
  "src/main/java/com/github/beng420/kung/feature/screen/ScreenTracker.java",
  "src/main/java/com/github/beng420/kung/feature/slayer/TarantulaHelperFeature.java",
]);

if (!fs.existsSync(firstRoot) || !fs.existsSync(secondRoot)) {
  console.error("Run from the repository root. Expected versions/mc26_1_2 and versions/mc26_2.");
  process.exit(2);
}

const firstFiles = filesUnder(firstRoot);
const secondFiles = filesUnder(secondRoot);
const allFiles = [...new Set([...firstFiles.keys(), ...secondFiles.keys()])]
  .filter((relative) => !ignored.has(relative.replaceAll("\\", "/")))
  .sort();

const missing = [];
const changed = [];
const knownChanged = [];

for (const relative of allFiles) {
  const first = firstFiles.get(relative);
  const second = secondFiles.get(relative);
  if (!first || !second) {
    missing.push(relative);
    continue;
  }
  if (hashFile(first) !== hashFile(second)) {
    if (knownVersionSpecific.has(relative)) {
      knownChanged.push(relative);
      continue;
    }
    changed.push(relative);
  }
}

console.log(`Compared ${allFiles.length} files.`);
printList("Missing in one 26er module", missing);
printList("Known version-specific differences", knownChanged);
printList("Different content", changed);

if (missing.length > 0 || changed.length > 0) {
  process.exitCode = 1;
}

function filesUnder(directory) {
  const result = new Map();
  walk(directory, directory, result);
  return result;
}

function walk(rootDirectory, directory, result) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      walk(rootDirectory, fullPath, result);
    } else if (entry.isFile()) {
      const relative = path.relative(rootDirectory, fullPath).replaceAll("\\", "/");
      if (!relative.startsWith("build/")) {
        result.set(relative, fullPath);
      }
    }
  }
}

function hashFile(file) {
  return crypto.createHash("sha256").update(fs.readFileSync(file)).digest("hex");
}

function printList(title, values) {
  console.log(`${title}: ${values.length}`);
  for (const value of values.slice(0, 80)) {
    console.log(`  ${value}`);
  }
  if (values.length > 80) {
    console.log(`  ... ${values.length - 80} more`);
  }
}
