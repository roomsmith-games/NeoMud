import { describe, it, expect, beforeAll, afterAll, beforeEach, vi } from 'vitest'
import express, { type Express } from 'express'
import request from 'supertest'
import { createTestApp } from './helpers.js'
import { playtestRouter } from '../routes/playtest.js'
import { _resetAllActivePlaytests, setActivePlaytest, getActivePlaytest } from '../playtestState.js'
import { slugifyProjectName } from '../routes/playtest.js'

// Default-pass the playtest-preflight validation. The pre-#298-trio tests
// here exercise the platform-call flow on top of an empty test project,
// which would otherwise fail validation (no spawnRoom). Specific tests
// that exercise the validation branch override this with mockResolvedValueOnce.
vi.mock('../validate.js', () => ({
  validateProject: vi.fn().mockResolvedValue({ errors: [], warnings: [] }),
}))
import { validateProject } from '../validate.js'

/**
 * Mount the playtest router on top of a standard test-app. The helper
 * already injects a test user and project context onto each request,
 * so we just need to nest our router under /api/projects/:name/playtest
 * to match the production mount.
 */
async function setupPlaytestApp(): Promise<{
  app: Express
  projectName: string
  cleanup: () => Promise<void>
}> {
  const { app: baseApp, projectName, cleanup } = await createTestApp()
  // Re-wrap: the helper's app pre-mounts project context. Mount the
  // playtest router inside a new parent so :name matches correctly.
  const ptRouter = express.Router({ mergeParams: true })
  ptRouter.use(playtestRouter)
  baseApp.use('/api/projects/:name/playtest', ptRouter)
  return { app: baseApp, projectName, cleanup }
}

const TEST_USER_ID = 'test-user'
const BEARER = 'Bearer test-token-ignored-by-mocked-fetch'

// Record all fetch calls so individual tests can make assertions.
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
  _resetAllActivePlaytests()
  vi.unstubAllGlobals()
})

describe('slugifyProjectName', () => {
  it('lowercases and collapses non-alphanumerics to hyphens', () => {
    expect(slugifyProjectName('My World')).toBe('my-world')
    expect(slugifyProjectName('Dungeon_of_Doom!')).toBe('dungeon-of-doom')
    expect(slugifyProjectName('  leading   spaces  ')).toBe('leading-spaces')
    expect(slugifyProjectName('already-slugged')).toBe('already-slugged')
  })
})

describe('GET /api/projects/:name/playtest', () => {
  let app: Express
  let projectName: string
  let cleanup: () => Promise<void>

  beforeAll(async () => {
    ({ app, projectName, cleanup } = await setupPlaytestApp())
  })
  afterAll(async () => { await cleanup() })

  it('returns { playtest: null } when none active', async () => {
    const res = await request(app)
      .get(`/api/projects/${projectName}/playtest`)
      .set('Authorization', BEARER)
    expect(res.status).toBe(200)
    expect(res.body).toEqual({ playtest: null })
  })

  it('returns the active entry when one is set for this user', async () => {
    setActivePlaytest(TEST_USER_ID, {
      worldId: 'world-123',
      slug: `draft-${TEST_USER_ID}-${slugifyProjectName(projectName)}`,
      projectName,
      startedAt: 1_000,
    })
    const res = await request(app)
      .get(`/api/projects/${projectName}/playtest`)
      .set('Authorization', BEARER)
    expect(res.status).toBe(200)
    expect(res.body.playtest.worldId).toBe('world-123')
    expect(res.body.playtest.projectName).toBe(projectName)
  })
})

