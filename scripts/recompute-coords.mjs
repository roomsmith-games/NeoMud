#!/usr/bin/env node
/**
 * BFS-based coordinate recomputation for NeoMud world data.
 * Walks every exit from a root room, assigns (x, y, z) based on direction deltas,
 * and detects/fixes all coordinate inconsistencies.
 *
 * Usage:
 *   node scripts/recompute-coords.mjs              # dry-run (report only)
 *   node scripts/recompute-coords.mjs --fix         # rewrite zone JSONs with correct coords
 *   node scripts/recompute-coords.mjs --fix-all     # fix coords AND exit directions
 *   node scripts/recompute-coords.mjs --lint        # exit non-zero on any violation
 */

import fs from 'fs';
import path from 'path';

const WORLD_DIR = path.resolve('maker/default_world_src/world');
const ROOT_ROOM = 'town:square';

const DX = {
  NORTH: 0, SOUTH: 0, EAST: 1, WEST: -1,
  NORTHEAST: 1, NORTHWEST: -1, SOUTHEAST: 1, SOUTHWEST: -1,
  UP: 0, DOWN: 0,
};
const DY = {
  NORTH: 1, SOUTH: -1, EAST: 0, WEST: 0,
  NORTHEAST: 1, NORTHWEST: 1, SOUTHEAST: -1, SOUTHWEST: -1,
  UP: 0, DOWN: 0,
};
const DZ = {
  NORTH: 0, SOUTH: 0, EAST: 0, WEST: 0,
  NORTHEAST: 0, NORTHWEST: 0, SOUTHEAST: 0, SOUTHWEST: 0,
  UP: 1, DOWN: -1,
};

const OPPOSITES = {
  NORTH: 'SOUTH', SOUTH: 'NORTH', EAST: 'WEST', WEST: 'EAST',
  NORTHEAST: 'SOUTHWEST', SOUTHWEST: 'NORTHEAST',
  NORTHWEST: 'SOUTHEAST', SOUTHEAST: 'NORTHWEST',
  UP: 'DOWN', DOWN: 'UP',
};

function deltaToDirection(dx, dy, dz) {
  for (const [dir, ddx] of Object.entries(DX)) {
    if (ddx === dx && DY[dir] === dy && DZ[dir] === dz) return dir;
  }
  return null;
}

function loadZones() {
  const zones = {};
  const rooms = {};
  for (const fname of fs.readdirSync(WORLD_DIR).sort()) {
    if (!fname.endsWith('.zone.json')) continue;
    const filePath = path.join(WORLD_DIR, fname);
    const zone = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    zones[zone.id] = { data: zone, file: fname, filePath };
    for (const room of zone.rooms) {
      rooms[room.id] = { ...room, zoneId: zone.id, exits: room.exits || {}, z: room.z ?? 0 };
    }
  }
  return { zones, rooms };
}

function bfsAssignCoords(rooms, rootId) {
  const assigned = new Map();
  const conflicts = [];
  if (!rooms[rootId]) { console.error(`Root '${rootId}' not found!`); process.exit(1); }

  // Phase 1: Global BFS for reachability only (follows all exits)
  const reachable = new Set();
  {
    const q = [rootId]; reachable.add(rootId); let h = 0;
    while (h < q.length) {
      const cid = q[h++];
      for (const [, tid] of Object.entries(rooms[cid].exits)) {
        if (!rooms[tid] || reachable.has(tid)) continue;
        reachable.add(tid); q.push(tid);
      }
    }
  }

  // Phase 2: Per-zone BFS for coordinate assignment (only follows intra-zone exits)
  const zoneRooms = {};
  for (const [rid, room] of Object.entries(rooms)) {
    if (!zoneRooms[room.zoneId]) zoneRooms[room.zoneId] = [];
    zoneRooms[room.zoneId].push(rid);
  }

  for (const [, roomIds] of Object.entries(zoneRooms)) {
    const zoneRoot = roomIds.includes(rootId) ? rootId : roomIds[0];
    assigned.set(zoneRoot, { x: 0, y: 0, z: 0 });
    const queue = [zoneRoot];
    let head = 0;
    while (head < queue.length) {
      const cid = queue[head++];
      const cc = assigned.get(cid);
      for (const [dir, tid] of Object.entries(rooms[cid].exits)) {
        if (!rooms[tid]) continue;
        if (rooms[tid].zoneId !== rooms[cid].zoneId) continue; // skip cross-zone
        const ex = cc.x + (DX[dir] ?? 0), ey = cc.y + (DY[dir] ?? 0), ez = cc.z + (DZ[dir] ?? 0);
        if (assigned.has(tid)) {
          const a = assigned.get(tid);
          if (a.x !== ex || a.y !== ey || a.z !== ez) {
            conflicts.push({ from: cid, to: tid, dir, expected: { x: ex, y: ey, z: ez }, actual: a });
          }
        } else {
          assigned.set(tid, { x: ex, y: ey, z: ez });
          queue.push(tid);
        }
      }
    }
  }

  const unreachable = Object.keys(rooms).filter(id => !reachable.has(id));
  return { assigned, conflicts, unreachable };
}

