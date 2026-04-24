import { useEffect, useState, useRef } from 'react';
import type { CSSProperties } from 'react';
import api, { resolveUrl } from '../api';
import { useSignedAssetUrl } from '../hooks/useSignedAssetUrl';

interface ImagePreviewProps {
  entityType: string;     // "room" | "npc" | "item"
  entityId: string;       // e.g. "town:square", "npc:town_guard"
  description?: string;   // entity description for auto-filling new templates
  assetPath?: string;     // override filename (rooms store backgroundImage field)
  imagePrompt?: string;
  imageStyle?: string;
  imageNegativePrompt?: string;
  imageWidth?: number;
  imageHeight?: number;
  maxWidth?: number;
  maxHeight?: number;
  onUpdate?: (fields: { imagePrompt: string; imageStyle: string; imageNegativePrompt: string; imageWidth: number; imageHeight: number }) => void;
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
  imageArea: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#f0f0f0',
    maxHeight: 180,
    overflow: 'hidden',
    position: 'relative',
  },
  image: {
    display: 'block',
    maxWidth: '100%',
    maxHeight: 180,
    objectFit: 'contain',
  },
  placeholder: {
    width: '100%',
    height: 100,
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
  placeholderName: {
    fontSize: 10,
    color: '#e57373',
    marginTop: 2,
    maxWidth: '80%',
    textAlign: 'center',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
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
  input: {
    width: '100%',
    padding: '4px 6px',
    fontSize: 12,
    border: '1px solid #ccc',
    borderRadius: 3,
    boxSizing: 'border-box' as const,
    marginBottom: 4,
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
  fieldLabel: {
    fontSize: 10,
    fontWeight: 600,
    color: '#888',
    marginBottom: 1,
    marginTop: 4,
  },
  row: {
    display: 'flex',
    gap: 6,
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

function resolveAssetPath(entityType: string, entityId: string, assetPath?: string): string {
  if (entityType === 'room') {
    if (!assetPath) return '';
    const filename = assetPath.split('/').pop() || assetPath;
    const withExt = filename.includes('.') ? filename : `${filename}.webp`;
    return `images/rooms/${withExt}`;
  }
  if (assetPath) return assetPath;
  const filename = entityId.replace(':', '_');
  return `images/${entityType}s/${filename}.webp`;
}

/**
 * Build the project-scoped API path (including /projects/{name}) for an
 * asset. Used as the input to `useSignedAssetUrl` below.
 */
function getAssetApiPath(assetPath: string): string {
  if (!assetPath) return '';
  return resolveUrl(`/assets/${assetPath}`);
}

function defaultDimensions(entityType: string): { w: number; h: number } {
  switch (entityType) {
    case 'item': case 'coin': return { w: 256, h: 256 };
    case 'npc': return { w: 384, h: 512 };
    case 'player': return { w: 384, h: 512 };
    default: return { w: 1024, h: 576 }; // room backgrounds
  }
}

function ImagePreview({ entityType, entityId, description, assetPath, imagePrompt, imageStyle, imageNegativePrompt, imageWidth, imageHeight, maxWidth, maxHeight, onUpdate }: ImagePreviewProps) {
  const defaults = defaultDimensions(entityType);
  const [imgError, setImgError] = useState(false);
  const [localPrompt, setLocalPrompt] = useState(imagePrompt || '');
  const [localStyle, setLocalStyle] = useState(imageStyle || '');
  const [localNeg, setLocalNeg] = useState(imageNegativePrompt || '');
  const [localW, setLocalW] = useState(imageWidth || defaults.w);
  const [localH, setLocalH] = useState(imageHeight || defaults.h);
  const [generating, setGenerating] = useState(false);
  const [cacheBust, setCacheBust] = useState(0);
  const [undoDepth, setUndoDepth] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const resolved = resolveAssetPath(entityType, entityId, assetPath);
  const assetApiPath = getAssetApiPath(resolved);
  const imageUrl = useSignedAssetUrl(assetApiPath, cacheBust);

  useEffect(() => {
    setLocalPrompt(imagePrompt || '');
    setLocalStyle(imageStyle || '');
    setLocalNeg(imageNegativePrompt || '');
    setLocalW(imageWidth || defaults.w);
    setLocalH(imageHeight || defaults.h);
  }, [entityId, imagePrompt, imageStyle, imageNegativePrompt, imageWidth, imageHeight]);

  useEffect(() => {
    setImgError(false);
    setCacheBust(0);
  }, [entityId, assetPath]);

  // Load undo depth
  useEffect(() => {
    if (!resolved) return;
    api.get<{ depth: number }>(`/asset-mgmt/history?path=${encodeURIComponent(resolved)}`)
      .then((r) => setUndoDepth(r.depth))
      .catch(() => setUndoDepth(0));
  }, [resolved, cacheBust]);

  const fireUpdate = (overrides: Partial<{ imagePrompt: string; imageStyle: string; imageNegativePrompt: string; imageWidth: number; imageHeight: number }>) => {
    if (!onUpdate) return;
    onUpdate({
      imagePrompt: overrides.imagePrompt ?? localPrompt,
      imageStyle: overrides.imageStyle ?? localStyle,
      imageNegativePrompt: overrides.imageNegativePrompt ?? localNeg,
      imageWidth: overrides.imageWidth ?? localW,
      imageHeight: overrides.imageHeight ?? localH,
    });
  };

  const handleGenerate = async () => {
    if (!resolved || !localPrompt || generating) return;
    setGenerating(true);
    try {
      await api.post('/generate/image', {
        prompt: localPrompt,
        style: localStyle,
        negativePrompt: localNeg,
        width: localW,
        height: localH,
        assetPath: resolved,
      });
      setCacheBust(Date.now());
      setImgError(false);
    } catch (err: any) {
      alert(`Image generation failed: ${err.message}`);
    } finally {
      setGenerating(false);
    }
  };

  const handleUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file || !resolved) return;
    try {
      await api.upload('/asset-mgmt/upload', file, { assetPath: resolved });
      setCacheBust(Date.now());
      setImgError(false);
    } catch (err: any) {
      alert(`Upload failed: ${err.message}`);
    }
    e.target.value = '';
  };

  const handleUndo = async () => {
    if (!resolved) return;
    try {
      await api.post('/asset-mgmt/undo', { assetPath: resolved });
      setCacheBust(Date.now());
      setImgError(false);
    } catch (err: any) {
      alert(`Undo failed: ${err.message}`);
    }
  };

  const handleClear = async () => {
    if (!resolved) return;
    try {
      await api.post('/asset-mgmt/clear', { assetPath: resolved });
      setCacheBust(Date.now());
      setImgError(true);
    } catch (err: any) {
      alert(`Clear failed: ${err.message}`);
    }
  };

  const handleCopyPrompt = () => {
    const fullPrompt = [localPrompt, localStyle && `Style: ${localStyle}`, localNeg && `Negative: ${localNeg}`]
      .filter(Boolean)
      .join('\n');
    navigator.clipboard.writeText(fullPrompt);
  };

  return (
    <div style={styles.container}>
      {/* Image Area */}
      <div style={{ ...styles.imageArea, position: 'relative' as const }}>
        {imageUrl && !imgError ? (
          <img
            src={imageUrl}
            alt={entityId}
            style={styles.image}
            onError={() => setImgError(true)}
          />
        ) : (
          <div style={styles.placeholder}>
            <div style={styles.placeholderIcon}>
              <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="#d32f2f" strokeWidth="1.5">
                <rect x="3" y="3" width="18" height="18" rx="2" />
                <path d="M8 8l8 8M16 8l-8 8" />
              </svg>
            </div>
            <div style={styles.placeholderText}>Missing Asset</div>
            <div style={styles.placeholderName}>{entityId}</div>
          </div>
        )}
        {generating && (
          <div style={styles.spinnerOverlay}>Generating...</div>
        )}
      </div>

      {/* Toolbar */}
      <div style={styles.toolbar}>
        <button
          style={{ ...styles.toolBtnPrimary, opacity: generating || !localPrompt ? 0.5 : 1 }}
          onClick={handleGenerate}
          disabled={generating || !localPrompt}
          title="Generate image with AI"
        >
          <svg width="12" height="12" viewBox="0 0 16 16" fill="currentColor">
            <path d="M8 0l1.5 4.5L14 6l-4.5 1.5L8 12l-1.5-4.5L2 6l4.5-1.5z" />
            <path d="M12 10l.75 2.25L15 13l-2.25.75L12 16l-.75-2.25L9 13l2.25-.75z" opacity="0.6" />
          </svg>
          Generate
        </button>
        <button
          style={styles.toolBtn}
          onClick={() => fileInputRef.current?.click()}
          title="Upload image file"
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
          style={styles.toolBtn}
          onClick={handleClear}
          title="Clear image"
        >
          Clear
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept="image/*"
          style={{ display: 'none' }}
          onChange={handleUpload}
        />
      </div>

      {/* Prompt Fields Section */}
      <div style={styles.promptSection}>
        <div style={styles.promptLabel}>Image Prompt</div>
        <div style={styles.fieldLabel}>Prompt</div>
        <textarea
          style={styles.textarea}
          value={localPrompt}
          onChange={(e) => { setLocalPrompt(e.target.value); fireUpdate({ imagePrompt: e.target.value }); }}
          rows={3}
          placeholder={description || 'Describe the image to generate...'}
        />
        <div style={styles.fieldLabel}>Style</div>
        <input
          style={styles.input}
          value={localStyle}
          onChange={(e) => { setLocalStyle(e.target.value); fireUpdate({ imageStyle: e.target.value }); }}
          placeholder="e.g. pixel art, watercolor"
        />
        <div style={styles.fieldLabel}>Negative Prompt</div>
        <input
          style={styles.input}
          value={localNeg}
          onChange={(e) => { setLocalNeg(e.target.value); fireUpdate({ imageNegativePrompt: e.target.value }); }}
          placeholder="e.g. blurry, low quality"
        />
        <div style={styles.row}>
          <div style={{ flex: 1 }}>
            <div style={styles.fieldLabel}>Width{maxWidth ? ` (max ${maxWidth})` : ''}</div>
            <input
              style={styles.input}
              type="number"
              value={localW}
              max={maxWidth}
              onChange={(e) => { let v = parseInt(e.target.value) || 0; if (maxWidth) v = Math.min(v, maxWidth); setLocalW(v); fireUpdate({ imageWidth: v }); }}
            />
          </div>
          <div style={{ flex: 1 }}>
            <div style={styles.fieldLabel}>Height{maxHeight ? ` (max ${maxHeight})` : ''}</div>
            <input
              style={styles.input}
              type="number"
              value={localH}
              max={maxHeight}
              onChange={(e) => { let v = parseInt(e.target.value) || 0; if (maxHeight) v = Math.min(v, maxHeight); setLocalH(v); fireUpdate({ imageHeight: v }); }}
            />
          </div>
        </div>
        <span style={styles.copyLink} onClick={handleCopyPrompt}>Copy Prompt</span>
      </div>
    </div>
  );
}

export default ImagePreview;