describe('POST /api/projects/:name/playtest', () => {
  let app: Express
  let projectName: string
  let cleanup: () => Promise<void>

  beforeAll(async () => {
    ({ app, projectName, cleanup } = await setupPlaytestApp())
  })
  afterAll(async () => { await cleanup() })

  it('rejects requests without a Bearer token (401)', async () => {
    const res = await request(app).post(`/api/projects/${projectName}/playtest`)
    expect(res.status).toBe(401)
  })

  it('calls platform drafts/upsert then start, records the entry, returns playPath', async () => {
    mockFetch((url) => {
      if (url.endsWith('/api/v1/worlds/drafts/upsert')) {
        return jsonResponse(200, { worldId: 'world-xyz', slug: `draft-${TEST_USER_ID}-${slugifyProjectName(projectName)}` })
      }
      if (url.endsWith('/start')) {
        return jsonResponse(201, {
          slug: `draft-${TEST_USER_ID}-${slugifyProjectName(projectName)}`,
          playPath: `/play/draft-${TEST_USER_ID}-${slugifyProjectName(projectName)}`,
        })
      }
      return new Response('unexpected', { status: 500 })
    })

    const res = await request(app)
      .post(`/api/projects/${projectName}/playtest`)
      .set('Authorization', BEARER)
      .send({})

    expect(res.status).toBe(200)
    expect(res.body.worldId).toBe('world-xyz')
    expect(res.body.playPath).toMatch(/^\/play\/draft-test-user-/)

    const active = getActivePlaytest(TEST_USER_ID)
    expect(active?.worldId).toBe('world-xyz')
    expect(active?.projectName).toBe(projectName)

    // Both platform calls should have received the forwarded Bearer.
    const upsertCall = fetchCalls.find((c) => c.url.endsWith('/drafts/upsert'))
    const startCall = fetchCalls.find((c) => c.url.endsWith('/start'))
    expect((upsertCall!.init!.headers as Record<string, string>).Authorization).toBe(BEARER)
    expect((startCall!.init!.headers as Record<string, string>).Authorization).toBe(BEARER)
  })

  it('rejects with 400 + structured validation when the project would crash the game-server', async () => {
    // Simulates the uat-dungeon failure: no zone has a spawnRoom set.
    // Without preflight, this used to upsert + spawn a container that
    // hard-throws inside WorldLoader and ate a 40-second health-check
    // timeout. We expect to short-circuit at the maker before any platform
    // call happens.
    vi.mocked(validateProject).mockResolvedValueOnce({
      errors: ['No zone defines a spawnRoom. At least one zone must specify a spawnRoom.'],
      warnings: [],
    })

    mockFetch(() => new Response('should not be called', { status: 500 }))

    const res = await request(app)
      .post(`/api/projects/${projectName}/playtest`)
      .set('Authorization', BEARER)
      .send({})

    expect(res.status).toBe(400)
    expect(res.body.error).toMatch(/fix the following/i)
    expect(res.body.validation.errors).toContain(
      'No zone defines a spawnRoom. At least one zone must specify a spawnRoom.'
    )
    // No platform calls should have been made.
    expect(fetchCalls.length).toBe(0)
    // No active playtest should have been recorded.
    expect(getActivePlaytest(TEST_USER_ID)).toBeUndefined()
  })

  it('stops a prior playtest for a different project before starting the new one', async () => {
    setActivePlaytest(TEST_USER_ID, {
      worldId: 'old-world-id',
      slug: 'draft-old',
      projectName: 'some-other-project',
      startedAt: 0,
    })

    mockFetch((url) => {
      if (url.endsWith(`/api/v1/worlds/old-world-id/stop`)) {
        return jsonResponse(200, { status: 'OFFLINE' })
      }
      if (url.endsWith('/drafts/upsert')) {
        return jsonResponse(200, { worldId: 'new-world', slug: `draft-${TEST_USER_ID}-${slugifyProjectName(projectName)}` })
      }
      if (url.endsWith('/start')) {
        return jsonResponse(201, {
          slug: `draft-${TEST_USER_ID}-${slugifyProjectName(projectName)}`,
          playPath: `/play/draft-${TEST_USER_ID}-${slugifyProjectName(projectName)}`,
        })
      }
      return new Response('unexpected', { status: 500 })
    })

    const res = await request(app)
      .post(`/api/projects/${projectName}/playtest`)
      .set('Authorization', BEARER)
      .send({})

    expect(res.status).toBe(200)
    // The prior's stop must have been called before upsert.
    const stopIdx = fetchCalls.findIndex((c) => c.url.endsWith('/old-world-id/stop'))
    const upsertIdx = fetchCalls.findIndex((c) => c.url.endsWith('/drafts/upsert'))
    expect(stopIdx).toBeGreaterThanOrEqual(0)
    expect(stopIdx).toBeLessThan(upsertIdx)
    expect(getActivePlaytest(TEST_USER_ID)?.worldId).toBe('new-world')
  })
})