function findOverlaps(assigned, rooms) {
  const byCoord = new Map();
  for (const [rid, c] of assigned.entries()) {
    const key = `${rooms[rid].zoneId}:${c.x},${c.y},${c.z}`;
    if (!byCoord.has(key)) byCoord.set(key, []);
    byCoord.get(key).push(rid);
  }
  return [...byCoord.entries()].filter(([, rids]) => rids.length > 1).map(([key, roomIds]) => ({ key, roomIds }));
}

function findNonReciprocals(rooms) {
  const issues = [];
  for (const [rid, room] of Object.entries(rooms)) {
    for (const [dir, tid] of Object.entries(room.exits)) {
      if (!rooms[tid]) continue;
      const target = rooms[tid];
      const hasReturn = Object.values(target.exits).includes(rid);
      if (!hasReturn) { issues.push({ from: rid, to: tid, dir, type: 'no_return' }); continue; }
      const opp = OPPOSITES[dir];
      for (const [tdir, ttid] of Object.entries(target.exits)) {
        if (ttid === rid && tdir !== opp) {
          issues.push({ from: rid, to: tid, dir, returnDir: tdir, expectedReturn: opp, type: 'wrong_dir' });
        }
      }
    }
  }
  return issues;
}

function suggestDirectionFixes(conflicts, assigned) {
  const fixes = [];
  const seen = new Set();

  for (const c of conflicts) {
    const key = [c.from, c.to].sort().join('|');
    if (seen.has(key)) continue;
    seen.add(key);

    const fromCoord = assigned.get(c.from);
    const toCoord = assigned.get(c.to);
    const dx = toCoord.x - fromCoord.x;
    const dy = toCoord.y - fromCoord.y;
    const dz = toCoord.z - fromCoord.z;

    const correctDir = deltaToDirection(dx, dy, dz);
    const correctReturn = correctDir ? OPPOSITES[correctDir] : null;

    // Find current directions
    const fwdConflict = conflicts.find(cc => cc.from === c.from && cc.to === c.to);
    const revConflict = conflicts.find(cc => cc.from === c.to && cc.to === c.from);
    const currentFwd = fwdConflict?.dir;
    const currentRev = revConflict?.dir;

    fixes.push({
      from: c.from,
      to: c.to,
      delta: { dx, dy, dz },
      currentFwd,
      currentRev,
      suggestedFwd: correctDir,
      suggestedRev: correctReturn,
      fixable: !!correctDir,
    });
  }
  return fixes;
}

function applyCoordFixes(zones, rooms, assigned) {
  let filesWritten = 0;
  for (const [, zoneInfo] of Object.entries(zones)) {
    let modified = false;
    for (const room of zoneInfo.data.rooms) {
      const nc = assigned.get(room.id);
      if (!nc) continue;
      if (room.x !== nc.x || room.y !== nc.y || (room.z ?? 0) !== nc.z) {
        room.x = nc.x; room.y = nc.y; room.z = nc.z;
        modified = true;
      } else if (room.z === undefined) {
        room.z = nc.z;
        modified = true;
      }
    }
    if (modified) {
      fs.writeFileSync(zoneInfo.filePath, JSON.stringify(zoneInfo.data, null, 2) + '\n', 'utf8');
      filesWritten++;
    }
  }
  return filesWritten;
}

