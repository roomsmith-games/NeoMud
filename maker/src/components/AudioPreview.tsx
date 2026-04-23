import { useEffect, useState, useRef } from 'react';
import type { CSSProperties } from 'react';
import api from '../api';

interface AudioPreviewProps {
  entityType: string;          // "zone" | "room"
  entityId: string;
  bgm?: string;                // track ID (display + audio player)
  bgmPrompt?: string;
  bgmDuration?: number;
  defaultBgmPrompt?: string;   // zone fallback shown as placeholder
  defaultBgmDuration?: number;
  onUpdate?: (fields: { bgmPrompt: string; bgmDuration: number }) => void;
}

const styles: Record<string, CSSProperties> = {
  container: {
    marginBottom: 16,
    borderRadius: 6,
    border: '1px solid #ddd',
    overflow: 'hidden',
    backgroundColor: '#fff',
    maxWidth: 320,
  },
  audioArea: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f0f0f0',
    padding: 12,
    gap: 6,
    position: 'relative',
  },
  placeholder: {
    width: '100%',
    height: 80,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#fff0f0',
    color: '#d32f2f',
    border: '2px dashed #e57373',
    borderRadius: 4,
  },
  placeholderIcon: {
    fontSize: 28,
    marginBottom: 4,
    color: '#d32f2f',
  },
  placeholderText: {
    fontSize: 11,
    fontWeight: 600,
    color: '#d32f2f',
  },
  trackId: {
    fontSize: 10,
    color: '#888',
    textAlign: 'center',
  },
  toolbar: {
    display: 'flex',
    gap: 4,
    padding: '6px 10px',
    borderTop: '1px solid #eee',
    borderBottom: '1px solid #eee',
    backgroundColor: '#fafafa',
    alignItems: 'center',
    flexWrap: 'wrap',
  },
  toolBtn: {
    padding: '3px 8px',
    fontSize: 11,
    fontWeight: 600,
    backgroundColor: '#fff',
    color: '#1a1a2e',
    border: '1px solid #ccc',
    borderRadius: 3,
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    gap: 3,
  },
  toolBtnPrimary: {
    padding: '3px 8px',
    fontSize: 11,
    fontWeight: 600,
    backgroundColor: '#1a1a2e',
    color: '#fff',
    border: '1px solid #1a1a2e',
    borderRadius: 3,
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    gap: 3,
  },
  promptSection: {
    padding: 10,
  },
  promptLabel: {
    fontSize: 11,
    fontWeight: 600,
    color: '#666',
    marginBottom: 4,
  },
  fieldLabel: {
    fontSize: 10,
    fontWeight: 600,
    color: '#888',
    marginBottom: 1,
    marginTop: 4,
  },
  textarea: {
    width: '100%',
    padding: '4px 6px',
    fontSize: 12,
    border: '1px solid #ccc',
    borderRadius: 3,
    boxSizing: 'border-box' as const,
    resize: 'vertical' as const,
    minHeight: 48,
    marginBottom: 4,
  },
  input: {
    width: '100%',
    padding: '4px 6px',
    fontSize: 12,
    border: '1px solid #ccc',
    borderRadius: 3,
    boxSizing: 'border-box' as const,
    marginBottom: 4,
  },
  copyLink: {
    fontSize: 10,
    color: '#666',
    cursor: 'pointer',
    textDecoration: 'underline',
    marginTop: 4,
    display: 'inline-block',
  },
  spinnerOverlay: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(0,0,0,0.5)',
    color: '#fff',
    fontSize: 13,
    fontWeight: 600,
  },
};

