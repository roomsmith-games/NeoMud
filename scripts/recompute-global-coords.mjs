#!/usr/bin/env node
/**
 * Global BFS coordinate recomputation for NeoMud world data.
 *
 * Unlike recompute-coords.mjs (per-zone BFS), this script assigns globally
 * unique (x, y, z) coordinates from a single BFS root (town:square).
 *
 * It also applies a predefined set of exit direction changes needed to
 * separate overlapping zones in 3D space, and designates one cross-zone
 * connection as a "portal" (BFS-excluded) to break the world graph's
 * single cycle.
 *
 * Usage:
 *   node scripts/recompute-global-coords.mjs              # dry-run
 *   node scripts/recompute-global-coords.mjs --fix         # apply changes
 *   node scripts/recompute-global-coords.mjs --validate    # exit non-zero on violations
 *   node scripts/recompute-global-coords.mjs --audit-desc  # show descriptions referencing changed dirs
 */

import fs from 'fs';
import path from 'path';

const WORLD_DIR = path.resolve('maker/default_world_src/world');
const ROOT_ROOM = 'town:square';

const DX = {
  NORTH: [0, 1, 0], SOUTH: [0, -1, 0], EAST: [1, 0, 0], WEST: [-1, 0, 0],
  NORTHEAST: [1, 1, 0], NORTHWEST: [-1, 1, 0], SOUTHEAST: [1, -1, 0], SOUTHWEST: [-1, -1, 0],
  UP: [0, 0, 1], DOWN: [0, 0, -1],
};

const OPPOSITE = {
  NORTH: 'SOUTH', SOUTH: 'NORTH', EAST: 'WEST', WEST: 'EAST',
  NORTHEAST: 'SOUTHWEST', SOUTHWEST: 'NORTHEAST',
  NORTHWEST: 'SOUTHEAST', SOUTHEAST: 'NORTHWEST',
  UP: 'DOWN', DOWN: 'UP',
};

// Portal: BFS does not propagate coordinates across this connection.
// In-game it remains a normal UP/DOWN exit.
const PORTALS = [
  { from: 'first_seal:watcher_perch', to: 'glass_desert:spire_base' },
];

// Direction changes needed to produce a collision-free global layout.
// Order matters: internal rearrangements must free slots before cross-zone
// changes can claim them.
const DIRECTION_CHANGES = [
  // Marsh to z=-1 (descend into the marsh from the gorge)
  { room: 'gorge:mouth', oldDir: 'SOUTH', newDir: 'DOWN' },
  { room: 'marsh:heart', oldDir: 'NORTH', newDir: 'UP' },

  // Ashwood branch to z=-1 (descend from salt coast into ashwood)
  { room: 'salt_coast:western_cliffs', oldDir: 'WEST', newDir: 'DOWN' },
  { room: 'ashwood:scorched_trail', oldDir: 'EAST', newDir: 'UP' },

  // Forest→gorge: shift gorge east (NORTHEAST instead of NORTH)
  { room: 'forest:stream', oldDir: 'NORTH', newDir: 'NORTHEAST' },
  { room: 'gorge:fissure', oldDir: 'SOUTH', newDir: 'SOUTHWEST' },

  // Watchers barrow to z=1 (ascend into the barrow)
  { room: 'highmoor:barrow_threshold', oldDir: 'EAST', newDir: 'UP' },
  { room: 'watchers_barrow:vestibule', oldDir: 'WEST', newDir: 'DOWN' },

  // Warlords hold: rearrange internal layout first (free UP slot)
  { room: 'warlords_hold:great_hall', oldDir: 'UP', newDir: 'SOUTH' },
  { room: 'warlords_hold:upper_gallery', oldDir: 'DOWN', newDir: 'NORTH' },
  // Then cross-zone: descend from cracked plains into warlords hold
  { room: 'cracked_plains:hold_gates', oldDir: 'EAST', newDir: 'DOWN' },
  { room: 'warlords_hold:great_hall', oldDir: 'WEST', newDir: 'UP' },

  // Mirage spire deeper (DOWN from glass desert spire gates)
  { room: 'glass_desert:spire_gates', oldDir: 'SOUTH', newDir: 'DOWN' },
  { room: 'mirage_spire:entry_hall', oldDir: 'NORTH', newDir: 'UP' },

  // Bone ridge lookout: horizontal instead of vertical (avoid ashwood overlap)
  { room: 'bone_wastes:bone_ridge', oldDir: 'UP', newDir: 'NORTH' },
  { room: 'bone_wastes:bone_ridge_lookout', oldDir: 'DOWN', newDir: 'SOUTH' },

  // Necropolis: west from bone wastes instead of down (avoid skyveil overlap)
  { room: 'bone_wastes:necropolis_overlook', oldDir: 'DOWN', newDir: 'WEST' },
  { room: 'necropolis_of_vael:entrance_hall', oldDir: 'UP', newDir: 'EAST' },

  // Stormcrown: northwest from skyveil (avoid warlords overlap)
  { room: 'skyveil_reach:stormcrown_gates', oldDir: 'NORTH', newDir: 'NORTHWEST' },
  { room: 'stormcrown_keep:gatehouse', oldDir: 'SOUTH', newDir: 'SOUTHEAST' },
];

