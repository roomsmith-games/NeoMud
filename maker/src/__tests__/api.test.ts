// @vitest-environment jsdom
import { describe, it, expect, afterEach, beforeEach, vi } from 'vitest'
import api, { resolveUrl, assetUrl, setProjectScope } from '../api'

const TOKEN_KEY = 'neomud_access_token'

beforeEach(() => {
  localStorage.removeItem(TOKEN_KEY)
})

afterEach(() => {
  setProjectScope(null)
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('resolveUrl', () => {
  it('prepends /api to unscoped paths by default', () => {
    expect(resolveUrl('/projects')).toBe('/api/projects')
    expect(resolveUrl('/zones/main')).toBe('/api/zones/main')
  })

  it('prefixes project scope with /api/projects/{name}', () => {
    setProjectScope('my world')
    expect(resolveUrl('/zones')).toBe('/api/projects/my%20world/zones')
    expect(resolveUrl('/items/potion')).toBe('/api/projects/my%20world/items/potion')
  })

  it('leaves /projects-prefixed paths unscoped', () => {
    setProjectScope('active')
    expect(resolveUrl('/projects')).toBe('/api/projects')
    expect(resolveUrl('/projects/other')).toBe('/api/projects/other')
  })
})

describe('assetUrl', () => {
  it('prepends /api to asset paths regardless of project scope', () => {
    expect(assetUrl('/assets/images/foo.webp')).toBe('/api/assets/images/foo.webp')
    setProjectScope('world')
    expect(assetUrl('/assets/audio/bgm.mp3')).toBe('/api/assets/audio/bgm.mp3')
    expect(assetUrl('/export/nmd')).toBe('/api/export/nmd')
  })

  it('is reachable via the api default export', () => {
    expect(api.assetUrl('/assets/x.webp')).toBe('/api/assets/x.webp')
  })
})

describe('token storage', () => {
  it('reads and writes under neomud_access_token (matches platform)', () => {
    api.setToken('tok-1')
    expect(localStorage.getItem(TOKEN_KEY)).toBe('tok-1')
    expect(api.getToken()).toBe('tok-1')
    expect(api.isAuthenticated()).toBe(true)
    api.clearToken()
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(api.isAuthenticated()).toBe(false)
  })
})

describe('401 handling', () => {
  it('clears the token and redirects to / on 401', async () => {
    localStorage.setItem(TOKEN_KEY, 'stale-tok')
    const assignSpy = vi.fn()
    Object.defineProperty(window, 'location', {
      value: { ...window.location, assign: assignSpy },
      writable: true,
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 401,
      statusText: 'Unauthorized',
      headers: new Headers(),
      text: async () => 'unauthorized',
    }))

    await expect(api.get('/projects')).rejects.toThrow(/sign in|session expired/i)
    expect(localStorage.getItem(TOKEN_KEY)).toBeNull()
    expect(assignSpy).toHaveBeenCalledWith('/')
  })

  it('does not redirect on non-401 errors', async () => {
    localStorage.setItem(TOKEN_KEY, 'good-tok')
    const assignSpy = vi.fn()
    Object.defineProperty(window, 'location', {
      value: { ...window.location, assign: assignSpy },
      writable: true,
    })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false,
      status: 500,
      statusText: 'Internal Server Error',
      headers: new Headers(),
      text: async () => 'boom',
    }))

    await expect(api.get('/projects')).rejects.toThrow()
    expect(localStorage.getItem(TOKEN_KEY)).toBe('good-tok')
    expect(assignSpy).not.toHaveBeenCalled()
  })
})