describe('POST /api/projects/:name/playtest/reload', () => {
  let app: Express
  let projectName: string
  let cleanup: () => Promise<void>

  beforeAll(async () => {
    ({ app, projectName, cleanup } = await setupPlaytestApp())
  })
  afterAll(async () => { await cleanup() })

  it('returns 404 when no playtest is active for this user', async () => {
    const res = await request(app)
      .post(`/api/projects/${projectName}/playtest/reload`)
      .set('Authorization', BEARER)
    expect(res.status).toBe(404)
  })

  it('returns 409 when the active playtest is for a different project', async () => {
    setActivePlaytest(TEST_USER_ID, {
      worldId: 'world-other',
      slug: 'draft-other',
      projectName: 'some-other-project',
      startedAt: 0,
    })
    const res = await request(app)
      .post(`/api/projects/${projectName}/playtest/reload`)
      .set('Authorization', BEARER)
    expect(res.status).toBe(409)
  })

  it('happy path: upsert + restart, returns readyInMs', async () => {
    setActivePlaytest(TEST_USER_ID, {
      worldId: 'world-abc',
      slug: `draft-${TEST_USER_ID}-${slugifyProjectName(projectName)}`,
      projectName,
      startedAt: 0,
    })
    mockFetch((url) => {
      if (url.endsWith('/drafts/upsert')) {
        return jsonResponse(200, { worldId: 'world-abc', slug: `draft-${TEST_USER_ID}-${slugifyProjectName(projectName)}` })
      }
      if (url.endsWith('/restart')) {
        return jsonResponse(200, { readyInMs: 4321 })
      }
      return new Response('unexpected', { status: 500 })
    })

    const res = await request(app)
      .post(`/api/projects/${projectName}/playtest/reload`)
      .set('Authorization', BEARER)

    expect(res.status).toBe(200)
    expect(res.body.readyInMs).toBe(4321)
    const upsertIdx = fetchCalls.findIndex((c) => c.url.endsWith('/drafts/upsert'))
    const restartIdx = fetchCalls.findIndex((c) => c.url.endsWith('/restart'))
    expect(upsertIdx).toBeGreaterThanOrEqual(0)
    expect(upsertIdx).toBeLessThan(restartIdx)
  })

  it('passes through 504 when platform restart times out', async () => {
    setActivePlaytest(TEST_USER_ID, {
      worldId: 'world-abc',
      slug: `draft-${TEST_USER_ID}-${slugifyProjectName(projectName)}`,
      projectName,
      startedAt: 0,
    })
    mockFetch((url) => {
      if (url.endsWith('/drafts/upsert')) {
        return jsonResponse(200, { worldId: 'world-abc', slug: 'x' })
      }
      if (url.endsWith('/restart')) {
        return jsonResponse(504, { error: 'readiness timeout' })
      }
      return new Response('unexpected', { status: 500 })
    })
    const res = await request(app)
      .post(`/api/projects/${projectName}/playtest/reload`)
      .set('Authorization', BEARER)
    expect(res.status).toBe(504)
  })
})

describe('POST /api/projects/:name/playtest/stop', () => {
  let app: Express
  let projectName: string
  let cleanup: () => Promise<void>

  beforeAll(async () => {
    ({ app, projectName, cleanup } = await setupPlaytestApp())
  })
  afterAll(async () => { await cleanup() })

  it('returns { stopped: false } when nothing active', async () => {
    const res = await request(app)
      .post(`/api/projects/${projectName}/playtest/stop`)
      .set('Authorization', BEARER)
    expect(res.status).toBe(200)
    expect(res.body).toEqual({ stopped: false })
  })

  it('stops an active playtest and clears the entry', async () => {
    setActivePlaytest(TEST_USER_ID, {
      worldId: 'world-abc',
      slug: 'draft-x',
      projectName,
      startedAt: 0,
    })
    mockFetch((url) => {
      if (url.endsWith('/world-abc/stop')) {
        return jsonResponse(200, { status: 'OFFLINE' })
      }
      return new Response('unexpected', { status: 500 })
    })
    const res = await request(app)
      .post(`/api/projects/${projectName}/playtest/stop`)
      .set('Authorization', BEARER)
    expect(res.status).toBe(200)
    expect(res.body).toEqual({ stopped: true, worldId: 'world-abc' })
    expect(getActivePlaytest(TEST_USER_ID)).toBeUndefined()
  })

  it('still clears local entry even if the platform stop call fails', async () => {
    setActivePlaytest(TEST_USER_ID, {
      worldId: 'world-abc',
      slug: 'draft-x',
      projectName,
      startedAt: 0,
    })
    mockFetch(() => new Response('boom', { status: 500 }))
    const res = await request(app)
      .post(`/api/projects/${projectName}/playtest/stop`)
      .set('Authorization', BEARER)
    expect(res.status).toBe(200)
    expect(res.body.stopped).toBe(true)
    expect(getActivePlaytest(TEST_USER_ID)).toBeUndefined()
  })
})