const DIR_WORDS = {
  NORTH: ['north', 'northward', 'northerly'],
  SOUTH: ['south', 'southward', 'southerly'],
  EAST: ['east', 'eastward', 'easterly'],
  WEST: ['west', 'westward', 'westerly'],
  NORTHEAST: ['northeast', 'northeastward'],
  NORTHWEST: ['northwest', 'northwestward'],
  SOUTHEAST: ['southeast', 'southeastward'],
  SOUTHWEST: ['southwest', 'southwestward'],
  UP: ['up', 'upward', 'above', 'ascend', 'climb up', 'rises', 'stairway up', 'stairs up', 'ladder up'],
  DOWN: ['down', 'downward', 'below', 'descend', 'climb down', 'drops', 'stairway down', 'stairs down', 'ladder down'],
};

function loadZones() {
  const zones = {};
  const allRooms = {};
  for (const fname of fs.readdirSync(WORLD_DIR).sort()) {
    if (!fname.endsWith('.zone.json')) continue;
    const filePath = path.join(WORLD_DIR, fname);
    const zone = JSON.parse(fs.readFileSync(filePath, 'utf8'));
    zones[zone.id] = { data: zone, file: fname, filePath };
    for (const room of zone.rooms) {
      allRooms[room.id] = { ...room, zoneId: zone.id, exits: room.exits || {}, z: room.z ?? 0 };
    }
  }
  return { zones, rooms: allRooms };
}

function applyDirectionChanges(rooms) {
  const errors = [];
  let applied = 0;
  let alreadyDone = 0;
  for (const ch of DIRECTION_CHANGES) {
    const room = rooms[ch.room];
    if (!room) { errors.push(`Room not found: ${ch.room}`); continue; }
    const target = room.exits[ch.oldDir];
    if (!target) {
      if (room.exits[ch.newDir]) { alreadyDone++; continue; }
      errors.push(`Exit not found: ${ch.room} ${ch.oldDir}`);
      continue;
    }
    if (room.exits[ch.newDir] !== undefined && room.exits[ch.newDir] !== target) {
      // newDir slot occupied by a different target — might be a multi-step
      // change on the same room where a prior step already claimed newDir.
      // Check if the change is effectively done (target reached newDir via earlier step).
      alreadyDone++;
      continue;
    }
    if (room.exits[ch.newDir] === target) { alreadyDone++; continue; }
    delete room.exits[ch.oldDir];
    room.exits[ch.newDir] = target;
    applied++;
  }
  return { errors, applied, alreadyDone };
}

function globalBFS(rooms) {
  const assigned = {};
  const queue = [ROOT_ROOM];
  assigned[ROOT_ROOM] = [0, 0, 0];
  let conflicts = 0;
  const conflictList = [];

  while (queue.length > 0) {
    const cur = queue.shift();
    const [cx, cy, cz] = assigned[cur];
    const room = rooms[cur];
    if (!room) continue;

    for (const [dir, target] of Object.entries(room.exits)) {
      if (!DX[dir]) continue;
      const isPortal = PORTALS.some(p =>
        (p.from === cur && p.to === target) || (p.from === target && p.to === cur));
      if (isPortal) continue;

      const [dx, dy, dz] = DX[dir];
      const expected = [cx + dx, cy + dy, cz + dz];

      if (assigned[target] !== undefined) {
        const [ax, ay, az] = assigned[target];
        if (ax !== expected[0] || ay !== expected[1] || az !== expected[2]) {
          conflicts++;
          conflictList.push({ from: cur, to: target, dir, expected, actual: [ax, ay, az] });
        }
      } else {
        assigned[target] = expected;
        queue.push(target);
      }
    }
  }

  return { assigned, conflicts, conflictList };
}

