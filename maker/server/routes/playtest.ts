import { Router } from 'express'
import { buildNmdBundle } from './export.js'
import {
  getActivePlaytest,
  setActivePlaytest,
  clearActivePlaytest,
  type ActivePlaytest,
} from '../playtestState.js'

export const playtestRouter = Router({ mergeParams: true })

const PLATFORM_API_URL = process.env.PLATFORM_API_URL || 'http://localhost:3000'

/**
 * Turn a free-form project name into the trailing segment of a draft
 * slug. The platform expects `draft-{userId}-{segment}` where segment
 * matches `[a-z0-9]+(?:-[a-z0-9]+)*` (see Phase 4A draftUpsertSchema).
 */
export function slugifyProjectName(name: string): string {
  return name
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
}

/** Forward the user's Bearer to the Platform so owner checks run there. */
function forwardAuth(req: import('express').Request): string | null {
  const authHeader = req.headers.authorization
  return authHeader?.startsWith('Bearer ') ? authHeader : null
}

/** POST bundle + slug + projectName to Platform drafts/upsert. Returns worldId. */
async function platformUpsertDraft(
  authHeader: string,
  bundle: Buffer,
  slug: string,
  projectName: string
): Promise<{ worldId: string; slug: string }> {
  const form = new FormData()
  form.set('bundle', new Blob([bundle], { type: 'application/zip' }), 'draft.nmd')
  form.set('slug', slug)
  form.set('projectName', projectName)

  const res = await fetch(`${PLATFORM_API_URL}/api/v1/worlds/drafts/upsert`, {
    method: 'POST',
    headers: { Authorization: authHeader },
    body: form,
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`drafts/upsert failed (${res.status}): ${text}`)
  }
  return (await res.json()) as { worldId: string; slug: string }
}

async function platformStart(authHeader: string, worldId: string): Promise<{ slug: string; playPath: string }> {
  const res = await fetch(`${PLATFORM_API_URL}/api/v1/worlds/${worldId}/start`, {
    method: 'POST',
    headers: { Authorization: authHeader, 'Content-Type': 'application/json' },
    body: JSON.stringify({}),
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`start failed (${res.status}): ${text}`)
  }
  return (await res.json()) as { slug: string; playPath: string }
}

async function platformRestart(authHeader: string, worldId: string): Promise<{ readyInMs: number }> {
  const res = await fetch(`${PLATFORM_API_URL}/api/v1/worlds/${worldId}/restart`, {
    method: 'POST',
    headers: { Authorization: authHeader },
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    const err = new Error(`restart failed (${res.status}): ${text}`) as Error & { status?: number }
    err.status = res.status
    throw err
  }
  return (await res.json()) as { readyInMs: number }
}

async function platformStop(authHeader: string, worldId: string): Promise<void> {
  const res = await fetch(`${PLATFORM_API_URL}/api/v1/worlds/${worldId}/stop`, {
    method: 'POST',
    headers: { Authorization: authHeader },
  })
  if (!res.ok && res.status !== 404) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`stop failed (${res.status}): ${text}`)
  }
}

// ─── GET /api/projects/:name/playtest ───────────────────
// Returns the current active playtest for this user (or null). The UI
// uses this on mount to decide whether to show the Reload button.
playtestRouter.get('/', (req, res) => {
  const userId = req.user!.userId
  const entry = getActivePlaytest(userId) ?? null
  res.json({ playtest: entry })
})

// ─── POST /api/projects/:name/playtest ──────────────────
// Builds a bundle from the active project, upserts it as a DRAFT on the
// platform, and starts the container. If another project for this user
// already has an active playtest, it's implicitly stopped first (one
// slot per user — see open Q4).
playtestRouter.post('/', async (req, res, next) => {
  try {
    const userId = req.user!.userId
    const projectName = req.projectName!
    const prisma = req.db!
    const assetsDir = req.projectDir!

    const authHeader = forwardAuth(req)
    if (!authHeader) {
      res.status(401).json({ error: 'Authentication required' })
      return
    }

    // Implicit slot handoff: if there's an active playtest for a
    // different project, stop it before spinning up the new one.
    const prior = getActivePlaytest(userId)
    if (prior && prior.projectName !== projectName) {
      try { await platformStop(authHeader, prior.worldId) } catch (err) {
        console.warn(`[playtest] pre-stop of prior playtest failed:`, err instanceof Error ? err.message : err)
      }
      clearActivePlaytest(userId)
    }

    const bundle = await buildNmdBundle(prisma, assetsDir)
    const slug = `draft-${userId}-${slugifyProjectName(projectName)}`

    const upsert = await platformUpsertDraft(authHeader, bundle, slug, projectName)
    const start = await platformStart(authHeader, upsert.worldId)

    const entry: ActivePlaytest = {
      worldId: upsert.worldId,
      slug: upsert.slug,
      projectName,
      startedAt: Date.now(),
    }
    setActivePlaytest(userId, entry)

    res.status(200).json({
      worldId: entry.worldId,
      slug: entry.slug,
      playPath: start.playPath,
    })
  } catch (err) {
    next(err)
  }
})

// ─── POST /api/projects/:name/playtest/reload ───────────
// Rebuilds the bundle, upserts, and restarts the existing draft
// container. Only valid when the active playtest matches the current
// project — switching projects should go through Playtest, not Reload.
playtestRouter.post('/reload', async (req, res, next) => {
  try {
    const userId = req.user!.userId
    const projectName = req.projectName!
    const prisma = req.db!
    const assetsDir = req.projectDir!

    const authHeader = forwardAuth(req)
    if (!authHeader) {
      res.status(401).json({ error: 'Authentication required' })
      return
    }

    const entry = getActivePlaytest(userId)
    if (!entry) {
      res.status(404).json({ error: 'No active playtest for this user' })
      return
    }
    if (entry.projectName !== projectName) {
      res.status(409).json({ error: 'Active playtest is for a different project; click Playtest to switch' })
      return
    }

    const bundle = await buildNmdBundle(prisma, assetsDir)
    const slug = `draft-${userId}-${slugifyProjectName(projectName)}`

    await platformUpsertDraft(authHeader, bundle, slug, projectName)
    try {
      const result = await platformRestart(authHeader, entry.worldId)
      res.status(200).json({ worldId: entry.worldId, slug: entry.slug, readyInMs: result.readyInMs })
    } catch (err) {
      const status = (err as { status?: number }).status
      if (status === 504) {
        res.status(504).json({ error: 'Platform restart did not complete within timeout' })
        return
      }
      throw err
    }
  } catch (err) {
    next(err)
  }
})

// ─── POST /api/projects/:name/playtest/stop ─────────────
// Idempotent — stopping when nothing is active returns { stopped: false }.
playtestRouter.post('/stop', async (req, res, next) => {
  try {
    const userId = req.user!.userId
    const authHeader = forwardAuth(req)
    if (!authHeader) {
      res.status(401).json({ error: 'Authentication required' })
      return
    }

    const entry = getActivePlaytest(userId)
    if (!entry) {
      res.status(200).json({ stopped: false })
      return
    }

    try {
      await platformStop(authHeader, entry.worldId)
    } catch (err) {
      console.warn(`[playtest] platformStop failed:`, err instanceof Error ? err.message : err)
    }
    clearActivePlaytest(userId)
    res.status(200).json({ stopped: true, worldId: entry.worldId })
  } catch (err) {
    next(err)
  }
})
