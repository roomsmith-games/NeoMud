#!/usr/bin/env node
/**
 * Second-pass fixer: resolves coordinate overlaps and remaining mismatches
 * after BFS. Uses local search to shift rooms to minimize total violations.
 * Run: node scripts/fix-map-pass2.mjs [--dry-run]
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

const files = readdirSync(worldDir).filter(f => f.endsWith('.zone.json'));
const zoneData = new Map();
const rooms = new Map();

for (const file of files) {
  const zone = JSON.parse(readFileSync(join(worldDir, file), 'utf8'));
  zoneData.set(file, zone);
  for (const room of zone.rooms || []) {
    rooms.set(room.id, { x: room.x, y: room.y, exits: room.exits || {}, zone: zone.id, file });
  }
}

function countMismatches(roomId) {
  const room = rooms.get(roomId);
  let count = 0;
  for (const [dir, targetId] of Object.entries(room.exits)) {
    if (dir === 'UP' || dir === 'DOWN' || !DX[dir]) continue;
    const target = rooms.get(targetId);
    if (!target) continue;
    if (room.x + DX[dir] !== target.x || room.y + DY[dir] !== target.y) count++;
  }
  // Also count incoming exits that expect us at a different position
  for (const [otherId, other] of rooms) {
    for (const [dir, targetId] of Object.entries(other.exits)) {
      if (targetId !== roomId || dir === 'UP' || dir === 'DOWN' || !DX[dir]) continue;
      if (other.x + DX[dir] !== room.x || other.y + DY[dir] !== room.y) count++;
    }
  }
  return count;
}

function getOccupied(zone) {
  const occupied = new Set();
  for (const [id, r] of rooms) {
    if (r.zone === zone) occupied.add(`${r.x},${r.y}`);
  }
  return occupied;
}

function isUpDownConnected(roomId, otherId) {
  const r = rooms.get(roomId);
  const o = rooms.get(otherId);
  return Object.entries(r.exits).some(([d, t]) => (d === 'UP' || d === 'DOWN') && t === otherId)
    || Object.entries(o.exits).some(([d, t]) => (d === 'UP' || d === 'DOWN') && t === roomId);
}

let totalMoves = 0;

// Pass 1: Fix overlaps by shifting rooms to adjacent free cells
for (let iteration = 0; iteration < 10; iteration++) {
  let moved = false;
  const coordMap = new Map();
  for (const [id, r] of rooms) {
    const key = `${r.zone}:${r.x},${r.y}`;
    if (!coordMap.has(key)) coordMap.set(key, []);
    coordMap.get(key).push(id);
  }

  for (const [key, ids] of coordMap) {
    if (ids.length <= 1) continue;
    // Filter to non-UP/DOWN-connected pairs
    const groups = [];
    const assigned = new Set();
    for (const id of ids) {
      if (assigned.has(id)) continue;
      const group = [id];
      assigned.add(id);
      for (const other of ids) {
        if (!assigned.has(other) && isUpDownConnected(id, other)) {
          group.push(other);
          assigned.add(other);
        }
      }
      groups.push(group);
    }
    if (groups.length <= 1) continue;

    // Need to move all but one group
    const zone = rooms.get(ids[0]).zone;
    const occupied = getOccupied(zone);

    // Keep the largest group in place, move others
    groups.sort((a, b) => b.length - a.length);
    for (let g = 1; g < groups.length; g++) {
      for (const roomId of groups[g]) {
        const room = rooms.get(roomId);
        // Try to find the best position: check where this room's exits WANT it to be
        let bestPos = null;
        let bestScore = Infinity;

        // Candidates: expected positions from each incoming/outgoing exit
        const candidates = [];
        for (const [dir, targetId] of Object.entries(room.exits)) {
          if (dir === 'UP' || dir === 'DOWN' || !DX[dir]) continue;
          const target = rooms.get(targetId);
          if (!target) continue;
          candidates.push({ x: target.x - DX[dir], y: target.y - DY[dir] });
        }
        for (const [otherId, other] of rooms) {
          for (const [dir, targetId] of Object.entries(other.exits)) {
            if (targetId !== roomId || dir === 'UP' || dir === 'DOWN' || !DX[dir]) continue;
            candidates.push({ x: other.x + DX[dir], y: other.y + DY[dir] });
          }
        }
        // Also try offsets from current position
        for (const dx of [-1, 0, 1]) {
          for (const dy of [-1, 0, 1]) {
            if (dx === 0 && dy === 0) continue;
            candidates.push({ x: room.x + dx, y: room.y + dy });
          }
        }

        for (const { x, y } of candidates) {
          const posKey = `${x},${y}`;
          if (occupied.has(posKey) && !(x === room.x && y === room.y)) continue;
          // Score: count mismatches if we move here
          const oldX = room.x, oldY = room.y;
          room.x = x; room.y = y;
          const score = countMismatches(roomId);
          room.x = oldX; room.y = oldY;
          if (score < bestScore) {
            bestScore = score;
            bestPos = { x, y };
          }
        }

        if (bestPos && (bestPos.x !== room.x || bestPos.y !== room.y)) {
          occupied.delete(`${room.x},${room.y}`);
          console.log(`  MOVE ${roomId}: (${room.x},${room.y}) → (${bestPos.x},${bestPos.y}) [mismatches: ${countMismatches(roomId)}→${bestScore}]`);
          room.x = bestPos.x;
          room.y = bestPos.y;
          occupied.add(`${bestPos.x},${bestPos.y}`);
          totalMoves++;
          moved = true;
        }
      }
    }
  }
  if (!moved) break;
}

// Pass 2: Fix remaining mismatches by adjusting individual rooms
for (let iteration = 0; iteration < 5; iteration++) {
  let improved = false;
  for (const [roomId, room] of rooms) {
    const currentMismatches = countMismatches(roomId);
    if (currentMismatches === 0) continue;

    const occupied = getOccupied(room.zone);
    let bestPos = null;
    let bestScore = currentMismatches;

    // Try positions suggested by exits
    const candidates = [];
    for (const [dir, targetId] of Object.entries(room.exits)) {
      if (dir === 'UP' || dir === 'DOWN' || !DX[dir]) continue;
      const target = rooms.get(targetId);
      if (!target) continue;
      candidates.push({ x: target.x - DX[dir], y: target.y - DY[dir] });
    }
    for (const [otherId, other] of rooms) {
      for (const [dir, targetId] of Object.entries(other.exits)) {
        if (targetId !== roomId || dir === 'UP' || dir === 'DOWN' || !DX[dir]) continue;
        candidates.push({ x: other.x + DX[dir], y: other.y + DY[dir] });
      }
    }

    for (const { x, y } of candidates) {
      if (x === room.x && y === room.y) continue;
      const posKey = `${x},${y}`;
      if (occupied.has(posKey)) continue;
      const oldX = room.x, oldY = room.y;
      room.x = x; room.y = y;
      const score = countMismatches(roomId);
      room.x = oldX; room.y = oldY;
      if (score < bestScore) {
        bestScore = score;
        bestPos = { x, y };
      }
    }

    if (bestPos) {
      console.log(`  ADJUST ${roomId}: (${room.x},${room.y}) → (${bestPos.x},${bestPos.y}) [mismatches: ${currentMismatches}→${bestScore}]`);
      room.x = bestPos.x;
      room.y = bestPos.y;
      totalMoves++;
      improved = true;
    }
  }
  if (!improved) break;
}

// Write back
let filesChanged = 0;
for (const [file, zone] of zoneData) {
  let changed = false;
  for (const room of zone.rooms || []) {
    const r = rooms.get(room.id);
    if (r && (room.x !== r.x || room.y !== r.y)) {
      if (!dryRun) { room.x = r.x; room.y = r.y; }
      changed = true;
    }
  }
  if (changed) {
    filesChanged++;
    if (!dryRun) writeFileSync(join(worldDir, file), JSON.stringify(zone, null, 2) + '\n');
  }
}

console.log(`\n${dryRun ? '[DRY RUN] Would move' : 'Moved'} ${totalMoves} rooms across ${filesChanged} zone files`);