function applyDirectionFixes(zones, fixes) {
  const fixableOnly = fixes.filter(f => f.fixable);
  if (fixableOnly.length === 0) return 0;

  // Build lookup: roomId -> zone data object
  const roomToZone = {};
  for (const [, zi] of Object.entries(zones)) {
    for (const room of zi.data.rooms) {
      roomToZone[room.id] = { room, zoneInfo: zi };
    }
  }

  const modifiedFiles = new Set();
  for (const fix of fixableOnly) {
    // Fix forward direction
    const fwd = roomToZone[fix.from];
    if (fwd && fix.currentFwd && fix.suggestedFwd && fix.currentFwd !== fix.suggestedFwd) {
      const targetId = fwd.room.exits[fix.currentFwd];
      if (targetId === fix.to) {
        delete fwd.room.exits[fix.currentFwd];
        fwd.room.exits[fix.suggestedFwd] = fix.to;
        modifiedFiles.add(fwd.zoneInfo.filePath);
      }
    }

    // Fix reverse direction
    const rev = roomToZone[fix.to];
    if (rev && fix.currentRev && fix.suggestedRev && fix.currentRev !== fix.suggestedRev) {
      const targetId = rev.room.exits[fix.currentRev];
      if (targetId === fix.from) {
        delete rev.room.exits[fix.currentRev];
        rev.room.exits[fix.suggestedRev] = fix.from;
        modifiedFiles.add(rev.zoneInfo.filePath);
      }
    }
  }

  for (const fp of modifiedFiles) {
    const zi = Object.values(zones).find(z => z.filePath === fp);
    if (zi) fs.writeFileSync(fp, JSON.stringify(zi.data, null, 2) + '\n', 'utf8');
  }
  return modifiedFiles.size;
}

