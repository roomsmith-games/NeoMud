import { createHmac, timingSafeEqual } from 'node:crypto'

/**
 * Short-lived HMAC tokens for authenticating asset URLs that can't carry
 * an Authorization header — `<img src>`, `<audio src>`, anchor downloads.
 *
 * The token binds a userId + path + expiry together. Leakage is scoped to
 * that specific path for at most 15 minutes. Signing key is JWT_SECRET so
 * rotating JWT invalidates all outstanding asset tokens too (same blast
 * radius as session cookies). No separate secret to manage.
 *
 * Token format (URL-safe base64 of):
 *   userId.exp.hmac_hex
 *
 * Where hmac = HMAC-SHA256(userId + "." + path + "." + exp) keyed on JWT_SECRET.
 */

const DEFAULT_TTL_SECONDS = 15 * 60

function getSigningKey(): Buffer {
  const secret =
    process.env.JWT_SECRET ||
    'dev-secret-change-me-minimum-thirty-two-characters'
  return Buffer.from(secret, 'utf8')
}

function b64urlEncode(s: string): string {
  return Buffer.from(s, 'utf8')
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

function b64urlDecode(s: string): string {
  const pad = s.length % 4 === 0 ? '' : '='.repeat(4 - (s.length % 4))
  return Buffer.from(s.replace(/-/g, '+').replace(/_/g, '/') + pad, 'base64').toString(
    'utf8',
  )
}

function computeHmac(userId: string, path: string, exp: number): string {
  return createHmac('sha256', getSigningKey())
    .update(`${userId}.${path}.${exp}`)
    .digest('hex')
}

/**
 * Sign a single path for a user. Returns the opaque token value to stuff
 * into `?t=...`. Caller composes the final URL themselves since they know
 * the base (e.g. `/maker-api` vs `/api`).
 */
export function signAssetToken(
  userId: string,
  path: string,
  ttlSeconds: number = DEFAULT_TTL_SECONDS,
): { token: string; exp: number } {
  const exp = Math.floor(Date.now() / 1000) + ttlSeconds
  const hmac = computeHmac(userId, path, exp)
  const token = b64urlEncode(`${userId}.${exp}.${hmac}`)
  return { token, exp }
}

export interface VerifiedAssetToken {
  userId: string
  exp: number
}

/**
 * Verify a token against a path. Returns the extracted userId on success,
 * null on any failure (malformed, expired, HMAC mismatch, path rebind).
 *
 * Path binding is what keeps a leaked token scoped: the HMAC includes the
 * exact request path, so the token for `/foo.webp` cannot be replayed
 * against `/bar.webp`.
 */
export function verifyAssetToken(
  token: string,
  path: string,
): VerifiedAssetToken | null {
  let decoded: string
  try {
    decoded = b64urlDecode(token)
  } catch {
    return null
  }
  const parts = decoded.split('.')
  if (parts.length !== 3) return null
  const [userId, expStr, givenHmac] = parts
  if (!userId || !expStr || !givenHmac) return null
  const exp = Number.parseInt(expStr, 10)
  if (!Number.isFinite(exp)) return null
  if (Math.floor(Date.now() / 1000) >= exp) return null

  const expectedHmac = computeHmac(userId, path, exp)
  // timing-safe compare — both sides are hex of SHA-256, so same length.
  if (expectedHmac.length !== givenHmac.length) return null
  const ok = timingSafeEqual(Buffer.from(expectedHmac), Buffer.from(givenHmac))
  if (!ok) return null
  return { userId, exp }
}
