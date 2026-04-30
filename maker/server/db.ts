import { PrismaClient } from './generated/prisma/client.js'
import { PrismaBetterSqlite3 } from '@prisma/adapter-better-sqlite3'
import { execSync } from 'child_process'
import { randomUUID } from 'crypto'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { getProjectsDir, evictProject } from './projectContext.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export interface ProjectInfo {
  name: string
  readOnly: boolean
}

/** Resolve the user's project directory, creating it if needed. */
function userDir(userId: string): string {
  const dir = path.join(getProjectsDir(), userId)
  if (!fs.existsSync(dir)) {
    fs.mkdirSync(dir, { recursive: true })
  }
  return dir
}

/** Resolve the DB path for a user's project. */
function userDbPath(userId: string, projectName: string): string {
  return path.join(userDir(userId), `${projectName}.db`)
}

/** Resolve the DB URL for Prisma. */
function dbUrl(userId: string, projectName: string): string {
  return `file:${userDbPath(userId, projectName)}`
}

/**
 * List projects visible to a specific user.
 * Includes the user's own projects plus, for ADMIN users, shared templates
 * from `_shared/` (e.g. `_default_world`) so admins can edit + republish
 * the demo world directly through the Maker UI.
 *
 * Non-admin users (CREATOR, MODERATOR) see only their own projects.
 */
export async function listProjects(userId: string, role?: string): Promise<ProjectInfo[]> {
  const results: ProjectInfo[] = []

  // User's own projects
  const dir = path.join(getProjectsDir(), userId)
  if (fs.existsSync(dir)) {
    const names = fs
      .readdirSync(dir)
      .filter((f) => f.endsWith('.db'))
      .map((f) => f.replace('.db', ''))

    for (const name of names) {
      const readOnly = await isProjectReadOnly(userId, name)
      results.push({ name, readOnly })
    }
  }

  // Shared templates from `_shared/` (e.g., `_default_world`). These are
  // routed to a single canonical DB so all admins editing them see the
  // same content (see projectContext.ts SHARED_PROJECT_NAMES). Surfaced
  // ONLY to ADMIN role to keep the standard creator listing clean and
  // prevent rank-and-file users from accidentally landing on the demo
  // world's editable source. Read/write status is whatever the DB row
  // says — the readOnly flag still applies if the admin wants to lock it.
  if (role === 'ADMIN') {
    const sharedDir = path.join(getProjectsDir(), '_shared')
    if (fs.existsSync(sharedDir)) {
      const names = fs
        .readdirSync(sharedDir)
        .filter((f) => f.endsWith('.db'))
        .map((f) => f.replace('.db', ''))
      for (const name of names) {
        // _shared DBs aren't keyed by user, so check readOnly directly on the
        // shared file rather than userDbPath().
        const sharedDbFile = path.join(sharedDir, `${name}.db`)
        const readOnly = await isSharedProjectReadOnly(sharedDbFile)
        results.push({ name, readOnly })
      }
    }
  }

  return results
}

async function isSharedProjectReadOnly(dbFile: string): Promise<boolean> {
  if (!fs.existsSync(dbFile)) return false
  const adapter = new PrismaBetterSqlite3({ url: `file:${dbFile}` })
  const tmpClient = new PrismaClient({ adapter })
  try {
    const meta = await tmpClient.projectMeta.findUnique({ where: { key: 'readOnly' } })
    return meta?.value === 'true'
  } catch {
    return false
  } finally {
    await tmpClient.$disconnect()
  }
}

async function isProjectReadOnly(userId: string, name: string): Promise<boolean> {
  const dbFile = userDbPath(userId, name)
  if (!fs.existsSync(dbFile)) return false

  const adapter = new PrismaBetterSqlite3({ url: `file:${dbFile}` })
  const tmpClient = new PrismaClient({ adapter })
  try {
    const meta = await tmpClient.projectMeta.findUnique({ where: { key: 'readOnly' } })
    return meta?.value === 'true'
  } catch {
    return false
  } finally {
    await tmpClient.$disconnect()
  }
}

/**
 * Create a new project for a user.
 * Returns a temporary PrismaClient for seeding (caller should close it or let the pool manage it).
 */
