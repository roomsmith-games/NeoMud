import { Router, Request, Response } from 'express'
import { Prisma } from '../generated/prisma/client.js'
import { rejectIfReadOnly } from '../middleware/readOnly.js'

const VALID_ID_RE = /^[a-zA-Z0-9_:\-]+$/

function validateId(id: string | undefined, entityLabel: string, res: Response): boolean {
  if (!id || !id.trim()) {
    res.status(400).json({ error: `${entityLabel} ID is required` })
    return false
  }
  if (!VALID_ID_RE.test(id)) {
    res.status(400).json({ error: `${entityLabel} ID may only contain letters, numbers, underscores, hyphens, and colons` })
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
      body[key] = body[key].trim()
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
    res.status(400).json({ error: 'Invalid data — check required fields and types' })
    return
  }
  console.error(`[${entityLabel}] Unhandled error:`, err)
  res.status(500).json({ error: 'Internal server error' })
}

/** Extract a route parameter as a string (Express 5 types return string | string[]). */
function param(req: Request, name: string): string {
  const val = req.params[name]
  return Array.isArray(val) ? val[0] : val ?? ''
}

export const zonesRouter = Router()

const DIRECTION_OPPOSITES: Record<string, string> = {
  NORTH: 'SOUTH',
  SOUTH: 'NORTH',
  EAST: 'WEST',
  WEST: 'EAST',
  NORTHEAST: 'SOUTHWEST',
  SOUTHWEST: 'NORTHEAST',
  NORTHWEST: 'SOUTHEAST',
  SOUTHEAST: 'NORTHWEST',
  UP: 'DOWN',
  DOWN: 'UP',
}

// ─── Zone routes ───────────────────────────────────────────────

