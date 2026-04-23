import type { CSSProperties } from 'react';

interface UpsellModalProps {
  plan: string;
  upgradeUrl: string;
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
  title: { fontSize: 16, fontWeight: 700 },
  body: { fontSize: 13, lineHeight: 1.5, color: '#c0c0d0' },
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

function UpsellModal({ plan, upgradeUrl, platformOrigin, onClose }: UpsellModalProps) {
  const href = platformOrigin ? `${platformOrigin}${upgradeUrl}` : upgradeUrl;
  return (
    <div style={styles.overlay} onClick={onClose}>
      <div style={styles.modal} onClick={(e) => e.stopPropagation()}>
        <div style={styles.title}>Publishing needs a creator subscription</div>
        <div style={styles.body}>
          Your account is on the <strong>{plan}</strong> plan. Publishing a world to the NeoMud marketplace
          requires a <strong>CREATOR</strong> or <strong>PRO</strong> subscription.
          <br /><br />
          Paid plans aren't live yet — during early access you can request a beta bypass from your account page.
        </div>
        <div style={styles.actions}>
          <button style={{ ...styles.btn, ...styles.btnSecondary }} onClick={onClose}>Close</button>
          <a
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            style={{ ...styles.btn, ...styles.btnPrimary, textDecoration: 'none', display: 'inline-block' }}
            onClick={onClose}
          >
            Go to Account
          </a>
        </div>
      </div>
    </div>
  );
}

export default UpsellModal;
