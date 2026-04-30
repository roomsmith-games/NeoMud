import type { Request, Response, NextFunction } from 'express'
import { PrismaClient } from './generated/prisma/client.js'
import { PrismaBetterSqlite3 } from '@prisma/adapter-better-sqlite3'
import { execSync } from 'child_process'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { isValidProjectName } from './middleware/validateInput.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

const PROJECTS_DIR = process.env.MAKER_PROJECTS_DIR || path.join(__dirname, '..', 'projects')
const POOL_EVICTION_INTERVAL_MS = 60_000 // Check every 60s
const POOL_IDLE_TIMEOUT_MS = 5 * 60_000 // Evict after 5min idle

interface PooledClient {
  client: PrismaClient
  readOnly: boolean
  lastUsed: number
}

const pool = new Map<string, PooledClient>()
const migrationLocks = new Map<string, Promise<void>>()

/** Get the base projects directory. */
export function getProjectsDir(): string {
  return PROJECTS_DIR
}

/** Resolve the directory for a user's projects. */
function userDir(userId: string): string {
  return path.join(PROJECTS_DIR, userId)
}

/**
 * Names of admin-editable shared templates. When the project name matches
 * one of these, the resolver routes to `_shared/` instead of the user's
 * per-userId dir, so multiple admins editing the same shared project see
 * the same data. Visibility (whether a user is allowed to OPEN the project)
 * is enforced separately by listProjects in db.ts (gated on req.user.role
 * === 'ADMIN'); routing here is purely path-resolution.
 */
const SHARED_PROJECT_NAMES = new Set(['_default_world'])

function isShared(projectName: string): boolean {
  return SHARED_PROJECT_NAMES.has(projectName)
}

/** Resolve the DB path for a project. Shared templates route to _shared/. */
function dbPath(userId: string, projectName: string): string {
  if (isShared(projectName)) {
    return path.join(PROJECTS_DIR, '_shared', `${projectName}.db`)
  }
  return path.join(userDir(userId), `${projectName}.db`)
}

/** Resolve the assets root for a project. Shared templates route to _shared/. */
export function assetsRoot(userId: string, projectName: string): string {
  if (isShared(projectName)) {
    return path.join(PROJECTS_DIR, '_shared', `${projectName}_assets`, 'assets')
  }
  return path.join(userDir(userId), `${projectName}_assets`, 'assets')
}

/** Run schema migration on a SQLite database file. */
async function migrateSchema(dbFile: string): Promise<void> {
  const schemaPath = path.join(__dirname, '..', 'prisma', 'schema.prisma')
  execSync(`npx prisma db push --schema="${schemaPath}" --accept-data-loss`, {
    env: { ...process.env, DATABASE_URL: `file:${dbFile}` },
    cwd: path.join(__dirname, '..'),
    stdio: 'pipe',
  })
}

/**
 * Get or create a PrismaClient for a specific user's project.
 * Lazily opens the database, runs schema migration on first access.
 */
export async function getProjectClient(userId: string, projectName: string): Promise<PooledClient> {
  const key = `${userId}/${projectName}`

  // Check pool
  const existing = pool.get(key)
  if (existing) {
    existing.lastUsed = Date.now()
    return existing
  }

  // Serialize first-open per project to prevent concurrent migrations
  const lockKey = key
  const existingLock = migrationLocks.get(lockKey)
  if (existingLock) {
    await existingLock
    const afterLock = pool.get(key)
    if (afterLock) {
      afterLock.lastUsed = Date.now()
      return afterLock
    }
  }

  const dbFile = dbPath(userId, projectName)
  if (!fs.existsSync(dbFile)) {
    throw new Error(`Project not found: ${projectName}`)
  }

  // Run migration with lock
  const migrationPromise = migrateSchema(dbFile)
  migrationLocks.set(lockKey, migrationPromise)
  try {
    await migrationPromise
  } finally {
    migrationLocks.delete(lockKey)
  }

  // Create client
  const adapter = new PrismaBetterSqlite3({ url: `file:${dbFile}` })
  const client = new PrismaClient({ adapter })

  // Check read-only status
  let readOnly = false
  try {
    const meta = await client.projectMeta.findUnique({ where: { key: 'readOnly' } })
    readOnly = meta?.value === 'true'
  } catch {
    // Table might not exist yet
  }

  // Auto-seed defaults if needed
  try {
    const sfxCount = await client.defaultSfx.count()
    if (sfxCount === 0) {
      const { seedDefaultSfx } = await import('./defaultSfxDefaults.js')
      await seedDefaultSfx(client)
    }
  } catch {
    // Seeding is best-effort
  }

  const pooled: PooledClient = { client, readOnly, lastUsed: Date.now() }
  pool.set(key, pooled)
  return pooled
}

/**
 * Evict a specific project from the pool (e.g., before deletion).
 */
export async function evictProject(userId: string, projectName: string): Promise<void> {
  const key = `${userId}/${projectName}`
  const entry = pool.get(key)
  if (entry) {
    await entry.client.$disconnect()
    pool.delete(key)
  }
}

/**
 * Close all pooled connections. Call on shutdown.
 */
export async function closeAllConnections(): Promise<void> {
  for (const [key, entry] of pool) {
    await entry.client.$disconnect()
    pool.delete(key)
  }
}

// Pool eviction timer
const evictionTimer = setInterval(async () => {
  const now = Date.now()
  for (const [key, entry] of pool) {
    if (now - entry.lastUsed > POOL_IDLE_TIMEOUT_MS) {
      await entry.client.$disconnect()
      pool.delete(key)
    }
  }
}, POOL_EVICTION_INTERVAL_MS)

// Allow Node to exit without waiting for the timer
if (evictionTimer.unref) evictionTimer.unref()

/**
 * Express middleware that resolves the project context for a request.
 * Expects route to have `:name` param and `req.user` to be set (by auth middleware).
 * Sets req.db, req.projectName, req.projectDir, req.readOnly.
 */
export function projectMiddleware(req: Request, res: Response, next: NextFunction): void {
  const projectName = (req.params as Record<string, string>).name
  const userId = req.user?.userId

  if (!userId) {
    res.status(401).json({ error: 'Authentication required' })
    return
  }

  if (!projectName || !isValidProjectName(projectName)) {
    res.status(400).json({ error: 'Invalid project name' })
    return
  }

  getProjectClient(userId, projectName)
    .then((pooled) => {
      req.db = pooled.client
      req.projectName = projectName
      req.projectDir = assetsRoot(userId, projectName)
      req.readOnly = pooled.readOnly
      next()
    })
    .catch((err: Error) => {
      if (err.message.includes('not found')) {
        res.status(404).json({ error: 'Project not found' })
      } else {
        console.error('Project context error:', err.message)
        res.status(500).json({ error: 'Failed to open project' })
      }
    })
}

/** Get pool size (for health/monitoring). */
export function getPoolSize(): number {
  return pool.size
}