// GET /zones — list all zones with room count
zonesRouter.get('/zones', async (req, res) => {
  try {
    const zones = await req.db!.zone.findMany({
      include: { _count: { select: { rooms: true } } },
    })
    res.json(zones)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// POST /zones — create zone
zonesRouter.post('/zones', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (!validateId(req.body.id, 'Zone', res)) return
  if (!validateName(req.body.name, 'Zone', res)) return
  try {
    const {
      id, name, description,
      safe, bgm, bgmPrompt, bgmDuration,
      spawnRoom, spawnMaxEntities, spawnMaxPerRoom, spawnRateTicks,
    } = req.body
    const zone = await req.db!.zone.create({
      data: {
        id, name, description,
        safe: safe ?? true,
        bgm: bgm ?? '',
        bgmPrompt: bgmPrompt ?? '',
        bgmDuration: bgmDuration ?? 120,
        spawnRoom: spawnRoom ?? null,
        spawnMaxEntities: spawnMaxEntities ?? 0,
        spawnMaxPerRoom: spawnMaxPerRoom ?? 0,
        spawnRateTicks: spawnRateTicks ?? 0,
      },
    })
    res.json(zone)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// GET /zones/:id — get zone with rooms and exits
zonesRouter.get('/zones/:id', async (req, res) => {
  try {
    const zone = await req.db!.zone.findUnique({
      where: { id: param(req, 'id') },
      include: { rooms: { include: { exits: true } } },
    })
    if (!zone) {
      res.status(404).json({ error: 'Zone not found' })
      return
    }
    res.json(zone)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// PUT /zones/:id — update zone
zonesRouter.put('/zones/:id', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (req.body.name !== undefined && !validateName(req.body.name, 'Zone', res)) return
  try {
    const zone = await req.db!.zone.update({
      where: { id: param(req, 'id') },
      data: req.body,
    })
    res.json(zone)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// DELETE /zones/:id — delete zone (cascades rooms/exits)
zonesRouter.delete('/zones/:id', rejectIfReadOnly, async (req, res) => {
  try {
    await req.db!.zone.delete({ where: { id: param(req, 'id') } })
    res.json({ ok: true })
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// ─── Room routes ───────────────────────────────────────────────

// GET /zones/:zoneId/rooms — list rooms in zone
zonesRouter.get('/zones/:zoneId/rooms', async (req, res) => {
  try {
    const rooms = await req.db!.room.findMany({
      where: { zoneId: param(req, 'zoneId') },
      include: { exits: true },
    })
    res.json(rooms)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// POST /zones/:zoneId/rooms — create room
zonesRouter.post('/zones/:zoneId/rooms', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (!validateId(req.body.id, 'Room', res)) return
  if (!validateName(req.body.name, 'Room', res)) return
  try {
    const zoneId = param(req, 'zoneId')
    const {
      id, name, description, x, y, z,
      backgroundImage, effects, bgm, bgmPrompt, bgmDuration, departSound,
    } = req.body
    const fullId = id.startsWith(`${zoneId}:`) ? id : `${zoneId}:${id}`

    // Validate no room exists at the same coordinates globally (shared coordinate space)
    if (x !== undefined && y !== undefined) {
      const overlapping = await req.db!.room.findFirst({
        where: { x, y, z: z ?? 0 },
      })
      if (overlapping) {
        res.status(409).json({ error: `A room already exists at coordinates (${x}, ${y}, ${z ?? 0})` })
        return
      }
    }

    const room = await req.db!.room.create({
      data: {
        id: fullId,
        zoneId,
        name,
        description,
        x,
        y,
        z: z ?? 0,
        backgroundImage: backgroundImage ?? '',
        effects: effects ?? '[]',
        bgm: bgm ?? '',
        bgmPrompt: bgmPrompt ?? '',
        bgmDuration: bgmDuration ?? 0,
        departSound: departSound ?? '',
      },
    })
    res.json(room)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// GET /zones/:zoneId/rooms/:id — get room with exits (accepts local or fully-qualified ID)
zonesRouter.get('/zones/:zoneId/rooms/:id', async (req, res) => {
  try {
    const zoneId = param(req, 'zoneId')
    const rawId = param(req, 'id')
    const fullId = rawId.startsWith(`${zoneId}:`) ? rawId : `${zoneId}:${rawId}`
    const room = await req.db!.room.findUnique({
      where: { id: fullId },
      include: { exits: true },
    })
    if (!room) {
      res.status(404).json({ error: 'Room not found' })
      return
    }
    res.json(room)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// PUT /zones/:zoneId/rooms/:id — update room (accepts local or fully-qualified ID)
zonesRouter.put('/zones/:zoneId/rooms/:id', rejectIfReadOnly, async (req, res) => {
  sanitizeTextFields(req.body)
  if (req.body.name !== undefined && !validateName(req.body.name, 'Room', res)) return
  try {
    const zoneId = param(req, 'zoneId')
    const rawId = param(req, 'id')
    const fullId = rawId.startsWith(`${zoneId}:`) ? rawId : `${zoneId}:${rawId}`

    // Validate no other room exists at the same coordinates globally (shared coordinate space)
    if (req.body.x !== undefined && req.body.y !== undefined) {
      const zForCheck = req.body.z ?? (await req.db!.room.findUnique({
        where: { id: fullId }, select: { z: true }
      }))?.z ?? 0
      const overlapping = await req.db!.room.findFirst({
        where: { x: req.body.x, y: req.body.y, z: zForCheck, id: { not: fullId } },
      })
      if (overlapping) {
        res.status(409).json({ error: `A room already exists at coordinates (${req.body.x}, ${req.body.y}, ${zForCheck})` })
        return
      }
    }

    const room = await req.db!.room.update({
      where: { id: fullId },
      data: req.body,
    })
    res.json(room)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// PUT /zones/:zoneId/rooms/:id/rename — rename room (change slug and/or zone)
zonesRouter.put('/zones/:zoneId/rooms/:id/rename', rejectIfReadOnly, async (req, res) => {
  try {
    const zoneId = param(req, 'zoneId')
    const oldSlug = param(req, 'id')
    const { newId: newSlug, targetZoneId } = req.body
    const oldFullId = `${zoneId}:${oldSlug}`
    const effectiveZoneId = targetZoneId || zoneId
    const newFullId = `${effectiveZoneId}:${newSlug}`

    // Validate slug format
    if (!newSlug || typeof newSlug !== 'string') {
      res.status(400).json({ error: 'newId is required' })
      return
    }
    if (!/^[a-zA-Z0-9_]+$/.test(newSlug)) {
      res.status(400).json({ error: 'ID must contain only letters, numbers, and underscores' })
      return
    }
    if (newFullId === oldFullId) {
      res.status(400).json({ error: 'New ID is the same as the current ID' })
      return
    }

    // If moving to a different zone, validate that zone exists
    if (targetZoneId && targetZoneId !== zoneId) {
      const targetZone = await req.db!.zone.findUnique({ where: { id: targetZoneId } })
      if (!targetZone) {
        res.status(404).json({ error: `Target zone "${targetZoneId}" not found` })
        return
      }
    }

    // Check uniqueness
    const existing = await req.db!.room.findUnique({ where: { id: newFullId } })
    if (existing) {
      res.status(409).json({ error: `Room "${newFullId}" already exists` })
      return
    }

    // Find old room
    const oldRoom = await req.db!.room.findUnique({ where: { id: oldFullId }, include: { exits: true } })
    if (!oldRoom) {
      res.status(404).json({ error: 'Room not found' })
      return
    }

    // Transaction: create new → update exit FKs → delete old
    const result = await req.db!.$transaction(async (tx) => {
      // 1. Create new room with all fields from old room
      const { exits: _, ...roomData } = oldRoom as any
      const { id: __, zoneId: ___, ...fields } = roomData
      const newRoom = await tx.room.create({
        data: { id: newFullId, zoneId: effectiveZoneId, ...fields },
      })

      // 2. Update exits where fromRoomId = oldFullId
      await tx.exit.updateMany({
        where: { fromRoomId: oldFullId },
        data: { fromRoomId: newFullId },
      })

      // 3. Update exits where toRoomId = oldFullId
      await tx.exit.updateMany({
        where: { toRoomId: oldFullId },
        data: { toRoomId: newFullId },
      })

      // 4. Delete old room
      await tx.room.delete({ where: { id: oldFullId } })

      // Return new room with exits
      return tx.room.findUnique({ where: { id: newFullId }, include: { exits: true } })
    })

    res.json(result)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// DELETE /zones/:zoneId/rooms/:id — delete room (accepts local or fully-qualified ID)
zonesRouter.delete('/zones/:zoneId/rooms/:id', rejectIfReadOnly, async (req, res) => {
  try {
    const zoneId = param(req, 'zoneId')
    const rawId = param(req, 'id')
    // Accept both "room_slug" and "zone:room_slug" formats
    const fullId = rawId.startsWith(`${zoneId}:`) ? rawId : `${zoneId}:${rawId}`
    await req.db!.room.delete({ where: { id: fullId } })
    res.json({ ok: true })
  } catch (err: any) {
    handlePrismaError(err, 'Room', res)
  }
})

// ─── Exit routes ───────────────────────────────────────────────

// POST /rooms/:id/exits — create exit + reverse exit
zonesRouter.post('/rooms/:id/exits', rejectIfReadOnly, async (req, res) => {
  try {
    const fromRoomId = param(req, 'id')
    const { direction, toRoomId } = req.body

    if (!direction || !toRoomId) {
      res.status(400).json({ error: 'Both direction and toRoomId are required' })
      return
    }

    // Validate target room exists
    const targetRoom = await req.db!.room.findUnique({ where: { id: toRoomId } })
    if (!targetRoom) {
      res.status(400).json({ error: `Target room "${toRoomId}" does not exist` })
      return
    }

    // Check for duplicate exit direction
    const existing = await req.db!.exit.findUnique({
      where: { fromRoomId_direction: { fromRoomId, direction } },
    })
    if (existing) {
      res.status(409).json({ error: `This room already has a ${direction} exit` })
      return
    }

    const opposite = DIRECTION_OPPOSITES[direction]

    const exit = await req.db!.exit.create({
      data: { fromRoomId, direction, toRoomId },
    })

    // Create reverse exit if an opposite direction exists
    if (opposite) {
      await req.db!.exit.create({
        data: { fromRoomId: toRoomId, direction: opposite, toRoomId: fromRoomId },
      })
    }

    res.json(exit)
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})

// DELETE /rooms/:id/exits/:dir — delete exit + reverse exit (dir must be a direction name like NORTH, not a numeric ID)
zonesRouter.delete('/rooms/:id/exits/:dir', rejectIfReadOnly, async (req, res) => {
  try {
    const fromRoomId = param(req, 'id')
    const direction = param(req, 'dir')

    // Validate direction is a known compass direction
    const ALL_DIRECTIONS = Object.keys(DIRECTION_OPPOSITES)
    if (!ALL_DIRECTIONS.includes(direction)) {
      res.status(400).json({ error: `Invalid direction "${direction}". Must be one of: ${ALL_DIRECTIONS.join(', ')}` })
      return
    }

    const opposite = DIRECTION_OPPOSITES[direction]

    // Find the forward exit so we know the target room for reverse deletion
    const forwardExit = await req.db!.exit.findUnique({
      where: { fromRoomId_direction: { fromRoomId, direction } },
    })

    if (!forwardExit) {
      res.status(404).json({ error: `No ${direction} exit found from room "${fromRoomId}"` })
      return
    }

    // Delete forward exit
    await req.db!.exit.delete({
      where: { fromRoomId_direction: { fromRoomId, direction } },
    })

    // Delete reverse exit if it exists
    if (opposite) {
      await req.db!.exit.deleteMany({
        where: {
          fromRoomId: forwardExit.toRoomId,
          direction: opposite,
          toRoomId: fromRoomId,
        },
      })
    }

    res.json({ ok: true })
  } catch (err: any) {
    handlePrismaError(err, 'entity', res)
  }
})
