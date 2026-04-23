import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express, { type Express } from 'express'
import request from 'supertest'
import { createTestApp } from './helpers.js'
import { publishRouter } from '../routes/publish.js'

async function setupPublishApp(): Promise<{
  app: Express
  projectName: string
  cleanup: () => Promise<void>
}> {
  const { app: baseApp, projectName, cleanup } = await createTestApp()
  const pubRouter = express.Router({ mergeParams: true })
  pubRouter.use(publishRouter)
  baseApp.use('/api/projects/:name/publish', pubRouter)
  return { app: baseApp, projectName, cleanup }
}

const BEARER = 'Bearer test-token-ignored-by-mocked-fetch'

const fetchCalls: { url: string; init?: RequestInit }[] = []

function mockFetch(
  handler: (url: string, init?: RequestInit) => Promise<Response> | Response
) {
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    fetchCalls.push({ url, init })
    return handler(url, init)
  }))
}

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

beforeEach(() => {
  fetchCalls.length = 0
  vi.unstubAllGlobals()
})

describe('POST /api/projects/:name/publish — input validation', () => {
  let app: Express
  let projectName: string
  let cleanup: () => Promise<void>

  beforeAll(async () => { ({ app, projectName, cleanup } = await setupPublishApp()) })
  afterAll(async () => { await cleanup() })

  it('rejects missing version', async () => {
    const res = await request(app)
      .post(`/api/projects/${projectName}/publish`)
      .set('Authorization', BEARER)
      .send({ changelog: 'first release' })
    expect(res.status).toBe(400)
    expect(res.body.error).toMatch(/version/i)
  })

  it('rejects non-semver version', async () => {
    const res = await request(app)
      .post(`/api/projects/${projectName}/publish`)
      .set('Authorization', BEARER)
      .send({ version: '1', changelog: 'first release' })
    expect(res.status).toBe(400)
    expect(res.body.error).toMatch(/semver/)
  })

  it('rejects empty changelog', async () => {
    const res = await request(app)
      .post(`/api/projects/${projectName}/publish`)
      .set('Authorization', BEARER)
      .send({ version: '1.0.0', changelog: '   ' })
    expect(res.status).toBe(400)
    expect(res.body.error).toMatch(/changelog/i)
  })

  it('rejects malformed finalSlug', async () => {
    const res = await request(app)
      .post(`/api/projects/${projectName}/publish`)
      .set('Authorization', BEARER)
      .send({ version: '1.0.0', changelog: 'first', finalSlug: 'Not Valid!' })
    expect(res.status).toBe(400)
    expect(res.body.error).toMatch(/finalSlug/)
  })
})

