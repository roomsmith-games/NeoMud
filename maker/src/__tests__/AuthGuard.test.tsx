// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach, afterEach } from 'vitest'
import AuthGuard from '../components/AuthGuard'

const TOKEN_KEY = 'neomud_access_token'

beforeEach(() => {
  localStorage.removeItem(TOKEN_KEY)
})

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('AuthGuard', () => {
  it('renders children when a token is present', () => {
    localStorage.setItem(TOKEN_KEY, 'abc123')
    render(
      <AuthGuard>
        <div data-testid="protected">secret</div>
      </AuthGuard>
    )
    expect(screen.getByTestId('protected')).toBeInTheDocument()
  })

  it('renders the platform sign-in card when no token', () => {
    render(
      <AuthGuard>
        <div data-testid="protected">secret</div>
      </AuthGuard>
    )
    expect(screen.queryByTestId('protected')).not.toBeInTheDocument()
    expect(screen.getByText(/must sign in on the NeoMud Platform/i)).toBeInTheDocument()
  })

  it('links the Sign in button at the platform root (no preventDefault)', () => {
    render(<AuthGuard>x</AuthGuard>)
    const link = screen.getByRole('link', { name: /sign in with neomud platform/i })
    expect(link).toHaveAttribute('href', '/')
    // If an onClick preventDefault existed, the default-navigation MouseEvent
    // would be cancelled. Simulate a click and verify the event is allowed.
    const clickEvent = new MouseEvent('click', { bubbles: true, cancelable: true })
    link.dispatchEvent(clickEvent)
    expect(clickEvent.defaultPrevented).toBe(false)
  })

  it('hides the dev paste-in when build is not DEV', () => {
    vi.stubEnv('DEV', false)
    render(<AuthGuard>x</AuthGuard>)
    expect(screen.queryByTestId('dev-token-input')).not.toBeInTheDocument()
  })

  it('shows the dev paste-in when build is DEV', () => {
    vi.stubEnv('DEV', true)
    render(<AuthGuard>x</AuthGuard>)
    expect(screen.getByTestId('dev-token-input')).toBeInTheDocument()
  })

  it('dev paste-in stores token and reloads', async () => {
    vi.stubEnv('DEV', true)
    const reloadSpy = vi.fn()
    Object.defineProperty(window, 'location', {
      value: { ...window.location, reload: reloadSpy },
      writable: true,
    })
    render(<AuthGuard>x</AuthGuard>)
    const input = screen.getByPlaceholderText(/Paste Platform JWT/i)
    await userEvent.type(input, 'tok-from-paste')
    await userEvent.click(screen.getByRole('button', { name: /set token/i }))
    expect(localStorage.getItem(TOKEN_KEY)).toBe('tok-from-paste')
    expect(reloadSpy).toHaveBeenCalled()
  })
})
