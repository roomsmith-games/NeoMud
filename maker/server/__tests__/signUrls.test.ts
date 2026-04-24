import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import express, { type Express } from 'express'
import request from 'supertest'
import { signUrlsRouter } from '../routes/signUrls.js'
import { authenticate } from '../middleware/auth.js'
import { verifyAssetToken } from '../lib/assetSigning.js'

const USER_ID = 'test-user'
const ORIGINAL = {
  jwt: process.env.JWT_SECRET,
  devId: process.env.MAKER_DEV_USER_ID,
  nodeEnv: process.env.NODE_ENV,
}

beforeEach(() => {
  process.env.JWT_SECRET = 'test-secret-32-characters-minimum-000'
  process.env.MAKER_DEV_USER_ID = USER_ID
  process.env.NODE_ENV = 'development'
})

afterEach(() => {
  process.env.JWT_SECRET = ORIGINAL.jwt ?? ''
  if (ORIGINAL.devId === undefined) delete process.env.MAKER_DEV_USER_ID
  else process.env.MAKER_DEV_USER_ID = ORIGINAL.devId
  if (ORIGINAL.nodeEnv === undefined) delete process.env.NODE_ENV
  else process.env.NODE_ENV = ORIGINAL.nodeEnv
})

function buildApp(): Express {
  const app = express()
  app.use(express.json())
  app.use('/api', authenticate)
  app.use('/api/sign-urls', signUrlsRouter)
  return app
}

describe('POST /api/sign-urls', () => {
  it('returns a signed URL per path', async () => {
    const app = buildApp()
    const r = await request(app)
      .post('/api/sign-urls')
      .send({
        paths: [
          '/api/projects/uat/assets/images/items/foo.webp',
          '/api/projects/uat/assets/audio/bgm/theme.mp3',
        ],
      })
    expect(r.status).toBe(200)
    expect(Object.keys(r.body.urls)).toHaveLength(2)
    expect(r.body.urls['/api/projects/uat/assets/images/items/foo.webp']).toMatch(/\?t=[\w-]+$/)
  })

  it('signed token round-trips via verifyAssetToken', async () => {
    const app = buildApp()
    const path = '/api/projects/uat/assets/images/items/foo.webp'
    const r = await request(app).post('/api/sign-urls').send({ paths: [path] })
    const url: string = r.body.urls[path]
    const token = url.split('?t=')[1]
    expect(verifyAssetToken(token, path)).toMatchObject({ userId: USER_ID })
  })

  it('ignores paths that don\'t match the project-scoped prefix', async () => {
    const app = buildApp()
    const r = await request(app)
      .post('/api/sign-urls')
      .send({
        paths: [
          '/api/projects/uat/assets/images/x.webp',   // kept
          '/etc/passwd',                                // dropped
          'foo',                                        // dropped
          '/api/users/me/subscription',                 // dropped (not an asset-shaped path)
        ],
      })
    expect(r.status).toBe(200)
    expect(Object.keys(r.body.urls)).toEqual(['/api/projects/uat/assets/images/x.webp'])
  })

  it('rejects non-array paths with 400', async () => {
    const app = buildApp()
    const r = await request(app).post('/api/sign-urls').send({ paths: 'not-an-array' })
    expect(r.status).toBe(400)
  })

  it('enforces max-paths-per-request ceiling', async () => {
    const app = buildApp()
    const paths = Array.from({ length: 501 }, (_, i) =>
      `/api/projects/uat/assets/x${i}.webp`,
    )
    const r = await request(app).post('/api/sign-urls').send({ paths })
    expect(r.status).toBe(400)
    expect(r.body.error).toMatch(/too many paths/i)
  })

  it('requires authentication', async () => {
    // Turn off dev bypass for this test
    delete process.env.MAKER_DEV_USER_ID
    const app = buildApp()
    const r = await request(app).post('/api/sign-urls').send({ paths: [] })
    expect(r.status).toBe(401)
  })
})

describe('authenticate accepting ?t=… signed tokens', () => {
  it('lets a valid signed token through on the exact path it was signed for', async () => {
    const app = buildApp()
    const path = '/api/projects/uat/assets/x.webp'
    // Mount a downstream handler to confirm the middleware passed
    app.get('/api/projects/:name/assets/:filename', (req, res) => {
      res.json({ userId: req.user?.userId })
    })

    const signed = await request(app).post('/api/sign-urls').send({ paths: [path] })
    const token = signed.body.urls[path].split('?t=')[1]

    // Request the asset path WITHOUT an Authorization header, using ?t
    delete process.env.MAKER_DEV_USER_ID // turn off the dev bypass too
    const r = await request(app).get(`${path}?t=${token}`)
    expect(r.status).toBe(200)
    expect(r.body.userId).toBe(USER_ID)
  })

  it('preserves a non-t cache-bust query param through HMAC binding', async () => {
    const app = buildApp()
    const pathWithV = '/api/projects/uat/assets/x.webp?v=12345'
    app.get('/api/projects/:name/assets/:filename', (_req, res) => res.json({ ok: true }))

    const signed = await request(app).post('/api/sign-urls').send({ paths: [pathWithV] })
    const url: string = signed.body.urls[pathWithV]
    // Server appended &t=TOKEN (since ?v= already present)
    expect(url).toMatch(/\?v=12345&t=/)

    delete process.env.MAKER_DEV_USER_ID
    const r = await request(app).get(url)
    expect(r.status).toBe(200)
  })

  it('rejects a valid token used on a different path', async () => {
    const app = buildApp()
    const signedPath = '/api/projects/uat/assets/secret.webp'
    const otherPath = '/api/projects/uat/assets/public.webp'
    app.get('/api/projects/:name/assets/:filename', (_req, res) => res.json({ ok: true }))

    const signed = await request(app).post('/api/sign-urls').send({ paths: [signedPath] })
    const token = signed.body.urls[signedPath].split('?t=')[1]

    delete process.env.MAKER_DEV_USER_ID
    const r = await request(app).get(`${otherPath}?t=${token}`)
    expect(r.status).toBe(401)
  })
})
