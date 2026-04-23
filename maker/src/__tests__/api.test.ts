import { describe, it, expect, afterEach } from 'vitest'
import api, { resolveUrl, assetUrl, setProjectScope } from '../api'

afterEach(() => {
  setProjectScope(null)
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
