import { useEffect, useState } from 'react';
import api from '../api';
import EditorPageHeader from '../components/EditorPageHeader';
import type { CSSProperties } from 'react';

type WorldMeta = Record<string, string>;

const styles: Record<string, CSSProperties> = {
  page: { padding: 32, maxWidth: 720 },
  title: { fontSize: 22, fontWeight: 700, color: '#1a1a2e', marginBottom: 8 },
  subtitle: { fontSize: 13, color: '#666', marginBottom: 24 },
  card: {
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e0e0e0',
    padding: 20,
    marginBottom: 20,
  },
  sectionTitle: { fontSize: 16, fontWeight: 600, color: '#1a1a2e', marginBottom: 6 },
  sectionHint: { fontSize: 12, color: '#666', marginBottom: 12 },
  label: { display: 'block', fontSize: 12, fontWeight: 600, color: '#444', marginBottom: 4, marginTop: 8 },
  input: {
    width: '100%',
    padding: '8px 10px',
    fontSize: 13,
    border: '1px solid #ccc',
    borderRadius: 4,
    boxSizing: 'border-box' as const,
  },
  textarea: {
    width: '100%',
    padding: '8px 10px',
    fontSize: 13,
    border: '1px solid #ccc',
    borderRadius: 4,
    minHeight: 180,
    resize: 'vertical' as const,
    boxSizing: 'border-box' as const,
    fontFamily: 'inherit',
    lineHeight: 1.5,
  },
  actions: { display: 'flex', gap: 8, marginTop: 16 },
  btn: {
    padding: '8px 16px',
    fontSize: 13,
    fontWeight: 600,
    backgroundColor: '#1a1a2e',
    color: '#fff',
    border: 'none',
    borderRadius: 5,
    cursor: 'pointer',
  },
  status: { fontSize: 13, marginLeft: 12, color: '#4caf50', alignSelf: 'center' },
  err: { color: '#d32f2f', fontSize: 12, marginTop: 6 },
};

function WorldMetaEditor() {
  const [meta, setMeta] = useState<WorldMeta | null>(null);
  const [saveStatus, setSaveStatus] = useState('');
  const [error, setError] = useState('');

  useEffect(() => {
    api.get<WorldMeta>('/world-meta')
      .then((d) => setMeta(d ?? {}))
      .catch((e: Error) => setError(e.message || 'Failed to load world metadata'));
  }, []);

  const update = (key: string, value: string) => {
    if (!meta) return;
    setMeta({ ...meta, [key]: value });
  };

  const save = async () => {
    if (!meta) return;
    setError('');
    try {
      await api.put('/world-meta', meta);
      setSaveStatus('Saved');
      setTimeout(() => setSaveStatus(''), 2000);
    } catch (e: any) {
      setError(e.message || 'Save failed');
    }
  };

  if (!meta) {
    return (
      <div style={styles.page}>
        <h2 style={styles.title}>World Manifest</h2>
        {error && <p style={styles.err}>{error}</p>}
      </div>
    );
  }

  return (
    <form style={styles.page} onSubmit={(e) => { e.preventDefault(); save(); }}>
      <h2 style={styles.title}>World Manifest</h2>
      <EditorPageHeader storageKey="world-meta">
        These fields define your world's identity — they appear in the marketplace listing and ship
        inside every .nmd bundle. The intro script is shown as a lore modal on each player's first login.
      </EditorPageHeader>

      <div style={styles.card}>
        <div style={styles.sectionTitle}>Identity</div>
        <div style={styles.sectionHint}>Used on the project list and bundle metadata.</div>
        <label style={styles.label}>World Name</label>
        <input style={styles.input} type="text" value={meta.name ?? ''} onChange={(e) => update('name', e.target.value)} />
        <label style={styles.label}>Author</label>
        <input style={styles.input} type="text" value={meta.author ?? ''} onChange={(e) => update('author', e.target.value)} />
        <label style={styles.label}>Description</label>
        <input style={styles.input} type="text" value={meta.description ?? ''} onChange={(e) => update('description', e.target.value)} />
        <label style={styles.label}>Version</label>
        <input style={styles.input} type="text" value={meta.version ?? ''} onChange={(e) => update('version', e.target.value)} />
      </div>

      <div style={styles.card}>
        <div style={styles.sectionTitle}>Intro Script</div>
        <div style={styles.sectionHint}>
          Optional lore/setup shown as a blocking modal on every player's first login to this world (#272). Leave blank to skip.
          Plain text with paragraph breaks.
        </div>
        <textarea
          style={styles.textarea}
          value={meta.introScript ?? ''}
          onChange={(e) => update('introScript', e.target.value)}
          placeholder="A thousand years ago, the world cracked..."
        />
      </div>

      <div style={styles.actions}>
        <button style={styles.btn} type="submit">Save</button>
        {saveStatus && <span style={styles.status}>{saveStatus}</span>}
      </div>
      {error && <p style={styles.err}>{error}</p>}
    </form>
  );
}

export default WorldMetaEditor;
