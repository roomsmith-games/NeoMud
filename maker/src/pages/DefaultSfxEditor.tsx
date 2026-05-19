import { useEffect, useState } from 'react';
import api from '../api';
import SfxPreview from '../components/SfxPreview';
import EditorPageHeader from '../components/EditorPageHeader';
import type { CSSProperties } from 'react';

interface DefaultSfx {
  id: string;
  label: string;
  category: string;
  description: string;
  prompt: string;
  duration: number;
}

const CATEGORIES = ['combat', 'loot', 'item', 'magic', 'movement'];

function titleCase(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
}

const categoryColors: Record<string, { bg: string; fg: string }> = {
  combat: { bg: '#ffebee', fg: '#c62828' },
  loot: { bg: '#fff8e1', fg: '#f57f17' },
  item: { bg: '#e8f5e9', fg: '#2e7d32' },
  magic: { bg: '#f3e5f5', fg: '#7b1fa2' },
  movement: { bg: '#e3f2fd', fg: '#1565c0' },
};

const styles: Record<string, CSSProperties> = {
  container: {
    display: 'flex',
    height: '100%',
    overflow: 'hidden',
  },
  listPanel: {
    width: 280,
    minWidth: 280,
    borderRight: '1px solid #ddd',
    backgroundColor: '#fff',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  listTop: {
    padding: 12,
    borderBottom: '1px solid #eee',
  },
  select: {
    width: '100%',
    padding: '5px 6px',
    fontSize: 12,
    border: '1px solid #ccc',
    borderRadius: 3,
    backgroundColor: '#fff',
  },
  listCount: {
    fontSize: 11,
    color: '#999',
    textAlign: 'center',
    padding: '4px 0',
    borderBottom: '1px solid #f0f0f0',
  },
  listItems: {
    flex: 1,
    overflowY: 'auto',
    padding: '2px 0',
  },
  listItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '8px 12px',
    cursor: 'pointer',
    fontSize: 13,
    borderBottom: '1px solid #f5f5f5',
  },
  listItemSelected: {
    backgroundColor: '#e8eaf6',
    fontWeight: 600,
  },
  statusDot: {
    width: 8,
    height: 8,
    borderRadius: '50%',
    flexShrink: 0,
  },
  categoryTag: {
    fontSize: 10,
    fontWeight: 600,
    padding: '1px 5px',
    borderRadius: 3,
    flexShrink: 0,
    marginLeft: 'auto',
  },
  detailPanel: {
    flex: 1,
    padding: 24,
    overflowY: 'auto',
    backgroundColor: '#fafafa',
  },
  detailTitle: {
    fontSize: 20,
    fontWeight: 700,
    color: '#1a1a2e',
    marginBottom: 4,
  },
  detailSubtitle: {
    fontSize: 13,
    color: '#666',
    marginBottom: 4,
  },
  detailDescription: {
    fontSize: 13,
    color: '#555',
    marginBottom: 20,
    fontStyle: 'italic',
  },
  btnRow: {
    display: 'flex',
    gap: 6,
    marginTop: 8,
  },
  btnOutline: {
    padding: '5px 12px',
    fontSize: 11,
    fontWeight: 600,
    backgroundColor: '#fff',
    color: '#1a1a2e',
    border: '1px solid #1a1a2e',
    borderRadius: 3,
    cursor: 'pointer',
  },
  empty: {
    color: '#999',
    fontSize: 13,
    textAlign: 'center',
    marginTop: 60,
  },
  sfxSection: {
    marginTop: 20,
    padding: 16,
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e0e0e0',
  },
  sfxSectionTitle: {
    fontSize: 14,
    fontWeight: 700,
    color: '#1a1a2e',
    marginBottom: 12,
  },
};

