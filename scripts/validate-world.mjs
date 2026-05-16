#!/usr/bin/env node
/**
 * World data integrity validator. Runs as a Gradle build gate.
 *
 * ERRORS (block build):
 *   - Dangling exits (target room doesn't exist)
 *   - Duplicate room IDs
 *   - Missing reciprocal exits (A→B but B has no return exit at all)
 *   - Coordinate mismatches (exit direction doesn't match coord delta)
 *   - Global coordinate overlaps (any two rooms at same x,y,z)
 *   - Wrong reciprocal directions (A→DIR→B but B→OPPOSITE(DIR)→C)
 *
 * Coordinates are globally unique — every room has a unique (x,y,z).
 * Portal exits (BFS-excluded teleport connections) are exempt from
 * coordinate alignment checks.
 */
import { readFileSync, readdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const worldDir = join(__dirname, '..', 'maker', 'default_world_src', 'world');

const OPPOSITE = {
  NORTH: 'SOUTH', SOUTH: 'NORTH', EAST: 'WEST', WEST: 'EAST',
  UP: 'DOWN', DOWN: 'UP',
  NORTHEAST: 'SOUTHWEST', SOUTHWEST: 'NORTHEAST',
  NORTHWEST: 'SOUTHEAST', SOUTHEAST: 'NORTHWEST',
};
const DX = { NORTH: 0, SOUTH: 0, EAST: 1, WEST: -1, UP: 0, DOWN: 0, NORTHEAST: 1, NORTHWEST: -1, SOUTHEAST: 1, SOUTHWEST: -1 };
const DY = { NORTH: 1, SOUTH: -1, EAST: 0, WEST: 0, UP: 0, DOWN: 0, NORTHEAST: 1, NORTHWEST: 1, SOUTHEAST: -1, SOUTHWEST: -1 };
const DZ = { NORTH: 0, SOUTH: 0, EAST: 0, WEST: 0, UP: 1, DOWN: -1, NORTHEAST: 0, NORTHWEST: 0, SOUTHEAST: 0, SOUTHWEST: 0 };

// Portal exits: BFS-excluded connections where direction doesn't match coord delta.
// These are teleport/magical connections that break the spatial graph cycle.
const PORTALS = new Set([
  'first_seal:watcher_perch->glass_desert:spire_base',
  'glass_desert:spire_base->first_seal:watcher_perch',
]);

const files = readdirSync(worldDir).filter(f => f.endsWith('.zone.json'));
const allRooms = new Map();
const errors = [];

for (const file of files) {
  const zone = JSON.parse(readFileSync(join(worldDir, file), 'utf8'));
  const zoneId = zone.id || file.replace('.zone.json', '');
  for (const room of zone.rooms || []) {
    if (allRooms.has(room.id)) {
      errors.push(`DUPLICATE_ID: ${room.id} in both ${allRooms.get(room.id).file} and ${file}`);
    }
    allRooms.set(room.id, { x: room.x, y: room.y, z: room.z ?? 0, zone: zoneId, exits: room.exits || {}, file });
  }
}

// ── Check exits ─────────────────────────────────────────────────────────────
for (const [roomId, room] of allRooms) {
  for (const [dir, targetId] of Object.entries(room.exits)) {
    const target = allRooms.get(targetId);

    if (!target) {
      errors.push(`DANGLING_EXIT: ${roomId} --${dir}--> ${targetId} (not found)`);
      continue;
    }

    // Coordinate alignment: global (skip portal exits)
    if (DX[dir] !== undefined && !PORTALS.has(`${roomId}->${targetId}`)) {
      const ex = room.x + DX[dir];
      const ey = room.y + DY[dir];
      const ez = room.z + DZ[dir];
      if (target.x !== ex || target.y !== ey || target.z !== ez) {
        errors.push(`COORD_MISMATCH: ${roomId} (${room.x},${room.y},${room.z}) --${dir}--> ${targetId} (${target.x},${target.y},${target.z}) expected (${ex},${ey},${ez})`);
      }
    }

    // Reciprocal check
    const opp = OPPOSITE[dir];
    if (opp) {
      const anyReturn = Object.values(target.exits).includes(roomId);
      if (!anyReturn) {
        errors.push(`MISSING_RECIPROCAL: ${roomId} --${dir}--> ${targetId} has no return exit to ${roomId}`);
      } else if (target.exits[opp] && target.exits[opp] !== roomId) {
        errors.push(`WRONG_RECIPROCAL: ${roomId} --${dir}--> ${targetId}, but ${targetId} --${opp}--> ${target.exits[opp]}`);
      }
    }
  }
}

// ── Check global coordinate uniqueness ──────────────────────────────────────
const coordGlobal = new Map();
for (const [roomId, room] of allRooms) {
  const key = `${room.x},${room.y},${room.z}`;
  if (!coordGlobal.has(key)) coordGlobal.set(key, []);
  coordGlobal.get(key).push(roomId);
}

for (const [coord, ids] of coordGlobal) {
  if (ids.length <= 1) continue;
  const zones = [...new Set(ids.map(id => allRooms.get(id).zone))];
  errors.push(`COORD_OVERLAP: (${coord}) occupied by ${ids.length} rooms [${zones.join(',')}]: ${ids.join(', ')}`);
}

// ── Report ──────────────────────────────────────────────────────────────────
if (errors.length > 0) {
  console.error(`✗ World validation FAILED\n`);
  console.error(`  ${errors.length} error(s):`);
  for (const e of errors) console.error(`    ${e}`);
  process.exit(1);
} else {
  console.log(`✓ World validation passed (${allRooms.size} rooms, ${files.length} zones)`);
  process.exit(0);
}
