import { useState, useRef, useEffect } from 'react';
import type { CSSProperties } from 'react';
import api from '../api';

interface SfxPreviewProps {
  soundId: string;
  onSoundIdChange: (id: string) => void;
  entityLabel?: string;
  /** Audio subdirectory: 'general', 'npcs', 'items', 'spells', 'rooms' */
  audioCategory?: string;
  /** Pre-fill the AI prompt (e.g. from DB-persisted value) */
  initialPrompt?: string;
  /** Pre-fill the AI duration (e.g. from DB-persisted value) */
  initialDuration?: number;
  /** Called when prompt/duration change so the parent can persist them */
  onPromptChange?: (prompt: string, duration: number) => void;
  /** If true, the sound ID input is read-only */
  readOnlyId?: boolean;
}

const styles: Record<string, CSSProperties> = {
  row: {
    display: 'flex',
    gap: 4,
    alignItems: 'center',
  },
  input: {
    flex: 1,
    padding: '4px 6px',
    fontSize: 12,
    border: '1px solid #ccc',
    borderRadius: 3,
    boxSizing: 'border-box' as const,
    minWidth: 0,
  },
  btn: {
    padding: '3px 6px',
    fontSize: 10,
    fontWeight: 600,
    backgroundColor: '#fff',
    color: '#1a1a2e',
    border: '1px solid #ccc',
    borderRadius: 3,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
    flexShrink: 0,
  },
  btnPrimary: {
    padding: '3px 6px',
    fontSize: 10,
    fontWeight: 600,
    backgroundColor: '#1a1a2e',
    color: '#fff',
    border: '1px solid #1a1a2e',
    borderRadius: 3,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
    flexShrink: 0,
  },
  popover: {
    position: 'absolute',
    top: '100%',
    left: 0,
    right: 0,
    zIndex: 10,
    backgroundColor: '#fff',
    border: '1px solid #ddd',
    borderRadius: 4,
    padding: 8,
    boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
    marginTop: 2,
  },
  popLabel: {
    fontSize: 10,
    fontWeight: 600,
    color: '#888',
    marginBottom: 2,
  },
  popTextarea: {
    width: '100%',
    padding: '3px 5px',
    fontSize: 11,
    border: '1px solid #ccc',
    borderRadius: 3,
    boxSizing: 'border-box' as const,
    resize: 'vertical' as const,
    minHeight: 36,
    marginBottom: 4,
  },
  popInput: {
    width: '100%',
    padding: '3px 5px',
    fontSize: 11,
    border: '1px solid #ccc',
    borderRadius: 3,
    boxSizing: 'border-box' as const,
    marginBottom: 4,
  },
  popBtnRow: {
    display: 'flex',
    gap: 4,
  },
  spinnerText: {
    fontSize: 10,
    color: '#666',
    fontStyle: 'italic',
  },
};

