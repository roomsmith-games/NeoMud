import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// Import after we've set up env manipulation helpers.
import { logger } from '../lib/logger.js'

const ORIGINAL_TOKEN = process.env.BETTER_STACK_TOKEN
const ORIGINAL_HOST = process.env.BETTER_STACK_HOST

function setIngestionEnv(token?: string, host?: string): void {
  if (token === undefined) delete process.env.BETTER_STACK_TOKEN
  else process.env.BETTER_STACK_TOKEN = token
  if (host === undefined) delete process.env.BETTER_STACK_HOST
  else process.env.BETTER_STACK_HOST = host
}

function restoreIngestionEnv(): void {
  if (ORIGINAL_TOKEN === undefined) delete process.env.BETTER_STACK_TOKEN
  else process.env.BETTER_STACK_TOKEN = ORIGINAL_TOKEN
  if (ORIGINAL_HOST === undefined) delete process.env.BETTER_STACK_HOST
  else process.env.BETTER_STACK_HOST = ORIGINAL_HOST
}

beforeEach(() => {
  setIngestionEnv(undefined, undefined)
})

afterEach(() => {
  restoreIngestionEnv()
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

describe('logger — stdout', () => {
  it('writes structured JSON to stdout on info/debug', () => {
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
    logger.info('hello', { userId: 'u1', action: 'world_publish' })
    expect(logSpy).toHaveBeenCalledTimes(1)
    const line = logSpy.mock.calls[0][0] as string
    const parsed = JSON.parse(line)
    expect(parsed).toMatchObject({
      level: 'info',
      message: 'hello',
      userId: 'u1',
      action: 'world_publish',
    })
    expect(typeof parsed.ts).toBe('string')
    expect(new Date(parsed.ts).toString()).not.toBe('Invalid Date')
  })

  it('writes to stderr on warn/error', () => {
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})
    logger.warn('careful')
    logger.error('oops', { code: 'P3005' })
    expect(logSpy).not.toHaveBeenCalled()
    expect(errSpy).toHaveBeenCalledTimes(2)
    expect(JSON.parse(errSpy.mock.calls[0][0] as string).level).toBe('warn')
    expect(JSON.parse(errSpy.mock.calls[1][0] as string)).toMatchObject({
      level: 'error',
      message: 'oops',
      code: 'P3005',
    })
  })
})

