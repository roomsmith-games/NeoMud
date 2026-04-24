// @vitest-environment jsdom
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, it, expect, vi, afterEach } from 'vitest'
import { ErrorBoundary } from '../components/ErrorBoundary'

// A component that throws on render, used to trigger the boundary.
function Bomb({ message }: { message: string }): never {
  throw new Error(message)
}

function GoodKid() {
  return <div data-testid="good">hello</div>
}

// React logs caught errors to console.error — we silence it in tests so
// the boundary's own log isn't interleaved with vitest's output.
afterEach(() => {
  vi.restoreAllMocks()
})

describe('ErrorBoundary', () => {
  it('renders children when no error is thrown', () => {
    render(
      <ErrorBoundary label="TestEditor">
        <GoodKid />
      </ErrorBoundary>,
    )
    expect(screen.getByTestId('good')).toBeInTheDocument()
  })

  it('catches a render-time error and shows the fallback panel', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    render(
      <ErrorBoundary label="Zones">
        <Bomb message="Cannot read properties of undefined (reading 'find')" />
      </ErrorBoundary>,
    )
    const panel = screen.getByRole('alert')
    expect(panel).toBeInTheDocument()
    expect(panel.textContent).toContain('Zones crashed')
    expect(panel.textContent).toContain('Cannot read properties of undefined')
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
  })

  it('Retry clears the error and re-renders children', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    let shouldThrow = true
    function MaybeBomb() {
      if (shouldThrow) throw new Error('first render fails')
      return <div data-testid="recovered">ok now</div>
    }
    render(
      <ErrorBoundary label="Zones">
        <MaybeBomb />
      </ErrorBoundary>,
    )
    expect(screen.getByText(/Zones crashed/)).toBeInTheDocument()

    // Simulate the underlying condition resolving (e.g. a data fetch
    // that failed now succeeds on the next attempt).
    shouldThrow = false
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }))
    expect(screen.getByTestId('recovered')).toBeInTheDocument()
  })

  it('falls back to generic heading when no label is provided', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    render(
      <ErrorBoundary>
        <Bomb message="boom" />
      </ErrorBoundary>,
    )
    expect(screen.getByText('Something went wrong')).toBeInTheDocument()
  })
})
