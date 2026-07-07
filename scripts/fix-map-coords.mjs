#!/usr/bin/env node
/**
 * BFS coordinate recalculation + reciprocal exit repair.
 * Exits are source of truth; coordinates are recalculated to match.
 * Run: node scripts/fix-map-coords.mjs [--dry-run]
 */
import { readFileSync, writeFileSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const worldDir = join(__dirname, '..', 'maker', 'default_world_src', 'world');
const dryRun = process.argv.includes('--dry-run');

const OPPOSITE = {
  NORTH: 'SOUTH', SOUTH: 'NORTH', EAST: 'WEST', WEST: 'EAST',
  UP: 'DOWN', DOWN: 'UP',
  NORTHEAST: 'SOUTHWEST', SOUTHWEST: 'NORTHEAST',
  NORTHWEST: 'SOUTHEAST', SOUTHEAST: 'NORTHWEST',
};
const DX = { NORTH: 0, SOUTH: 0, EAST: 1, WEST: -1, UP: 0, DOWN: 0, NORTHEAST: 1, NORTHWEST: -1, SOUTHEAST: 1, SOUTHWEST: -1 };
const DY = { NORTH: 1, SOUTH: -1, EAST: 0, WEST: 0, UP: 0, DOWN: 0, NORTHEAST: 1, NORTHWEST: 1, SOUTHEAST: -1, SOUTHWEST: -1 };

// Load all zones
const files = readdirSync(worldDir).filter(f => f.endsWith('.zone.json'));
const zoneData = new Map(); // filename -> parsed JSON
const allRooms = new Map(); // roomId -> { x, y, exits, zone, file }

for (const file of files) {
  const zone = JSON.parse(readFileSync(join(worldDir, file), 'utf8'));
  zoneData.set(file, zone);
  for (const room of zone.rooms || []) {
    allRooms.set(room.id, { x: room.x, y: room.y, exits: room.exits || {}, zone: zone.id, file });
  }
}

console.log(`Loaded ${allRooms.size} rooms from ${files.length} zones`);

// Phase 1: Fix reciprocal exits — only ADD missing ones; never change existing exits
let reciprocalAdds = 0;
const reciprocalWarnings = [];
for (const [roomId, room] of allRooms) {
  for (const [dir, targetId] of Object.entries(room.exits)) {
    const target = allRooms.get(targetId);
    if (!target) continue;
    const opp = OPPOSITE[dir];
    if (!opp) continue;

    if (!target.exits[opp]) {
      console.log(`  RECIPROCAL ADD: ${targetId} --${opp}--> ${roomId}`);
      target.exits[opp] = roomId;
      reciprocalAdds++;
    } else if (target.exits[opp] !== roomId) {
      reciprocalWarnings.push(`${roomId} --${dir}--> ${targetId}, but ${targetId} --${opp}--> ${target.exits[opp]} (not ${roomId})`);
    }
  }
}
console.log(`Added ${reciprocalAdds} missing reciprocal exits`);
if (reciprocalWarnings.length > 0) {
  console.log(`\n${reciprocalWarnings.length} reciprocal conflict(s) (require manual review):`);
  for (const w of reciprocalWarnings) console.log(`  WARNING: ${w}`);
}

// Phase 2: BFS coordinate recalculation
const newCoords = new Map(); // roomId -> { x, y }
const visited = new Set();
const conflicts = [];

function bfs(startId, startX, startY) {
  const queue = [{ id: startId, x: startX, y: startY }];
  visited.add(startId);
  newCoords.set(startId, { x: startX, y: startY });

  while (queue.length > 0) {
    const { id, x, y } = queue.shift();
    const room = allRooms.get(id);
    if (!room) continue;

    for (const [dir, targetId] of Object.entries(room.exits)) {
      if (!allRooms.has(targetId)) continue;
      const dx = DX[dir] ?? 0;
      const dy = DY[dir] ?? 0;
      const nx = x + dx;
      const ny = y + dy;

      if (visited.has(targetId)) {
        const existing = newCoords.get(targetId);
        if (existing && (existing.x !== nx || existing.y !== ny)) {
          conflicts.push({ from: id, to: targetId, dir, computed: { x: nx, y: ny }, existing });
        }
        continue;
      }

      visited.add(targetId);
      newCoords.set(targetId, { x: nx, y: ny });
      queue.push({ id: targetId, x: nx, y: ny });
    }
  }
}

// Start BFS from town:square at (0,0)
if (allRooms.has('town:square')) {
  bfs('town:square', 0, 0);
  console.log(`BFS from town:square reached ${visited.size} rooms`);
}

// Handle disconnected subgraphs
for (const [roomId, room] of allRooms) {
  if (!visited.has(roomId)) {
    bfs(roomId, room.x, room.y);
    console.log(`BFS from disconnected ${roomId} at (${room.x},${room.y})`);
  }
}

if (conflicts.length > 0) {
  console.log(`\n${conflicts.length} BFS conflicts (cycle-induced coordinate disagreements):`);
  for (const c of conflicts) {
    console.log(`  ${c.from} --${c.dir}--> ${c.to}: computed (${c.computed.x},${c.computed.y}) vs existing (${c.existing.x},${c.existing.y})`);
  }
}

// Phase 3: Apply changes
let coordChanges = 0;
for (const [file, zone] of zoneData) {
  let changed = false;
  for (const room of zone.rooms || []) {
    const nc = newCoords.get(room.id);
    if (nc && (room.x !== nc.x || room.y !== nc.y)) {
      if (!dryRun) {
        room.x = nc.x;
        room.y = nc.y;
      }
      coordChanges++;
      changed = true;
    }
    // Also write back any reciprocal exit fixes
    const roomData = allRooms.get(room.id);
    if (roomData && JSON.stringify(room.exits) !== JSON.stringify(roomData.exits)) {
      if (!dryRun) {
        room.exits = roomData.exits;
      }
      changed = true;
    }
  }
  if (changed && !dryRun) {
    writeFileSync(join(worldDir, file), JSON.stringify(zone, null, 2) + '\n');
  }
}

console.log(`\n${dryRun ? '[DRY RUN] Would update' : 'Updated'} ${coordChanges} room coordinates`);
console.log(`Total rooms: ${allRooms.size}, Reached by BFS: ${visited.size}`);
