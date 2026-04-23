import { useState, type CSSProperties } from 'react';
import api from '../api';

export interface PublishSuccess {
  slug: string;
  publicUrl: string;
}

export interface PublishUpsell {
  plan: string;
  upgradeUrl: string;
}

interface PublishModalProps {
  projectName: string;
  onClose: () => void;
  onSuccess: (result: PublishSuccess) => void;
  onUpsell: (info: PublishUpsell) => void;
}

const styles: Record<string, CSSProperties> = {
  overlay: {
    position: 'fixed',
    inset: 0,
    backgroundColor: 'rgba(0,0,0,0.6)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 9999,
  },
  modal: {
    backgroundColor: '#1a1a2e',
    color: '#e0e0e0',
    borderRadius: 8,
    padding: '24px 28px',
    minWidth: 480,
    maxWidth: 600,
    boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
    display: 'flex',
    flexDirection: 'column',
    gap: 14,
  },
  title: { fontSize: 16, fontWeight: 700 },
  field: { display: 'flex', flexDirection: 'column', gap: 4 },
  label: { fontSize: 12, color: '#b0b0cc', fontWeight: 600 },
  hint: { fontSize: 11, color: '#8a8aa5' },
  input: {
    backgroundColor: '#0d0d1e',
    border: '1px solid #2a2a4a',
    color: '#e0e0e0',
    fontSize: 13,
    padding: '6px 10px',
    borderRadius: 4,
    fontFamily: 'inherit',
  },
  textarea: {
    backgroundColor: '#0d0d1e',
    border: '1px solid #2a2a4a',
    color: '#e0e0e0',
    fontSize: 13,
    padding: '6px 10px',
    borderRadius: 4,
    fontFamily: 'inherit',
    minHeight: 80,
    resize: 'vertical',
  },
  error: { color: '#ef5350', fontSize: 12, marginTop: 4 },
  actions: { display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 4 },
  btn: {
    padding: '6px 16px',
    borderRadius: 4,
    border: 'none',
    fontSize: 13,
    cursor: 'pointer',
  },
  btnPrimary: {
    backgroundColor: '#3949ab',
    color: '#fff',
  },
  btnSecondary: {
    backgroundColor: '#2a2a4a',
    color: '#b0b0cc',
  },
  btnDisabled: {
    opacity: 0.5,
    cursor: 'not-allowed',
  },
};

const SEMVER_RE = /^\d+\.\d+\.\d+$/;
const SLUG_RE = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;

function PublishModal({ projectName, onClose, onSuccess, onUpsell }: PublishModalProps) {
  const [version, setVersion] = useState('1.0.0');
  const [changelog, setChangelog] = useState('');
  const [finalSlug, setFinalSlug] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const canSubmit =
    !submitting &&
    SEMVER_RE.test(version) &&
    changelog.trim().length > 0 &&
    (finalSlug === '' || (SLUG_RE.test(finalSlug) && finalSlug.length >= 3 && finalSlug.length <= 50));

  const handleSubmit = async () => {
    setSubmitting(true);
    setError(null);
    try {
      const body: Record<string, string> = { version, changelog: changelog.trim() };
      if (finalSlug.trim()) body.finalSlug = finalSlug.trim();
      const result = await api.post<{ slug: string; publicUrl: string }>(
        `/projects/${encodeURIComponent(projectName)}/publish`,
        body,
      );
      onSuccess({ slug: result.slug, publicUrl: result.publicUrl });
    } catch (err) {
      // api.post throws Error with .status + .body (via api.ts error handler).
      // 402 Payment Required → bubble up to upsell modal.
      const apiErr = err as { status?: number; body?: { plan?: string; upgradeUrl?: string; error?: string } };
      if (apiErr.status === 402 && apiErr.body?.upgradeUrl) {
        onUpsell({
          plan: apiErr.body.plan ?? 'FREE',
          upgradeUrl: apiErr.body.upgradeUrl,
        });
        return;
      }
      setError(apiErr.body?.error ?? (err instanceof Error ? err.message : 'Publish failed'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={styles.overlay} onClick={onClose}>
      <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div style={styles.title}>Publish "{projectName}" to Marketplace</div>

        <div style={styles.field}>
          <label style={styles.label} htmlFor="pub-version">Version</label>
          <input
            id="pub-version"
            style={styles.input}
            value={version}
            onChange={(e) => setVersion(e.target.value)}
            placeholder="1.0.0"
            disabled={submitting}
          />
          <div style={styles.hint}>Semantic version (e.g. 1.0.0, 1.2.3)</div>
        </div>

        <div style={styles.field}>
          <label style={styles.label} htmlFor="pub-changelog">Changelog</label>
          <textarea
            id="pub-changelog"
            style={styles.textarea}
            value={changelog}
            onChange={(e) => setChangelog(e.target.value)}
            placeholder="Describe what's in this release…"
            disabled={submitting}
            maxLength={5000}
          />
        </div>

        <div style={styles.field}>
          <label style={styles.label} htmlFor="pub-slug">Custom URL slug (optional)</label>
          <input
            id="pub-slug"
            style={styles.input}
            value={finalSlug}
            onChange={(e) => setFinalSlug(e.target.value)}
            placeholder="auto-derived from project name"
            disabled={submitting}
          />
          <div style={styles.hint}>Lowercase letters, numbers, hyphens. 3–50 chars.</div>
        </div>

        {error && <div style={styles.error} data-testid="publish-error">{error}</div>}

        <div style={styles.actions}>
          <button
            style={{ ...styles.btn, ...styles.btnSecondary }}
            onClick={onClose}
            disabled={submitting}
          >
            Cancel
          </button>
          <button
            style={{
              ...styles.btn,
              ...styles.btnPrimary,
              ...(canSubmit ? {} : styles.btnDisabled),
            }}
            onClick={handleSubmit}
            disabled={!canSubmit}
            data-testid="publish-submit-btn"
          >
            {submitting ? 'Publishing…' : 'Publish'}
          </button>
        </div>
      </div>
    </div>
  );
}

export default PublishModal;
