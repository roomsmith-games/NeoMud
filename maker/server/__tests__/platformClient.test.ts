import { describe, it, expect } from 'vitest'
import { getProjectQuota } from '../services/platformClient.js'

describe('getProjectQuota', () => {
  it('returns 3 for FREE', () => {
    expect(getProjectQuota('FREE')).toBe(3)
  })

  it('returns 25 for CREATOR', () => {
    expect(getProjectQuota('CREATOR')).toBe(25)
  })

  it('returns Infinity for PRO', () => {
    expect(getProjectQuota('PRO')).toBe(Number.POSITIVE_INFINITY)
  })
})
