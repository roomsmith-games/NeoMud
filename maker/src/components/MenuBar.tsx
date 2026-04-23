import { useEffect, useRef, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../api';
import ValidationModal from './ValidationModal';
import type { CSSProperties } from 'react';

type PlaytestStatus = 'idle' | 'starting' | 'active' | 'reloading' | 'error';

interface ActivePlaytest {
  worldId: string;
  slug: string;
  projectName: string;
  startedAt: number;
}

const styles: Record<string, CSSProperties> = {
  bar: {
    display: 'flex',
    alignItems: 'center',
    gap: 4,
    height: 32,
    backgroundColor: '#12122a',
    padding: '0 12px',
    borderBottom: '1px solid #2a2a4a',
  },
  btn: {
    background: 'none',
    border: 'none',
    color: '#b0b0cc',
    fontSize: 12,
    padding: '4px 10px',
    cursor: 'pointer',
    borderRadius: 3,
  },
  separator: {
    width: 1,
    height: 16,
    backgroundColor: '#2a2a4a',
    margin: '0 4px',
  },
  spacer: {
    flex: 1,
  },
  btnPlaytest: {
    background: 'linear-gradient(180deg, #3c5c2c, #2a4a1c)',
    border: '1px solid #4c6c3c',
    color: '#e0f0d0',
    fontSize: 12,
    padding: '4px 12px',
    cursor: 'pointer',
    borderRadius: 3,
    fontWeight: 600,
  },
  btnReload: {
    background: 'linear-gradient(180deg, #2c4c5c, #1c3a4a)',
    border: '1px solid #3c6c7c',
    color: '#d0e8f0',
    fontSize: 12,
    padding: '4px 12px',
    cursor: 'pointer',
    borderRadius: 3,
    fontWeight: 600,
  },
  btnDisabled: {
    opacity: 0.5,
    cursor: 'default',
  },
  statusBadge: {
    fontSize: 11,
    color: '#9090a0',
    marginLeft: 6,
    fontStyle: 'italic' as const,
  },
  statusBadgeError: {
    fontSize: 11,
    color: '#ff8080',
    marginLeft: 6,
  },
};

function MenuBar() {
  const { name } = useParams<{ name: string }>();
  const navigate = useNavigate();
  const [validation, setValidation] = useState<{ errors: string[]; warnings: string[]; actionLabel?: string; onAction?: () => void } | null>(null);
  const [playtestStatus, setPlaytestStatus] = useState<PlaytestStatus>('idle');
  const [playtestError, setPlaytestError] = useState<string | null>(null);
  const [activePlaytest, setActivePlaytest] = useState<ActivePlaytest | null>(null);
  const lastActiveRef = useRef<ActivePlaytest | null>(null);

  // Load current playtest state for this user on mount and whenever the
  // current project changes (the entry is user-scoped on the server so
  // switching projects doesn't necessarily clear it).
  useEffect(() => {
    let cancelled = false;
    if (!name) return;
    api
      .get<{ playtest: ActivePlaytest | null }>(`/projects/${encodeURIComponent(name)}/playtest`)
      .then((body) => {
        if (cancelled) return;
        setActivePlaytest(body.playtest);
        lastActiveRef.current = body.playtest;
        setPlaytestStatus(body.playtest ? 'active' : 'idle');
      })
      .catch(() => {
        if (cancelled) return;
        setActivePlaytest(null);
        setPlaytestStatus('idle');
      });
    return () => { cancelled = true };
  }, [name]);

  // Proactive stop on tab close / navigation away from the Maker
  // entirely. Uses fetch with keepalive so the Bearer header is sent;
  // reaper remains as fallback if the browser can't complete this in
  // time. We only fire when a playtest is active.
  useEffect(() => {
    const handler = () => {
      const active = lastActiveRef.current;
      if (!active || !name) return;
      const token = api.getToken();
      if (!token) return;
      try {
        fetch(api.assetUrl(`/projects/${encodeURIComponent(name)}/playtest/stop`), {
          method: 'POST',
          headers: { Authorization: `Bearer ${token}` },
          keepalive: true,
        }).catch(() => {});
      } catch { /* best-effort */ }
    };
    window.addEventListener('pagehide', handler);
    return () => window.removeEventListener('pagehide', handler);
  }, [name]);

  // Keep the ref in sync with state so the pagehide handler sees the
  // latest entry without re-binding the event listener every render.
  useEffect(() => { lastActiveRef.current = activePlaytest }, [activePlaytest]);

  const activeForThisProject = activePlaytest && activePlaytest.projectName === name;

  const handlePlaytest = async () => {
    if (!name) return;
    setPlaytestStatus('starting');
    setPlaytestError(null);
    try {
      const body = await api.post<{ worldId: string; slug: string; playPath: string }>(
        `/projects/${encodeURIComponent(name)}/playtest`,
      );
      const entry: ActivePlaytest = {
        worldId: body.worldId,
        slug: body.slug,
        projectName: name,
        startedAt: Date.now(),
      };
      setActivePlaytest(entry);
      setPlaytestStatus('active');
      window.open(body.playPath, '_blank', 'noopener,noreferrer');
    } catch (err) {
      setPlaytestError(err instanceof Error ? err.message : 'Playtest failed');
      setPlaytestStatus('error');
    }
  };

  const handleReload = async () => {
    if (!name) return;
    setPlaytestStatus('reloading');
    setPlaytestError(null);
    try {
      await api.post<{ worldId: string; slug: string; readyInMs: number }>(
        `/projects/${encodeURIComponent(name)}/playtest/reload`,
      );
      setPlaytestStatus('active');
    } catch (err) {
      setPlaytestError(err instanceof Error ? err.message : 'Reload failed');
      setPlaytestStatus('error');
    }
  };

  const handleSaveAs = async () => {
    const newName = prompt('Save project as:', `${name}_copy`);
    if (!newName || !newName.trim()) return;
    try {
      await api.post(`/projects/${encodeURIComponent(name!)}/fork`, {
        newName: newName.trim(),
      });
      await api.post(`/projects/${encodeURIComponent(newName.trim())}/open`);
      navigate(`/project/${encodeURIComponent(newName.trim())}/zones`);
    } catch (err: any) {
      alert(err.message || 'Save As failed');
    }
  };

  const handleSwitchProject = () => {
    navigate('/');
  };

  const handleQuit = async () => {
    if (!confirm('Shut down the Maker server?')) return;
    try {
      await api.post('/shutdown');
    } catch {
      // server is already gone
    }
  };

  const handleValidate = async () => {
    try {
      const result = await api.get<{ errors: string[]; warnings: string[] }>('/export/validate');
      setValidation(result);
    } catch (err: any) {
      setValidation({ errors: [err.message || 'Validation failed'], warnings: [] });
    }
  };

  const handleExportNmd = () => {
    const a = document.createElement('a');
    a.href = api.assetUrl('/export/nmd');
    a.click();
  };

  const doPackageDownload = () => {
    const a = document.createElement('a');
    a.href = api.assetUrl('/export/package');
    a.click();
  };

  const handlePackage = async () => {
    try {
      const result = await api.get<{ errors: string[]; warnings: string[] }>('/export/validate');
      if (result.errors.length > 0) {
        setValidation(result);
        return;
      }
      if (result.warnings.length > 0) {
        setValidation({
          ...result,
          actionLabel: 'Package Anyway',
          onAction: doPackageDownload,
        });
        return;
      }
      doPackageDownload();
    } catch (err: any) {
      setValidation({ errors: [err.message || 'Package failed'], warnings: [] });
    }
  };

  const hoverOn = (e: React.MouseEvent<HTMLButtonElement>) =>
    (e.currentTarget.style.backgroundColor = '#2a2a4a');
  const hoverOff = (e: React.MouseEvent<HTMLButtonElement>) =>
    (e.currentTarget.style.backgroundColor = 'transparent');

  return (
    <>
      <div style={styles.bar}>
        <button style={styles.btn} onClick={handleSaveAs} onMouseEnter={hoverOn} onMouseLeave={hoverOff}>
          Save As
        </button>
        <button style={styles.btn} onClick={handleSwitchProject} onMouseEnter={hoverOn} onMouseLeave={hoverOff}>
          Switch Project
        </button>
        <div style={styles.separator} />
        <button style={styles.btn} onClick={handleValidate} onMouseEnter={hoverOn} onMouseLeave={hoverOff}>
          Validate
        </button>
        <button style={styles.btn} onClick={handleExportNmd} onMouseEnter={hoverOn} onMouseLeave={hoverOff}>
          Export .nmd
        </button>
        <button style={styles.btn} onClick={handlePackage} onMouseEnter={hoverOn} onMouseLeave={hoverOff}>
          Package .nmd
        </button>
        <div style={styles.separator} />
        {activeForThisProject ? (
          <button
            style={{ ...styles.btnReload, ...(playtestStatus === 'reloading' ? styles.btnDisabled : {}) }}
            onClick={handleReload}
            disabled={playtestStatus === 'reloading'}
            data-testid="reload-playtest-btn"
          >
            {playtestStatus === 'reloading' ? 'Reloading…' : 'Reload Playtest'}
          </button>
        ) : (
          <button
            style={{ ...styles.btnPlaytest, ...(playtestStatus === 'starting' ? styles.btnDisabled : {}) }}
            onClick={handlePlaytest}
            disabled={playtestStatus === 'starting'}
            data-testid="playtest-btn"
          >
            {playtestStatus === 'starting' ? 'Starting…' : 'Playtest'}
          </button>
        )}
        {playtestStatus === 'error' && playtestError && (
          <span style={styles.statusBadgeError} data-testid="playtest-error">
            {playtestError}
          </span>
        )}
        {playtestStatus === 'active' && activeForThisProject && (
          <span style={styles.statusBadge} data-testid="playtest-active">
            Running
          </span>
        )}
        <div style={styles.spacer} />
        <button style={{ ...styles.btn, color: '#ff6b6b' }} onClick={handleQuit} onMouseEnter={hoverOn} onMouseLeave={hoverOff}>
          Quit Server
        </button>
      </div>
      {validation && (
        <ValidationModal
          errors={validation.errors}
          warnings={validation.warnings}
          onClose={() => setValidation(null)}
          actionLabel={validation.actionLabel}
          onAction={validation.onAction}
        />
      )}
    </>
  );
}

export default MenuBar;
