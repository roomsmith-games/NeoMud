import AdmZip from 'adm-zip'
import fs from 'fs'
import path from 'path'
import { PrismaClient } from './generated/prisma/client.js'
import { PrismaBetterSqlite3 } from '@prisma/adapter-better-sqlite3'
import { createProject } from './db.js'
import { getProjectsDir } from './projectContext.js'

const MAX_ZIP_ENTRIES = 10_000
const MAX_ZIP_DECOMPRESSED_SIZE = 500 * 1024 * 1024 // 500MB
// 200MB. Raised from 100MB on 2026-04-24 (Phase 6F) to accommodate the
// official seed world bundle (113MB). Temporary headroom; the real fix
// is slimming the seed — tracked as roomsmith-games/NeoMud#296.
const MAX_COMPRESSED_SIZE = 200 * 1024 * 1024 // 200MB on disk
const MAX_COMPRESSION_RATIO = 100 // reject suspiciously high ratios (ZIP bomb indicator)

/**
 * Import an .nmd bundle (ZIP) into a new maker project.
 * @param nmdPath - Path to the .nmd file
 * @param userId - User ID who owns the project (or '_shared' for templates)
 * @param projectName - Name for the new project
 * @param readOnly - Whether the project should be read-only
 */
export async function importNmd(nmdPath: string, userId: string, projectName: string, readOnly = false): Promise<void> {
  // Pre-check: reject oversized compressed files before opening
  const compressedSize = fs.statSync(nmdPath).size
  if (compressedSize > MAX_COMPRESSED_SIZE) {
    throw new Error(`Bundle compressed size (${Math.round(compressedSize / 1024 / 1024)}MB) exceeds ${MAX_COMPRESSED_SIZE / 1024 / 1024}MB limit`)
  }

  const zip = new AdmZip(nmdPath)

  // ZIP bomb protection: check entry count and total decompressed size
  const allEntries = zip.getEntries()
  if (allEntries.length > MAX_ZIP_ENTRIES) {
    throw new Error(`Bundle contains too many files (${allEntries.length} > ${MAX_ZIP_ENTRIES})`)
  }
  let totalSize = 0
  for (const entry of allEntries) {
    totalSize += entry.header.size
    if (totalSize > MAX_ZIP_DECOMPRESSED_SIZE) {
      throw new Error('Bundle decompressed size exceeds 500MB limit')
    }
    // Check for suspicious compression ratios per entry
    if (entry.header.compressedSize > 0) {
      const ratio = entry.header.size / entry.header.compressedSize
      if (ratio > MAX_COMPRESSION_RATIO) {
        throw new Error(`Suspicious compression ratio (${Math.round(ratio)}:1) in entry "${entry.entryName}"`)
      }
    }
  }

  // Create the project DB
  await createProject(userId, projectName, readOnly)

  // Open a temporary client for importing data
  const dbFile = path.join(getProjectsDir(), userId, `${projectName}.db`)
  const adapter = new PrismaBetterSqlite3({ url: `file:${dbFile}` })
  const prisma = new PrismaClient({ adapter })

  try {

  // ─── Extract assets ──────────────────────────────────────
  const assetsDir = path.join(getProjectsDir(), userId, `${projectName}_assets`)
  const assetEntries = zip.getEntries().filter(
    (e) => e.entryName.startsWith('assets/') && !e.isDirectory
  )
  const resolvedAssetsDir = path.resolve(assetsDir)
  let actualDecompressedSize = 0
  for (const entry of assetEntries) {
    // ZIP slip protection: reject traversal paths
    if (entry.entryName.includes('..') || path.isAbsolute(entry.entryName)) {
      throw new Error(`Invalid ZIP entry path: ${entry.entryName}`)
    }
    const destPath = path.resolve(path.join(assetsDir, entry.entryName))
    if (!destPath.startsWith(resolvedAssetsDir + path.sep)) {
      throw new Error(`ZIP entry escapes assets directory: ${entry.entryName}`)
    }
    const data = entry.getData()
    actualDecompressedSize += data.length
    if (actualDecompressedSize > MAX_ZIP_DECOMPRESSED_SIZE) {
      throw new Error('Actual decompressed size exceeds 500MB limit')
    }
    const destDir = path.dirname(destPath)
    if (!fs.existsSync(destDir)) {
      fs.mkdirSync(destDir, { recursive: true })
    }
    fs.writeFileSync(destPath, data)
  }
  if (assetEntries.length > 0) {
    console.log(`[import] Extracted ${assetEntries.length} asset files to ${assetsDir}`)
  }

  // ─── Manifest ──────────────────────────────────────────
  const manifestEntry = zip.getEntry('manifest.json')
  if (manifestEntry) {
    const manifest = JSON.parse(zip.readAsText(manifestEntry))
    const metaEntries = Object.entries(manifest) as [string, unknown][]
    for (const [key, value] of metaEntries) {
      if (key === 'readOnly') continue // Don't overwrite our readOnly flag
      await prisma.projectMeta.upsert({
        where: { key },
        create: { key, value: String(value) },
        update: { value: String(value) },
      })
    }
  }

  // ─── Classes ───────────────────────────────────────────
  const classesEntry = zip.getEntry('world/classes.json')
  if (classesEntry) {
    const { classes } = JSON.parse(zip.readAsText(classesEntry))
    for (const cls of classes) {
      await prisma.characterClass.create({
        data: {
          id: cls.id,
          name: cls.name,
          description: cls.description ?? '',
          minimumStats: JSON.stringify(cls.minimumStats ?? {}),
          skills: JSON.stringify(cls.skills ?? []),
          properties: JSON.stringify(cls.properties ?? {}),
          hpPerLevelMin: cls.hpPerLevelMin ?? 4,
          hpPerLevelMax: cls.hpPerLevelMax ?? 8,
          mpPerLevelMin: cls.mpPerLevelMin ?? 0,
          mpPerLevelMax: cls.mpPerLevelMax ?? 0,
          xpModifier: cls.xpModifier ?? 1.0,
          magicSchools: JSON.stringify(cls.magicSchools ?? {}),
          imagePrompt: cls.imagePrompt ?? '',
          imageStyle: cls.imageStyle ?? '',
          imageNegativePrompt: cls.imageNegativePrompt ?? '',
          imageWidth: cls.imageWidth ?? 256,
          imageHeight: cls.imageHeight ?? 256,
        },
      })
    }
  }

  // ─── Races ─────────────────────────────────────────────
  const racesEntry = zip.getEntry('world/races.json')
  if (racesEntry) {
    const { races } = JSON.parse(zip.readAsText(racesEntry))
    for (const race of races) {
      await prisma.race.create({
        data: {
          id: race.id,
          name: race.name,
          description: race.description ?? '',
          statModifiers: JSON.stringify(race.statModifiers ?? {}),
          xpModifier: race.xpModifier ?? 1.0,
          imagePrompt: race.imagePrompt ?? '',
          imageStyle: race.imageStyle ?? '',
          imageNegativePrompt: race.imageNegativePrompt ?? '',
          imageWidth: race.imageWidth ?? 256,
          imageHeight: race.imageHeight ?? 256,
        },
      })
    }
  }

  // ─── Items ─────────────────────────────────────────────
  const legacyTypeMap: Record<string, string> = { junk: 'misc', material: 'crafting', key: 'misc' }
  const itemsEntry = zip.getEntry('world/items.json')
  if (itemsEntry) {
    const { items } = JSON.parse(zip.readAsText(itemsEntry))
    for (const item of items) {
      const rawType = (item.type ?? '').toLowerCase()
      await prisma.item.create({
        data: {
          id: item.id,
          name: item.name,
          description: item.description ?? '',
          type: legacyTypeMap[rawType] ?? rawType,
          slot: item.slot ?? '',
          damageBonus: item.damageBonus ?? 0,
          damageRange: item.damageRange ?? 0,
          armorValue: item.armorValue ?? 0,
          value: item.value ?? 0,
          weight: item.weight ?? 0,
          stackable: item.stackable ?? false,
          maxStack: item.maxStack ?? 1,
          useEffect: item.useEffect ?? '',
          levelRequirement: item.levelRequirement ?? 0,
          attackSound: item.attackSound ?? '',
          missSound: item.missSound ?? '',
          useSound: item.useSound ?? '',
          imagePrompt: item.imagePrompt ?? '',
          imageStyle: item.imageStyle ?? '',
          imageNegativePrompt: item.imageNegativePrompt ?? '',
          imageWidth: item.imageWidth ?? 256,
          imageHeight: item.imageHeight ?? 256,
        },
      })
    }
  }

  // ─── Skills ────────────────────────────────────────────
  const skillsEntry = zip.getEntry('world/skills.json')
  if (skillsEntry) {
    const { skills } = JSON.parse(zip.readAsText(skillsEntry))
    for (const skill of skills) {
      await prisma.skill.create({
        data: {
          id: skill.id,
          name: skill.name,
          description: skill.description ?? '',
          category: skill.category ?? '',
          primaryStat: skill.primaryStat ?? '',
          secondaryStat: skill.secondaryStat ?? '',
          cooldownTicks: skill.cooldownTicks ?? 0,
          manaCost: skill.manaCost ?? 0,
          difficulty: skill.difficulty ?? 15,
          isPassive: skill.isPassive ?? false,
          classRestrictions: JSON.stringify(skill.classRestrictions ?? []),
          properties: JSON.stringify(skill.properties ?? {}),
          imagePrompt: skill.imagePrompt ?? '',
          imageStyle: skill.imageStyle ?? '',
          imageNegativePrompt: skill.imageNegativePrompt ?? '',
          imageWidth: skill.imageWidth ?? 256,
          imageHeight: skill.imageHeight ?? 256,
        },
      })
    }
  }

  // ─── Spells ────────────────────────────────────────────
  const spellsEntry = zip.getEntry('world/spells.json')
  if (spellsEntry) {
    const { spells } = JSON.parse(zip.readAsText(spellsEntry))
    for (const spell of spells) {
      await prisma.spell.create({
        data: {
          id: spell.id,
          name: spell.name,
          description: spell.description ?? '',
          school: spell.school ?? '',
          spellType: spell.spellType ?? '',
          manaCost: spell.manaCost ?? 0,
          cooldownTicks: spell.cooldownTicks ?? 0,
          levelRequired: spell.levelRequired ?? 1,
          primaryStat: spell.primaryStat ?? 'intellect',
          basePower: spell.basePower ?? 0,
          targetType: spell.targetType ?? 'ENEMY',
          effectType: spell.effectType ?? '',
          effectDuration: spell.effectDuration ?? 0,
          castMessage: spell.castMessage ?? '',
          castSound: spell.castSound ?? '',
          impactSound: spell.impactSound ?? '',
          missSound: spell.missSound ?? '',
          imagePrompt: spell.imagePrompt ?? '',
          imageStyle: spell.imageStyle ?? '',
          imageNegativePrompt: spell.imageNegativePrompt ?? '',
          imageWidth: spell.imageWidth ?? 256,
          imageHeight: spell.imageHeight ?? 256,
        },
      })
    }
  }

  // ─── Recipes ──────────────────────────────────────────
  const recipesEntry = zip.getEntry('world/recipes.json')
  if (recipesEntry) {
    const { recipes } = JSON.parse(zip.readAsText(recipesEntry))
    for (const recipe of recipes) {
      await prisma.recipe.create({
        data: {
          id: recipe.id,
          name: recipe.name,
          description: recipe.description ?? '',
          category: recipe.category ?? '',
          materials: JSON.stringify(recipe.materials ?? []),
          cost: JSON.stringify(recipe.cost ?? {}),
          outputItemId: recipe.outputItemId ?? '',
          outputQuantity: recipe.outputQuantity ?? 1,
          levelRequirement: recipe.levelRequirement ?? 1,
          classRestriction: recipe.classRestriction ?? '',
        },
      })
    }
  }

  // ─── Legacy Loot Tables (backward-compat: merge into NPCs) ───
  const lootEntry = zip.getEntry('world/loot_tables.json')
  if (lootEntry) {
    const { tables } = JSON.parse(zip.readAsText(lootEntry))
    for (const [npcId, table] of Object.entries(tables) as [string, any][]) {
      try {
        await prisma.npc.update({
          where: { id: npcId },
          data: {
            lootItems: JSON.stringify(table.items ?? []),
            coinDrop: JSON.stringify(table.coinDrop ?? {}),
          },
        })
      } catch {
        // NPC may not exist — skip silently
      }
    }
    console.log(`[import] Migrated ${Object.keys(tables).length} legacy loot tables into NPC records`)
  }

  // ─── PC Sprites ───────────────────────────────────────
  const pcSpritesEntry = zip.getEntry('world/pc_sprites.json')
  if (pcSpritesEntry) {
    const { sprites } = JSON.parse(zip.readAsText(pcSpritesEntry))
    // Clear any seeded defaults first
    await prisma.pcSprite.deleteMany()
    for (const s of sprites) {
      await prisma.pcSprite.create({
        data: {
          id: s.id,
          race: s.race,
          gender: s.gender,
          characterClass: s.characterClass,
          imagePrompt: s.imagePrompt ?? '',
          imageStyle: s.imageStyle ?? '',
          imageNegativePrompt: s.imageNegativePrompt ?? '',
          imageWidth: s.imageWidth ?? 384,
          imageHeight: s.imageHeight ?? 512,
        },
      })
    }
  }

  // ─── Legacy prompt_templates.json migration ────────────
  // If old bundle has prompt_templates.json, build a lookup to apply
  // template data to entities after they are created.
  const promptEntry = zip.getEntry('world/prompt_templates.json')
  const legacyTemplates: Map<string, any> = new Map()
  if (promptEntry) {
    const { templates } = JSON.parse(zip.readAsText(promptEntry))
    for (const tmpl of templates) {
      legacyTemplates.set(`${tmpl.entityType}:${tmpl.entityId}`, tmpl)
    }
    console.log(`[import] Found legacy prompt_templates.json with ${legacyTemplates.size} templates — will migrate to inline fields`)
  }

  // ─── Zone files ────────────────────────────────────────
  // Two-pass approach: first create all zones/rooms/npcs, then exits
  // (exits can reference rooms in other zones)
  const zoneEntries = zip.getEntries().filter((e) => e.entryName.match(/^world\/.*\.zone\.json$/))
  const parsedZones: any[] = []

  for (const entry of zoneEntries) {
    const zone = JSON.parse(zip.readAsText(entry))
    parsedZones.push(zone)

    const spawnConfig = zone.spawnConfig ?? {}

    await prisma.zone.create({
      data: {
        id: zone.id,
        name: zone.name,
        description: zone.description ?? '',
        safe: zone.safe ?? true,
        bgm: zone.bgm ?? '',
        spawnRoom: zone.spawnRoom ?? null,
        spawnMaxEntities: spawnConfig.maxEntities ?? 0,
        spawnMaxPerRoom: spawnConfig.maxPerRoom ?? 0,
        spawnRateTicks: spawnConfig.rateTicks ?? 0,
        imageStyle: zone.imageStyle ?? '',
        imageNegativePrompt: zone.imageNegativePrompt ?? '',
      },
    })

    // Rooms
    for (const room of zone.rooms ?? []) {
      // Check for legacy template data
      const legacyRoom = legacyTemplates.get(`room:${room.id}`)
      await prisma.room.create({
        data: {
          id: room.id,
          zoneId: zone.id,
          name: room.name,
          description: room.description ?? '',
          x: room.x ?? 0,
          y: room.y ?? 0,
          backgroundImage: room.backgroundImage ?? '',
          effects: JSON.stringify(
            room.effects ??
            (room.healPerTick > 0 ? [{ type: 'HEAL', value: room.healPerTick }] : [])
          ),
          bgm: room.bgm ?? '',
          departSound: room.departSound ?? '',
          lockedExits: JSON.stringify(room.lockedExits ?? {}),
          lockResetTicks: JSON.stringify(room.lockResetTicks ?? {}),
          hiddenExits: JSON.stringify(room.hiddenExits ?? {}),
          interactables: JSON.stringify(room.interactables ?? []),
          unpickableExits: JSON.stringify(room.unpickableExits ?? []),
          imagePrompt: room.imagePrompt ?? legacyRoom?.prompt ?? '',
          imageStyle: room.imageStyle ?? legacyRoom?.style ?? '',
          imageNegativePrompt: room.imageNegativePrompt ?? legacyRoom?.negativePrompt ?? '',
          imageWidth: room.imageWidth ?? legacyRoom?.width ?? 1024,
          imageHeight: room.imageHeight ?? legacyRoom?.height ?? 576,
          maxHostileNpcs: room.maxHostileNpcs ?? null,
        },
      })
    }

    // NPCs (upsert: duplicate IDs across zones take the last definition)
    for (const npc of zone.npcs ?? []) {
      const legacyNpc = legacyTemplates.get(`npc:${npc.id}`)
      const npcData = {
          id: npc.id,
          zoneId: zone.id,
          name: npc.name,
          description: npc.description ?? '',
          startRoomId: npc.startRoomId ?? '',
          behaviorType: npc.behaviorType ?? 'idle',
          patrolRoute: JSON.stringify(npc.patrolRoute ?? []),
          hostile: npc.hostile ?? false,
          maxHp: npc.maxHp ?? 0,
          damage: npc.damage ?? 0,
          level: npc.level ?? 1,
          perception: npc.perception ?? 0,
          xpReward: npc.xpReward ?? 0,
          accuracy: npc.accuracy ?? 0,
          defense: npc.defense ?? 0,
          evasion: npc.evasion ?? 0,
          agility: npc.agility ?? 10,
          vendorItems: JSON.stringify(npc.vendorItems ?? []),
          crafterRecipes: JSON.stringify(npc.crafterRecipes ?? []),
          trainerConfig: npc.trainerConfig ? JSON.stringify(npc.trainerConfig) : '',
          dialogueScript: npc.dialogueScript ?? '',
          grantItemId: npc.grantItemId ?? '',
          grantItemFlag: npc.grantItemFlag ?? '',
          repeatDialogueScript: npc.repeatDialogueScript ?? '',
          spawnPoints: JSON.stringify(npc.spawnPoints ?? []),
          attackSound: npc.attackSound ?? '',
          missSound: npc.missSound ?? '',
          deathSound: npc.deathSound ?? '',
          interactSound: npc.interactSound ?? '',
          lootItems: JSON.stringify(npc.lootItems ?? []),
          coinDrop: JSON.stringify(npc.coinDrop ?? {}),
          exitSound: npc.exitSound ?? '',
          imagePrompt: npc.imagePrompt ?? legacyNpc?.prompt ?? '',
          imageStyle: npc.imageStyle ?? legacyNpc?.style ?? '',
          imageNegativePrompt: npc.imageNegativePrompt ?? legacyNpc?.negativePrompt ?? '',
          imageWidth: npc.imageWidth ?? legacyNpc?.width ?? 384,
          imageHeight: npc.imageHeight ?? legacyNpc?.height ?? 512,
          phases: JSON.stringify(npc.phases ?? []),
          onKillFlags: JSON.stringify(npc.onKillFlags ?? {}),
          onSpawnRelockExits: JSON.stringify(npc.onSpawnRelockExits ?? []),
      }
      const { id: _id, ...npcUpdateData } = npcData
      await prisma.npc.upsert({
        where: { id: npc.id },
        create: npcData,
        update: npcUpdateData,
      })
    }
  }

  // Migrate legacy item templates (update items already created above)
  if (legacyTemplates.size > 0) {
    for (const [key, tmpl] of legacyTemplates) {
      if (!key.startsWith('item:')) continue
      const itemId = key.substring(5) // strip "item:" prefix
      try {
        await prisma.item.update({
          where: { id: itemId },
          data: {
            imagePrompt: tmpl.prompt ?? '',
            imageStyle: tmpl.style ?? '',
            imageNegativePrompt: tmpl.negativePrompt ?? '',
            imageWidth: tmpl.width ?? 256,
            imageHeight: tmpl.height ?? 256,
          },
        })
      } catch {
        // Item may not exist — skip silently
      }
    }
  }

  // ─── Default SFX ─────────────────────────────────────
  const sfxCount = await prisma.defaultSfx.count()
  if (sfxCount === 0) {
    const { seedDefaultSfx } = await import('./defaultSfxDefaults.js')
    await seedDefaultSfx(prisma)
  }

  // Second pass: create all exits now that every room exists
  for (const zone of parsedZones) {
    for (const room of zone.rooms ?? []) {
      const exits = room.exits ?? {}
      for (const [direction, toRoomId] of Object.entries(exits) as [string, string][]) {
        await prisma.exit.upsert({
          where: { fromRoomId_direction: { fromRoomId: room.id, direction } },
          create: { fromRoomId: room.id, direction, toRoomId },
          update: { toRoomId },
        })
      }
    }
  }

  } finally {
    await prisma.$disconnect()
  }
}
