import { Component, type ReactNode, type ErrorInfo, type CSSProperties } from 'react'

interface Props {
  children: ReactNode
  /** Shown in the fallback panel so users can tell which editor crashed. */
  label?: string
}

interface State {
  error: Error | null
}

const styles: Record<string, CSSProperties> = {
  panel: {
    margin: '24px',
    padding: '20px 24px',
    backgroundColor: '#2a1e1e',
    border: '1px solid #6b2e2e',
    borderRadius: 6,
    color: '#f0d0d0',
    fontFamily: 'inherit',
  },
  heading: { fontSize: 15, fontWeight: 600, marginBottom: 10, color: '#ff8080' },
  message: { fontSize: 13, marginBottom: 8, fontFamily: 'monospace' },
  stack: { fontSize: 11, whiteSpace: 'pre-wrap', color: '#c0a0a0', maxHeight: 200, overflow: 'auto' },
  retry: {
    marginTop: 12,
    padding: '6px 14px',
    borderRadius: 4,
    border: 'none',
    backgroundColor: '#3949ab',
    color: '#fff',
    fontSize: 13,
    cursor: 'pointer',
  },
}

/**
 * Wraps a subtree so a render-time crash yields a diagnostic panel
 * instead of an empty <div> + opaque console error. Added for Phase 6E
 * after empty-project Zones view crashed with a minified stack trace
 * (`c0 at index-...js:20:1856`) that pointed nowhere useful.
 *
 * Any error that bubbles here is also logged to console.error so our
 * request middleware / Better Stack pipeline can surface it.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null }

  static getDerivedStateFromError(error: Error): State {
    return { error }
  }

  componentDidCatch(error: Error, info: ErrorInfo): void {
    // eslint-disable-next-line no-console
    console.error(`[ErrorBoundary${this.props.label ? ': ' + this.props.label : ''}]`, error, info.componentStack)
  }

  private handleRetry = () => {
    this.setState({ error: null })
  }

  render(): ReactNode {
    if (!this.state.error) return this.props.children
    return (
      <div style={styles.panel} role="alert">
        <div style={styles.heading}>
          {this.props.label ? `${this.props.label} crashed` : 'Something went wrong'}
        </div>
        <div style={styles.message}>{this.state.error.message}</div>
        {this.state.error.stack && (
          <div style={styles.stack}>{this.state.error.stack}</div>
        )}
        <button style={styles.retry} onClick={this.handleRetry}>Retry</button>
      </div>
    )
  }
}
