# Playtester Agent Memory

## Characters Created
- **Brunak Ironbeard** (username: brunak01) -- DWARF WARRIOR, Level 1 (previous session)
- **Aelindra Starweave** (username: elfmage02, password: testpass123) -- ELF MAGE, Level 1, 66/100 XP
- **Gromm Thunderfist** (username: audiotest1, password: testpass123) -- HALF_ORC WARRIOR, Level 1, 329/100 XP (cannot level up)
- **Sister Meridia** (username: clerictest1, password: testpass123) -- HUMAN CLERIC, Level 1, 176/100 XP (cannot level up)
- **Newbie** (username: tutorial_tester, password: test1234) -- HUMAN WARRIOR, Level 1, 177/100 XP (new player experience test)
- **BoneWalker** (staging, platform auth admin@neomud.app) -- HUMAN WARRIOR, Level 25 (admin setlevel), Phase 8H playtest
- **UatTester** (staging, platform auth admin@neomud.app) -- HUMAN WARRIOR, Level 30 (admin setlevel), Phase 8J playtest
- **Bob** (username: bob, password: testpass123) -- HUMAN WARRIOR, Level 30 (admin setlevel), pre-release hardening pass (local dev, Sessions 11+13)
- **SmokeBot** (staging, platform auth admin@neomud.app, shattered-reach world) -- HUMAN WARRIOR, Level 1, Shattered Reach smoke test (Session 12)
- **Hardtest** (username: hardtest1, password: testpass123) -- HUMAN PALADIN, Level 1, pre-release hardening new player test (local dev, Session 13)

