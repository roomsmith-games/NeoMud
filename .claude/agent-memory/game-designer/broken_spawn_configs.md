---
name: broken-spawn-configs
description: 4 zones use wrong SpawnConfig field names causing NPCs to never respawn after death
metadata:
  type: project
---

Four zones have completely broken spawn configurations because they use wrong field names that the `SpawnConfig` Kotlin data class does not recognize:

| Zone | Wrong Fields Used | Correct Fields |
|------|------------------|---------------|
| cradle_of_the_seal | maxNpcsPerRoom, spawnIntervalTicks, maxTotalNpcs | maxPerRoom, rateTicks, maxEntities |
| first_seal | maxNpcsPerRoom, spawnIntervalTicks, maxTotalNpcs | maxPerRoom, rateTicks, maxEntities |
| stormcrown_keep | maxNpcsPerRoom, spawnIntervalTicks, maxTotalNpcs | maxPerRoom, rateTicks, maxEntities |
| sealed_threshold | maxNpcsPerRoom, spawnIntervalTicks, maxTotalNpcs | maxPerRoom, rateTicks, maxEntities |

The `SpawnConfig` class (`server/.../world/Zone.kt`) only has: `maxEntities`, `maxPerRoom`, `rateTicks`. Unknown JSON fields are silently ignored by kotlinx.serialization, defaulting to 0. Since `rateTicks == 0 || maxEntities == 0` skips the spawn loop, these zones NEVER respawn NPCs.

**Why:** These zones were likely created later using a different naming convention than the original zones.

**How to apply:** When creating or reviewing zone JSON files, always verify spawn config uses `maxEntities`/`maxPerRoom`/`rateTicks`. File an issue/fix immediately if wrong field names are found.

Related: [[armor-slot-gaps]], [[weapon-diversity-gaps]]