describe('logger — Better Stack ingestion', () => {
  it('does NOT fetch when BETTER_STACK_TOKEN is unset', () => {
    const fetchSpy = vi.fn().mockResolvedValue(new Response('{}', { status: 202 }))
    vi.stubGlobal('fetch', fetchSpy)
    vi.spyOn(console, 'log').mockImplementation(() => {})
    logger.info('noop', {})
    expect(logger._isIngestionEnabled()).toBe(false)
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('does NOT fetch when BETTER_STACK_HOST is unset', () => {
    setIngestionEnv('the-token', undefined)
    const fetchSpy = vi.fn().mockResolvedValue(new Response('{}', { status: 202 }))
    vi.stubGlobal('fetch', fetchSpy)
    vi.spyOn(console, 'log').mockImplementation(() => {})
    logger.info('noop', {})
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('POSTs to https://{host} with Bearer token when both env vars are set', async () => {
    setIngestionEnv('tok-xyz', 'ingest.example.com')
    const fetchSpy = vi.fn().mockResolvedValue(new Response('', { status: 202 }))
    vi.stubGlobal('fetch', fetchSpy)
    vi.spyOn(console, 'log').mockImplementation(() => {})

    logger.info('event', { foo: 'bar' })
    // Event is emitted fire-and-forget — give the microtask queue a tick
    // to resolve the fetch mock.
    await new Promise((resolve) => setImmediate(resolve))

    expect(logger._isIngestionEnabled()).toBe(true)
    expect(fetchSpy).toHaveBeenCalledTimes(1)
    const [url, init] = fetchSpy.mock.calls[0]
    expect(url).toBe('https://ingest.example.com')
    expect(init.method).toBe('POST')
    expect(init.headers.Authorization).toBe('Bearer tok-xyz')
    expect(init.headers['Content-Type']).toBe('application/json')
    const body = JSON.parse(init.body)
    expect(body).toMatchObject({
      level: 'info',
      message: 'event',
      foo: 'bar',
    })
    expect(typeof body.dt).toBe('string')
  })

  it('tags every event with its environment (stdout and ingest payload)', async () => {
    // Mirrors the platform logger: prod and staging shared one Better
    // Stack source at launch and unlabeled events were indistinguishable
    // in Live Tail — the env field is the guard.
    vi.stubEnv('NODE_ENV', 'production')
    setIngestionEnv('tok-env', 'ingest.example.com')
    const fetchSpy = vi.fn().mockResolvedValue(new Response('', { status: 202 }))
    vi.stubGlobal('fetch', fetchSpy)
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})

    logger.info('tagged event', {})
    await new Promise((resolve) => setImmediate(resolve))

    expect(JSON.parse(logSpy.mock.calls[0][0] as string).env).toBe('production')
    expect(JSON.parse(fetchSpy.mock.calls[0][1].body).env).toBe('production')
    vi.unstubAllEnvs()
  })

  it('does not throw when Better Stack is unreachable (network error)', async () => {
    setIngestionEnv('tok', 'offline.example.com')
    const fetchSpy = vi.fn().mockRejectedValue(new Error('ENOTFOUND'))
    vi.stubGlobal('fetch', fetchSpy)
    vi.spyOn(console, 'log').mockImplementation(() => {})
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    expect(() => logger.info('fire', {})).not.toThrow()
    // Wait for the catch handler to run — it logs to stderr but does
    // not re-enter the logger (would recurse).
    await new Promise((resolve) => setImmediate(resolve))
    expect(errSpy.mock.calls.some((c) =>
      String(c[0]).includes('Better Stack ingestion failed'),
    )).toBe(true)
  })

  it('logs a warning when Better Stack returns non-2xx', async () => {
    setIngestionEnv('tok', 'bad.example.com')
    const fetchSpy = vi.fn().mockResolvedValue(
      new Response('unauthorized', { status: 401, statusText: 'Unauthorized' }),
    )
    vi.stubGlobal('fetch', fetchSpy)
    vi.spyOn(console, 'log').mockImplementation(() => {})
    const errSpy = vi.spyOn(console, 'error').mockImplementation(() => {})

    logger.info('event', {})
    await new Promise((resolve) => setImmediate(resolve))

    expect(errSpy.mock.calls.some((c) =>
      String(c[0]).includes('Better Stack responded 401'),
    )).toBe(true)
  })
})

describe('logger.metric', () => {
  it('emits a structured event with metric, value, and label fields', () => {
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
    logger.metric('publish_attempts', 1, { outcome: 'allowed', plan: 'FREE' })
    expect(logSpy).toHaveBeenCalledTimes(1)
    const parsed = JSON.parse(logSpy.mock.calls[0][0] as string)
    expect(parsed).toMatchObject({
      level: 'info',
      message: 'metric:publish_attempts',
      metric: 'publish_attempts',
      value: 1,
      outcome: 'allowed',
      plan: 'FREE',
    })
  })

  it('handles numeric values for histograms/gauges', () => {
    const logSpy = vi.spyOn(console, 'log').mockImplementation(() => {})
    logger.metric('api_request_ms', 237, { route: '/worlds', status: 200 })
    const parsed = JSON.parse(logSpy.mock.calls[0][0] as string)
    expect(parsed.value).toBe(237)
    expect(parsed.route).toBe('/worlds')
    expect(parsed.status).toBe(200)
  })

  it('ships metrics through the same Better Stack pipeline as log events', async () => {
    setIngestionEnv('tok', 'ingest.example.com')
    const fetchSpy = vi.fn().mockResolvedValue(new Response('', { status: 202 }))
    vi.stubGlobal('fetch', fetchSpy)
    vi.spyOn(console, 'log').mockImplementation(() => {})

    logger.metric('draft_worlds_active', 3, {})
    await new Promise((resolve) => setImmediate(resolve))

    expect(fetchSpy).toHaveBeenCalledTimes(1)
    const body = JSON.parse(fetchSpy.mock.calls[0][1].body)
    expect(body).toMatchObject({
      metric: 'draft_worlds_active',
      value: 3,
    })
  })
})
