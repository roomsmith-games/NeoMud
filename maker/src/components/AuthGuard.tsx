import { type ReactNode, useState, useEffect } from 'react';
import api, { PLATFORM_URL } from '../api';
import type { CSSProperties } from 'react';

const styles: Record<string, CSSProperties> = {
  page: {
    minHeight: '100vh',
    backgroundColor: '#f5f5f5',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
  },
  card: {
    width: 400,
    padding: '40px 32px',
    backgroundColor: '#fff',
    borderRadius: 12,
    boxShadow: '0 2px 12px rgba(0,0,0,0.08)',
    textAlign: 'center',
  },
  title: {
    fontSize: 22,
    fontWeight: 700,
    color: '#1a1a2e',
    marginBottom: 12,
  },
  message: {
    fontSize: 14,
    color: '#666',
    marginBottom: 24,
    lineHeight: 1.5,
  },
  link: {
    display: 'inline-block',
    padding: '12px 28px',
    fontSize: 14,
    fontWeight: 600,
    backgroundColor: '#1a1a2e',
    color: '#fff',
    borderRadius: 6,
    textDecoration: 'none',
    cursor: 'pointer',
  },
  devInput: {
    marginTop: 24,
    paddingTop: 24,
    borderTop: '1px solid #eee',
  },
  tokenInput: {
    width: '100%',
    padding: '10px 14px',
    fontSize: 13,
    border: '1px solid #ddd',
    borderRadius: 6,
    marginBottom: 8,
    boxSizing: 'border-box',
  },
  devButton: {
    padding: '8px 16px',
    fontSize: 13,
    fontWeight: 600,
    backgroundColor: '#3949ab',
    color: '#fff',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
  },
};

interface AuthGuardProps {
  children: ReactNode;
}

/**
 * Wraps the app and checks for a Platform JWT in localStorage.
 * Shows a sign-in prompt if not authenticated, with a link back to the
 * platform root. In dev builds, also renders a paste-in token field.
 */
export default function AuthGuard({ children }: AuthGuardProps) {
  const [authMode, setAuthMode] = useState<'jwt' | 'open' | 'loading'>('loading');

  useEffect(() => {
    if (api.isAuthenticated()) {
      setAuthMode('jwt');
      return;
    }
    fetch(`${import.meta.env.VITE_API_BASE ?? ''}/api/auth/mode`)
      .then((r) => r.json())
      .then((data) => setAuthMode(data.mode === 'open' ? 'open' : 'jwt'))
      .catch(() => setAuthMode('jwt'));
  }, []);

  if (authMode === 'loading') return null;
  if (authMode === 'open' || api.isAuthenticated()) {
    return <>{children}</>;
  }

  const handleDevToken = () => {
    const token = (document.getElementById('dev-token') as HTMLInputElement)?.value?.trim();
    if (token) {
      api.setToken(token);
      window.location.reload();
    }
  };

  return (
    <div style={styles.page}>
      <div style={styles.card}>
        <h1 style={styles.title}>NeoMUD Maker</h1>
        <p style={styles.message}>
          You must sign in on the NeoMud Platform to build worlds.
        </p>
        <a style={styles.link} href={PLATFORM_URL}>
          Sign in with NeoMud Platform
        </a>
        {import.meta.env.DEV && (
          <div style={styles.devInput} data-testid="dev-token-input">
            <p style={{ fontSize: 12, color: '#999', marginBottom: 8 }}>
              Development: paste a JWT token
            </p>
            <input
              id="dev-token"
              style={styles.tokenInput}
              type="text"
              placeholder="Paste Platform JWT..."
              onKeyDown={(e) => e.key === 'Enter' && handleDevToken()}
            />
            <button style={styles.devButton} onClick={handleDevToken}>
              Set Token
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
