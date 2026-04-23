// @vitest-environment jsdom
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest'
import { MemoryRouter, Routes, Route } from 'react-router-dom'

vi.mock('../api', () => {
  return {
    default: {
      get: vi.fn().mockResolvedValue({ playtest: null }),
      post: vi.fn().mockResolvedValue({}),
      put: vi.fn().mockResolvedValue({}),
      del: vi.fn().mockResolvedValue({}),
      assetUrl: vi.fn((p: string) => p),
      getToken: vi.fn(() => 'test-bearer-token'),
    },
  }
})

import api from '../api'
import MenuBar from '../components/MenuBar'

const mockApi = vi.mocked(api)

/** Render MenuBar inside a route with :name so useParams sees the project. */
function renderAt(projectName = 'my-world') {
  return render(
    <MemoryRouter initialEntries={[`/project/${projectName}`]}>
      <Routes>
        <Route path="/project/:name" element={<MenuBar />} />
      </Routes>
    </MemoryRouter>
  )
}

describe('MenuBar — Playtest / Reload buttons', () => {
  const originalOpen = window.open

  beforeEach(() => {
    vi.clearAllMocks()
    mockApi.get.mockResolvedValue({ playtest: null })
    // Default: token is present so pagehide handler would run.
    mockApi.getToken.mockReturnValue('test-bearer-token')
    // Stub window.open so Playtest-click assertions work in jsdom.
    window.open = vi.fn() as unknown as typeof window.open
  })

  afterEach(() => {
    window.open = originalOpen
    vi.unstubAllGlobals()
  })

  it('on mount, fetches current playtest state', async () => {
    renderAt('my-world')
    await waitFor(() => {
      expect(mockApi.get).toHaveBeenCalledWith('/projects/my-world/playtest')
    })
  })

  it('shows Playtest button when nothing is active', async () => {
    renderAt('my-world')
    await waitFor(() => {
      expect(screen.getByTestId('playtest-btn')).toHaveTextContent(/Playtest/i)
      expect(screen.queryByTestId('reload-playtest-btn')).not.toBeInTheDocument()
    })
  })

  it('shows Reload button when an active playtest matches the current project', async () => {
    mockApi.get.mockResolvedValueOnce({
      playtest: {
        worldId: 'world-xyz',
        slug: 'draft-u-my-world',
        projectName: 'my-world',
        startedAt: 1000,
      },
    })
    renderAt('my-world')
    await waitFor(() => {
      expect(screen.getByTestId('reload-playtest-btn')).toHaveTextContent(/Reload Playtest/i)
      expect(screen.queryByTestId('playtest-btn')).not.toBeInTheDocument()
    })
  })

  it('shows Playtest (not Reload) when active playtest is for a different project', async () => {
    mockApi.get.mockResolvedValueOnce({
      playtest: {
        worldId: 'world-xyz',
        slug: 'draft-u-other',
        projectName: 'other-project',
        startedAt: 1000,
      },
    })
    renderAt('my-world')
    await waitFor(() => {
      expect(screen.getByTestId('playtest-btn')).toBeInTheDocument()
      expect(screen.queryByTestId('reload-playtest-btn')).not.toBeInTheDocument()
    })
  })

  it('clicking Playtest posts, opens playPath in a new tab, transitions to active', async () => {
    mockApi.post.mockResolvedValueOnce({
      worldId: 'world-xyz',
      slug: 'draft-u-my-world',
      playPath: '/play/draft-u-my-world',
    })
    renderAt('my-world')
    const btn = await screen.findByTestId('playtest-btn')
    await userEvent.click(btn)

    await waitFor(() => {
      expect(mockApi.post).toHaveBeenCalledWith('/projects/my-world/playtest')
      expect(window.open).toHaveBeenCalledWith('/play/draft-u-my-world', '_blank', 'noopener,noreferrer')
      expect(screen.getByTestId('playtest-active')).toBeInTheDocument()
    })
  })

  it('after a successful Playtest click, swaps to a Reload button', async () => {
    mockApi.post.mockResolvedValueOnce({
      worldId: 'world-xyz',
      slug: 'draft-u-my-world',
      playPath: '/play/draft-u-my-world',
    })
    renderAt('my-world')
    await userEvent.click(await screen.findByTestId('playtest-btn'))
    await waitFor(() => {
      expect(screen.getByTestId('reload-playtest-btn')).toBeInTheDocument()
    })
  })

  it('shows an error badge when Playtest fails', async () => {
    mockApi.post.mockRejectedValueOnce(new Error('platform unreachable'))
    renderAt('my-world')
    await userEvent.click(await screen.findByTestId('playtest-btn'))
    await waitFor(() => {
      expect(screen.getByTestId('playtest-error')).toHaveTextContent('platform unreachable')
    })
    expect(window.open).not.toHaveBeenCalled()
  })

  it('clicking Reload posts to /playtest/reload and keeps the entry active', async () => {
    mockApi.get.mockResolvedValueOnce({
      playtest: {
        worldId: 'world-xyz',
        slug: 'draft-u-my-world',
        projectName: 'my-world',
        startedAt: 1000,
      },
    })
    mockApi.post.mockResolvedValueOnce({ worldId: 'world-xyz', slug: 'draft-u-my-world', readyInMs: 1234 })
    renderAt('my-world')
    const btn = await screen.findByTestId('reload-playtest-btn')
    await userEvent.click(btn)
    await waitFor(() => {
      expect(mockApi.post).toHaveBeenCalledWith('/projects/my-world/playtest/reload')
      expect(screen.getByTestId('reload-playtest-btn')).toBeInTheDocument()
    })
  })

  it('pagehide triggers a keepalive fetch to /playtest/stop when a playtest is active', async () => {
    mockApi.get.mockResolvedValueOnce({
      playtest: {
        worldId: 'world-xyz',
        slug: 'draft-u-my-world',
        projectName: 'my-world',
        startedAt: 1000,
      },
    })
    const fetchSpy = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchSpy)

    renderAt('my-world')
    // Wait for the initial GET to populate state before firing pagehide.
    await waitFor(() => expect(screen.getByTestId('reload-playtest-btn')).toBeInTheDocument())

    window.dispatchEvent(new Event('pagehide'))

    expect(fetchSpy).toHaveBeenCalledWith(
      '/projects/my-world/playtest/stop',
      expect.objectContaining({
        method: 'POST',
        keepalive: true,
        headers: expect.objectContaining({ Authorization: 'Bearer test-bearer-token' }),
      })
    )
  })

  it('pagehide is a no-op when no playtest is active', async () => {
    const fetchSpy = vi.fn().mockResolvedValue(new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchSpy)

    renderAt('my-world')
    await waitFor(() => expect(screen.getByTestId('playtest-btn')).toBeInTheDocument())

    window.dispatchEvent(new Event('pagehide'))
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