describe('POST /api/projects/:name/publish — platform integration', () => {
  let app: Express
  let projectName: string
  let cleanup: () => Promise<void>

  beforeAll(async () => { ({ app, projectName, cleanup } = await setupPublishApp()) })
  afterAll(async () => { await cleanup() })

  it('bubbles up 402 from platform when canPublish=false', async () => {
    mockFetch((url) => {
      if (url.endsWith('/users/me/subscription')) {
        return jsonResponse(200, {
          plan: 'FREE',
          status: 'ACTIVE',
          canPublish: false,
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })

    const res = await request(app)
      .post(`/api/projects/${projectName}/publish`)
      .set('Authorization', BEARER)
      .send({ version: '1.0.0', changelog: 'first release' })

    expect(res.status).toBe(402)
    expect(res.body).toMatchObject({
      plan: 'FREE',
      upgradeUrl: '/account#subscription',
    })
    // Did NOT call drafts/upsert or versions when blocked
    expect(fetchCalls.some((c) => c.url.includes('/drafts/upsert'))).toBe(false)
    expect(fetchCalls.some((c) => c.url.includes('/versions'))).toBe(false)
  })

  it('returns 409 when the semver version already exists', async () => {
    mockFetch((url) => {
      if (url.endsWith('/users/me/subscription')) {
        return jsonResponse(200, { plan: 'CREATOR', status: 'ACTIVE', canPublish: true })
      }
      if (url.endsWith('/drafts/upsert')) {
        return jsonResponse(200, { worldId: 'w123', slug: `draft-test-user-${projectName}` })
      }
      if (url.endsWith('/versions')) {
        return jsonResponse(409, { error: 'Version already exists' })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })

    const res = await request(app)
      .post(`/api/projects/${projectName}/publish`)
      .set('Authorization', BEARER)
      .send({ version: '1.0.0', changelog: 'first release' })

    expect(res.status).toBe(409)
    expect(res.body.error).toMatch(/Version 1\.0\.0 already exists/)
  })

  it('bubbles up 409 name-conflict from platform publish', async () => {
    mockFetch((url) => {
      if (url.endsWith('/users/me/subscription')) {
        return jsonResponse(200, { plan: 'CREATOR', status: 'ACTIVE', canPublish: true })
      }
      if (url.endsWith('/drafts/upsert')) {
        return jsonResponse(200, { worldId: 'w123', slug: `draft-test-user-${projectName}` })
      }
      if (url.endsWith('/versions')) {
        return jsonResponse(201, { version: '1.0.0' })
      }
      if (url.endsWith('/publish')) {
        return jsonResponse(409, { error: 'A world named "X" already exists in the marketplace.' })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })

    const res = await request(app)
      .post(`/api/projects/${projectName}/publish`)
      .set('Authorization', BEARER)
      .send({ version: '1.0.0', changelog: 'first release' })

    expect(res.status).toBe(409)
    expect(res.body.error).toMatch(/already exists/)
  })

  it('happy path: returns marketplace URL and forwards finalSlug', async () => {
    mockFetch((url, init) => {
      if (url.endsWith('/users/me/subscription')) {
        return jsonResponse(200, { plan: 'CREATOR', status: 'ACTIVE', canPublish: true })
      }
      if (url.endsWith('/drafts/upsert')) {
        return jsonResponse(200, { worldId: 'w123', slug: `draft-test-user-${projectName}` })
      }
      if (url.endsWith('/versions')) {
        return jsonResponse(201, { version: '1.0.0' })
      }
      if (url.endsWith('/publish')) {
        // Verify finalSlug made it through
        const body = JSON.parse(init?.body as string)
        expect(body).toEqual({ finalSlug: 'my-clean-slug' })
        return jsonResponse(200, {
          id: 'w123',
          slug: 'my-clean-slug',
          status: 'PUBLISHED',
          publishedAt: '2026-04-23T12:00:00.000Z',
          publicUrl: '/worlds/my-clean-slug',
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })

    const res = await request(app)
      .post(`/api/projects/${projectName}/publish`)
      .set('Authorization', BEARER)
      .send({ version: '1.0.0', changelog: 'first release', finalSlug: 'my-clean-slug' })

    expect(res.status).toBe(200)
    expect(res.body).toMatchObject({
      worldId: 'w123',
      slug: 'my-clean-slug',
      status: 'PUBLISHED',
      publicUrl: '/worlds/my-clean-slug',
    })
  })

  it('happy path with no finalSlug: publish body is empty object', async () => {
    mockFetch((url, init) => {
      if (url.endsWith('/users/me/subscription')) {
        return jsonResponse(200, { plan: 'CREATOR', status: 'ACTIVE', canPublish: true })
      }
      if (url.endsWith('/drafts/upsert')) {
        return jsonResponse(200, { worldId: 'w999', slug: `draft-test-user-${projectName}` })
      }
      if (url.endsWith('/versions')) {
        return jsonResponse(201, { version: '2.0.0' })
      }
      if (url.endsWith('/publish')) {
        const body = JSON.parse(init?.body as string)
        expect(body).toEqual({})
        return jsonResponse(200, {
          id: 'w999',
          slug: 'auto-derived-slug',
          status: 'PUBLISHED',
          publishedAt: '2026-04-23T12:00:00.000Z',
          publicUrl: '/worlds/auto-derived-slug',
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })

    const res = await request(app)
      .post(`/api/projects/${projectName}/publish`)
      .set('Authorization', BEARER)
      .send({ version: '2.0.0', changelog: 'new release' })

    expect(res.status).toBe(200)
    expect(res.body.slug).toBe('auto-derived-slug')
  })
})
