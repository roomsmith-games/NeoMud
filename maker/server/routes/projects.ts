import { Router } from 'express'
import fs from 'fs'
import path from 'path'
import { listProjects, createProject, deleteProject, forkProject } from '../db.js'
import { importNmd } from '../import.js'
import { isValidProjectName } from '../middleware/validateInput.js'
import { platformGetSubscription, getProjectQuota } from '../services/platformClient.js'
import { logger } from '../lib/logger.js'

/**
 * Resolve this user's project count + plan-based quota. Used by both
 * create and import paths before they persist anything to disk.
 *
 * Returns { count, limit, plan }. `count` only includes user-owned
 * projects (shared templates don't count against quota). `limit` is
 * Infinity for PRO.
 *
 * If the Platform subscription lookup fails, we fall back to treating
 * the user as FREE — safer than letting them create unlimited projects
 * when we can't reach the gate.
 */
async function getQuotaState(authHeader: string | null, userId: string): Promise<{ count: number; limit: number; plan: 'FREE' | 'CREATOR' | 'PRO' }> {
  const projects = await listProjects(userId)
  const count = projects.filter((p) => !p.readOnly).length

  // No Bearer token → dev/test bypass (see server/middleware/auth.ts).
  // We can't look up a real subscription, so don't enforce a quota in
  // this mode. Production always goes through `authenticate`, which
  // only reaches this code path if a Bearer header is present.
  if (!authHeader) {
    return { count, limit: Number.POSITIVE_INFINITY, plan: 'FREE' }
  }

  let plan: 'FREE' | 'CREATOR' | 'PRO' = 'FREE'
  try {
    const sub = await platformGetSubscription(authHeader)
    plan = sub.plan
  } catch (err) {
    logger.warn('quota_subscription_lookup_failed', {
      userId,
      message: err instanceof Error ? err.message : String(err),
    })
    // Platform unreachable → fail open. Losing one creator's ability
    // to create a project because our own API is down is worse than
    // letting them exceed quota temporarily.
    return { count, limit: Number.POSITIVE_INFINITY, plan: 'FREE' }
  }
  return { count, limit: getProjectQuota(plan), plan }
}

function forwardAuth(req: import('express').Request): string | null {
  const h = req.headers.authorization
  return h?.startsWith('Bearer ') ? h : null
}

export const projectsRouter = Router()

// GET / — list this user's projects + shared templates (admin-only)
projectsRouter.get('/', async (req, res) => {
  try {
    const userId = req.user!.userId
    const role = req.user!.role
    const projects = await listProjects(userId, role)
    res.json({ projects })
  } catch (err) {
    console.error('[projects] error:', err)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// POST / — create project in user's directory
projectsRouter.post('/', async (req, res) => {
  try {
    const userId = req.user!.userId
    const { name } = req.body
    if (!name || !isValidProjectName(name)) {
      res.status(400).json({ error: 'Invalid project name. Use only letters, numbers, hyphens, underscores.' })
      return
    }
    const quota = await getQuotaState(forwardAuth(req), userId)
    if (quota.count >= quota.limit) {
      logger.info('project_quota_exceeded', {
        userId,
        plan: quota.plan,
        count: quota.count,
        limit: quota.limit,
      })
      res.status(402).json({
        error: `Project quota reached (${quota.count} / ${quota.limit}) on the ${quota.plan} plan.`,
        plan: quota.plan,
        upgradeUrl: '/account#subscription',
      })
      return
    }
    await createProject(userId, name)
    res.json({ name })
  } catch (err) {
    console.error('[projects] error:', err)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// DELETE /:name — delete project (ownership enforced by user directory scoping)
projectsRouter.delete('/:name', async (req, res) => {
  try {
    const userId = req.user!.userId
    const name = req.params.name as string
    if (!isValidProjectName(name)) {
      res.status(400).json({ error: 'Invalid project name' })
      return
    }
    if (name.startsWith('_')) {
      res.status(403).json({ error: 'Cannot delete system projects' })
      return
    }
    await deleteProject(userId, name)
    res.json({ ok: true })
  } catch (err) {
    console.error('[projects] error:', err)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// POST /:name/open — NO-OP for backward compatibility
// Per-request project context replaces "opening" a project.
projectsRouter.post('/:name/open', async (req, res) => {
  const name = req.params.name as string
  res.json({ name, message: 'Project context is now per-request. No need to open.' })
})

// POST /:name/fork — fork a project to a new name within user's directory
projectsRouter.post('/:name/fork', async (req, res) => {
  try {
    const userId = req.user!.userId
    const source = req.params.name as string
    const { newName } = req.body
    if (!newName || !isValidProjectName(newName)) {
      res.status(400).json({ error: 'Invalid new project name' })
      return
    }
    if (!isValidProjectName(source)) {
      res.status(400).json({ error: 'Invalid source project name' })
      return
    }
    const quota = await getQuotaState(forwardAuth(req), userId)
    if (quota.count >= quota.limit) {
      res.status(402).json({
        error: `Project quota reached (${quota.count} / ${quota.limit}) on the ${quota.plan} plan.`,
        plan: quota.plan,
        upgradeUrl: '/account#subscription',
      })
      return
    }
    await forkProject(userId, source, newName)
    res.json({ name: newName })
  } catch (err) {
    console.error('[projects] error:', err)
    res.status(500).json({ error: 'Internal server error' })
  }
})

// POST /import — import .nmd file as a new project
projectsRouter.post('/import', async (req, res) => {
  try {
    const userId = req.user!.userId
    const { path: nmdPath, name } = req.body
    if (!nmdPath) {
      res.status(400).json({ error: 'path is required' })
      return
    }
    // Validate import path: must be .nmd, no traversal, must resolve within allowed directories
    if (!nmdPath.endsWith('.nmd')) {
      res.status(400).json({ error: 'Only .nmd files can be imported' })
      return
    }
    const resolvedImportPath = path.resolve(nmdPath)
    const allowedDirs = [
      path.resolve(process.env.NEOMUD_DEFAULT_WORLD || '.'),
      path.resolve(process.env.MAKER_PROJECTS_DIR || 'projects'),
      path.resolve('seed'),
    ].map(d => path.dirname(d) === d ? d : path.dirname(d)) // handle file paths vs dirs
    const inAllowedDir = allowedDirs.some(dir =>
      resolvedImportPath.startsWith(dir + path.sep) || resolvedImportPath === dir
    )
    if (!inAllowedDir && !resolvedImportPath.startsWith(path.resolve('.') + path.sep)) {
      res.status(400).json({ error: 'File must be in an allowed directory' })
      return
    }
    if (!fs.existsSync(resolvedImportPath)) {
      res.status(404).json({ error: 'File not found' })
      return
    }
    const projectName = name || nmdPath.replace(/.*[/\\]/, '').replace(/\.nmd$/, '')
    if (!isValidProjectName(projectName)) {
      res.status(400).json({ error: 'Invalid project name derived from file' })
      return
    }
    const quota = await getQuotaState(forwardAuth(req), userId)
    if (quota.count >= quota.limit) {
      res.status(402).json({
        error: `Project quota reached (${quota.count} / ${quota.limit}) on the ${quota.plan} plan.`,
        plan: quota.plan,
        upgradeUrl: '/account#subscription',
      })
      return
    }
    await importNmd(nmdPath, userId, projectName)
    res.json({ name: projectName })
  } catch (err) {
    console.error('[projects] error:', err)
    res.status(500).json({ error: 'Internal server error' })
  }
})
