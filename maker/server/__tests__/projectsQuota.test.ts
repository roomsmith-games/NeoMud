import { describe, it, expect, beforeEach, afterEach, beforeAll, afterAll, vi } from 'vitest'
import request from 'supertest'
import type { Express } from 'express'
import { createTestApp } from './helpers.js'

const BEARER = 'Bearer test-token'

const fetchCalls: { url: string }[] = []

function mockFetch(
  handler: (url: string) => Promise<Response> | Response,
) {
  vi.stubGlobal('fetch', vi.fn(async (url: string) => {
    fetchCalls.push({ url })
    return handler(url)
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

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('POST /api/projects — plan-based quota (FREE=3)', () => {
  let app: Express
  let cleanup: () => Promise<void>

  beforeAll(async () => {
    const ctx = await createTestApp()
    app = ctx.app
    cleanup = ctx.cleanup
  })
  afterAll(async () => { await cleanup() })

  it('does not call Platform subscription when request has no Authorization header', async () => {
    // Dev/test path: no Bearer token. Quota helper should short-circuit
    // and NOT hit the Platform API, and must not apply a quota.
    mockFetch(() => {
      throw new Error('fetch should not be called when authHeader is absent')
    })
    const res = await request(app)
      .post('/api/projects')
      .send({ name: `noauth_${Date.now()}` })
    // Either 200 (created) or some other non-quota error, but NOT 402.
    expect(res.status).not.toBe(402)
    expect(fetchCalls.some((c) => c.url.includes('/users/me/subscription'))).toBe(false)
  })

  it('returns 402 with upgrade info when FREE user is at quota', async () => {
    // Simulate FREE plan + already 3 user-owned projects. The test-app
    // helper created one for this file, and the previous "does not
    // call Platform..." test created another. We need ≥3 to trip the
    // gate — create two more under the same user via direct calls,
    // then one extra attempt via Bearer (platform mock returns FREE).
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

    // Ensure there are at least 3 user-owned projects already. Create
    // via direct requests without Authorization so they bypass quota.
    await request(app).post('/api/projects').send({ name: `q1_${Date.now()}` })
    await request(app).post('/api/projects').send({ name: `q2_${Date.now()}` })
    await request(app).post('/api/projects').send({ name: `q3_${Date.now()}` })

    // Now the Bearer-authenticated call with a FREE plan should 402.
    const res = await request(app)
      .post('/api/projects')
      .set('Authorization', BEARER)
      .send({ name: `q4_${Date.now()}` })

    expect(res.status).toBe(402)
    expect(res.body).toMatchObject({
      plan: 'FREE',
      upgradeUrl: '/account#subscription',
    })
    expect(res.body.error).toMatch(/quota reached/i)
  })

  it('allows creation when Platform subscription returns CREATOR', async () => {
    mockFetch((url) => {
      if (url.endsWith('/users/me/subscription')) {
        return jsonResponse(200, {
          plan: 'CREATOR',
          status: 'ACTIVE',
          canPublish: true,
        })
      }
      throw new Error(`Unexpected fetch: ${url}`)
    })

    const res = await request(app)
      .post('/api/projects')
      .set('Authorization', BEARER)
      .send({ name: `creator_${Date.now()}` })

    expect(res.status).not.toBe(402)
  })

  it('fails open (no 402) when Platform subscription lookup fails', async () => {
    mockFetch(() => jsonResponse(500, { error: 'platform down' }))

    const res = await request(app)
      .post('/api/projects')
      .set('Authorization', BEARER)
      .send({ name: `failopen_${Date.now()}` })

    expect(res.status).not.toBe(402)
  })
})
