/**
 * Process-local state for active playtests. One slot per user (open Q4
 * from docs/maker-playtest-plan.md — one active playtest per user, not
 * per project). Phase 6 hardens this to persistent storage.
 */

export interface ActivePlaytest {
  worldId: string
  slug: string
  projectName: string
  startedAt: number
}

const activePlaytests = new Map<string, ActivePlaytest>()

export function getActivePlaytest(userId: string): ActivePlaytest | undefined {
  return activePlaytests.get(userId)
}

export function setActivePlaytest(userId: string, entry: ActivePlaytest): void {
  activePlaytests.set(userId, entry)
}

export function clearActivePlaytest(userId: string): void {
  activePlaytests.delete(userId)
}

/** Test helper — wipe the map between tests. Not for production use. */
export function _resetAllActivePlaytests(): void {
  activePlaytests.clear()
}
