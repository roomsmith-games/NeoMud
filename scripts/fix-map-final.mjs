#!/usr/bin/env node
/**
 * Final targeted fix pass:
 * 1. Fix all wrong reciprocal exits
 * 2. Resolve all coordinate overlaps by shifting rooms to nearest free cell
 * 3. Minimize remaining coordinate mismatches
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
    rooms.set(room.id, { x: room.x, y: room.y, exits: { ...room.exits }, zone: zone.id, file });
  }
}

// ── Phase 1: Fix reciprocal exits ───────────────────────────────────────────

// These are the 5 specific reciprocal issues. For each, we choose the fix
// that best preserves the zone's intended layout.
const reciprocalFixes = [
  // ashwood:wraith_hollow NORTH→ember_pit, but ember_pit SOUTH→burned_clearing
  // wraith_hollow is a dead-end area accessed from ruined_waystation. Remove the one-way NORTH shortcut.
  { roomId: 'ashwood:wraith_hollow', removeExit: 'NORTH' },

  // bone_wastes:bone_wastes_edge SOUTH→skyveil_reach:mountain_pass, but mountain_pass NORTH→highland_trail
  // This is a one-way zone transition. bone_wastes_edge SOUTH enters skyveil_reach; the path continues north.
  // Fix: Remove bone_wastes_edge.SOUTH and add bone_wastes_edge.DOWN→mountain_pass (vertical zone transition)
  // Actually simpler: just redirect mountain_pass so it has the right connections
  // mountain_pass should have NORTH→highland_trail AND SOUTH→bone_wastes_edge (as cross-zone return)
  // But a direction map can only have one exit per direction. mountain_pass already has NORTH→highland_trail.
  // Fix: Change bone_wastes_edge.SOUTH to bone_wastes_edge.DOWN (treats it as a descent into skyveil)
  { roomId: 'bone_wastes:bone_wastes_edge', changeExit: 'SOUTH', newDir: 'DOWN' },

  // cracked_plains:rift_floor_south UP→rift_edge_south, but rift_edge_south DOWN→ashwood:ridge_lookout
  // rift_edge_south DOWN should go to rift_floor_south (the rift below the edge)
  { roomId: 'cracked_plains:rift_edge_south', setExit: { dir: 'DOWN', target: 'cracked_plains:rift_floor_south' } },

  // first_seal:seal_forecourt WEST→overgrown_plaza, but overgrown_plaza EAST→pillar_walk
  // These are adjacent rooms in a row. overgrown_plaza connects EAST to pillar_walk (which is further east).
  // seal_forecourt WEST goes to overgrown_plaza, but the return path is through sentinel_grounds.
  // Fix: Add seal_forecourt.SOUTH or another direction to connect properly. Or remove the one-way WEST.
  // Actually: seal_forecourt(8,26) WEST→overgrown_plaza(7,26), but overgrown_plaza EAST→pillar_walk(8,26)
  // overgrown_plaza has EAST→pillar_walk, and seal_forecourt is also at x=8. So they can't both be EAST.
  // Fix: overgrown_plaza EAST should → seal_forecourt (not pillar_walk). pillar_walk needs different access.
  // Actually overgrown_plaza has: SOUTH→sentinel_grounds, WEST→collapsed_arch, EAST→pillar_walk
  // seal_forecourt has: SOUTH→inner_approach, WEST→overgrown_plaza, EAST→seal_threshold
  // pillar_walk has: WEST→overgrown_plaza, EAST→watcher_perch
  // pillar_walk and seal_forecourt are both at (8,26) — overlap!
  // Fix: Move pillar_walk to (8,27) (north of seal_forecourt), change overgrown_plaza.EAST→seal_forecourt,
  // add overgrown_plaza.NORTH→pillar_walk
  { roomId: 'first_seal:overgrown_plaza', setExit: { dir: 'EAST', target: 'first_seal:seal_forecourt' } },
  { roomId: 'first_seal:overgrown_plaza', setExit: { dir: 'NORTH', target: 'first_seal:pillar_walk' } },
  { roomId: 'first_seal:pillar_walk', setExit: { dir: 'SOUTH', target: 'first_seal:overgrown_plaza' } },
  { roomId: 'first_seal:pillar_walk', removeExit: 'WEST' },
  { roomId: 'first_seal:pillar_walk', setCoord: { x: 7, y: 27 } },

  // skyveil_reach:mountain_pass SOUTH→bone_wastes_edge, but bone_wastes_edge NORTH→death_gate
  // We already changed bone_wastes_edge.SOUTH to DOWN above.
  // Now mountain_pass.SOUTH points to bone_wastes_edge but bone_wastes_edge.NORTH→death_gate (correct for bone_wastes path)
  // mountain_pass.SOUTH becomes mountain_pass.UP (ascending from skyveil back to bone_wastes)
  { roomId: 'skyveil_reach:mountain_pass', changeExit: 'SOUTH', newDir: 'UP' },
];

let fixCount = 0;
for (const fix of reciprocalFixes) {
  const room = rooms.get(fix.roomId);
  if (!room) { console.log(`  SKIP: ${fix.roomId} not found`); continue; }

  if (fix.removeExit) {
    if (room.exits[fix.removeExit]) {
      console.log(`  REMOVE EXIT: ${fix.roomId} ${fix.removeExit}→${room.exits[fix.removeExit]}`);
      delete room.exits[fix.removeExit];
      fixCount++;
    }
  }
  if (fix.changeExit) {
    const target = room.exits[fix.changeExit];
    if (target) {
      console.log(`  CHANGE DIR: ${fix.roomId} ${fix.changeExit}→${fix.newDir} (target: ${target})`);
      delete room.exits[fix.changeExit];
      room.exits[fix.newDir] = target;
      fixCount++;
    }
  }
  if (fix.setExit) {
    console.log(`  SET EXIT: ${fix.roomId} ${fix.setExit.dir}→${fix.setExit.target}`);
    room.exits[fix.setExit.dir] = fix.setExit.target;
    fixCount++;
  }
  if (fix.setCoord) {
    console.log(`  SET COORD: ${fix.roomId} (${room.x},${room.y})→(${fix.setCoord.x},${fix.setCoord.y})`);
    room.x = fix.setCoord.x;
    room.y = fix.setCoord.y;
    fixCount++;
  }
}
console.log(`Applied ${fixCount} reciprocal/exit fixes\n`);

// ── Phase 2: Resolve overlaps ───────────────────────────────────────────────

function getZoneCoords(zone) {
  const occupied = new Map(); // "x,y" → [roomIds]
  for (const [id, r] of rooms) {
    if (r.zone !== zone) continue;
    const key = `${r.x},${r.y}`;
    if (!occupied.has(key)) occupied.set(key, []);
    occupied.get(key).push(id);
  }
  return occupied;
}

function isUpDownGroup(ids) {
  if (ids.length <= 1) return true;
  // Union-find
  const parent = Object.fromEntries(ids.map(r => [r, r]));
  function find(x) { return parent[x] === x ? x : (parent[x] = find(parent[x])); }
  function union(a, b) { parent[find(a)] = find(b); }
  for (const rid of ids) {
    const rm = rooms.get(rid);
    for (const [d, t] of Object.entries(rm.exits)) {
      if ((d === 'UP' || d === 'DOWN') && ids.includes(t)) union(rid, t);
    }
  }
  return new Set(ids.map(r => find(r))).size === 1;
}

function findFreeCell(zone, nearX, nearY, preferDir) {
  const occupied = new Set();
  for (const [id, r] of rooms) {
    if (r.zone === zone) occupied.add(`${r.x},${r.y}`);
  }
  // Try preferred direction first, then spiral outward
  const dirs = preferDir
    ? [preferDir, ...[[0,1],[0,-1],[1,0],[-1,0],[1,1],[-1,1],[1,-1],[-1,-1]]]
    : [[0,1],[0,-1],[1,0],[-1,0],[1,1],[-1,1],[1,-1],[-1,-1]];

  for (let radius = 1; radius <= 5; radius++) {
    for (let dx = -radius; dx <= radius; dx++) {
      for (let dy = -radius; dy <= radius; dy++) {
        if (Math.abs(dx) !== radius && Math.abs(dy) !== radius) continue;
        const key = `${nearX + dx},${nearY + dy}`;
        if (!occupied.has(key)) return { x: nearX + dx, y: nearY + dy };
      }
    }
  }
  return null;
}

let overlapFixes = 0;
const allZones = new Set([...rooms.values()].map(r => r.zone));
for (const zone of allZones) {
  const coords = getZoneCoords(zone);
  for (const [key, ids] of coords) {
    if (ids.length <= 1 || isUpDownGroup(ids)) continue;

    // Find groups connected by UP/DOWN
    const parent = Object.fromEntries(ids.map(r => [r, r]));
    function find(x) { return parent[x] === x ? x : (parent[x] = find(parent[x])); }
    function union(a, b) { parent[find(a)] = find(b); }
    for (const rid of ids) {
      const rm = rooms.get(rid);
      for (const [d, t] of Object.entries(rm.exits)) {
        if ((d === 'UP' || d === 'DOWN') && ids.includes(t)) union(rid, t);
      }
    }
    const groups = new Map();
    for (const id of ids) {
      const root = find(id);
      if (!groups.has(root)) groups.set(root, []);
      groups.get(root).push(id);
    }

    // Keep largest group in place, move others
    const sorted = [...groups.values()].sort((a, b) => b.length - a.length);
    for (let g = 1; g < sorted.length; g++) {
      for (const roomId of sorted[g]) {
        const room = rooms.get(roomId);
        // Find ideal position based on exits
        let bestCell = null;
        let bestMismatches = Infinity;

        // Check positions suggested by neighboring rooms
        for (const [dir, targetId] of Object.entries(room.exits)) {
          if (dir === 'UP' || dir === 'DOWN' || !DX[dir]) continue;
          const target = rooms.get(targetId);
          if (!target) continue;
          const candX = target.x - DX[dir];
          const candY = target.y - DY[dir];
          const occupied = new Set();
          for (const [id, r] of rooms) {
            if (r.zone === zone && id !== roomId) occupied.add(`${r.x},${r.y}`);
          }
          if (!occupied.has(`${candX},${candY}`)) {
            if (!bestCell || 0 < bestMismatches) { // Any exit-aligned position is great
              bestCell = { x: candX, y: candY };
              bestMismatches = 0;
            }
          }
        }

        if (!bestCell) bestCell = findFreeCell(zone, room.x, room.y);

        if (bestCell) {
          console.log(`  DEOVERLAP: ${roomId} (${room.x},${room.y}) → (${bestCell.x},${bestCell.y})`);
          room.x = bestCell.x;
          room.y = bestCell.y;
          overlapFixes++;
        } else {
          console.log(`  WARN: Could not deoverlap ${roomId} at (${room.x},${room.y})`);
        }
      }
    }
  }
}
console.log(`Resolved ${overlapFixes} overlap(s)\n`);

// ── Phase 3: Final mismatch reduction pass ──────────────────────────────────

function countMismatches(roomId) {
  const room = rooms.get(roomId);
  let count = 0;
  for (const [dir, targetId] of Object.entries(room.exits)) {
    if (dir === 'UP' || dir === 'DOWN' || !DX[dir]) continue;
    const target = rooms.get(targetId);
    if (!target) continue;
    if (room.x + DX[dir] !== target.x || room.y + DY[dir] !== target.y) count++;
  }
  return count;
}

let adjustments = 0;
for (let iter = 0; iter < 10; iter++) {
  let improved = false;
  for (const [roomId, room] of rooms) {
    const current = countMismatches(roomId);
    if (current === 0) continue;

    const occupied = new Set();
    for (const [id, r] of rooms) {
      if (r.zone === room.zone && id !== roomId) occupied.add(`${r.x},${r.y}`);
    }

    let bestPos = null;
    let bestScore = current;

    for (const [dir, targetId] of Object.entries(room.exits)) {
      if (dir === 'UP' || dir === 'DOWN' || !DX[dir]) continue;
      const target = rooms.get(targetId);
      if (!target) continue;
      const cx = target.x - DX[dir];
      const cy = target.y - DY[dir];
      if (occupied.has(`${cx},${cy}`)) continue;
      const oldX = room.x, oldY = room.y;
      room.x = cx; room.y = cy;
      const score = countMismatches(roomId);
      room.x = oldX; room.y = oldY;
      if (score < bestScore) { bestScore = score; bestPos = { x: cx, y: cy }; }
    }

    if (bestPos) {
      console.log(`  ADJUST: ${roomId} (${room.x},${room.y}) → (${bestPos.x},${bestPos.y}) [${current}→${bestScore}]`);
      room.x = bestPos.x;
      room.y = bestPos.y;
      adjustments++;
      improved = true;
    }
  }
  if (!improved) break;
}
console.log(`Made ${adjustments} mismatch-reducing adjustments\n`);

// ── Write back ──────────────────────────────────────────────────────────────

if (dryRun) {
  console.log('[DRY RUN] No files written');
} else {
  let filesWritten = 0;
  for (const [file, zone] of zoneData) {
    let changed = false;
    for (const room of zone.rooms || []) {
      const r = rooms.get(room.id);
      if (!r) continue;
      if (room.x !== r.x || room.y !== r.y) { room.x = r.x; room.y = r.y; changed = true; }
      if (JSON.stringify(room.exits) !== JSON.stringify(r.exits)) { room.exits = { ...r.exits }; changed = true; }
    }
    if (changed) {
      writeFileSync(join(worldDir, file), JSON.stringify(zone, null, 2) + '\n');
      filesWritten++;
    }
  }
  console.log(`Wrote ${filesWritten} zone files`);
}
