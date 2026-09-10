// Read-only audit: correlate observed room transitions with the bundled catalog.
import fs from 'node:fs';
import path from 'node:path';

const directory = process.argv[2];
if (!directory) throw new Error('Usage: node tools/audit-dungeon-traces.mjs <kung-debug directory>');
const data = JSON.parse(fs.readFileSync('versions/mc26_1_2/src/main/resources/kung-dungeon-scans/known-rooms.json', 'utf8'));
const known = new Map();
for (const room of data.rooms) {
  for (const variant of room.variants) for (const component of variant.components) for (const hash of component.hashes) {
    for (const key of [hash.core, hash.stable].filter(Boolean)) {
      const identities = known.get(key) ?? new Set();
      identities.add(`${room.name}|${room.type}|${room.secrets}`);
      known.set(key, identities);
    }
  }
}
const observations = new Map();
const unknown = new Map();
const seen = new Set();
for (const name of fs.readdirSync(directory).filter(name => name.endsWith('.log'))) {
  for (const [index, line] of fs.readFileSync(path.join(directory, name), 'utf8').split(/\r?\n/).entries()) {
    const transition = line.match(/grid=(\d+,\d+) previous=ROOM:.*?:core=(-?\d+):stable=(-?\d+):type=\w+ next=ROOM:.*?:core=(-?\d+):stable=(-?\d+)/);
    if (transition && !seen.has(line)) {
      seen.add(line);
      const [, cell, before, beforeStable, after, afterStable] = transition;
      const identity = known.get(+afterStable) ?? known.get(+after);
      if (identity?.size === 1) for (const hash of [+before, +beforeStable]) {
        if (hash === 0 || hash === -318865360 || known.has(hash)) continue;
        const entry = observations.get(hash) ?? { hash, identities: new Set(), evidence: [] };
        entry.identities.add([...identity][0]);
        entry.evidence.push({ file: name, line: index + 1, cell, before: +before, beforeStable: +beforeStable, after: +after, afterStable: +afterStable });
        observations.set(hash, entry);
      }
    }
    const point = line.match(/(?:next|point)=ROOM:.*?:core=(-?\d+):stable=(-?\d+)/);
    if (point && !known.has(+point[1]) && !known.has(+point[2]) && +point[1] !== -318865360) {
      const key = `${point[1]}/${point[2]}`;
      unknown.set(key, (unknown.get(key) ?? 0) + 1);
    }
  }
}
console.log(JSON.stringify({
  transitions: [...observations.values()].map(entry => ({ ...entry, identities: [...entry.identities] })),
  unknownPairs: Object.fromEntries([...unknown].sort((a, b) => b[1] - a[1]))
}, null, 2));
