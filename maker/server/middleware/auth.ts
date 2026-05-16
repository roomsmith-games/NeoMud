import type { Request, Response, NextFunction } from 'express'
import jwt from 'jsonwebtoken'
import { verifyAssetToken, canonicalizeAssetPath } from '../lib/assetSigning.js'

// Fail fast at module load if the dangerous dev bypass is enabled in prod.
if (process.env.NODE_ENV === 'production' && process.env.MAKER_DEV_USER_ID) {
  console.error('FATAL: MAKER_DEV_USER_ID must not be set in production')
  process.exit(1)
}

// Fail fast at module load if no JWT key is configured in prod.
if (process.env.NODE_ENV === 'production' && !process.env.JWT_SECRET && !process.env.JWT_PUBLIC_KEY) {
  console.error('FATAL: JWT_SECRET or JWT_PUBLIC_KEY must be set in production')
  process.exit(1)
}

// Open mode: no JWT config and not production — local dev, no auth required.
// Evaluated lazily so tests can set env vars before the first request.
let _openModeLogged = false
function isOpenModeActive(): boolean {
  const open = !process.env.JWT_SECRET && !process.env.JWT_PUBLIC_KEY && process.env.NODE_ENV !== 'production'
  if (open && !_openModeLogged) {
    _openModeLogged = true
    console.log('[auth] No JWT config found, running in open mode (local dev only)')
  }
  return open
}

interface TokenPayload {
  userId: string
  role: string
}

/**
 * Remove only the `t` query param from a URL, preserving any others.
 * The signed HMAC binds to the URL the client asked for (possibly with a
 * `?v=...` cache-bust); the token was appended on top as `t=...`, so
 * strip just that before verifying.
 */
function stripTokenParam(originalUrl: string): string {
  const qIdx = originalUrl.indexOf('?')
  if (qIdx < 0) return originalUrl
  const base = originalUrl.slice(0, qIdx)
  const params = new URLSearchParams(originalUrl.slice(qIdx + 1))
  params.delete('t')
  const rest = params.toString()
  return rest ? `${base}?${rest}` : base
}

// Read env lazily so tests can toggle these between runs and so a runtime
// secret rotation is picked up without a process restart. The fail-fast
// checks above still run at module load on the initial env snapshot.
function getJwtSecret(): string {
  return process.env.JWT_SECRET || 'dev-secret-change-me-minimum-thirty-two-characters'
}
function getJwtPublicKey(): string | null {
  return process.env.JWT_PUBLIC_KEY || null
}
function getDevUserId(): string | null {
  return process.env.MAKER_DEV_USER_ID || null
}

function getVerifyConfig(): { key: jwt.Secret; algorithms: jwt.Algorithm[] } {
  const pub = getJwtPublicKey()
  if (pub) {
    return {
      key: Buffer.from(pub, 'base64'),
      algorithms: ['RS256'],
    }
  }
  return {
    key: getJwtSecret(),
    algorithms: ['HS256'],
  }
}

/**
 * JWT authentication middleware for the Maker.
 * Validates Platform-issued JWTs. In dev mode with MAKER_DEV_USER_ID set,
 * allows unauthenticated requests with a synthetic user.
 */
export function isOpenMode(): boolean {
  return isOpenModeActive()
}

export function authenticate(req: Request, res: Response, next: NextFunction): void {
  // Open mode: no JWT config, not production — skip auth entirely.
  // Grant ADMIN so the default world template in _shared/ is visible.
  if (isOpenModeActive()) {
    req.user = { userId: 'local-dev', role: 'ADMIN' }
    next()
    return
  }

  const authHeader = req.headers.authorization
  const devUserId = getDevUserId()

  // Dev mode bypass (only when NODE_ENV is explicitly 'development')
  if (!authHeader && devUserId && process.env.NODE_ENV === 'development') {
    req.user = { userId: devUserId, role: 'CREATOR' }
    next()
    return
  }

  // Signed-URL bypass: `<img>`/`<audio>` can't send Authorization headers,
  // so we accept `?t=TOKEN` in lieu of a Bearer token. The token is bound
  // to the exact request path (HMAC includes it), so leakage is scoped;
  // expiry defaults to 15 minutes. Client obtains tokens via the
  // /sign-urls endpoint, which does require a Bearer header. See
  // server/lib/assetSigning.ts.
  if (!authHeader) {
    const tokenParam = req.query.t
    if (typeof tokenParam === 'string' && tokenParam.length > 0) {
      // Bind against the canonical path minus the `t` param. `req.path`
      // is relative to the middleware mount and drops the `/api` prefix
      // entirely; `req.originalUrl` includes it. Canonicalization then
      // strips any `/api` or `/maker-api` prefix so the HMAC binding is
      // topology-independent — Caddy rewrites `/maker-api/*` → `/api/*`
      // in stg, so the client-visible path differs from what the server
      // sees. Preserve any non-`t` query params (e.g. cache-bust `v=`).
      const pathForVerify = canonicalizeAssetPath(stripTokenParam(req.originalUrl))
      const verified = verifyAssetToken(tokenParam, pathForVerify)
      if (verified) {
        req.user = { userId: verified.userId, role: 'CREATOR' }
        next()
        return
      }
      res.status(401).json({ error: 'Invalid or expired asset token' })
      return
    }
  }

  if (!authHeader?.startsWith('Bearer ')) {
    res.status(401).json({ error: 'Authentication required' })
    return
  }

  const token = authHeader.slice(7)
  try {
    const { key, algorithms } = getVerifyConfig()
    const decoded = jwt.verify(token, key, {
      algorithms,
      issuer: 'neomud-platform',
    }) as TokenPayload

    req.user = { userId: decoded.userId, role: decoded.role }
    next()
  } catch {
    res.status(401).json({ error: 'Authentication required' })
  }
}
