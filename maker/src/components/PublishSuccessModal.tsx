import type { CSSProperties } from 'react';

interface PublishSuccessModalProps {
  slug: string;
  publicUrl: string;
  platformOrigin?: string;
  onClose: () => void;
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
    minWidth: 420,
    maxWidth: 520,
    boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
    display: 'flex',
    flexDirection: 'column',
    gap: 14,
  },
  title: { fontSize: 16, fontWeight: 700, color: '#66bb6a' },
  body: { fontSize: 13, lineHeight: 1.5, color: '#c0c0d0' },
  slug: { fontFamily: 'monospace', color: '#9fd', fontSize: 13 },
  actions: { display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 4 },
  btn: {
    padding: '6px 16px',
    borderRadius: 4,
    border: 'none',
    fontSize: 13,
    cursor: 'pointer',
  },
  btnPrimary: { backgroundColor: '#3949ab', color: '#fff' },
  btnSecondary: { backgroundColor: '#2a2a4a', color: '#b0b0cc' },
};

function PublishSuccessModal({ slug, publicUrl, platformOrigin, onClose }: PublishSuccessModalProps) {
  const href = platformOrigin ? `${platformOrigin}${publicUrl}` : publicUrl;
  return (
    <div style={styles.overlay} onClick={onClose}>
      <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div style={styles.title}>Published!</div>
        <div style={styles.body}>
          Your world is live in the marketplace at <span style={styles.slug}>/worlds/{slug}</span>.
        </div>
        <div style={styles.actions}>
          <button style={{ ...styles.btn, ...styles.btnSecondary }} onClick={onClose}>Close</button>
          <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            style={{ ...styles.btn, ...styles.btnPrimary, textDecoration: 'none', display: 'inline-block' }}
            onClick={onClose}
            data-testid="publish-view-btn"
          >
            View in Marketplace
          </a>
        </div>
      </div>
    </div>
  );
}

export default PublishSuccessModal;