function main() {
  const args = process.argv.slice(2);
  const doFix = args.includes('--fix') || args.includes('--fix-all');
  const doFixAll = args.includes('--fix-all');
  const doLint = args.includes('--lint');

  console.log('=== NeoMud World Coordinate Recomputation ===\n');
  const { zones, rooms } = loadZones();
  console.log(`Loaded ${Object.keys(rooms).length} rooms from ${Object.keys(zones).length} zones\n`);

  // Check dangling
  const dangling = [];
  for (const [rid, room] of Object.entries(rooms)) {
    for (const [dir, tid] of Object.entries(room.exits)) {
      if (!rooms[tid]) dangling.push({ from: rid, to: tid, dir });
    }
  }
  if (dangling.length) {
    console.log(`DANGLING EXITS: ${dangling.length}`);
    dangling.forEach(d => console.log(`  ${d.from} --${d.dir}--> ${d.to}`));
    console.log();
  }

  // Check reciprocals
  const nonRecip = findNonReciprocals(rooms);
  const noReturn = nonRecip.filter(r => r.type === 'no_return');
  const wrongDir = nonRecip.filter(r => r.type === 'wrong_dir');
  if (noReturn.length) {
    console.log(`NON-RECIPROCAL EXITS: ${noReturn.length}`);
    noReturn.forEach(r => console.log(`  ${r.from} --${r.dir}--> ${r.to}`));
    console.log();
  }
  if (wrongDir.length) {
    console.log(`WRONG-DIRECTION RECIPROCALS: ${wrongDir.length}`);
    wrongDir.forEach(r => console.log(`  ${r.from} --${r.dir}--> ${r.to}, return via ${r.returnDir} (expected ${r.expectedReturn})`));
    console.log();
  }

  // BFS
  const { assigned, conflicts, unreachable } = bfsAssignCoords(rooms, ROOT_ROOM);

  if (unreachable.length) {
    console.log(`UNREACHABLE: ${unreachable.length}`);
    unreachable.forEach(r => console.log(`  ${r}`));
    console.log();
  }

  // Suggest direction fixes for conflicts
  const dirFixes = suggestDirectionFixes(conflicts, assigned);
  const fixable = dirFixes.filter(f => f.fixable);
  const unfixable = dirFixes.filter(f => !f.fixable);

  if (fixable.length) {
    console.log(`FIXABLE DIRECTION CONFLICTS (${fixable.length}):`);
    fixable.forEach(f => {
      console.log(`  ${f.from} -> ${f.to}: ${f.currentFwd || '?'}/${f.currentRev || '?'} → ${f.suggestedFwd}/${f.suggestedRev} (delta ${f.delta.dx},${f.delta.dy},${f.delta.dz})`);
    });
    console.log();
  }

  if (unfixable.length) {
    console.log(`UNFIXABLE CONFLICTS — need restructuring (${unfixable.length}):`);
    unfixable.forEach(f => {
      console.log(`  ${f.from} -> ${f.to}: delta (${f.delta.dx},${f.delta.dy},${f.delta.dz}) — no valid direction`);
    });
    console.log();
  }

  // Overlaps
  const overlaps = findOverlaps(assigned, rooms);
  if (overlaps.length) {
    console.log(`OVERLAPS (${overlaps.length}):`);
    overlaps.forEach(o => console.log(`  ${o.key}: ${o.roomIds.join(', ')}`));
    console.log();
  }

  // Changes
  const changes = [];
  for (const [rid, nc] of assigned.entries()) {
    const r = rooms[rid];
    const oz = r.z ?? 0;
    if (r.x !== nc.x || r.y !== nc.y || oz !== nc.z) {
      changes.push({ roomId: rid, zoneId: r.zoneId, old: { x: r.x, y: r.y, z: oz }, new: nc });
    }
  }

  // Summary
  console.log('=== SUMMARY ===');
  console.log(`  Rooms: ${Object.keys(rooms).length} | Reachable: ${assigned.size}`);
  console.log(`  Dangling: ${dangling.length} | Non-reciprocal: ${noReturn.length} | Wrong-dir: ${wrongDir.length}`);
  console.log(`  Fixable conflicts: ${fixable.length} | Unfixable: ${unfixable.length} | Overlaps: ${overlaps.length}`);
  console.log(`  Coord changes needed: ${changes.length}`);

  const totalIssues = dangling.length + noReturn.length + wrongDir.length + unfixable.length + overlaps.length;

  if (doFix) {
    if (changes.length) {
      const n = applyCoordFixes(zones, rooms, assigned);
      console.log(`\nApplied coordinate fixes to ${n} zone file(s).`);
    }
    if (doFixAll && fixable.length) {
      // Reload after coord fixes
      const reloaded = loadZones();
      const n = applyDirectionFixes(reloaded.zones, dirFixes);
      console.log(`Applied direction fixes to ${n} zone file(s).`);
      console.log('\nRe-running BFS to verify...\n');
      // Re-run to verify
      const r2 = loadZones();
      const bfs2 = bfsAssignCoords(r2.rooms, ROOT_ROOM);
      const fixes2 = suggestDirectionFixes(bfs2.conflicts, bfs2.assigned);
      const overlaps2 = findOverlaps(bfs2.assigned, r2.rooms);
      console.log(`After fix: ${bfs2.conflicts.length} conflicts, ${overlaps2.length} overlaps, ${bfs2.unreachable.length} unreachable`);
      if (fixes2.filter(f => !f.fixable).length) {
        console.log(`Still unfixable: ${fixes2.filter(f => !f.fixable).length}`);
        fixes2.filter(f => !f.fixable).forEach(f => {
          console.log(`  ${f.from} -> ${f.to}: delta (${f.delta.dx},${f.delta.dy},${f.delta.dz})`);
        });
      }
    }
  } else if (doLint) {
    if (totalIssues > 0 || changes.length > 0) {
      console.log(`\nLINT FAILED: ${totalIssues} structural issues, ${changes.length} coord changes needed.`);
      process.exit(1);
    }
    console.log('\nLINT PASSED.');
  } else {
    console.log('\nRun --fix for coords only, --fix-all for coords + directions.');
  }
}

main();
