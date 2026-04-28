import { Router, Request, Response } from 'express'
import { Prisma } from '../generated/prisma/client.js'
import { rejectIfReadOnly } from '../middleware/readOnly.js'

/** Extract a route parameter as a string (Express 5 types return string | string[]). */
function param(req: Request, name: string): string {
  const val = req.params[name]
  return Array.isArray(val) ? val[0] : val ?? ''
}

export const entitiesRouter = Router()

const VALID_ID_RE = /^[a-zA-Z0-9_:]+$/

function validateId(id: string | undefined, entityLabel: string, res: Response): boolean {
  if (!id || !id.trim()) {
    res.status(400).json({ error: `${entityLabel} ID is required` })
    return false
  }
  if (!VALID_ID_RE.test(id)) {
    res.status(400).json({ error: `${entityLabel} ID may only contain letters, numbers, underscores, and colons` })
    return false
  }
  return true
}

function validateName(name: string | undefined, entityLabel: string, res: Response): boolean {
  if (!name || !name.trim()) {
    res.status(400).json({ error: `${entityLabel} name is required` })
    return false
  }
  if (name.length > 100) {
    res.status(400).json({ error: `${entityLabel} name must be 100 characters or fewer` })
    return false
  }
  if (/<[^>]*>/.test(name)) {
    res.status(400).json({ error: `${entityLabel} name must not contain HTML tags` })
    return false
  }
  return true
}

const MAX_TEXT_LENGTH = 5000

function sanitizeTextFields(body: Record<string, any>): void {
  for (const key of Object.keys(body)) {
    if (typeof body[key] === 'string') {
      // Trim all string fields
      body[key] = body[key].trim()
      // Enforce max length on text content fields (not JSON blobs)
      if (body[key].length > MAX_TEXT_LENGTH && !body[key].startsWith('[') && !body[key].startsWith('{')) {
        body[key] = body[key].substring(0, MAX_TEXT_LENGTH)
      }
    }
  }
}

function articleFor(label: string): string {
  if (/^[aeiou]/i.test(label)) return 'An'
  if (/^[A-Z]{2,}/.test(label) && /^[AEFHILMNORSX]/i.test(label)) return 'An'
  return 'A'
}

interface NumericRule {
  field: string;
  label: string;
  min: number;
}

const NPC_NUMERIC_RULES: NumericRule[] = [
  { field: 'level', label: 'Level', min: 1 },
  { field: 'maxHp', label: 'Max HP', min: 0 },
  { field: 'damage', label: 'Damage', min: 0 },
  { field: 'accuracy', label: 'Accuracy', min: 0 },
  { field: 'defense', label: 'Defense', min: 0 },
  { field: 'evasion', label: 'Evasion', min: 0 },
  { field: 'agility', label: 'Agility', min: 0 },
  { field: 'perception', label: 'Perception', min: 0 },
  { field: 'xpReward', label: 'XP Reward', min: 0 },
]

const ITEM_NUMERIC_RULES: NumericRule[] = [
  { field: 'value', label: 'Value', min: 0 },
  { field: 'weight', label: 'Weight', min: 0 },
  { field: 'damageBonus', label: 'Damage Bonus', min: 0 },
  { field: 'damageRange', label: 'Damage Range', min: 0 },
  { field: 'armorValue', label: 'Armor Value', min: 0 },
  { field: 'levelRequirement', label: 'Level Requirement', min: 0 },
  { field: 'maxStack', label: 'Max Stack', min: 1 },
]

function validateNumericRanges(body: Record<string, any>, rules: NumericRule[], res: Response): boolean {
  for (const rule of rules) {
    const val = body[rule.field]
    if (val !== undefined && val !== null && typeof val === 'number' && val < rule.min) {
      res.status(400).json({ error: `${rule.label} must be at least ${rule.min}` })
      return false
    }
  }
  return true
}

function handlePrismaError(err: unknown, entityLabel: string, res: Response) {
  if (err instanceof Prisma.PrismaClientKnownRequestError) {
    if (err.code === 'P2002') {
      res.status(409).json({ error: `${articleFor(entityLabel)} ${entityLabel} with that ID already exists` })
      return
    }
    if (err.code === 'P2025') {
      res.status(404).json({ error: `${entityLabel} not found` })
      return
    }
  }
  if (err instanceof Prisma.PrismaClientValidationError) {
    const msg = (err as any).message || ''
    const missing = msg.match(/Argument `(\w+)` is missing/)
    if (missing) {
      res.status(400).json({ error: `Required field '${missing[1]}' is missing` })
      return
    }
    res.status(400).json({ error: 'Invalid data — check required fields and types' })
    return
  }
  console.error(`[entities] Unhandled error:`, err)
  res.status(500).json({ error: 'Internal server error' })
}

// ─── Items ─────────────────────────────────────────────────────