export async function createProject(userId: string, name: string, readOnly = false): Promise<PrismaClient> {
  const dbFile = userDbPath(userId, name)
  if (fs.existsSync(dbFile)) {
    throw new Error(`Project "${name}" already exists`)
  }

  // Ensure user directory exists
  userDir(userId)

  // Push schema to the new database
  const schemaPath = path.join(__dirname, '..', 'prisma', 'schema.prisma')
  execSync(`npx prisma db push --schema="${schemaPath}"`, {
    env: { ...process.env, DATABASE_URL: `file:${dbFile}` },
    cwd: path.join(__dirname, '..'),
    stdio: 'pipe',
  })

  // Open a temporary client for seeding
  const adapter = new PrismaBetterSqlite3({ url: `file:${dbFile}` })
  const client = new PrismaClient({ adapter })

  if (readOnly) {
    await client.projectMeta.create({ data: { key: 'readOnly', value: 'true' } })
  }

  // Generate unique world ID
  await client.projectMeta.create({ data: { key: 'worldId', value: randomUUID() } })

  // Seed default PC sprites
  const { seedPcSprites, generatePlaceholderSprites } = await import('./pcSpriteDefaults.js')
  await seedPcSprites(client)
  generatePlaceholderSprites(path.join(userDir(userId), `${name}_assets`))

  // Seed default SFX entries
  const { seedDefaultSfx } = await import('./defaultSfxDefaults.js')
  await seedDefaultSfx(client)

  await client.$disconnect()
  return client
}

/**
 * Delete a project and its assets.
 */
export async function deleteProject(userId: string, name: string): Promise<void> {
  // Evict from connection pool
  await evictProject(userId, name)

  const dbFile = userDbPath(userId, name)
  if (fs.existsSync(dbFile)) {
    fs.unlinkSync(dbFile)
  }
  const assetsPath = path.join(userDir(userId), `${name}_assets`)
  if (fs.existsSync(assetsPath)) {
    fs.rmSync(assetsPath, { recursive: true, force: true })
  }
}

/**
 * Fork a project within the user's directory.
 * Can fork from _shared templates or the user's own projects.
 */
export async function forkProject(userId: string, source: string, newName: string): Promise<void> {
  // Try user's own directory first, then _shared
  let srcPath = userDbPath(userId, source)
  let srcAssetsBase = path.join(userDir(userId), `${source}_assets`)

  if (!fs.existsSync(srcPath)) {
    // Try _shared directory
    const sharedPath = path.join(getProjectsDir(), '_shared', `${source}.db`)
    if (fs.existsSync(sharedPath)) {
      srcPath = sharedPath
      srcAssetsBase = path.join(getProjectsDir(), '_shared', `${source}_assets`)
    } else {
      throw new Error(`Source project "${source}" not found`)
    }
  }

  const destPath = userDbPath(userId, newName)
  if (fs.existsSync(destPath)) {
    throw new Error(`Project "${newName}" already exists`)
  }

  // Ensure user directory exists
  userDir(userId)

  // Copy the DB file
  fs.copyFileSync(srcPath, destPath)

  // Copy assets directory if it exists
  const destAssets = path.join(userDir(userId), `${newName}_assets`)
  if (fs.existsSync(srcAssetsBase)) {
    fs.cpSync(srcAssetsBase, destAssets, { recursive: true })
  }

  // Open the copy: remove read-only flag and generate new worldId
  const forkAdapter = new PrismaBetterSqlite3({ url: `file:${destPath}` })
  const tmpClient = new PrismaClient({ adapter: forkAdapter })
  try {
    await tmpClient.projectMeta.deleteMany({ where: { key: 'readOnly' } })
    await tmpClient.projectMeta.upsert({
      where: { key: 'worldId' },
      create: { key: 'worldId', value: randomUUID() },
      update: { value: randomUUID() },
    })
  } finally {
    await tmpClient.$disconnect()
  }
}

// ─── Legacy exports for backward compatibility during migration ───
// These will be removed once all routes use req.db from projectContext

/** @deprecated Use getProjectsDir() from projectContext.ts instead */
export { getProjectsDir }