function SfxPreview({ soundId, onSoundIdChange, entityLabel, audioCategory = 'general', initialPrompt, initialDuration, onPromptChange, readOnlyId }: SfxPreviewProps) {
  const [showPopover, setShowPopover] = useState(false);
  const [prompt, setPrompt] = useState(initialPrompt ?? '');
  const [duration, setDuration] = useState(initialDuration ?? 5);
  const [generating, setGenerating] = useState(false);
  const [undoDepth, setUndoDepth] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // Sync from parent when initialPrompt/initialDuration change (e.g. selecting a different entry)
  useEffect(() => {
    if (initialPrompt !== undefined) setPrompt(initialPrompt);
  }, [initialPrompt]);
  useEffect(() => {
    if (initialDuration !== undefined) setDuration(initialDuration);
  }, [initialDuration]);

  const assetPath = soundId ? `audio/${audioCategory}/${soundId}.mp3` : '';

  useEffect(() => {
    if (!assetPath) { setUndoDepth(0); return; }
    api.get<{ depth: number }>(`/asset-mgmt/history?path=${encodeURIComponent(assetPath)}`)
      .then((r) => setUndoDepth(r.depth))
      .catch(() => setUndoDepth(0));
  }, [assetPath]);

  const handlePlay = () => {
    if (!assetPath) return;
    const audio = new Audio(api.assetUrl(`/assets/${assetPath}`));
    audio.play().catch(() => {});
  };

  const handleAIOpen = () => {
    if (!prompt && entityLabel) {
      setPrompt(`${entityLabel} sound effect`);
    }
    setShowPopover(true);
  };

  const updatePrompt = (p: string) => {
    setPrompt(p);
    onPromptChange?.(p, duration);
  };

  const updateDuration = (d: number) => {
    setDuration(d);
    onPromptChange?.(prompt, d);
  };

  const handleAIGenerate = async () => {
    let effectiveSoundId = soundId;
    if (!effectiveSoundId && entityLabel) {
      effectiveSoundId = entityLabel.toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/(^_|_$)/g, '');
      onSoundIdChange(effectiveSoundId);
    }
    if (!effectiveSoundId || !prompt) return;

    const effectiveAssetPath = `audio/${audioCategory}/${effectiveSoundId}.mp3`;
    setGenerating(true);
    try {
      await api.post('/generate/sound', {
        prompt,
        duration,
        assetPath: effectiveAssetPath,
      });
      setShowPopover(false);
    } catch (err: any) {
      alert(`SFX generation failed: ${err.message}`);
    } finally {
      setGenerating(false);
    }
  };

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    let effectiveSoundId = soundId;
    if (!effectiveSoundId) {
      effectiveSoundId = file.name.replace(/\.[^.]+$/, '').replace(/[^a-zA-Z0-9_-]/g, '_');
      onSoundIdChange(effectiveSoundId);
    }
    const effectiveAssetPath = `audio/${audioCategory}/${effectiveSoundId}.mp3`;
    try {
      await api.upload('/asset-mgmt/upload', file, { assetPath: effectiveAssetPath });
    } catch (err: any) {
      alert(`Upload failed: ${err.message}`);
    }
    e.target.value = '';
  };

  const handleUndo = async () => {
    if (!assetPath) return;
    try {
      await api.post('/asset-mgmt/undo', { assetPath });
      setUndoDepth((d) => Math.max(0, d - 1));
    } catch (err: any) {
      alert(`Undo failed: ${err.message}`);
    }
  };

  const handleClear = async () => {
    if (!assetPath) return;
    try {
      await api.post('/asset-mgmt/clear', { assetPath });
    } catch (err: any) {
      alert(`Clear failed: ${err.message}`);
    }
  };

  return (
    <div style={{ position: 'relative' }}>
      <div style={styles.row}>
        <input
          style={styles.input}
          value={soundId}
          onChange={(e) => onSoundIdChange(e.target.value)}
          placeholder="sound_id"
          readOnly={readOnlyId}
        />
        <button style={styles.btn} onClick={handlePlay} disabled={!soundId} title="Play">
          Play
        </button>
        <button style={{ ...styles.btnPrimary, display: 'flex', alignItems: 'center', gap: 2 }} onClick={handleAIOpen} title="Generate with AI">
          <svg width="10" height="10" viewBox="0 0 16 16" fill="currentColor">
            <path d="M8 0l1.5 4.5L14 6l-4.5 1.5L8 12l-1.5-4.5L2 6l4.5-1.5z" />
            <path d="M12 10l.75 2.25L15 13l-2.25.75L12 16l-.75-2.25L9 13l2.25-.75z" opacity="0.6" />
          </svg>
          AI
        </button>
        <button style={styles.btn} onClick={() => fileInputRef.current?.click()} title="Upload">
          Up
        </button>
        <button style={{ ...styles.btn, opacity: undoDepth > 0 ? 1 : 0.4 }} onClick={handleUndo} disabled={undoDepth === 0} title="Undo">
          Undo
        </button>
        <button style={styles.btn} onClick={handleClear} disabled={!soundId} title="Clear">
          X
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept="audio/*"
          style={{ display: 'none' }}
          onChange={handleUpload}
        />
      </div>

      {showPopover && (
        <div style={styles.popover}>
          <div style={styles.popLabel}>SFX Prompt</div>
          <textarea
            style={styles.popTextarea}
            value={prompt}
            onChange={(e) => updatePrompt(e.target.value)}
            rows={2}
            placeholder="Describe the sound effect..."
          />
          <div style={styles.popLabel}>Duration (s)</div>
          <input
            style={styles.popInput}
            type="number"
            value={duration}
            onChange={(e) => updateDuration(parseInt(e.target.value) || 5)}
          />
          <div style={styles.popBtnRow}>
            <button
              style={{ ...styles.btnPrimary, opacity: generating || !prompt ? 0.5 : 1 }}
              onClick={handleAIGenerate}
              disabled={generating || !prompt}
            >
              {generating ? 'Generating...' : 'Generate'}
            </button>
            <button style={styles.btn} onClick={() => setShowPopover(false)}>
              Cancel
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

export default SfxPreview;
