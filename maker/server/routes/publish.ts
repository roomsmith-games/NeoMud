import { Router } from 'express'
import { buildNmdBundle } from './export.js'
import { slugifyProjectName } from './playtest.js'

export const publishRouter = Router({ mergeParams: true })

const PLATFORM_API_URL = process.env.PLATFORM_API_URL || 'http://localhost:3000'

function forwardAuth(req: import('express').Request): string | null {
  const authHeader = req.headers.authorization
  return authHeader?.startsWith('Bearer ') ? authHeader : null
}

interface SubscriptionResponse {
  plan: string
  status: string
  canPublish: boolean
  bypassReason?: string
}

async function platformGetSubscription(authHeader: string): Promise<SubscriptionResponse> {
  const res = await fetch(`${PLATFORM_API_URL}/api/v1/users/me/subscription`, {
    headers: { Authorization: authHeader },
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`subscription lookup failed (${res.status}): ${text}`)
  }
  return (await res.json()) as SubscriptionResponse
}

async function platformUpsertDraft(
  authHeader: string,
  bundle: Buffer,
  slug: string,
  projectName: string
): Promise<{ worldId: string; slug: string }> {
  const form = new FormData()
  form.set('bundle', new Blob([bundle], { type: 'application/zip' }), 'draft.nmd')
  form.set('slug', slug)
  form.set('projectName', projectName)

  const res = await fetch(`${PLATFORM_API_URL}/api/v1/worlds/drafts/upsert`, {
    method: 'POST',
    headers: { Authorization: authHeader },
    body: form,
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`drafts/upsert failed (${res.status}): ${text}`)
  }
  return (await res.json()) as { worldId: string; slug: string }
}

async function platformCreateVersion(
  authHeader: string,
  worldId: string,
  bundle: Buffer,
  version: string,
  changelog: string
): Promise<{ version: string }> {
  const form = new FormData()
  form.set('bundle', new Blob([bundle], { type: 'application/zip' }), `${version}.nmd`)
  form.set('version', version)
  form.set('changelog', changelog)

  const res = await fetch(`${PLATFORM_API_URL}/api/v1/worlds/${worldId}/versions`, {
    method: 'POST',
    headers: { Authorization: authHeader },
    body: form,
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    const err = new Error(`versions failed (${res.status}): ${text}`) as Error & {
      status?: number
      body?: string
    }
    err.status = res.status
    err.body = text
    throw err
  }
  return (await res.json()) as { version: string }
}

interface PublishResult {
  id: string
  slug: string
  status: string
  publishedAt: string
  publicUrl: string
}

async function platformPublish(
  authHeader: string,
  worldId: string,
  finalSlug?: string
): Promise<{ status: number; body: unknown }> {
  const res = await fetch(`${PLATFORM_API_URL}/api/v1/worlds/${worldId}/publish`, {
    method: 'POST',
    headers: { Authorization: authHeader, 'Content-Type': 'application/json' },
    body: JSON.stringify(finalSlug ? { finalSlug } : {}),
  })
  const body = await res.json().catch(() => ({}))
  return { status: res.status, body }
}

// ─── POST /api/projects/:name/publish ─────────────────────
// Promotes a DRAFT world to PUBLISHED. Preflight against the platform
// subscription gate; 402 bubbles up verbatim so the UI can show an
// upsell. Builds a fresh bundle, upserts a DRAFT world (so first-time
// publishers don't need to have playtested first), uploads it as a
// proper semver WorldVersion, then flips status.
publishRouter.post('/', async (req, res, next) => {
  try {
    const userId = req.user!.userId
    const projectName = req.projectName!
    const prisma = req.db!
    const assetsDir = req.projectDir!

    const authHeader = forwardAuth(req)
    if (!authHeader) {
      res.status(401).json({ error: 'Authentication required' })
      return
    }

    const { version, changelog, finalSlug } = req.body as {
      version?: unknown
      changelog?: unknown
      finalSlug?: unknown
    }

    if (typeof version !== 'string' || !/^\d+\.\d+\.\d+$/.test(version)) {
      res.status(400).json({ error: 'version is required and must be semver (e.g. 1.0.0)' })
      return
    }
    if (typeof changelog !== 'string' || changelog.trim().length === 0) {
      res.status(400).json({ error: 'changelog is required and cannot be empty' })
      return
    }
    if (changelog.length > 5000) {
      res.status(400).json({ error: 'changelog must be 5000 characters or fewer' })
      return
    }
    if (finalSlug !== undefined && (typeof finalSlug !== 'string' || !/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(finalSlug) || finalSlug.length < 3 || finalSlug.length > 50)) {
      res.status(400).json({ error: 'finalSlug must be 3-50 lowercase alphanumeric chars with hyphens' })
      return
    }

    const subscription = await platformGetSubscription(authHeader)
    if (!subscription.canPublish) {
      res.status(402).json({
        error: 'Publishing requires a CREATOR or PRO subscription.',
        plan: subscription.plan,
        status: subscription.status,
        upgradeUrl: '/account#subscription',
      })
      return
    }

    const bundle = await buildNmdBundle(prisma, assetsDir)
    const slug = `draft-${userId}-${slugifyProjectName(projectName)}`

    const upsert = await platformUpsertDraft(authHeader, bundle, slug, projectName)

    try {
      await platformCreateVersion(authHeader, upsert.worldId, bundle, version, changelog)
    } catch (err) {
      const status = (err as { status?: number }).status
      if (status === 409) {
        res.status(409).json({
          error: `Version ${version} already exists for this world. Bump the version number.`,
        })
        return
      }
      throw err
    }

    const publish = await platformPublish(
      authHeader,
      upsert.worldId,
      typeof finalSlug === 'string' ? finalSlug : undefined,
    )

    if (publish.status === 402 || publish.status === 409 || publish.status === 400) {
      res.status(publish.status).json(publish.body)
      return
    }
    if (publish.status !== 200) {
      const errBody = publish.body as { error?: string }
      throw new Error(`publish failed (${publish.status}): ${errBody.error ?? 'unknown'}`)
    }

    const result = publish.body as PublishResult
    res.status(200).json({
      worldId: result.id,
      slug: result.slug,
      status: result.status,
      publishedAt: result.publishedAt,
      publicUrl: result.publicUrl,
    })
  } catch (err) {
    next(err)
  }
})
