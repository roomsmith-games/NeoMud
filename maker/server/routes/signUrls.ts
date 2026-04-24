import { Router } from 'express'
import { signAssetToken, canonicalizeAssetPath } from '../lib/assetSigning.js'

export const signUrlsRouter = Router()

const MAX_PATHS_PER_REQUEST = 500
// The HMAC binds to the canonical form (no API-base prefix), so the
// allowlist runs against that canonical path. `canonicalizeAssetPath`
// strips `/api` or `/maker-api`; anything else is treated as-is.
const ALLOWED_PATH_PREFIX = /^\/projects\/[A-Za-z0-9_-]+\//

/**
 * Bulk-sign a list of asset paths for the authenticated user. Each
 * returned URL carries an HMAC `?t=TOKEN` the browser can use on an
 * `<img>`/`<audio>`/anchor that can't send an Authorization header.
 *
 * POST /api/sign-urls
 * Body: { paths: string[] }           // each path is absolute, e.g. /api/projects/foo/assets/images/x.webp
 * Returns: { urls: Record<string, string> }
 *
 * This endpoint itself is Bearer-authenticated via the global
 * `authenticate` middleware. No sensitive information in the response
 * beyond the signed tokens themselves — which each bind to one path
 * and expire in 15 minutes.
 */
signUrlsRouter.post('/', (req, res) => {
  const { paths } = (req.body ?? {}) as { paths?: unknown }
  if (!Array.isArray(paths)) {
    res.status(400).json({ error: 'paths must be an array of strings' })
    return
  }
  if (paths.length === 0) {
    res.json({ urls: {} })
    return
  }
  if (paths.length > MAX_PATHS_PER_REQUEST) {
    res.status(400).json({ error: `too many paths; max ${MAX_PATHS_PER_REQUEST}` })
    return
  }
  const userId = req.user!.userId
  const urls: Record<string, string> = {}
  for (const path of paths) {
    if (typeof path !== 'string') continue
    // Only sign paths we'd actually serve — must look like a
    // project-scoped path. Keeps the endpoint from being a generic
    // HMAC oracle for arbitrary strings.
    const canonical = canonicalizeAssetPath(path)
    if (!ALLOWED_PATH_PREFIX.test(canonical)) continue
    const { token } = signAssetToken(userId, canonical)
    urls[path] = `${path}${path.includes('?') ? '&' : '?'}t=${token}`
  }
  res.json({ urls })
})