function findCollisions(assigned) {
  const coordMap = {};
  for (const [id, [x, y, z]] of Object.entries(assigned)) {
    const key = `${x},${y},${z}`;
    if (!coordMap[key]) coordMap[key] = [];
    coordMap[key].push(id);
  }
  return Object.entries(coordMap)
    .filter(([, v]) => v.length > 1)
    .map(([coord, roomIds]) => ({ coord, roomIds }));
}

function checkReciprocals(rooms) {
  const issues = [];
  for (const [id, room] of Object.entries(rooms)) {
    for (const [dir, target] of Object.entries(room.exits)) {
      if (!rooms[target]) { issues.push({ type: 'broken', from: id, to: target, dir }); continue; }
      const opp = OPPOSITE[dir];
      if (!opp) continue;
      const hasReturn = Object.values(rooms[target].exits).includes(id);
      if (!hasReturn) issues.push({ type: 'one_way', from: id, to: target, dir });
    }
  }
  return issues;
}

function checkContinuity(rooms) {
  const zoneRooms = {};
  for (const [id, room] of Object.entries(rooms)) {
    if (!zoneRooms[room.zoneId]) zoneRooms[room.zoneId] = new Set();
    zoneRooms[room.zoneId].add(id);
  }

  const issues = [];
  for (const [zoneId, roomSet] of Object.entries(zoneRooms)) {
    const start = roomSet.values().next().value;
    const visited = new Set([start]);
    const queue = [start];
    while (queue.length > 0) {
      const cur = queue.shift();
      for (const target of Object.values(rooms[cur].exits)) {
        if (roomSet.has(target) && !visited.has(target)) {
          visited.add(target);
          queue.push(target);
        }
      }
    }
    if (visited.size < roomSet.size) {
      const unreachable = [...roomSet].filter(id => !visited.has(id));
      issues.push({ zone: zoneId, reachable: visited.size, total: roomSet.size, unreachable });
    }
  }
  return issues;
}

function auditDescriptions(zones) {
  const flags = [];
  for (const ch of DIRECTION_CHANGES) {
    const zoneId = ch.room.split(':')[0];
    const roomLocalId = ch.room;
    const zone = zones[zoneId];
    if (!zone) continue;
    const room = zone.data.rooms.find(r => r.id === roomLocalId);
    if (!room) continue;

    const oldWords = DIR_WORDS[ch.oldDir] || [];
    const desc = (room.description || '').toLowerCase();
    const name = (room.name || '').toLowerCase();
    const text = desc + ' ' + name;

    for (const word of oldWords) {
      if (text.includes(word)) {
        flags.push({
          room: ch.room,
          oldDir: ch.oldDir,
          newDir: ch.newDir,
          matchedWord: word,
          description: room.description,
        });
        break;
      }
    }

    // Also check the TARGET room's description for old return direction
    const targetRoomId = Object.values(room.exits).find((_, i) =>
      Object.keys(room.exits)[i] === ch.newDir) || null;
    if (targetRoomId) {
      const targetZoneId = targetRoomId.split(':')[0];
      const tZone = zones[targetZoneId];
      if (tZone) {
        const tRoom = tZone.data.rooms.find(r => r.id === targetRoomId);
        if (tRoom) {
          const oldReturn = OPPOSITE[ch.oldDir];
          const returnWords = DIR_WORDS[oldReturn] || [];
          const tText = ((tRoom.description || '') + ' ' + (tRoom.name || '')).toLowerCase();
          for (const word of returnWords) {
            if (tText.includes(word)) {
              flags.push({
                room: targetRoomId,
                oldDir: oldReturn,
                newDir: OPPOSITE[ch.newDir],
                matchedWord: word,
                description: tRoom.description,
              });
              break;
            }
          }
        }
      }
    }
  }
  return flags;
}

