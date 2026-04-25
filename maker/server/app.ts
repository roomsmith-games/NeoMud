import express from 'express'
import helmet from 'helmet'
import cors from 'cors'
import rateLimit from 'express-rate-limit'
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { authenticate } from './middleware/auth.js'
import { errorHandler } from './middleware/errorHandler.js'
import { requestMetrics } from './middleware/requestMetrics.js'
import { projectMiddleware, getProjectsDir } from './projectContext.js'
import { projectsRouter } from './routes/projects.js'
import { zonesRouter } from './routes/zones.js'
import { entitiesRouter } from './routes/entities.js'
import { exportRouter } from './routes/export.js'
import { settingsRouter } from './routes/settings.js'
import { pcSpritesRouter } from './routes/pcSprites.js'
import { defaultSfxRouter } from './routes/defaultSfx.js'
import { generateRouter } from './routes/generate.js'
import { assetMgmtRouter } from './routes/assets.js'
import { playtestRouter } from './routes/playtest.js'
import { publishRouter } from './routes/publish.js'
import { signUrlsRouter } from './routes/signUrls.js'
import { deleteProject } from './db.js'
import { importNmd } from './import.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

export const app = express()

// ─── Security middleware ────────────────────────────────
app.use(helmet({
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'"],
      styleSrc: ["'self'", "'unsafe-inline'"],
      imgSrc: ["'self'", 'data:', 'blob:'],
    },
  },
  hsts: process.env.NODE_ENV === 'production'
    ? { maxAge: 31536000, includeSubDomains: true }
    : false,
}))

const allowedOrigins = process.env.ALLOWED_ORIGINS
  ? process.env.ALLOWED_ORIGINS.split(',').map((s) => s.trim())
  : ['http://localhost:5173', 'http://localhost:3000']
app.use(cors({ origin: allowedOrigins, credentials: true }))

// Global rate limiter — generous for interactive editor use
app.use(rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 1000,
  standardHeaders: true,
  legacyHeaders: false,
}))

// Stricter rate limiters for expensive/sensitive operations
const importLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 10,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many import requests, please try again later' },
})

const generateLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 60, // generous for iterative asset generation
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: 'Too many generation requests, please try again later' },
})

app.use(express.json({ limit: '1mb' }))

// ─── Request metrics (fire-and-forget, emits on response finish) ──
app.use(requestMetrics)

// ─── Auth on all API routes ─────────────────────────────
app.use('/api', authenticate)

// ─── Project list/create/delete (no project context needed) ─
// Apply import limiter before the projects router handles /import
app.use('/api/projects/import', importLimiter)
app.use('/api/projects', projectsRouter)
app.use('/api/sign-urls', signUrlsRouter)

// ─── Project-scoped routes (project context resolves DB per request) ─
const projectRouter = express.Router({ mergeParams: true })
projectRouter.use(projectMiddleware)
projectRouter.use(zonesRouter)
projectRouter.use(entitiesRouter)
projectRouter.use('/export', exportRouter)
projectRouter.use('/settings', settingsRouter)
projectRouter.use('/pc-sprites', pcSpritesRouter)
projectRouter.use('/default-sfx', defaultSfxRouter)
projectRouter.use('/generate', generateLimiter, generateRouter)
projectRouter.use('/asset-mgmt', assetMgmtRouter)
projectRouter.use('/playtest', playtestRouter)
projectRouter.use('/publish', publishRouter)

// Serve assets from the project's assets directory
projectRouter.use('/assets', (req, res, next) => {
  if (!req.projectDir) {
    res.status(404).json({ error: 'No project context' })
    return
  }
  express.static(req.projectDir)(req, res, next)
})

app.use('/api/projects/:name', projectRouter)

// ─── JSON error handler (must follow API routes) ─────────
// Without this, any route that calls next(err) falls through to Express's
// default HTML error page, which the SPA can't parse and surfaces as a
// generic "Something went wrong". See errorHandler.ts header for context.
app.use('/api', errorHandler)

// ─── Production: serve React static build ────────────────
if (process.env.NODE_ENV === 'production') {
  const distDir = path.join(__dirname, '..', 'dist')
  app.use(express.static(distDir))
  app.get('/{*path}', (_req, res) => {
    res.sendFile(path.join(distDir, 'index.html'))
  })
}

/**
 * Auto-import the default world as a shared read-only template.
 * Stored in projects/_shared/ so all users can see it.
 */
export async function autoImportDefaultWorld() {
  const nmdPath = process.env.NEOMUD_DEFAULT_WORLD
    || path.resolve(__dirname, '..', 'default_world.nmd')
  if (!fs.existsSync(nmdPath)) {
    console.log(`[startup] No default-world.nmd found at ${nmdPath}, skipping auto-import.`)
    return
  }

  const sharedDir = path.join(getProjectsDir(), '_shared')
  if (!fs.existsSync(sharedDir)) {
    fs.mkdirSync(sharedDir, { recursive: true })
  }

  const nmdMtime = fs.statSync(nmdPath).mtimeMs.toString()
  const dbPath = path.join(sharedDir, '_default_world.db')

  if (fs.existsSync(dbPath)) {
    const mtimePath = path.join(sharedDir, '_default_world.mtime')
    const storedMtime = fs.existsSync(mtimePath) ? fs.readFileSync(mtimePath, 'utf-8').trim() : null
    if (storedMtime === nmdMtime) {
      console.log('[startup] _default_world is up-to-date, skipping re-import.')
      return
    }
    console.log('[startup] default-world.nmd has changed, re-importing...')
    await deleteProject('_shared', '_default_world')
  }

  try {
    console.log(`[startup] Importing default world from ${nmdPath}...`)
    await importNmd(nmdPath, '_shared', '_default_world', true)
    fs.writeFileSync(path.join(sharedDir, '_default_world.mtime'), nmdMtime)
    console.log('[startup] Default world imported successfully as read-only shared template.')
  } catch (err) {
    console.error('[startup] Failed to import default world:', err)
  }
}