function AudioPreview({ entityType, entityId, bgm, bgmPrompt, bgmDuration, defaultBgmPrompt, defaultBgmDuration, onUpdate }: AudioPreviewProps) {
  const [localPrompt, setLocalPrompt] = useState(bgmPrompt || '');
  const [localDuration, setLocalDuration] = useState(bgmDuration || 0);
  const [generating, setGenerating] = useState(false);
  const [cacheBust, setCacheBust] = useState(0);
  const [undoDepth, setUndoDepth] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const assetPath = bgm ? `audio/bgm/${bgm}.mp3` : '';

  useEffect(() => {
    setLocalPrompt(bgmPrompt || '');
    setLocalDuration(bgmDuration || 0);
  }, [entityId, bgmPrompt, bgmDuration]);

  useEffect(() => {
    setCacheBust(0);
  }, [entityId, bgm]);

  // Load undo depth
  useEffect(() => {
    if (!assetPath) { setUndoDepth(0); return; }
    api.get<{ depth: number }>(`/asset-mgmt/history?path=${encodeURIComponent(assetPath)}`)
      .then((r) => setUndoDepth(r.depth))
      .catch(() => setUndoDepth(0));
  }, [assetPath, cacheBust]);

  const fireUpdate = (overrides: Partial<{ bgmPrompt: string; bgmDuration: number }>) => {
    if (!onUpdate) return;
    onUpdate({
      bgmPrompt: overrides.bgmPrompt ?? localPrompt,
      bgmDuration: overrides.bgmDuration ?? localDuration,
    });
  };

  const audioUrl = assetPath ? api.assetUrl(`/assets/${assetPath}${cacheBust ? `?t=${cacheBust}` : ''}`) : '';

  const effectivePrompt = localPrompt || defaultBgmPrompt || '';
  const effectiveDuration = localDuration || defaultBgmDuration || 120;

  const handleGenerate = async () => {
    if (!assetPath || !effectivePrompt || generating) return;
    setGenerating(true);
    try {
      await api.post('/generate/sound', {
        prompt: effectivePrompt,
        duration: effectiveDuration,
        assetPath,
      });
      setCacheBust(Date.now());
    } catch (err: any) {
      alert(`Sound generation failed: ${err.message}`);
    } finally {
      setGenerating(false);
    }
  };

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !assetPath) return;
    try {
      await api.upload('/asset-mgmt/upload', file, { assetPath });
      setCacheBust(Date.now());
    } catch (err: any) {
      alert(`Upload failed: ${err.message}`);
    }
    e.target.value = '';
  };

  const handleUndo = async () => {
    if (!assetPath) return;
    try {
      await api.post('/asset-mgmt/undo', { assetPath });
      setCacheBust(Date.now());
    } catch (err: any) {
      alert(`Undo failed: ${err.message}`);
    }
  };

  const handleClear = async () => {
    if (!assetPath) return;
    try {
      await api.post('/asset-mgmt/clear', { assetPath });
      setCacheBust(Date.now());
    } catch (err: any) {
      alert(`Clear failed: ${err.message}`);
    }
  };

  const handleCopyPrompt = () => {
    const payload = JSON.stringify({
      text: effectivePrompt,
      duration_seconds: effectiveDuration,
      prompt_influence: 0.3,
      output_format: 'ogg_48000',
    }, null, 2);
    navigator.clipboard.writeText(payload);
  };

  const promptPlaceholder = entityType === 'room' && defaultBgmPrompt
    ? `Zone default: ${defaultBgmPrompt}`
    : 'Describe the BGM atmosphere...';

  const durationPlaceholder = entityType === 'room' && defaultBgmDuration
    ? `Zone default: ${defaultBgmDuration}s`
    : '';

  return (
    <div style={styles.container}>
      {/* Audio Area */}
      <div style={{ ...styles.audioArea, position: 'relative' as const }}>
        {bgm ? (
          <>
            <audio controls src={audioUrl} key={audioUrl} style={{ width: '100%', maxWidth: 280 }} />
            <div style={styles.trackId}>{bgm}.mp3</div>
          </>
        ) : (
          <div style={styles.placeholder}>
            <div style={styles.placeholderIcon}>
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#d32f2f" strokeWidth="1.5">
                <path d="M9 18V5l12-2v13" />
                <circle cx="6" cy="18" r="3" />
                <circle cx="18" cy="16" r="3" />
                <path d="M2 2l20 20" strokeWidth="2" />
              </svg>
            </div>
            <div style={styles.placeholderText}>Missing BGM</div>
          </div>
        )}
        {generating && (
          <div style={styles.spinnerOverlay}>Generating...</div>
        )}
      </div>

      {/* Toolbar */}
      <div style={styles.toolbar}>
        <button
          style={{ ...styles.toolBtnPrimary, opacity: generating || !assetPath || !effectivePrompt ? 0.5 : 1 }}
          onClick={handleGenerate}
          disabled={generating || !assetPath || !effectivePrompt}
          title="Generate audio with AI"
        >
          <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor">
            <path d="M8 0l1.5 4.5L14 6l-4.5 1.5L8 12l-1.5-4.5L2 6l4.5-1.5z" />
            <path d="M12 10l.75 2.25L15 13l-2.25.75L12 16l-.75-2.25L9 13l2.25-.75z" opacity="0.6" />
          </svg>
          Generate
        </button>
        <button
          style={{ ...styles.toolBtn, opacity: assetPath ? 1 : 0.4 }}
          onClick={() => fileInputRef.current?.click()}
          disabled={!assetPath}
          title="Upload audio file"
        >
          Upload
        </button>
        <button
          style={{ ...styles.toolBtn, opacity: undoDepth > 0 ? 1 : 0.4 }}
          onClick={handleUndo}
          disabled={undoDepth === 0}
          title={`Undo (${undoDepth} levels)`}
        >
          Undo
        </button>
        <button
          style={{ ...styles.toolBtn, opacity: assetPath ? 1 : 0.4 }}
          onClick={handleClear}
          disabled={!assetPath}
          title="Clear audio"
        >
          Clear
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept="audio/*"
          style={{ display: 'none' }}
          onChange={handleUpload}
        />
      </div>

      {/* Prompt Fields Section */}
      <div style={styles.promptSection}>
        <div style={styles.promptLabel}>BGM Prompt</div>
        <div style={styles.fieldLabel}>Prompt</div>
        <textarea
          style={styles.textarea}
          value={localPrompt}
          onChange={(e) => { setLocalPrompt(e.target.value); fireUpdate({ bgmPrompt: e.target.value }); }}
          rows={3}
          placeholder={promptPlaceholder}
        />
        <div style={styles.fieldLabel}>Duration (seconds)</div>
        <input
          style={styles.input}
          type="number"
          value={localDuration || ''}
          onChange={(e) => { const v = parseInt(e.target.value) || 0; setLocalDuration(v); fireUpdate({ bgmDuration: v }); }}
          placeholder={durationPlaceholder}
        />
        <span style={styles.copyLink} onClick={handleCopyPrompt}>Copy Prompt</span>
      </div>
    </div>
  );
}

export default AudioPreview;