function writeZoneFiles(zones, rooms, assigned) {
  let filesWritten = 0;
  for (const [, zoneInfo] of Object.entries(zones)) {
    let modified = false;
    for (const room of zoneInfo.data.rooms) {
      const newCoord = assigned[room.id];
      if (!newCoord) continue;
      const [nx, ny, nz] = newCoord;
      if (room.x !== nx || room.y !== ny || (room.z ?? 0) !== nz) {
        room.x = nx;
        room.y = ny;
        room.z = nz;
        modified = true;
      }
      // Sync exits from the modified rooms map
      const modRoom = rooms[room.id];
      if (modRoom && JSON.stringify(room.exits) !== JSON.stringify(modRoom.exits)) {
        room.exits = { ...modRoom.exits };
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

function printVisualMap(assigned, rooms) {
  const zLevels = {};
  for (const [id, [x, y, z]] of Object.entries(assigned)) {
    if (!zLevels[z]) zLevels[z] = [];
    zLevels[z].push({ id, x, y, zone: rooms[id].zoneId });
  }

  const ZONE_CHARS = {};
  const ZONE_NAMES = Object.keys(
    Object.values(rooms).reduce((a, r) => { a[r.zoneId] = 1; return a; }, {})
  ).sort();
  const chars = 'TABCDEFGHIJKLMNOPQRSUVWXYZ0123456789abcdefghijklmnopqrstuvwxyz';
  ZONE_NAMES.forEach((z, i) => { ZONE_CHARS[z] = chars[i % chars.length]; });

  for (const z of Object.keys(zLevels).map(Number).sort((a, b) => b - a)) {
    const layer = zLevels[z];
    const minX = Math.min(...layer.map(r => r.x));
    const maxX = Math.max(...layer.map(r => r.x));
    const minY = Math.min(...layer.map(r => r.y));
    const maxY = Math.max(...layer.map(r => r.y));

    console.log(`\n=== Z = ${z} (${layer.length} rooms) ===`);

    const grid = {};
    for (const r of layer) grid[`${r.x},${r.y}`] = ZONE_CHARS[r.zone] || '?';

    for (let y = maxY; y >= minY; y--) {
      let row = '';
      for (let x = minX; x <= maxX; x++) {
        row += grid[`${x},${y}`] || '.';
      }
      const yLabel = String(y).padStart(3);
      console.log(`  ${yLabel} |${row}|`);
    }

    const xLabels = [];
    for (let x = minX; x <= maxX; x++) xLabels.push(String(Math.abs(x % 10)));
    console.log(`      ${xLabels.join('')}`);
  }

  console.log('\nLegend:');
  for (const [zone, ch] of Object.entries(ZONE_CHARS).sort((a, b) => a[0].localeCompare(b[0]))) {
    const count = Object.values(rooms).filter(r => r.zoneId === zone).length;
    console.log(`  ${ch} = ${zone} (${count} rooms)`);
  }
}

function main() {
  const args = process.argv.slice(2);
  const doFix = args.includes('--fix');
  const doValidate = args.includes('--validate');
  const doAuditDesc = args.includes('--audit-desc');
  const showMap = args.includes('--map');

  console.log('=== NeoMud Global Coordinate System ===\n');

  const { zones, rooms } = loadZones();
  const totalRooms = Object.keys(rooms).length;
  const totalZones = Object.keys(zones).length;
  console.log(`Loaded ${totalRooms} rooms from ${totalZones} zones`);

  // Apply direction changes
  const { errors: changeErrors, applied, alreadyDone } = applyDirectionChanges(rooms);
  if (changeErrors.length) {
    console.log('\nDIRECTION CHANGE ERRORS:');
    changeErrors.forEach(e => console.log(`  ${e}`));
    if (doValidate) process.exit(1);
  }
  if (alreadyDone === DIRECTION_CHANGES.length) {
    console.log(`Direction changes already applied (${alreadyDone} exits in target state)`);
  } else if (applied > 0) {
    console.log(`Applied ${applied} direction changes, ${alreadyDone} already done`);
  }

  // Global BFS
  const { assigned, conflicts, conflictList } = globalBFS(rooms);
  console.log(`\nBFS from ${ROOT_ROOM}: ${Object.keys(assigned).length}/${totalRooms} rooms assigned`);

  if (conflicts > 0) {
    console.log(`\nBFS CONFLICTS: ${conflicts}`);
    conflictList.forEach(c =>
      console.log(`  ${c.from} ${c.dir} -> ${c.to}: expected (${c.expected.join(',')}) got (${c.actual.join(',')})`)
    );
  }

  // Unassigned rooms
  const unassigned = Object.keys(rooms).filter(id => !assigned[id]);
  if (unassigned.length) {
    console.log(`\nUNASSIGNED ROOMS: ${unassigned.length}`);
    unassigned.forEach(r => console.log(`  ${r}`));
  }

  // Collisions
  const collisions = findCollisions(assigned);
  if (collisions.length) {
    console.log(`\nCOORDINATE COLLISIONS: ${collisions.length}`);
    collisions.forEach(c => console.log(`  (${c.coord}): ${c.roomIds.join(', ')}`));
  }

  // Reciprocals
  const recipIssues = checkReciprocals(rooms);
  const broken = recipIssues.filter(i => i.type === 'broken');
  const oneWay = recipIssues.filter(i => i.type === 'one_way');
  if (broken.length) {
    console.log(`\nBROKEN EXITS: ${broken.length}`);
    broken.forEach(i => console.log(`  ${i.from} ${i.dir} -> ${i.to} (target not found)`));
  }
  if (oneWay.length) {
    console.log(`\nONE-WAY EXITS: ${oneWay.length}`);
    oneWay.forEach(i => console.log(`  ${i.from} ${i.dir} -> ${i.to}`));
  }

  // Zone continuity
  const contIssues = checkContinuity(rooms);
  if (contIssues.length) {
    console.log(`\nDISCONTINUOUS ZONES: ${contIssues.length}`);
    contIssues.forEach(i => {
      console.log(`  ${i.zone}: ${i.reachable}/${i.total} reachable`);
      i.unreachable.forEach(r => console.log(`    unreachable: ${r}`));
    });
  }

  // Description audit
  if (doAuditDesc || doFix) {
    const descFlags = auditDescriptions(zones);
    if (descFlags.length) {
      console.log(`\nDESCRIPTION AUDIT: ${descFlags.length} rooms reference changed directions`);
      for (const f of descFlags) {
        console.log(`  ${f.room}: "${f.matchedWord}" (was ${f.oldDir}, now ${f.newDir})`);
        console.log(`    ${f.description?.slice(0, 120)}...`);
      }
    } else {
      console.log('\nDESCRIPTION AUDIT: no direction references found in affected rooms');
    }
  }

  // Summary
  const issues = conflicts + collisions.length + broken.length + oneWay.length +
    unassigned.length + contIssues.length + (changeErrors?.length || 0);
  console.log(`\n=== SUMMARY ===`);
  console.log(`  Rooms: ${totalRooms} | Assigned: ${Object.keys(assigned).length}`);
  console.log(`  BFS conflicts: ${conflicts} | Collisions: ${collisions.length}`);
  console.log(`  Broken exits: ${broken.length} | One-way: ${oneWay.length}`);
  console.log(`  Discontinuous zones: ${contIssues.length}`);
  console.log(`  Total issues: ${issues}`);

  // Coordinate ranges
  const coords = Object.values(assigned);
  if (coords.length) {
    const xs = coords.map(c => c[0]), ys = coords.map(c => c[1]), zs = coords.map(c => c[2]);
    console.log(`  X: ${Math.min(...xs)} to ${Math.max(...xs)}`);
    console.log(`  Y: ${Math.min(...ys)} to ${Math.max(...ys)}`);
    console.log(`  Z: ${Math.min(...zs)} to ${Math.max(...zs)}`);
  }

  // Visual map
  if (showMap || doFix) {
    printVisualMap(assigned, rooms);
  }

  // Apply
  if (doFix) {
    if (issues > 0) {
      console.log('\nCANNOT FIX: resolve issues above first.');
      process.exit(1);
    }
    const written = writeZoneFiles(zones, rooms, assigned);
    console.log(`\nWrote ${written} zone files with global coordinates.`);
  }

  if (doValidate) {
    process.exit(issues > 0 ? 1 : 0);
  }
}

main();
