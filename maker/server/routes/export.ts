import { Router } from 'express'
import fs from 'fs'
import path from 'path'
import AdmZip from 'adm-zip'
import { PrismaClient } from '../generated/prisma/client.js'
import { validateProject } from '../validate.js'

export const exportRouter = Router()

// Keep in sync with shared/src/.../NeoMudVersion.kt VERSION_CODE
const NEOMUD_ENGINE_VERSION = '0.1.0.0'

/** Safely parse a JSON string field, returning the fallback if empty or invalid. */
function parseJsonField(value: string, fallback: any = {}): any {
  if (!value || value === '') return fallback
  try {
    return JSON.parse(value)
  } catch {
    return fallback
  }
}

/** Build a .nmd ZIP bundle from the active project's data + assets. */
export async function buildNmdBundle(prisma: PrismaClient, assetsDir: string): Promise<Buffer> {
  const zip = new AdmZip()

  // ─── Manifest ───────────────────────────────────────
  const metaRows = await prisma.projectMeta.findMany()
  const manifest: Record<string, any> = {}
  for (const row of metaRows) {
    if (row.key === 'readOnly') continue
    const num = Number(row.value)
    if (!isNaN(num) && row.value !== '') manifest[row.key] = num
    else if (row.value === 'true') manifest[row.key] = true
    else if (row.value === 'false') manifest[row.key] = false
    else manifest[row.key] = row.value
  }
  // Stamp defaults for every field the platform's bundleManifestSchema
  // requires. Most of these are populated by the user via the settings
  // editor; for un-configured projects (including freshly-created ones
  // used for playtest) we fall back to sensible placeholders so the
  // bundle still validates downstream.
  if (!manifest.engineVersion) manifest.engineVersion = NEOMUD_ENGINE_VERSION
  if (!manifest.engineVersionMin) manifest.engineVersionMin = NEOMUD_ENGINE_VERSION
  if (!manifest.formatVersion) manifest.formatVersion = 1
  if (!manifest.name || typeof manifest.name !== 'string' || manifest.name.length === 0) {
    manifest.name = 'Untitled World'
  }
  if (!manifest.author || typeof manifest.author !== 'string' || manifest.author.length === 0) {
    manifest.author = 'Unknown'
  }
  if (!manifest.version || typeof manifest.version !== 'string' || manifest.version.length === 0) {
    manifest.version = '0.0.1'
  }
  manifest.createdWithMaker = true
  zip.addFile('manifest.json', Buffer.from(JSON.stringify(manifest, null, 2)))

  // ─── Fetch all data ─────────────────────────────────
  const [zones, items, npcs, classes, races, skills, spells, recipes] =
    await Promise.all([
      prisma.zone.findMany({ include: { rooms: { include: { exits: true } } } }),
      prisma.item.findMany(),
      prisma.npc.findMany(),
      prisma.characterClass.findMany(),
      prisma.race.findMany(),
      prisma.skill.findMany(),
      prisma.spell.findMany(),
      prisma.recipe.findMany(),
    ])

  // ─── Zone files (one per zone) ──────────────────────
  for (const zone of zones) {
    const zoneNpcs = npcs.filter((n) => n.zoneId === zone.id)
    const zoneOut: any = {
      id: zone.id,
      name: zone.name,
      description: zone.description,
      safe: zone.safe,
      bgm: zone.bgm,
      bgmPrompt: zone.bgmPrompt,
      bgmDuration: zone.bgmDuration,
      spawnRoom: zone.spawnRoom,
      imageStyle: zone.imageStyle,
      imageNegativePrompt: zone.imageNegativePrompt,
      spawnConfig: {
        maxEntities: zone.spawnMaxEntities,
        maxPerRoom: zone.spawnMaxPerRoom,
        rateTicks: zone.spawnRateTicks,
      },
      rooms: zone.rooms.map((room) => {
        const exits: Record<string, string> = {}
        for (const exit of room.exits) {
          exits[exit.direction] = exit.toRoomId
        }
        const lockedExits = parseJsonField(room.lockedExits, {})
        const lockResetTicks = parseJsonField(room.lockResetTicks, {})
        const hiddenExits = parseJsonField(room.hiddenExits, {})
        return {
          id: room.id,
          name: room.name,
          description: room.description,
          x: room.x,
          y: room.y,
          backgroundImage: room.backgroundImage,
          ...((() => { const e = parseJsonField(room.effects, []); return e.length > 0 ? { effects: e } : {}; })()),
          bgm: room.bgm,
          bgmPrompt: room.bgmPrompt,
          bgmDuration: room.bgmDuration,
          departSound: room.departSound,
          imagePrompt: room.imagePrompt,
          imageStyle: room.imageStyle,
          imageNegativePrompt: room.imageNegativePrompt,
          imageWidth: room.imageWidth,
          imageHeight: room.imageHeight,
          exits,
          ...(Object.keys(lockedExits).length > 0 ? { lockedExits } : {}),
          ...(Object.keys(lockResetTicks).length > 0 ? { lockResetTicks } : {}),
          ...(Object.keys(hiddenExits).length > 0 ? { hiddenExits } : {}),
          ...((() => { const i = parseJsonField(room.interactables, []); return i.length > 0 ? { interactables: i } : {}; })()),
          ...((() => { const u = parseJsonField(room.unpickableExits, []); return u.length > 0 ? { unpickableExits: u } : {}; })()),
          ...(room.maxHostileNpcs != null ? { maxHostileNpcs: room.maxHostileNpcs } : {}),
        }
      }),
      npcs: zoneNpcs.map((npc) => ({
        id: npc.id,
        name: npc.name,
        description: npc.description,
        startRoomId: npc.startRoomId,
        behaviorType: npc.behaviorType,
        patrolRoute: parseJsonField(npc.patrolRoute, []),
        hostile: npc.hostile,
        maxHp: npc.maxHp,
        damage: npc.damage,
        level: npc.level,
        perception: npc.perception,
        xpReward: npc.xpReward,
        accuracy: npc.accuracy,
        defense: npc.defense,
        evasion: npc.evasion,
        agility: npc.agility,
        vendorItems: parseJsonField(npc.vendorItems, []),
        spawnPoints: parseJsonField(npc.spawnPoints, []),
        attackSound: npc.attackSound,
        missSound: npc.missSound,
        deathSound: npc.deathSound,
        interactSound: npc.interactSound,
        exitSound: npc.exitSound,
        imagePrompt: npc.imagePrompt,
        imageStyle: npc.imageStyle,
        imageNegativePrompt: npc.imageNegativePrompt,
        imageWidth: npc.imageWidth,
        imageHeight: npc.imageHeight,
        ...((() => { const li = parseJsonField(npc.lootItems, []); return li.length > 0 ? { lootItems: li } : {}; })()),
        ...((() => { const cd = parseJsonField(npc.coinDrop); return Object.keys(cd).length > 0 ? { coinDrop: cd } : {}; })()),
        ...((() => { const cr = parseJsonField(npc.crafterRecipes, []); return cr.length > 0 ? { crafterRecipes: cr } : {}; })()),
      })),
    }
    zip.addFile(`world/${zone.id}.zone.json`, Buffer.from(JSON.stringify(zoneOut, null, 2)))
  }

  // ─── Catalog files ──────────────────────────────────
  // Items
  zip.addFile(
    'world/items.json',
    Buffer.from(
      JSON.stringify(
        {
          items: items.map((item) => ({
            id: item.id,
            name: item.name,
            description: item.description,
            type: item.type,
            slot: item.slot,
            damageBonus: item.damageBonus,
            damageRange: item.damageRange,
            armorValue: item.armorValue,
            value: item.value,
            weight: item.weight,
            stackable: item.stackable,
            maxStack: item.maxStack,
            useEffect: item.useEffect,
            levelRequirement: item.levelRequirement,
            attackSound: item.attackSound,
            missSound: item.missSound,
            useSound: item.useSound,
            imagePrompt: item.imagePrompt,
            imageStyle: item.imageStyle,
            imageNegativePrompt: item.imageNegativePrompt,
            imageWidth: item.imageWidth,
            imageHeight: item.imageHeight,
          })),
        },
        null,
        2
      )
    )
  )

  // Classes
  zip.addFile(
    'world/classes.json',
    Buffer.from(
      JSON.stringify(
        {
          classes: classes.map((cls) => ({
            id: cls.id,
            name: cls.name,
            description: cls.description,
            minimumStats: parseJsonField(cls.minimumStats),
            skills: parseJsonField(cls.skills, []),
            properties: parseJsonField(cls.properties),
            hpPerLevelMin: cls.hpPerLevelMin,
            hpPerLevelMax: cls.hpPerLevelMax,
            mpPerLevelMin: cls.mpPerLevelMin,
            mpPerLevelMax: cls.mpPerLevelMax,
            xpModifier: cls.xpModifier,
            magicSchools: parseJsonField(cls.magicSchools),
            imagePrompt: cls.imagePrompt,
            imageStyle: cls.imageStyle,
            imageNegativePrompt: cls.imageNegativePrompt,
            imageWidth: cls.imageWidth,
            imageHeight: cls.imageHeight,
          })),
        },
        null,
        2
      )
    )
  )

  // Races
  zip.addFile(
    'world/races.json',
    Buffer.from(
      JSON.stringify(
        {
          races: races.map((race) => ({
            id: race.id,
            name: race.name,
            description: race.description,
            statModifiers: parseJsonField(race.statModifiers),
            xpModifier: race.xpModifier,
            imagePrompt: race.imagePrompt,
            imageStyle: race.imageStyle,
            imageNegativePrompt: race.imageNegativePrompt,
            imageWidth: race.imageWidth,
            imageHeight: race.imageHeight,
          })),
        },
        null,
        2
      )
    )
  )

  // Skills
  zip.addFile(
    'world/skills.json',
    Buffer.from(
      JSON.stringify(
        {
          skills: skills.map((skill) => ({
            id: skill.id,
            name: skill.name,
            description: skill.description,
            category: skill.category,
            primaryStat: skill.primaryStat,
            secondaryStat: skill.secondaryStat,
            cooldownTicks: skill.cooldownTicks,
            manaCost: skill.manaCost,
            difficulty: skill.difficulty,
            isPassive: skill.isPassive,
            classRestrictions: parseJsonField(skill.classRestrictions, []),
            properties: parseJsonField(skill.properties),
            imagePrompt: skill.imagePrompt,
            imageStyle: skill.imageStyle,
            imageNegativePrompt: skill.imageNegativePrompt,
            imageWidth: skill.imageWidth,
            imageHeight: skill.imageHeight,
          })),
        },
        null,
        2
      )
    )
  )

  // Spells
  zip.addFile(
    'world/spells.json',
    Buffer.from(
      JSON.stringify(
        {
          spells: spells.map((spell) => ({
            id: spell.id,
            name: spell.name,
            description: spell.description,
            school: spell.school,
            spellType: spell.spellType,
            manaCost: spell.manaCost,
            cooldownTicks: spell.cooldownTicks,
            levelRequired: spell.levelRequired,
            primaryStat: spell.primaryStat,
            basePower: spell.basePower,
            targetType: spell.targetType,
            effectType: spell.effectType,
            effectDuration: spell.effectDuration,
            castMessage: spell.castMessage,
            castSound: spell.castSound,
            impactSound: spell.impactSound,
            missSound: spell.missSound,
            imagePrompt: spell.imagePrompt,
            imageStyle: spell.imageStyle,
            imageNegativePrompt: spell.imageNegativePrompt,
            imageWidth: spell.imageWidth,
            imageHeight: spell.imageHeight,
          })),
        },
        null,
        2
      )
    )
  )

  // ─── Recipes ──────────────────────────────────────
  zip.addFile(
    'world/recipes.json',
    Buffer.from(
      JSON.stringify(
        {
          recipes: recipes.map((recipe) => ({
            id: recipe.id,
            name: recipe.name,
            description: recipe.description,
            category: recipe.category,
            materials: parseJsonField(recipe.materials, []),
            cost: parseJsonField(recipe.cost),
            outputItemId: recipe.outputItemId,
            outputQuantity: recipe.outputQuantity,
            levelRequirement: recipe.levelRequirement,
            ...(recipe.classRestriction ? { classRestriction: recipe.classRestriction } : {}),
          })),
        },
        null,
        2
      )
    )
  )

  // ─── PC Sprites ────────────────────────────────────
  const pcSprites = await prisma.pcSprite.findMany({ orderBy: { id: 'asc' } })
  if (pcSprites.length > 0) {
    zip.addFile(
      'world/pc_sprites.json',
      Buffer.from(
        JSON.stringify(
          {
            sprites: pcSprites.map((s) => ({
              id: s.id,
              race: s.race,
              gender: s.gender,
              characterClass: s.characterClass,
              imagePrompt: s.imagePrompt,
              imageStyle: s.imageStyle,
              imageNegativePrompt: s.imageNegativePrompt,
              imageWidth: s.imageWidth,
              imageHeight: s.imageHeight,
            })),
          },
          null,
          2
        )
      )
    )
  }

  // ─── Assets ─────────────────────────────────────────
  if (fs.existsSync(assetsDir)) {
    function addDirToZip(dirPath: string, zipPrefix: string) {
      for (const entry of fs.readdirSync(dirPath)) {
        const fullPath = path.join(dirPath, entry)
        const zipPath = `${zipPrefix}/${entry}`
        if (fs.statSync(fullPath).isDirectory()) {
          addDirToZip(fullPath, zipPath)
        } else {
          zip.addLocalFile(fullPath, path.dirname(zipPath))
        }
      }
    }
    addDirToZip(assetsDir, 'assets')
  }

  return zip.toBuffer()
}

