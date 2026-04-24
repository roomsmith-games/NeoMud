// @vitest-environment jsdom
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'

vi.mock('../api', () => ({
  default: {
    post: vi.fn(),
  },
}))

import api from '../api'
import PublishModal from '../components/PublishModal'

const mockApi = vi.mocked(api)

function noop() {}

beforeEach(() => {
  vi.clearAllMocks()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

/**
 * These tests exercise the modal's client-side validation in isolation.
 * MenuBar.test.tsx covers the happy path + server response handling; this
 * file pins down the regex + length bounds that prevent bad requests from
 * leaving the browser.
 */
describe('PublishModal — submit gating', () => {
  it('keeps Publish disabled until changelog is non-empty', async () => {
    render(
      <PublishModal
        projectName="p1"
        onClose={noop}
        onSuccess={noop}
        onUpsell={noop}
      />,
    )
    const submit = screen.getByTestId('publish-submit-btn')
    expect(submit).toBeDisabled()

    await userEvent.type(screen.getByLabelText('Changelog'), 'first release')
    expect(submit).not.toBeDisabled()
  })

  it('disables Publish when version is not semver', async () => {
    render(
      <PublishModal
        projectName="p1"
        onClose={noop}
        onSuccess={noop}
        onUpsell={noop}
      />,
    )
    const version = screen.getByLabelText('Version')
    await userEvent.clear(version)
    await userEvent.type(version, '1.0')
    await userEvent.type(screen.getByLabelText('Changelog'), 'first release')
    expect(screen.getByTestId('publish-submit-btn')).toBeDisabled()

    await userEvent.type(version, '.0')
    expect(screen.getByTestId('publish-submit-btn')).not.toBeDisabled()
  })

  it('disables Publish when custom slug has invalid chars', async () => {
    render(
      <PublishModal
        projectName="p1"
        onClose={noop}
        onSuccess={noop}
        onUpsell={noop}
      />,
    )
    await userEvent.type(screen.getByLabelText('Changelog'), 'first release')
    const slug = screen.getByLabelText('Custom URL slug (optional)')
    await userEvent.type(slug, 'Bad Slug!')
    expect(screen.getByTestId('publish-submit-btn')).toBeDisabled()

    await userEvent.clear(slug)
    expect(screen.getByTestId('publish-submit-btn')).not.toBeDisabled()

    await userEvent.type(slug, 'good-slug')
    expect(screen.getByTestId('publish-submit-btn')).not.toBeDisabled()
  })

  it('disables Publish when custom slug is under the 3-char minimum', async () => {
    render(
      <PublishModal
        projectName="p1"
        onClose={noop}
        onSuccess={noop}
        onUpsell={noop}
      />,
    )
    await userEvent.type(screen.getByLabelText('Changelog'), 'ok')
    const slug = screen.getByLabelText('Custom URL slug (optional)')
    await userEvent.type(slug, 'ab')
    expect(screen.getByTestId('publish-submit-btn')).toBeDisabled()

    await userEvent.type(slug, 'c')
    expect(screen.getByTestId('publish-submit-btn')).not.toBeDisabled()
  })
})

describe('PublishModal — submit payload', () => {
  it('omits finalSlug from the POST body when the slug input is empty', async () => {
    mockApi.post.mockResolvedValueOnce({ slug: 'p1', publicUrl: '/worlds/p1' })
    const onSuccess = vi.fn()
    render(
      <PublishModal
        projectName="p1"
        onClose={noop}
        onSuccess={onSuccess}
        onUpsell={noop}
      />,
    )
    await userEvent.type(screen.getByLabelText('Changelog'), 'first release')
    await userEvent.click(screen.getByTestId('publish-submit-btn'))

    expect(mockApi.post).toHaveBeenCalledWith(
      '/projects/p1/publish',
      { version: '1.0.0', changelog: 'first release' },
    )
    expect(onSuccess).toHaveBeenCalledWith({ slug: 'p1', publicUrl: '/worlds/p1' })
  })

  it('includes finalSlug (trimmed) when provided', async () => {
    mockApi.post.mockResolvedValueOnce({ slug: 'custom', publicUrl: '/worlds/custom' })
    render(
      <PublishModal
        projectName="p1"
        onClose={noop}
        onSuccess={noop}
        onUpsell={noop}
      />,
    )
    await userEvent.type(screen.getByLabelText('Changelog'), 'first')
    await userEvent.type(screen.getByLabelText('Custom URL slug (optional)'), '  custom  ')
    // Surrounding spaces fail the regex, so submit stays disabled until the
    // user trims — which is what we want. Re-type cleanly:
    const slug = screen.getByLabelText('Custom URL slug (optional)')
    await userEvent.clear(slug)
    await userEvent.type(slug, 'custom')
    await userEvent.click(screen.getByTestId('publish-submit-btn'))

    expect(mockApi.post).toHaveBeenCalledWith(
      '/projects/p1/publish',
      { version: '1.0.0', changelog: 'first', finalSlug: 'custom' },
    )
  })

  it('forwards .body.upgradeUrl to onUpsell on 402', async () => {
    const err = new Error('Subscription required') as Error & {
      status?: number
      body?: Record<string, unknown>
    }
    err.status = 402
    err.body = { plan: 'FREE', upgradeUrl: '/account#subscription' }
    mockApi.post.mockRejectedValueOnce(err)

    const onUpsell = vi.fn()
    const onSuccess = vi.fn()
    render(
      <PublishModal
        projectName="p1"
        onClose={noop}
        onSuccess={onSuccess}
        onUpsell={onUpsell}
      />,
    )
    await userEvent.type(screen.getByLabelText('Changelog'), 'first')
    await userEvent.click(screen.getByTestId('publish-submit-btn'))

    expect(onUpsell).toHaveBeenCalledWith({
      plan: 'FREE',
      upgradeUrl: '/account#subscription',
    })
    expect(onSuccess).not.toHaveBeenCalled()
  })

  it('surfaces server error text inline (not via onUpsell) for 409', async () => {
    const err = new Error('Version already exists') as Error & {
      status?: number
      body?: { error?: string }
    }
    err.status = 409
    err.body = { error: 'Version 1.0.0 already exists for this world.' }
    mockApi.post.mockRejectedValueOnce(err)

    const onUpsell = vi.fn()
    const onSuccess = vi.fn()
    render(
      <PublishModal
        projectName="p1"
        onClose={noop}
        onSuccess={onSuccess}
        onUpsell={onUpsell}
      />,
    )
    await userEvent.type(screen.getByLabelText('Changelog'), 'first')
    await userEvent.click(screen.getByTestId('publish-submit-btn'))

    expect(screen.getByTestId('publish-error')).toHaveTextContent(/already exists/i)
    expect(onUpsell).not.toHaveBeenCalled()
    expect(onSuccess).not.toHaveBeenCalled()
  })
})
