# Game Designer Agent Memory

## Key File Locations
- Data files: `maker/default_world_src/world/` (classes.json, races.json, items.json, skills.json, spells.json, recipes.json)
- Zone files: `maker/default_world_src/world/*.zone.json` (23 zones total)
- Loot tables: Embedded in zone JSON NPC definitions (lootItems array with `chance` field, NOT `dropChance`)
- CoinDrop model: `shared/.../model/CoinDrop.kt` (supports all: minCopper/maxCopper/minSilver/maxSilver/minGold/maxGold/minPlatinum/maxPlatinum)
- SpawnConfig class: `server/.../world/Zone.kt` -- only recognizes `maxEntities`, `maxPerRoom`, `rateTicks`
- GameConfig: `server/src/main/kotlin/com/neomud/server/game/GameConfig.kt`
- Combat: `server/.../game/combat/CombatManager.kt`
- XP Calculator: `server/.../game/progression/XpCalculator.kt`
- NPC HP field: `maxHp` in zone JSON, NOT `hp`

## Core Formula Summary (CURRENT as of May 2026)
- Melee damage: STR/3 + weaponBonus + thresholdBonus + random(1..weaponRange)
- NPC damage to player: (npc.damage + random(1..npc.damage/3)) - totalArmor/2, min 1
- XP curve: 100 * level^2.2
- Kills-to-level target: ~18-20 for most of the game
- Vendor data: `vendorItems` field on non-hostile NPCs (list of item IDs)
- Zone spawn: `spawnConfig` top-level with `maxEntities`/`maxPerRoom`/`rateTicks` ONLY

## Critical Bugs Found (May 2026 audit)
- [Broken spawn configs](broken_spawn_configs.md) -- 4 zones use wrong field names
- [Weapon diversity gaps](weapon_diversity_gaps.md) -- daggers/staves/bows have 10-19 level upgrade deserts
- [Armor slot gaps](armor_slot_gaps.md) -- legs/hands/shield have no upgrades L6-L29

## Known Issues (May 2026)
- #351/#354/#355: FIXED (NPC stats, coinDrop format, trainer cap)
- #356-#389: Open -- full audit and fix proposals delivered May 2026
- BONUS: 4 zones broken spawn configs (cradle_of_the_seal, first_seal, stormcrown_keep, sealed_threshold)