## Areas Explored
- **Town**: Temple of the Dawn (respawn, healing aura), Town Square (trainer), Market Street (blacksmith vendor), The Enchanted Emporium (magic vendor), The Rusty Tankard (barkeep vendor), North Gate (guard), Grimjaw's Forge (NPC crafter, not a vendor)
- **Forest**: Forest Edge (rats, bandits, spiders, wolves), Winding Forest Path (hidden passage north), Sunlit Clearing (safe rest spot), Deep Forest (wolves, spiders, bandits)
- **Forest/Marsh Transition**: Overgrown Ruins (wolves, leads north to marsh)
- **Marsh**: Marsh Edge (Bog Lurkers -- 14 dmg per hit, instant death for level 1)
- **Locked**: Tavern Cellar (DOWN from tavern, "The way down is locked")
- **Iron Vein Mine** (dungeon, ~10 rooms, L8-12 estimated): mine_entrance (from forest), main_shaft, east_shaft, flooded_passage, abandoned_chamber (lever EXIT_OPEN interactable), hidden_alcove (TREASURE_DROP chest: Health Potion + Mana Potion + Foremans Iron Key + 181c 2s), deep_chamber, webbed_corridor (DAMAGE_TRAP sticky silk), cavemother_lair (boss: Cavemother Vrelda 420HP), treasure_chamber (empty, bug #389). NPCs: Cave Spider, Mine Ghoul (130HP, 28-35 dmg), Cavemother Vrelda (420HP, 40-53 dmg boss). Boss drops: Cavemothers Fang + Greater Health Potion + 2g 8s 85c.
- **Salt Coast** (overworld, L14-16 estimated): Saltpoint Square (hub), Saltpoint Dock (Captain Mara Tenwick quest NPC), Saltpoint Inn, Fishmonger, Long Pier, Tide Pools, Chapel Path (Riptide Crab 295HP 44-53 dmg), Chapel Steps, Chapel Threshold (cross-zone to drowned_chapel:vestibule), North Lookout, Saltpoint Outskirts.
- **Drowned Chapel** (dungeon, ~10 rooms, L14-16): vestibule (entry from salt_coast:chapel_threshold), flooded_narthex, nave, north_side_chapel (tide-bell #1), south_side_chapel (tide-bell #2), choir_loft (tide-bell #3), bell_tower (tide-bell #4), sanctum (tide-bell #5, PUZZLE_STEP gate), altar_chamber (boss: Tidewarden Korlach 620HP, 475 XP), reliquary. PUZZLE_STEP tide-bell system with 5 bells in sequence. Atmospheric rot/brine theme.
- **Watcher's Barrow** (dungeon, ~10 rooms, discovered Session 14): Entry from highmoor via DOWN. Contains sealed-eye gate (PUZZLE_STEP), boss ~540HP. Not fully tested due to relay issues.
- **Highmoor** (overworld, ~17 rooms, L16-18 estimated): Star Cairn PUZZLE_STEP interactables, Mountain Warden NPC, very sparse hostile NPC spawns (#408). Multiple rooms explored but relay multi-fire prevented systematic mapping.
- **Foothills** (~12 rooms): Vendor present, Mountain Warden NPC, connects to Skyveil Reach.
- **Skyveil Reach** (partially explored): sky_bridge, storm_ridge, windswept_crossing, skyveil_plateau (exits to archons_camp with T4 trainer Archon Veska). Not fully tested.
- **Cracked Plains** (23 rooms, L18-20): All rooms explored. Surface: rift_edge_south (entry from Ashwood via DOWN/UP), broken_bridge, east_plateau, collapsed_shelf, east_bluffs, cliff_roost, bone_field, war_road, north_plateau, west_ridge, signal_cairn (quest NPC Haela), ruined_fort, hold_approach, hold_gates, bandit_camp, bandit_watchtower. Rift floor: rift_floor_south, rift_tunnel, rift_junction, rift_floor_east, rift_spring (vendor Krenn), rift_floor_north, fungal_grotto. 12 vertical exits tested (all work except #316 one-way).
- **Warlord's Hold** (10 rooms, L19-20 dungeon): great_hall (hub), guard_hall, trophy_room (Lieutenant sub-boss 480HP), upper_gallery, armory, battlements, war_room (Warlord Drask boss 900HP), underhall, cells, vault (PLACE_ITEM locked door)
- **Bone Wastes** (23 rooms, L22-25 overworld): All rooms explored. Entry: glass_edge (WEST from glass_desert:buried_road). Key rooms: bone_spire (vendor Rask), death_gate (quest NPC Scholar Meris), necropolis_overlook (DOWN to dungeon), bone_ridge_lookout (vista), skull_ravine_rim/skull_ravine (vertical), bone_wastes_edge (forward stub). All exits bidirectional. Traps: Unstable Ground (fallen_standard), others.
- **Necropolis of Vael** (16+1 rooms, L23-25 dungeon): entrance_hall (entry from overlook), processional (hub, shortcut DOWN to sealed_passage), grand_atrium (hub, 4 exits), catacomb_landing, bone_passage, embalming_hall, descent_of_kings, east/west_crypt, ossuary_vault, dust_chamber, stairwell_deep, ritual_antechamber, khaim_sanctum (boss: Death-Speaker Khaim 1100HP), warding_circle, sealed_passage (PLACE_ITEM locked EAST to vault). Vault of the Seal-Keepers (behind locked door). All exits bidirectional. Traps: Carved Relief Panel, Pooled Chemical Residue, Disturbed Dust Layer.
- **Sealed Threshold** (22 rooms, L25-29, Phase 8J): Entry via stormcrown_keep:stormcrown_throne NORTH (EXIT_OPEN locked). Shared approach (10): descent, carved_passage, ward_corridor, guardians_alcove (Threshold Guardian 800HP mini-boss), seal_gallery, echo_chamber, outer_seal_ring, oracles_sanctum (Oracle trainer T5), threshold_gate (CONDITIONAL_TRIGGER, needs Threshold Key), fracture_chamber (CHOICE_PROMPT hub). Seal branch (6, WEST): seal_approach, seal_corridor, seal_ward_room, seal_antechamber, seal_sanctum (The Sealed One seal 1800HP boss), seal_epilogue (Seal Warden Spirit + TREASURE_DROP). Sunder branch (6, EAST): sunder_approach, sunder_corridor, sunder_chaos_room, sunder_antechamber, sunder_sanctum (The Sealed One sunder 1400HP boss), sunder_epilogue (Freed Presence + TREASURE_DROP). Traps: Ward Surge, Resonance Burst, Fracture Detonation. All exits bidirectional. Cross-zone exit to stormcrown_keep works.

## Known Bugs Already Reported
- #53 -- Multiple relay instances corrupt shared state file
- #54 -- Forest Edge spider kills level 1 players instantly with no reaction time
- #55 -- No way to discover available spells -- mage cannot figure out what to cast
- #56 -- pickup_coins does not support "all" coin type
- #57 -- No HP regeneration outside of potions (REST skill now exists, addresses this)
- #58 -- Hostile NPCs attack immediately with no grace period for new arrivals
- #104 -- scroll_of_fireball references wrong audio path (audio/items/spell_cast.mp3 but file is in audio/spells/)
- #105 -- Missing BGM files for Marsh and Gorge zones (marsh_danger.mp3, gorge_danger.mp3)
- #107 -- Kick skill description does not mention required direction parameter
- #109 -- No way to discover level up command -- trainer says ready but won't level you up
- #110 -- Rustic Dagger uses sword sounds instead of dagger-specific sounds (dagger_slash/dagger_miss exist but unused)
- #223 -- Cleric Minor Heal heals far less than Smite damages for same mana cost (6 HP heal vs 29 dmg)
- #224 -- NPC respawn is too fast -- new hostile spawns 1 second after kill
- #225 -- Rest skill event shows raw roll number -- debug info leaking to players
- #226 -- Consumable items outclass Cleric healing spells at level 1
- #233 -- No welcome message, tutorial, or gameplay hints on first login
- #234 -- Non-combat NPCs display 0/0 HP
- #235 -- No vendor announcement when entering shop rooms
- #236 -- No crafter announcement or discoverable interaction for Grimjaw's Forge
- #237 -- Purchased items not auto-equipped into empty slots
- #238 -- Loot items have no description -- players can't tell their purpose
- #239 -- Hostile NPCs attack instantly with no warning for new players
- #240 -- Tavern cellar locked with no hint about how to unlock
- #241 -- Vendor shop does not show item stats or comparison to equipped gear
- #242 -- NPC targeting requires exact instance ID with # suffix
- #243 -- Say command produces no visible feedback to the speaker
- #244 -- Forest Edge often empty -- new player waits for first enemy
- #316 -- One-way exit: rift_floor_south UP leads to rift_edge_south but no DOWN return path
- #317 -- Hostile NPCs wander into rift_spring vendor room and attack during shopping
- #318 -- Cracked Plains NPCs drop zero coins -- no economy for L18-20 zone
- #319 -- Bandit Camp has no bandit NPCs -- only wandering Rift Stalkers
- #320 -- Missing room background assets for all Cracked Plains and Warlords Hold rooms (33 rooms)
- #321 -- Krenn the Peddler vendor NPC missing exitSound
- #322 -- Cracked Plains/Warlords Hold items have unknown slot none -- not equippable
- #323 -- Two vendor NPCs have empty vendorItems: charcoal_merchant_npc and seal_road_vendor
- #324 -- Consumable items seal_ward_scroll and greater_fortifying_tonic missing useEffect and useSound
- #326 -- Hostile NPCs wander into quest NPC rooms in Bone Wastes (War Revenant in death_gate with Scholar Meris)
- #334 -- Locked exits appear in room exit list, misleading players (shows as available but blocks on move)
- #335 -- CONDITIONAL_TRIGGER and CHOICE_PROMPT locked doors show generic lockpick message instead of contextual hint
- #336 -- The Sealed One boss (both seal and sunder variants) has no phase transitions during combat
- #337 -- Threshold Guardian mini-boss wanders out of guardians_alcove into shared corridor
- #338 -- Sunder branch has sparse NPC spawns compared to seal branch
- #339 -- Hostile NPCs can wander across zone boundaries (sealed_threshold into stormcrown_keep)
- #340 -- Boss rooms accessible without defeating the boss (seal/sunder epilogue bypass)
- #341 -- WebSocket relay lacks interact command for testing room interactables (enhancement)
- #342 -- Threshold Guardian still pursues player after combat (fix #337 not deployed to staging)
- #343 -- Locked exits still show lockpick message instead of quest/key hint (fix #335 not deployed to staging)
- #344 -- Boss phase transitions not functioning -- The Sealed One has no phases (fix #336 not deployed to staging)
- #345 -- Sunder branch NPC density still sparse after fix #338
- #346 -- v1.0.1 staging verification: Boss phase transitions still not functioning (#336)
- #347 -- Relay make_choice and place_item commands get "Invalid message format" from server (two-phase response commands not mapped to correct ClientMessage types)
- #350 -- Shattered Reach: New characters start with no equipment, no coins, and no weapon (dead-end progression)
- #353 -- Platform rate limiter escalates aggressively -- 3 login attempts trigger 14-minute lockout
- #380 -- pickup_coins all still broken -- returns Invalid coin type (CLOSED/FIXED)
- #381 -- Early game death loop -- Level 1 cannot rest in forest due to constant NPC spawns
- #383 -- Level up not possible -- trainer says ready but provides no action (CLOSED/FIXED)
- #386 -- Bog Lurker pursues player across marsh/forest zone boundary (CLOSED/FIXED)
- #387 -- Hostile NPCs wander into boss room after boss kill, killing players during loot phase
- #388 -- Level 30 warrior dies to mid-level NPCs without getting a chance to fight back
- #389 -- Iron Vein Cavemother treasure chamber is empty -- no loot or interactables
- #404 -- Rate limiter treats 429 retries as new login attempts, creating escalation death spiral
- #405 -- Relay multi-fire: file watcher triggers 4-10x per single command write
- #406 -- Concurrent Claude sessions share relay-command.json causing command interleaving
- #407 -- Relay replays massive stale command queue from previous sessions
- #408 -- Highmoor Steppes has very sparse NPC spawns -- only 1 hostile NPC across 17 rooms
- #409 -- Watcher's Barrow dungeon: PUZZLE_STEP sealed-eye gate untestable via relay
- #410 -- Endgame zones (Stormcrown, Sealed Threshold) untestable due to relay multi-fire
- #411 -- Highmoor Star Cairn PUZZLE_STEP: untested, needs verification
- #412 -- Stormcrown Keep: keep_entrance and stormcrown_entrance room IDs not found on staging
- #413 -- Drowned Chapel boss Tidewarden Korlach gives only 475 XP for dungeon boss
- #415 -- Tavern cellar only accessible via admin teleport
- #416 -- Drowned Chapel boss Tidewarden Korlach: no phase transitions during combat
- #417 -- Boss loot lost when player moved from room before pickup
- #418 -- Drowned Chapel tide-bell puzzle: excellent quest design, needs manual testing
- #419 -- Marsh Edge is a dead-end with no external zone connection
- #420 -- Game relay crashes silently after processing teleport commands
- #421 -- Platform admin account alternates between characters on relay reconnect

## Game State Observations
- **Warrior Combat**: Iron Sword does 21-25 damage per hit (STR 35). One-shots rats (15 HP), spiders (20 HP), bandits (20 HP). Two-shots wolves (30 HP).
- **Cleric Combat**: Iron Sword does 21-24 damage per hit (STR 25). Smite does 26-31 damage (WIL 28, basePower 10). Minor Heal restores only 6 HP (basePower 10). Bash does 20 damage.
- **Warrior Skills**: Bash does ~23 damage. Kick does ~17 damage + requires direction (targetId:DIRECTION format). REST heals 4 HP/tick.
- **Cleric Skills**: Bash does 20 damage. REST heals 3 HP/tick. Meditate restores 4 MP/tick.
- **Human Warrior Combat**: Iron Sword does 25 damage per hit (STR 30). One-shots rats (15 HP), bandits (20 HP), spiders (20 HP). Two-shots wolves (30 HP). HP: 22.
- **Consumables**: Grom's Stout Ale heals 5 HP (5c) [post-rebalance], Hearty Bread Loaf heals 15 HP (12c), Health Potion heals 20 HP (20c), Mana Potion restores 20 MP (25c).
- **Economy**: Rat drops 1-7c, Spider drops 30c + Spider Fang x1, Bandit drops 9-17c, Wolf drops 6-14c + Wolf Pelt (sells 4c) + sometimes 1s. Wolf Pelt sells for 4c.
- **XP**: Rats 15 XP, Bandits 18 XP, Spiders 44 XP, Wolves 28 XP. Level 2 = 100 XP total.
- **Leveling**: BLOCKED -- cannot find level up command. Trainer says "ready to level up" but provides no action. Tried: interact_trainer, level_up command, train_level command, use_skill level_up. None work.
- **Death**: No item/coin loss, respawn at Temple with full HP (MP only restored by temple healing aura if you stay). Ground loot persists after player death.
- **Temple**: Healing aura restores HP over time ("The temple's aura soothes your wounds."). Also restores full HP/MP on respawn.
- **Safe Zones**: Temple forbids violence ("The divine sanctuary of the temple forbids violence here.")
- **Marsh**: Extremely dangerous for level 1. Bog Lurker does 14 damage per hit.
- **Audio**: Sound IDs use bare names (e.g., "sword_swing") resolved to subdirectory paths by context. Server validates at startup.
- **XP loss on death**: 13 XP lost per death at Level 30. Confirmed in 3 consecutive deaths.
- **Iron Vein combat (L30 STR 80)**: Steel Greatsword does 46-54 damage per hit. Cavemother Vrelda (420HP boss) does 40-53 per hit. Mine Ghoul (130HP) does 28-35 per hit. Cave Spider does 29 per hit but misses ~90% of the time.
- **Salt Coast combat (L30 STR 80)**: Riptide Crab (295HP) does 44-53 damage per hit, same range as L30 player. Kills L30 in ~7 hits.

## Drowned Chapel Observations (Session 14, Staging)
- **Boss**: Tidewarden Korlach 620HP, defeated at L30 STR 100. Only 475 XP reward (#413). No phase transitions (#416).
- **Puzzle system**: 5 tide-bells in sequence (PUZZLE_STEP). Requires Captain Mara quest flag. Excellent design but untestable via relay (#418).
- **Boss loot**: Dropped on ground but lost due to relay multi-fire moving player out of room before pickup (#417).
- **Zone connections**: salt_coast:chapel_threshold -> drowned_chapel:vestibule (bidirectional).

## Relay Infrastructure Issues (Session 14, Critical)
- **Multi-fire**: macOS FSEvents triggers file watcher 4-10x per write. Toggle commands (godmode) become unusable. Idempotent commands (setstat) work as workaround. Filed #405.
- **Command interleaving**: Concurrent Claude sessions write to same relay-command.json. Filed #406.
- **Stale queue replay**: Relay replays old commands from previous sessions. Filed #407.
- **Rate limiter escalation**: 429 retries count as login attempts, extending lockout. Filed #404.
- **Character alternation**: Platform admin account alternates between UatTester and Bob on reconnect. Filed #421.
- **Net effect**: Endgame zones (Stormcrown Keep, Sealed Threshold) could NOT be systematically tested. Room-specific testing impossible when multi-fire teleports you through 4-10 rooms per command.

## Cleric Gameplay Notes (Session 4)
- **Spell system works well**: ready_spell + attack_toggle auto-casts each tick; cast_spell works for one-off casts out of combat
- **Smite is very strong**: 26-31 damage one-shots rats/bandits/spiders, two-shots wolves
- **Minor Heal is very weak**: Only 6 HP restored for 5 MP -- outclassed by 5c ale (10 HP) and 12c bread (15 HP)
- **Mana pool is tiny**: 10 MP total = 2 Smite casts or 1 Smite + 1 Minor Heal
- **Meditate works well**: 4 MP/tick, auto-stops when full, interrupted by combat
- **REST works**: 3 HP/tick for Cleric (vs 4 HP/tick for Warrior), interrupted by combat
- **Combat loop for Cleric**: Smite 1-2x -> melee cleanup -> meditate -> repeat. Needs safe window between fights.

## Audio Directory Structure (post-reorganization)
- `audio/bgm/` -- Background music (forest_danger, town_peaceful)
- `audio/general/` -- backstab, coin_pickup, dodge, item_pickup, loot_drop, miss, parry
- `audio/items/` -- bow_miss/shot, dagger_miss/slash, potion_drink, staff_miss/swing, sword_miss/swing
- `audio/npcs/` -- NPC attack/miss/death/interact/exit sounds
- `audio/rooms/` -- footstep_* depart sounds
- `audio/spells/` -- spell cast/impact/fizzle sounds, healing_aura

## Bone Wastes / Necropolis of Vael Observations (Session 7, Staging)
- **Overworld NPC HP ranges**: Bone Stalker 280, Dust Wraith 260, War Revenant 350, Carrion Golem 330, Bone Warden 560 (sub-boss)
- **Dungeon NPC HP ranges**: Crypt Guardian 310, Necropolis Shade 320, Embalmed Horror 360, Bone Colossus 380, Death-Speaker Khaim 1100 (boss)
- **Friendly NPCs**: Rask the Bone-Picker (vendor, 0/0 HP), Scholar Meris (quest, 200/200 HP)
- **Damage at L25 STR 20 with Bone-Edge Blade**: 34-43 melee damage per hit. Dodges and misses happen.
- **XP**: Dust Wraith 215 XP, Bone Warden 938 XP, Embalmed Horror 560 XP
- **Loot**: Dust Wraith drops 4s26c. Bone Warden drops Khaim's Ritual Key + 3g16s145c. Embalmed Horror drops 1g15s60c. Coin economy works (unlike Cracked Plains #318).
- **Vendor (Rask)**: Bone-Edge Blade (3s80c), Bone-Plate Cuirass (3s60c), Bone-Plate Helm (3s). All equip correctly.
- **Quest NPC (Scholar Meris)**: Present at death_gate. Not interactable via relay (no interact_quest command). necropolis_lore_tablet item exists in catalog.
- **PLACE_ITEM**: sealed_passage has no EAST exit (correctly locked). Vault of the Seal-Keepers exists and reachable via teleport (WEST exit back to sealed_passage).
- **Cross-zone transition**: bone_wastes:necropolis_overlook DOWN -> necropolis_of_vael:entrance_hall works bidirectionally.
- **Traps**: Multiple traps detected (Unstable Ground, Carved Relief Panel, Pooled Chemical Residue, Disturbed Dust Layer).
- **NPC wandering**: Hostile NPCs wander into quest NPC room (death_gate, #326). Boss room sometimes has wanderers but they seem transient.
- **Shortcut**: sealed_passage UP -> processional and processional DOWN -> sealed_passage work bidirectionally.

## Cracked Plains / Warlord's Hold Observations (Session 6)
- **NPC HP ranges**: Rift Stalker 220, Cliff Harpy 190, Rift Crawler 200, Plains Marauder 240, War Scout 250, Hold Crossbowman 210, War Hound 180, Hold Berserker 260, Drask's Lieutenant 480 (sub-boss), Warlord Drask 900 (boss)
- **Damage at STR 60**: Melee hits 31-53 damage with Iron Sword / Rift-Iron Sword. NPCs dodge and miss regularly.
- **XP**: Rift Stalker 233 XP, Drask's Lieutenant 713 XP, Warlord Drask 2900 XP
- **Loot**: Rift Stalker drops Rift Stalker Fang (no coins). Lieutenant drops Drask's War-Key. Drask drops Drask's Seal Shard. No coins from any tested NPC.
- **Vendor (Krenn)**: Rift-Iron Sword (2s40c), Rift-Iron Cuirass (2s60c), Rift-Iron Helm (1s60c). All equip correctly.
- **Trap detected**: "Crumbling Ledge" at rift_edge_south on first entry. Only trap observed in zone.
- **NPC wandering**: Hostile NPCs freely roam into vendor room (rift_spring) and quest NPC room (signal_cairn). No safe zones in the zone.
- **Vertical exits**: 12 total (10 in Cracked Plains, 2 in Warlord's Hold). All bidirectional except rift_floor_south->rift_edge_south (one-way, #316).
- **PLACE_ITEM**: Vault door in underhall correctly locked (no SOUTH exit until key used). Vault room exists and is reachable via teleport.

## Sealed Threshold Observations (Session 8, Staging, Phase 8J)
- **Zone structure**: 22 rooms total -- 10 shared approach, 6 seal branch, 6 sunder branch
- **Shared approach NPCs**: Threshold Shade 350HP (465 XP), Void Sentinel 420HP (540 XP), Threshold Guardian 800HP mini-boss (1875 XP)
- **Seal branch NPCs**: Seal Wraith 380HP (495 XP), The Sealed One (seal) 1800HP boss (9000 XP)
- **Sunder branch NPCs**: Fracture Elemental 340HP (495 XP), The Sealed One (sunder) 1400HP boss (9000 XP)
- **Loot**: Guardian drops Threshold Key + Threshold Seal Fragment + ~4g21s182c. Seal boss drops Seal Shard + ~13g46s324c. Sunder boss drops Seal Shard + Unchained Ruin Circlet + ~11g58s315c. Circlet requires L29 to equip.
- **Progression**: Kill Guardian -> get Threshold Key -> interact gate (CONDITIONAL_TRIGGER) -> enter Fracture Chamber -> CHOICE_PROMPT -> one branch
- **CHOICE_PROMPT**: Cannot test via relay (no interact command). Both WEST/EAST exits from fracture_chamber are correctly locked. Return paths from branches to fracture_chamber work.
- **Traps**: 3 DAMAGE_TRAPs -- Ward Surge (ward_corridor), Resonance Burst (echo_chamber), Fracture Detonation (sunder_corridor). All detected/spotted.
- **Cross-zone**: stormcrown_keep:stormcrown_throne SOUTH<->NORTH sealed_threshold:descent works bidirectionally. Entry from Stormcrown locked (EXIT_OPEN needed).
- **Boss phases**: NEITHER boss variant has phase transitions. HP goes straight through thresholds without clamping. Filed #336.
- **NPC wandering**: Threshold Guardian wandered 3 rooms from spawn. Threshold Shade wandered cross-zone. Filed #337, #339.
- **Epilogue rooms**: Accessible without killing boss (SOUTH exit from sanctum not locked). Filed #340.
- **Oracle trainer**: Works at oracles_sanctum. The Oracle shows 0/0 HP (#234). Trainer CP display shows "400 unspent / 340 earned" discrepancy.
- **Admin commands**: /godmode, /setlevel, /setstat, /grantitem, /spawn, /kill all work via say command with / prefix

## Bug Fix Verification Session (Session 9, Staging, 2026-05-13)
- **#337 Guardian pursuit fix: NOT DEPLOYED** -- Threshold Guardian still pursues after combat. Spawned Guardian at guardians_alcove, attacked to 400/800 HP, moved EAST. Guardian followed within 6 seconds. Additionally, a naturally spawned Guardian was roaming freely through descent, carved_passage, seal_gallery, echo_chamber -- pursuing UatTester across multiple rooms.
- **#335 Locked exit message fix: NOT DEPLOYED** -- All CONDITIONAL_TRIGGER and CHOICE_PROMPT exits still show "A skilled lockpick might be able to open it." Tested: threshold_gate NORTH, fracture_chamber WEST/EAST, sunder_approach EAST, seal_approach WEST. All show the old lockpick message.
- **#336 Boss phase transitions: NOT DEPLOYED** -- The Sealed One (seal variant, 1800 HP) fought from full to dead with ZERO phase transitions. HP passed exactly through both thresholds: 1240->1188 (66%) and 621->594 (33%) with no NpcPhaseShift event, no HP clamping, no stat changes, no sprite swap. Boss behaved as a basic NPC.
- **#338 Sunder spawn density: INCONCLUSIVE** -- Only 2 Fracture Elementals across 4 sunder rooms. 0 Seal Wraiths across 4 seal rooms. Total zone: ~5 NPCs across 22 rooms. Below even old cap of 8.
- **Sealed One (seal) loot**: Seal Shard + Warden's Oath Helm + 15g 44s 310c (6000 XP). New loot item not seen before.
- **Admin command note**: /tp is not valid, must use /teleport

## Bug Fix Verification Session (Session 10, Staging v1.0.1, 2026-05-13)
- **#337/#339 Guardian pursuit fix: PASS** -- Threshold Guardian at guardians_alcove (800HP). Attacked to 200/800 HP, moved EAST to ward_corridor. Waited 20+ seconds. Guardian did NOT follow. Returned to guardians_alcove -- Guardian still there at 200/800 HP. No cross-zone wandering into stormcrown_keep either (only Stormcrown boss present in throne room).
- **#335 Locked exit message fix: PASS** -- All locked exits now show "The door to the [direction] is locked. Perhaps a key or quest will grant access." Tested: threshold_gate NORTH, fracture_chamber WEST, fracture_chamber EAST. All show new message.
- **#338 Spawn density fix: PARTIAL PASS** -- Total hostile NPCs: 8 (excluding killed Guardian = 9 normally). Shared approach has good density (Threshold Shades in ward_corridor, echo_chamber, outer_seal_ring + Void Sentinel in seal_gallery). Both branches have only 1 trash mob each (Seal Wraith in seal_corridor, Fracture Elemental in sunder_corridor). Improved from old cap of 8 but branches remain sparse.
- **#336 Boss phase transitions: FAIL** -- The Sealed One (seal variant, 1800 HP) fought from full to 0 HP with ZERO phase transitions. HP hit EXACTLY 1188/1800 (66% threshold) then dropped to 1141 -- no clamp. HP hit EXACTLY 594/1800 (33% threshold) then continued dropping -- no clamp. 50 combat events total, 0 phase-related events. Filed #346.
- **Sealed One (seal) loot**: Seal Shard + Warden's Oath Sword + Warden's Oath Greaves + 20g 46s 248c (6000 XP).

## Interact Command Verification (Session 11, Local Dev, 2026-05-14)
- **room.interactables in state: PASS** -- Array appears on all rooms. Empty for rooms without interactables, populated with id/label/description/actionType/triggerType for rooms with them.
- **interact_feature command: PASS** -- Works for TREASURE_DROP (cave_chest at forest:cave), CONDITIONAL_TRIGGER (threshold_gate_lock at sealed_threshold:threshold_gate), DAMAGE_TRAP (spotted traps at cracked_plains:rift_edge_south and sealed_threshold:ward_corridor), and triggering CHOICE_PROMPT/PLACE_ITEM initial prompts.
- **pendingPrompt in state: PASS** -- Populated correctly for both CHOICE_PROMPT (type "choice", label, question, options array with id/label) and PLACE_ITEM (type "place_item", label, prompt).
- **TREASURE_DROP loot: PASS** -- cave_chest dropped Health Potion + Spider Silk Gloves + 15c on the ground. Second interact returns "It doesn't seem to do anything more."
- **CONDITIONAL_TRIGGER: PASS** -- Without Threshold Key: "The gate is sealed with ancient ward-glyphs. It requires a key bearing the guardian's mark." With key: "The threshold key slides into place. Ward-light erupts along the gate's inscriptions..."
- **DAMAGE_TRAP (ON_ENTER): PASS** -- Traps detected on room entry ("You spot a trap: Ward Surge."). Trap removed from interactables after detection. Teleport does not trigger traps.
- **make_choice command: FAIL** -- Server returns "Invalid message format" (#347). Two-phase response commands not mapped to correct ClientMessage types.
- **place_item command: FAIL** -- Server returns "Invalid message format" (#347). Same root cause as make_choice.
- **Error handling: PASS** -- Invalid featureId returns "You don't see anything like that here." Wrong-room interact returns same.

## Shattered Reach Smoke Test (Session 12, Staging, 2026-05-14)
- **World**: The Shattered Reach (Demo) v0.1.0.0, ~50 rooms, 5 zones, clockwork/post-apocalyptic theme
- **Character**: SmokeBot, HUMAN WARRIOR, Level 1 (platform auth admin@neomud.app)
- **World slug**: shattered-reach
- **Gearholm Town (6 rooms)**: workshop (spawn), plaza (trainer: Gearmaster Thane), market/Cogsmith's Row (vendor: Cogsmith Brenna), arcanum (vendor: Arcanist Voss), salvage_bar/The Leaky Piston (vendor: Barkeep Ratch), undercog_lift (DOWN to undercog:platform)
- **Vendor inventory**: Cogsmith Brenna sells weapons (Brass Dagger 25c, Iron Pipe 30c, Scrap Staff 20c, Junk Bow 28c), armor (Tin Cap 12c, Scrap Vest 35c, etc.), consumables. Barkeep Ratch sells consumables only. Arcanist Voss sells magic items (Conduit Staff 1s10c, scrolls, potions, accessories).
- **Gate**: The Outer Gate has friendly Gate Sentry (120 HP). NORTH leads to scrapyard:entrance.
- **Scrapyard**: Gear Spider (32 HP, hostile, 7-8 damage per hit). Killed unarmed Level 1 in 3 hits.
- **Critical bug**: #350 -- New characters start with zero equipment, zero coins, zero inventory. Dead-end progression.
- **Locked exit**: Bar cellar (DOWN from salvage_bar) locked with generic lockpick message (existing issue #334).
- **Hidden passage**: Discovered hidden DOWN exit in workshop to undercog_lift on pass-through.
- **What worked**: Registration, login, catalogs (69 items, 26 spells, 12 skills), movement, look, say (shows feedback!), vendor interaction, trainer interaction, tutorials (welcome, merchant, hostile, health low, death, hidden passage, locked passage), respawn at workshop with full HP.
- **NPCs with 0/0 HP**: Gearmaster Thane, Cogsmith Brenna, Barkeep Ratch, Arcanist Voss (existing #234).

## TODOs
- Test multiplayer interactions
- Explore Blackstone Gorge
- Try leveling up once bug #109 is fixed
- Test with MAGE, DRUID, or other caster classes
- Test crafting system once Grimjaw's Forge is functional
- Test Haela Riftwalker quest interaction via real client (relay cannot interact with PLACE_ITEM/interactables)
- Test make_choice and place_item once #347 is fixed
- Test TREASURE_DROP in seal/sunder epilogue rooms
- **BLOCKER**: Re-test Stormcrown Keep systematically once relay multi-fire (#405) is fixed
- **BLOCKER**: Test Sealed Threshold ending CHOICE_PROMPT end-to-end once relay is stable
- **BLOCKER**: Verify boss phase transitions on staging (Korlach #416, Sealed One #346 still broken)
- Test T4 trainer (Archon Veska) at Skyveil Reach
- Test T5 trainer (Oracle) at Sealed Threshold oracles_sanctum
- Verify Watcher's Barrow PUZZLE_STEP sealed-eye gate (#409)
- Verify Highmoor Star Cairn PUZZLE_STEP (#411)
- Verify Stormcrown Keep room IDs exist on staging (#412)
