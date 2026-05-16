import { describe, it, expect, beforeEach, afterEach } from 'vitest'
import express from 'express'
import request from 'supertest'
import jwt from 'jsonwebtoken'
import { authenticate } from '../middleware/auth.js'

const JWT_SECRET = 'dev-secret-change-me-minimum-thirty-two-characters'
const ORIGINAL_JWT_SECRET = process.env.JWT_SECRET

beforeEach(() => {
  process.env.JWT_SECRET = JWT_SECRET
})

afterEach(() => {
  if (ORIGINAL_JWT_SECRET !== undefined) process.env.JWT_SECRET = ORIGINAL_JWT_SECRET
  else delete process.env.JWT_SECRET
})

function createAuthTestApp() {
  const app = express()
  app.use(express.json())
  app.use(authenticate)
  app.get('/test', (req, res) => {
    res.json({ user: req.user })
  })
  return app
}

describe('authenticate middleware', () => {
  const app = createAuthTestApp()

  it('rejects requests without Authorization header', async () => {
    const res = await request(app).get('/test')
    expect(res.status).toBe(401)
    expect(res.body.error).toBe('Authentication required')
  })

  it('rejects requests with invalid Bearer token', async () => {
    const res = await request(app)
      .get('/test')
      .set('Authorization', 'Bearer garbage-token')
    expect(res.status).toBe(401)
    expect(res.body.error).toBe('Authentication required')
  })

  it('rejects requests with non-Bearer auth scheme', async () => {
    const res = await request(app)
      .get('/test')
      .set('Authorization', 'Basic dXNlcjpwYXNz')
    expect(res.status).toBe(401)
  })

  it('accepts valid JWT with correct issuer', async () => {
    const token = jwt.sign(
      { userId: 'user-123', role: 'CREATOR' },
      JWT_SECRET,
      { algorithm: 'HS256', issuer: 'neomud-platform' }
    )
    const res = await request(app)
      .get('/test')
      .set('Authorization', `Bearer ${token}`)
    expect(res.status).toBe(200)
    expect(res.body.user.userId).toBe('user-123')
    expect(res.body.user.role).toBe('CREATOR')
  })

  it('rejects JWT with wrong issuer', async () => {
    const token = jwt.sign(
      { userId: 'user-123', role: 'CREATOR' },
      JWT_SECRET,
      { algorithm: 'HS256', issuer: 'wrong-issuer' }
    )
    const res = await request(app)
      .get('/test')
      .set('Authorization', `Bearer ${token}`)
    expect(res.status).toBe(401)
  })

  it('rejects expired JWT', async () => {
    const token = jwt.sign(
      { userId: 'user-123', role: 'CREATOR' },
      JWT_SECRET,
      { algorithm: 'HS256', issuer: 'neomud-platform', expiresIn: '-1s' }
    )
    const res = await request(app)
      .get('/test')
      .set('Authorization', `Bearer ${token}`)
    expect(res.status).toBe(401)
  })
})
