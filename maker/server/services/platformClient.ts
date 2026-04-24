/**
 * Thin HTTP client for the NeoMud Platform. All calls forward the
 * user's Bearer token so the Platform can apply its own auth +
 * subscription checks. Isolated here so routes don't hand-roll fetch.
 *
 * PLATFORM_API_URL comes from env (maker.env on stg). Defaults to
 * http://localhost:3000 for local dev; stg compose sets the full
 * docker-DNS URL (http://platform-stg:3012).
 */

const PLATFORM_API_URL = process.env.PLATFORM_API_URL || 'http://localhost:3000'

export interface SubscriptionResponse {
  plan: 'FREE' | 'CREATOR' | 'PRO'
  status: 'ACTIVE' | 'PAST_DUE' | 'CANCELED' | 'TRIALING'
  canPublish: boolean
  bypassReason?: 'gate_disabled' | 'beta_allowlist'
}

export async function platformGetSubscription(authHeader: string): Promise<SubscriptionResponse> {
  const res = await fetch(`${PLATFORM_API_URL}/api/v1/users/me/subscription`, {
    headers: { Authorization: authHeader },
  })
  if (!res.ok) {
    const text = await res.text().catch(() => res.statusText)
    throw new Error(`subscription lookup failed (${res.status}): ${text}`)
  }
  return (await res.json()) as SubscriptionResponse
}

/**
 * Project quota per plan. FREE's intentionally low to keep the stg
 * disk in bounds until billing is live; paid tiers get room to grow.
 * Matches the numbers in the parent plan §4.3.
 */
const PROJECT_QUOTAS: Record<SubscriptionResponse['plan'], number> = {
  FREE: 3,
  CREATOR: 25,
  PRO: Number.POSITIVE_INFINITY,
}

export function getProjectQuota(plan: SubscriptionResponse['plan']): number {
  return PROJECT_QUOTAS[plan]
}