entitiesRouter.get('/items', async (req, res) => {
  try {
    const items = await req.db!.item.findMany()
    res.json(items)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.get('/items/:id', async (req, res) => {
  try {
    const item = await req.db!.item.findUnique({ where: { id: param(req, 'id') } })
    if (!item) { res.status(404).json({ error: 'Item not found' }); return }
    res.json(item)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.post('/items', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (!validateId(req.body.id, 'Item', res)) return
  if (!validateName(req.body.name, 'Item', res)) return
  if (!validateNumericRanges(req.body, ITEM_NUMERIC_RULES, res)) return
  try {
    const item = await req.db!.item.create({ data: req.body })
    res.json(item)
  } catch (err: any) {
    handlePrismaError(err, 'item', res)
  }
})

entitiesRouter.put('/items/:id', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (req.body.name !== undefined && !validateName(req.body.name, 'Item', res)) return
  if (!validateNumericRanges(req.body, ITEM_NUMERIC_RULES, res)) return
  try {
    const item = await req.db!.item.update({ where: { id: param(req, 'id') }, data: req.body })
    res.json(item)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.delete('/items/:id', rejectIfReadOnly, async (req, res) => {
  try {
    await req.db!.item.delete({ where: { id: param(req, 'id') } })
    res.json({ ok: true })
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// ─── NPCs ──────────────────────────────────────────────────────

entitiesRouter.get('/npcs', async (req, res) => {
  try {
    const where = req.query.zoneId ? { zoneId: req.query.zoneId as string } : {}
    const npcs = await req.db!.npc.findMany({ where })
    res.json(npcs)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.get('/npcs/:id', async (req, res) => {
  try {
    const npc = await req.db!.npc.findUnique({ where: { id: param(req, 'id') } })
    if (!npc) { res.status(404).json({ error: 'NPC not found' }); return }
    res.json(npc)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.post('/npcs', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (!validateId(req.body.id, 'NPC', res)) return
  if (!validateName(req.body.name, 'NPC', res)) return
  if (!validateNumericRanges(req.body, NPC_NUMERIC_RULES, res)) return
  try {
    const { zoneId } = req.body
    if (!zoneId || !zoneId.trim()) {
      res.status(400).json({ error: 'zoneId is required — every NPC must belong to a zone' }); return
    }
    const zone = await req.db!.zone.findUnique({ where: { id: zoneId } })
    if (!zone) {
      res.status(400).json({ error: `Zone "${zoneId}" does not exist` }); return
    }
    const npc = await req.db!.npc.create({ data: req.body })
    res.json(npc)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.put('/npcs/:id', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (req.body.name !== undefined && !validateName(req.body.name, 'NPC', res)) return
  if (!validateNumericRanges(req.body, NPC_NUMERIC_RULES, res)) return
  try {
    const npc = await req.db!.npc.update({ where: { id: param(req, 'id') }, data: req.body })
    res.json(npc)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.delete('/npcs/:id', rejectIfReadOnly, async (req, res) => {
  try {
    await req.db!.npc.delete({ where: { id: param(req, 'id') } })
    res.json({ ok: true })
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// ─── Character Classes ─────────────────────────────────────────

entitiesRouter.get('/character-classes', async (req, res) => {
  try {
    const classes = await req.db!.characterClass.findMany()
    res.json(classes)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.get('/character-classes/:id', async (req, res) => {
  try {
    const cls = await req.db!.characterClass.findUnique({ where: { id: param(req, 'id') } })
    if (!cls) { res.status(404).json({ error: 'Class not found' }); return }
    res.json(cls)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.post('/character-classes', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (!validateId(req.body.id, 'Class', res)) return
  if (!validateName(req.body.name, 'Class', res)) return
  try {
    const cls = await req.db!.characterClass.create({ data: req.body })
    res.json(cls)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.put('/character-classes/:id', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (req.body.name !== undefined && !validateName(req.body.name, 'Class', res)) return
  try {
    const cls = await req.db!.characterClass.update({ where: { id: param(req, 'id') }, data: req.body })
    res.json(cls)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.delete('/character-classes/:id', rejectIfReadOnly, async (req, res) => {
  try {
    await req.db!.characterClass.delete({ where: { id: param(req, 'id') } })
    res.json({ ok: true })
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// ─── Races ─────────────────────────────────────────────────────

entitiesRouter.get('/races', async (req, res) => {
  try {
    const races = await req.db!.race.findMany()
    res.json(races)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.get('/races/:id', async (req, res) => {
  try {
    const race = await req.db!.race.findUnique({ where: { id: param(req, 'id') } })
    if (!race) { res.status(404).json({ error: 'Race not found' }); return }
    res.json(race)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.post('/races', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (!validateId(req.body.id, 'Race', res)) return
  if (!validateName(req.body.name, 'Race', res)) return
  try {
    const race = await req.db!.race.create({ data: req.body })
    res.json(race)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.put('/races/:id', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (req.body.name !== undefined && !validateName(req.body.name, 'Race', res)) return
  try {
    const race = await req.db!.race.update({ where: { id: param(req, 'id') }, data: req.body })
    res.json(race)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.delete('/races/:id', rejectIfReadOnly, async (req, res) => {
  try {
    await req.db!.race.delete({ where: { id: param(req, 'id') } })
    res.json({ ok: true })
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// ─── Skills ────────────────────────────────────────────────────

entitiesRouter.get('/skills', async (req, res) => {
  try {
    const skills = await req.db!.skill.findMany()
    res.json(skills)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.get('/skills/:id', async (req, res) => {
  try {
    const skill = await req.db!.skill.findUnique({ where: { id: param(req, 'id') } })
    if (!skill) { res.status(404).json({ error: 'Skill not found' }); return }
    res.json(skill)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.post('/skills', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (!validateId(req.body.id, 'Skill', res)) return
  if (!validateName(req.body.name, 'Skill', res)) return
  try {
    const skill = await req.db!.skill.create({ data: req.body })
    res.json(skill)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.put('/skills/:id', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (req.body.name !== undefined && !validateName(req.body.name, 'Skill', res)) return
  try {
    const skill = await req.db!.skill.update({ where: { id: param(req, 'id') }, data: req.body })
    res.json(skill)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.delete('/skills/:id', rejectIfReadOnly, async (req, res) => {
  try {
    await req.db!.skill.delete({ where: { id: param(req, 'id') } })
    res.json({ ok: true })
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// ─── Spells ────────────────────────────────────────────────────

entitiesRouter.get('/spells', async (req, res) => {
  try {
    const spells = await req.db!.spell.findMany()
    res.json(spells)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.get('/spells/:id', async (req, res) => {
  try {
    const spell = await req.db!.spell.findUnique({ where: { id: param(req, 'id') } })
    if (!spell) { res.status(404).json({ error: 'Spell not found' }); return }
    res.json(spell)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.post('/spells', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (!validateId(req.body.id, 'Spell', res)) return
  if (!validateName(req.body.name, 'Spell', res)) return
  try {
    const spell = await req.db!.spell.create({ data: req.body })
    res.json(spell)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.put('/spells/:id', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (req.body.name !== undefined && !validateName(req.body.name, 'Spell', res)) return
  try {
    const spell = await req.db!.spell.update({ where: { id: param(req, 'id') }, data: req.body })
    res.json(spell)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.delete('/spells/:id', rejectIfReadOnly, async (req, res) => {
  try {
    await req.db!.spell.delete({ where: { id: param(req, 'id') } })
    res.json({ ok: true })
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// ─── Recipes ──────────────────────────────────────────────────

entitiesRouter.get('/recipes', async (req, res) => {
  try {
    const recipes = await req.db!.recipe.findMany()
    res.json(recipes)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.get('/recipes/:id', async (req, res) => {
  try {
    const recipe = await req.db!.recipe.findUnique({ where: { id: param(req, 'id') } })
    if (!recipe) { res.status(404).json({ error: 'Recipe not found' }); return }
    res.json(recipe)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.post('/recipes', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (!validateId(req.body.id, 'Recipe', res)) return
  if (!validateName(req.body.name, 'Recipe', res)) return
  try {
    const recipe = await req.db!.recipe.create({ data: req.body })
    res.json(recipe)
  } catch (err: any) {
    handlePrismaError(err, 'recipe', res)
  }
})

entitiesRouter.put('/recipes/:id', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (req.body.name !== undefined && !validateName(req.body.name, 'Recipe', res)) return
  try {
    const recipe = await req.db!.recipe.update({ where: { id: param(req, 'id') }, data: req.body })
    res.json(recipe)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

entitiesRouter.delete('/recipes/:id', rejectIfReadOnly, async (req, res) => {
  try {
    await req.db!.recipe.delete({ where: { id: param(req, 'id') } })
    res.json({ ok: true })
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// ─── World metadata (manifest fields stored as ProjectMeta rows) ──────
// Authoring surface for fields like `introScript` (#272). Stored as
// key-value pairs to keep the schema flexible — the export route
// reconstructs `manifest.json` from these rows on bundle export.

entitiesRouter.get('/world-meta', async (req, res) => {
  try {
    const rows = await req.db!.projectMeta.findMany()
    const meta: Record<string, string> = {}
    for (const row of rows) {
      if (row.key === 'readOnly') continue
      meta[row.key] = row.value
    }
    res.json(meta)
  } catch (err: any) {
    handlePrismaError(err, 'world-meta', res)
  }
})

entitiesRouter.put('/world-meta', rejectIfReadOnly, async (req, res) => {
  try {
    const incoming = req.body as Record<string, unknown>
    if (!incoming || typeof incoming !== 'object' || Array.isArray(incoming)) {
      res.status(400).json({ error: 'Body must be an object of key-value pairs' })
      return
    }
    for (const [key, value] of Object.entries(incoming)) {
      if (key === 'readOnly') continue
      await req.db!.projectMeta.upsert({
        where: { key },
        create: { key, value: String(value ?? '') },
        update: { value: String(value ?? '') },
      })
    }
    res.json({ ok: true })
  } catch (err: any) {
    handlePrismaError(err, 'world-meta', res)
  }
})