// GET /json — export all data as NeoMUD-format JSON
exportRouter.get('/json', async (req, res) => {
  try {
    const prisma = req.db!

    // Fetch all data in parallel
    const [
      zones,
      items,
      npcs,
      classes,
      races,
      skills,
      spells,
      recipes,
    ] = await Promise.all([
      prisma.zone.findMany({ include: { rooms: { include: { exits: true } } } }),
      prisma.item.findMany(),
      prisma.npc.findMany(),
      prisma.characterClass.findMany(),
      prisma.race.findMany(),
      prisma.skill.findMany(),
      prisma.spell.findMany(),
      prisma.recipe.findMany(),
    ])

    // ─── Zones + Rooms ──────────────────────────────────
    const zonesOut: Record<string, any> = {}
    for (const zone of zones) {
      const roomsOut: Record<string, any> = {}
      for (const room of zone.rooms) {
        const exits: Record<string, string> = {}
        for (const exit of room.exits) {
          exits[exit.direction] = exit.toRoomId
        }
        const lockedExits = parseJsonField(room.lockedExits, {})
        const lockResetTicks = parseJsonField(room.lockResetTicks, {})
        const hiddenExits = parseJsonField(room.hiddenExits, {})
        roomsOut[room.id] = {
          name: room.name,
          description: room.description,
          backgroundImage: room.backgroundImage,
          bgm: room.bgm,
          bgmPrompt: room.bgmPrompt,
          bgmDuration: room.bgmDuration,
          departSound: room.departSound,
          ...((() => { const e = parseJsonField(room.effects, []); return e.length > 0 ? { effects: e } : {}; })()),
          imagePrompt: room.imagePrompt,
          imageStyle: room.imageStyle,
          imageNegativePrompt: room.imageNegativePrompt,
          imageWidth: room.imageWidth,
          imageHeight: room.imageHeight,
          exits,
          ...(Object.keys(lockedExits).length > 0 ? { lockedExits } : {}),
          ...(Object.keys(lockResetTicks).length > 0 ? { lockResetTicks } : {}),
          ...(Object.keys(hiddenExits).length > 0 ? { hiddenExits } : {}),
          ...((() => { const i = parseJsonField(room.interactables, []); return i.length > 0 ? { interactables: i } : {}; })()),
          ...((() => { const u = parseJsonField(room.unpickableExits, []); return u.length > 0 ? { unpickableExits: u } : {}; })()),
          ...(room.maxHostileNpcs != null ? { maxHostileNpcs: room.maxHostileNpcs } : {}),
        }
      }

      // Build NPC map for this zone
      const zoneNpcs = npcs.filter((n) => n.zoneId === zone.id)
      const npcsOut: Record<string, any> = {}
      for (const npc of zoneNpcs) {
        npcsOut[npc.id] = {
          name: npc.name,
          description: npc.description,
          startRoomId: npc.startRoomId,
          behaviorType: npc.behaviorType,
          patrolRoute: parseJsonField(npc.patrolRoute, []),
          hostile: npc.hostile,
          maxHp: npc.maxHp,
          damage: npc.damage,
          level: npc.level,
          perception: npc.perception,
          xpReward: npc.xpReward,
          accuracy: npc.accuracy,
          defense: npc.defense,
          evasion: npc.evasion,
          agility: npc.agility,
          vendorItems: parseJsonField(npc.vendorItems, []),
          spawnPoints: parseJsonField(npc.spawnPoints, []),
          attackSound: npc.attackSound,
          missSound: npc.missSound,
          deathSound: npc.deathSound,
          interactSound: npc.interactSound,
          exitSound: npc.exitSound,
          imagePrompt: npc.imagePrompt,
          imageStyle: npc.imageStyle,
          imageNegativePrompt: npc.imageNegativePrompt,
          imageWidth: npc.imageWidth,
          imageHeight: npc.imageHeight,
          ...((() => { const li = parseJsonField(npc.lootItems, []); return li.length > 0 ? { lootItems: li } : {}; })()),
          ...((() => { const cd = parseJsonField(npc.coinDrop); return Object.keys(cd).length > 0 ? { coinDrop: cd } : {}; })()),
          ...((() => { const cr = parseJsonField(npc.crafterRecipes, []); return cr.length > 0 ? { crafterRecipes: cr } : {}; })()),
        }
      }

      zonesOut[zone.id] = {
        name: zone.name,
        description: zone.description,
        safe: zone.safe,
        bgm: zone.bgm,
        bgmPrompt: zone.bgmPrompt,
        bgmDuration: zone.bgmDuration,
        imageStyle: zone.imageStyle,
        imageNegativePrompt: zone.imageNegativePrompt,
        spawnConfig: {
          spawnRoom: zone.spawnRoom,
          maxEntities: zone.spawnMaxEntities,
          maxPerRoom: zone.spawnMaxPerRoom,
          rateTicks: zone.spawnRateTicks,
        },
        rooms: roomsOut,
        npcs: npcsOut,
      }
    }

    // ─── Items ──────────────────────────────────────────
    const itemsOut: Record<string, any> = {}
    for (const item of items) {
      itemsOut[item.id] = {
        name: item.name,
        description: item.description,
        type: item.type,
        slot: item.slot,
        damageBonus: item.damageBonus,
        damageRange: item.damageRange,
        armorValue: item.armorValue,
        value: item.value,
        weight: item.weight,
        stackable: item.stackable,
        maxStack: item.maxStack,
        useEffect: item.useEffect,
        levelRequirement: item.levelRequirement,
        attackSound: item.attackSound,
        missSound: item.missSound,
        useSound: item.useSound,
        imagePrompt: item.imagePrompt,
        imageStyle: item.imageStyle,
        imageNegativePrompt: item.imageNegativePrompt,
        imageWidth: item.imageWidth,
        imageHeight: item.imageHeight,
      }
    }

    // ─── Classes ────────────────────────────────────────
    const classesOut: Record<string, any> = {}
    for (const cls of classes) {
      classesOut[cls.id] = {
        name: cls.name,
        description: cls.description,
        minimumStats: parseJsonField(cls.minimumStats),
        skills: parseJsonField(cls.skills, []),
        properties: parseJsonField(cls.properties),
        hpPerLevelMin: cls.hpPerLevelMin,
        hpPerLevelMax: cls.hpPerLevelMax,
        mpPerLevelMin: cls.mpPerLevelMin,
        mpPerLevelMax: cls.mpPerLevelMax,
        xpModifier: cls.xpModifier,
        magicSchools: parseJsonField(cls.magicSchools),
        imagePrompt: cls.imagePrompt,
        imageStyle: cls.imageStyle,
        imageNegativePrompt: cls.imageNegativePrompt,
        imageWidth: cls.imageWidth,
        imageHeight: cls.imageHeight,
      }
    }

    // ─── Races ──────────────────────────────────────────
    const racesOut: Record<string, any> = {}
    for (const race of races) {
      racesOut[race.id] = {
        name: race.name,
        description: race.description,
        statModifiers: parseJsonField(race.statModifiers),
        xpModifier: race.xpModifier,
        imagePrompt: race.imagePrompt,
        imageStyle: race.imageStyle,
        imageNegativePrompt: race.imageNegativePrompt,
        imageWidth: race.imageWidth,
        imageHeight: race.imageHeight,
      }
    }

    // ─── Skills ─────────────────────────────────────────
    const skillsOut: Record<string, any> = {}
    for (const skill of skills) {
      skillsOut[skill.id] = {
        name: skill.name,
        description: skill.description,
        category: skill.category,
        primaryStat: skill.primaryStat,
        secondaryStat: skill.secondaryStat,
        cooldownTicks: skill.cooldownTicks,
        manaCost: skill.manaCost,
        difficulty: skill.difficulty,
        isPassive: skill.isPassive,
        classRestrictions: parseJsonField(skill.classRestrictions, []),
        properties: parseJsonField(skill.properties),
        imagePrompt: skill.imagePrompt,
        imageStyle: skill.imageStyle,
        imageNegativePrompt: skill.imageNegativePrompt,
        imageWidth: skill.imageWidth,
        imageHeight: skill.imageHeight,
      }
    }

    // ─── Spells ─────────────────────────────────────────
    const spellsOut: Record<string, any> = {}
    for (const spell of spells) {
      spellsOut[spell.id] = {
        name: spell.name,
        description: spell.description,
        school: spell.school,
        spellType: spell.spellType,
        manaCost: spell.manaCost,
        cooldownTicks: spell.cooldownTicks,
        levelRequired: spell.levelRequired,
        primaryStat: spell.primaryStat,
        basePower: spell.basePower,
        targetType: spell.targetType,
        effectType: spell.effectType,
        effectDuration: spell.effectDuration,
        castMessage: spell.castMessage,
        castSound: spell.castSound,
        impactSound: spell.impactSound,
        missSound: spell.missSound,
        imagePrompt: spell.imagePrompt,
        imageStyle: spell.imageStyle,
        imageNegativePrompt: spell.imageNegativePrompt,
        imageWidth: spell.imageWidth,
        imageHeight: spell.imageHeight,
      }
    }

    // ─── Recipes ─────────────────────────────────────
    const recipesOut: Record<string, any> = {}
    for (const recipe of recipes) {
      recipesOut[recipe.id] = {
        name: recipe.name,
        description: recipe.description,
        category: recipe.category,
        materials: parseJsonField(recipe.materials, []),
        cost: parseJsonField(recipe.cost),
        outputItemId: recipe.outputItemId,
        outputQuantity: recipe.outputQuantity,
        levelRequirement: recipe.levelRequirement,
        classRestriction: recipe.classRestriction,
      }
    }

    res.json({
      zones: zonesOut,
      items: itemsOut,
      classes: classesOut,
      races: racesOut,
      skills: skillsOut,
      spells: spellsOut,
      recipes: recipesOut,
    })
  } catch (err) {
    console.error('[export] error:', err)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// GET /nmd — export complete .nmd bundle (JSON data + assets), no validation
exportRouter.get('/nmd', async (req, res) => {
  try {
    const prisma = req.db!
    const projectName = req.projectName!

    const buffer = await buildNmdBundle(prisma, req.projectDir!)
    res.set({
      'Content-Type': 'application/zip',
      'Content-Disposition': `attachment; filename="${projectName}.nmd"`,
      'Content-Length': buffer.length.toString(),
    })
    res.send(buffer)
  } catch (err) {
    console.error('[export] error:', err)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// GET /validate — run validation checks, return results as JSON
exportRouter.get('/validate', async (req, res) => {
  try {
    const prisma = req.db!

    const assetExists = (p: string) => fs.existsSync(path.join(req.projectDir!, p))
    const result = await validateProject(prisma, assetExists)

    res.json(result)
  } catch (err) {
    console.error('[export] error:', err)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// GET /package — validate then export .nmd bundle
exportRouter.get('/package', async (req, res) => {
  try {
    const prisma = req.db!
    const projectName = req.projectName!

    const assetExists = (p: string) => fs.existsSync(path.join(req.projectDir!, p))
    const result = await validateProject(prisma, assetExists)

    if (result.errors.length > 0) {
      res.status(400).json({ errors: result.errors, warnings: result.warnings })
      return
    }

    const buffer = await buildNmdBundle(prisma, req.projectDir!)
    res.set({
      'Content-Type': 'application/zip',
      'Content-Disposition': `attachment; filename="${projectName}.nmd"`,
      'Content-Length': buffer.length.toString(),
      'X-Validation-Warnings': JSON.stringify(result.warnings),
    })
    res.send(buffer)
  } catch (err) {
    console.error('[export] error:', err)
    res.status(500).json({ error: 'Internal server error' })
  }
})