function DefaultSfxEditor() {
  const [entries, setEntries] = useState<DefaultSfx[]>([]);
  const [filterCategory, setFilterCategory] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const fetchEntries = () => {
    const qs = filterCategory ? `?category=${filterCategory}` : '';
    // Array-coerce at the setter boundary — see src/api.ts for rationale.
    api.get<DefaultSfx[]>(`/default-sfx${qs}`)
      .then((d) => setEntries(Array.isArray(d) ? d : []))
      .catch(console.error);
  };

  useEffect(() => { fetchEntries(); }, [filterCategory]);

  const selected = entries.find((e) => e.id === selectedId) || null;

  const handlePromptChange = (prompt: string, duration: number) => {
    if (!selectedId) return;
    api.put<DefaultSfx>(`/default-sfx/${selectedId}`, { prompt, duration })
      .then((updated) => {
        setEntries((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));
      })
      .catch(console.error);
  };

  const handleReset = () => {
    if (!confirm('Reset all Default SFX entries to their defaults? Prompt edits will be lost.')) return;
    api.post('/default-sfx/reset', {}).then(() => {
      fetchEntries();
      setSelectedId(null);
    }).catch(console.error);
  };

  return (
    <div style={styles.container}>
      {/* Left panel: list */}
      <div style={styles.listPanel}>
        <div style={styles.listTop}>
          <select style={styles.select} value={filterCategory} onChange={(e) => setFilterCategory(e.target.value)}>
            <option value="">All Categories</option>
            {CATEGORIES.map((c) => <option key={c} value={c}>{titleCase(c)}</option>)}
          </select>
        </div>
        <div style={styles.listCount}>{entries.length} sounds</div>
        <div style={styles.listItems}>
          {entries.map((entry) => {
            const colors = categoryColors[entry.category] || { bg: '#f5f5f5', fg: '#666' };
            return (
              <div
                key={entry.id}
                style={{ ...styles.listItem, ...(selectedId === entry.id ? styles.listItemSelected : {}) }}
                onClick={() => setSelectedId(entry.id)}
              >
                <div
                  style={{
                    ...styles.statusDot,
                    backgroundColor: colors.fg,
                  }}
                />
                <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {entry.label}
                </span>
                <span style={{ ...styles.categoryTag, backgroundColor: colors.bg, color: colors.fg }}>
                  {entry.category}
                </span>
              </div>
            );
          })}
        </div>
      </div>

      {/* Right panel: detail */}
      <div style={styles.detailPanel}>
        {selected ? (
          <>
            <div style={styles.detailTitle}>{selected.label}</div>
            <div style={styles.detailSubtitle}>ID: {selected.id}</div>
            <span style={{
              ...styles.categoryTag,
              backgroundColor: (categoryColors[selected.category] || { bg: '#f5f5f5' }).bg,
              color: (categoryColors[selected.category] || { fg: '#666' }).fg,
              display: 'inline-block',
              fontSize: 11,
              padding: '2px 8px',
              marginBottom: 8,
            }}>
              {titleCase(selected.category)}
            </span>
            <div style={styles.detailDescription}>{selected.description}</div>

            {/* SFX Preview (play/upload/undo/AI — prompt persisted to DB) */}
            <div style={styles.sfxSection}>
              <div style={styles.sfxSectionTitle}>Sound Preview</div>
              <SfxPreview
                soundId={selected.id}
                onSoundIdChange={() => {}}
                entityLabel={selected.label}
                audioCategory={
                  selected.category === 'combat' || selected.category === 'loot' ? 'general'
                  : selected.category === 'item' ? 'items'
                  : selected.category === 'magic' ? 'spells'
                  : selected.category === 'movement' ? 'rooms'
                  : 'general'
                }
                initialPrompt={selected.prompt}
                initialDuration={selected.duration}
                onPromptChange={handlePromptChange}
                readOnlyId
              />
            </div>

            <div style={styles.btnRow}>
              <button style={styles.btnOutline} onClick={handleReset}>
                Reset All to Defaults
              </button>
            </div>
          </>
        ) : (
          <div style={styles.empty}>
            <EditorPageHeader storageKey="default-sfx">
              Default SFX are the shared sounds used across combat, movement, and item interactions.
              Each sound has an AI generation prompt — edit it and regenerate to customize.
              Entity-specific sounds (NPC attack, weapon swing) are set on the entity itself.
            </EditorPageHeader>
            Select a sound effect from the list to preview and edit its AI generation prompt.
          </div>
        )}
      </div>
    </div>
  );
}

export default DefaultSfxEditor;
