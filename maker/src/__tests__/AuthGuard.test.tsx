// @vitest-environment jsdom
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest'
import AuthGuard from '../components/AuthGuard'

const TOKEN_KEY = 'neomud_access_token'

beforeEach(() => {
  localStorage.removeItem(TOKEN_KEY)
  // Default: mock /api/auth/mode returning jwt (auth required)
  vi.spyOn(globalThis, 'fetch').mockResolvedValue(
    new Response(JSON.stringify({ mode: 'jwt' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
  )
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.unstubAllEnvs()
})

describe('AuthGuard', () => {
  it('renders children when a token is present', async () => {
    localStorage.setItem(TOKEN_KEY, 'abc123')
    render(
      <AuthGuard>
        <div data-testid="protected">secret</div>
      </AuthGuard>
    )
    await waitFor(() => expect(screen.getByTestId('protected')).toBeInTheDocument())
  })

  it('renders the platform sign-in card when no token', async () => {
    render(
      <AuthGuard>
        <div data-testid="protected">secret</div>
      </AuthGuard>
    )
    await waitFor(() => expect(screen.getByText(/must sign in on the NeoMud Platform/i)).toBeInTheDocument())
    expect(screen.queryByTestId('protected')).not.toBeInTheDocument()
  })

  it('links the Sign in button at the platform root (no preventDefault)', async () => {
    render(<AuthGuard>x</AuthGuard>)
    await waitFor(() => expect(screen.getByRole('link', { name: /sign in with neomud platform/i })).toBeInTheDocument())
    const link = screen.getByRole('link', { name: /sign in with neomud platform/i })
    expect(link).toHaveAttribute('href', '/')
    const clickEvent = new MouseEvent('click', { bubbles: true, cancelable: true })
    link.dispatchEvent(clickEvent)
    expect(clickEvent.defaultPrevented).toBe(false)
  })

  it('uses VITE_PLATFORM_URL for sign-in link when set', async () => {
    vi.stubEnv('VITE_PLATFORM_URL', 'https://stage.neomud.app')
    vi.resetModules()
    const { default: AuthGuardFresh } = await import('../components/AuthGuard')
    render(<AuthGuardFresh>x</AuthGuardFresh>)
    await waitFor(() => expect(screen.getByRole('link', { name: /sign in with neomud platform/i })).toBeInTheDocument())
    const link = screen.getByRole('link', { name: /sign in with neomud platform/i })
    expect(link).toHaveAttribute('href', 'https://stage.neomud.app')
  })

  it('hides the dev paste-in when build is not DEV', async () => {
    vi.stubEnv('DEV', false)
    render(<AuthGuard>x</AuthGuard>)
    await waitFor(() => expect(screen.getByText(/must sign in/i)).toBeInTheDocument())
    expect(screen.queryByTestId('dev-token-input')).not.toBeInTheDocument()
  })

  it('shows the dev paste-in when build is DEV', async () => {
    vi.stubEnv('DEV', true)
    render(<AuthGuard>x</AuthGuard>)
    await waitFor(() => expect(screen.getByTestId('dev-token-input')).toBeInTheDocument())
  })

  it('dev paste-in stores token and reloads', async () => {
    vi.stubEnv('DEV', true)
    const reloadSpy = vi.fn()
    Object.defineProperty(window, 'location', {
      value: { ...window.location, reload: reloadSpy },
      writable: true,
    })
    render(<AuthGuard>x</AuthGuard>)
    await waitFor(() => expect(screen.getByPlaceholderText(/Paste Platform JWT/i)).toBeInTheDocument())
    const input = screen.getByPlaceholderText(/Paste Platform JWT/i)
    await userEvent.type(input, 'tok-from-paste')
    await userEvent.click(screen.getByRole('button', { name: /set token/i }))
    expect(localStorage.getItem(TOKEN_KEY)).toBe('tok-from-paste')
    expect(reloadSpy).toHaveBeenCalled()
  })

  it('renders children in open mode without a token', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(JSON.stringify({ mode: 'open' }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    )
    render(
      <AuthGuard>
        <div data-testid="protected">secret</div>
      </AuthGuard>
    )
    await waitFor(() => expect(screen.getByTestId('protected')).toBeInTheDocument())
  })
})
