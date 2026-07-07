---
name: session14-midgame-test
description: Session 14 playtest attempt of mid-to-late game zones on staging - blocked by relay infrastructure issues
metadata:
  type: project
---

Session 14 (2026-05-16) attempted end-to-end playtest of mid-to-late game zones on staging (Ashwood through Bone Wastes + dungeons).

**Outcome**: Largely BLOCKED by relay infrastructure problems. Only partial observations gathered.

**Why:** Three relay-layer bugs combined to make controlled testing impossible:
1. Relay multi-fire (#405) -- file watcher triggers 4-10x per command write
2. Concurrent Claude sessions share relay-command.json (#406) -- another agent session was actively spawning relay processes
3. Stale command queue replay (#407) -- hundreds of commands from previous sessions replayed for 15+ minutes

**How to apply:** Before next mid-to-late game playtest attempt:
- Fix #405 (multi-fire) and #406 (concurrent session isolation) first
- Consider per-instance state files (e.g., relay-state-{pid}.json)
- Add server-side session displacement (allow new connection to kick old session)
- Or test on local dev server with no concurrent sessions

**Partial observations gathered**:
- Ashwood Burn: Burned Clearing has good description, Ruins Scavenger (160HP) present, 3 exits (NORTH/EAST/WEST), Blasted Vale epicenter description mentions Vatric's experiment
- Drowned Chapel: Tide-bell PUZZLE_STEP system works (bell rings but requires hymn from coast NPCs), Tidewarden Korlach boss (620HP) killed with no phase transitions, boss gives only 475 XP (#413), boss drops good loot (Tidewarden's Maw, Sealed Tide Fragment, Korlach's Coral Cuirass, Sea-Walker's Boots, 4g14s149c)
- Salt Coast: Selwyn's Fishmonger vendor has items from 15c to 2s80c, Saltpoint Square has healing aura
- Zones NOT tested: Pyromancer's Folly, First Seal Approach, Cradle of the Seal, Cracked Plains (new pass), Warlord's Hold (new pass), Glass Desert, Mirage Spire, Bone Wastes (new pass), Necropolis of Vael (new pass)

**Bugs filed this session**: #407, #413, #414(closed false positive), #422(closed dup of #416), #423(closed dup of #406), #424
