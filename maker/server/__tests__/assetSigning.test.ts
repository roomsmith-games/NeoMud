import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest'
import { signAssetToken, verifyAssetToken } from '../lib/assetSigning.js'

const originalSecret = process.env.JWT_SECRET

beforeEach(() => {
  process.env.JWT_SECRET = 'test-secret-32-characters-minimum-000'
})

afterEach(() => {
  if (originalSecret === undefined) delete process.env.JWT_SECRET
  else process.env.JWT_SECRET = originalSecret
  vi.useRealTimers()
})

describe('signAssetToken / verifyAssetToken', () => {
  const USER_ID = 'dev-user'
  const PATH = '/api/projects/my-world/assets/images/items/foo.webp'

  it('round-trips userId and exp for a valid token', () => {
    const { token, exp } = signAssetToken(USER_ID, PATH)
    const verified = verifyAssetToken(token, PATH)
    expect(verified).toEqual({ userId: USER_ID, exp })
  })

  it('rejects a token bound to a different path (path-binding)', () => {
    const { token } = signAssetToken(USER_ID, PATH)
    const other = '/api/projects/my-world/assets/images/items/other.webp'
    expect(verifyAssetToken(token, other)).toBeNull()
  })

  it('rejects an expired token', () => {
    vi.useFakeTimers()
    const { token } = signAssetToken(USER_ID, PATH, 60) // 60s TTL
    vi.setSystemTime(Date.now() + 61_000)
    expect(verifyAssetToken(token, PATH)).toBeNull()
  })

  it('rejects a token signed with a different secret', () => {
    const { token } = signAssetToken(USER_ID, PATH)
    process.env.JWT_SECRET = 'completely-different-secret-minimum-32x'
    expect(verifyAssetToken(token, PATH)).toBeNull()
  })

  it('rejects a malformed token', () => {
    expect(verifyAssetToken('not-a-valid-token', PATH)).toBeNull()
    expect(verifyAssetToken('', PATH)).toBeNull()
  })

  it('rejects a token where the HMAC has been tampered', () => {
    const { token } = signAssetToken(USER_ID, PATH)
    // Flip the last char of the base64url. Decoded form's HMAC hex won't match.
    const bad = token.slice(0, -1) + (token.at(-1) === 'a' ? 'b' : 'a')
    expect(verifyAssetToken(bad, PATH)).toBeNull()
  })

  it('produces different tokens for different users', () => {
    const a = signAssetToken('user-a', PATH).token
    const b = signAssetToken('user-b', PATH).token
    expect(a).not.toBe(b)
  })

  it('binds to path exactly (trailing slash / case matter)', () => {
    const { token } = signAssetToken(USER_ID, '/a/B.webp')
    expect(verifyAssetToken(token, '/a/B.webp')).not.toBeNull()
    expect(verifyAssetToken(token, '/a/b.webp')).toBeNull()
    expect(verifyAssetToken(token, '/a/B.webp/')).toBeNull()
  })
})
