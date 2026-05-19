import { useState } from 'react';
import type { CSSProperties } from 'react';

const styles: Record<string, CSSProperties> = {
  wrapper: {
    marginBottom: 16,
    borderRadius: 6,
    border: '1px solid #e0e0e0',
    backgroundColor: '#fff',
    overflow: 'hidden',
  },
  toggle: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    width: '100%',
    padding: '8px 12px',
    background: 'none',
    border: 'none',
    cursor: 'pointer',
    fontSize: 12,
    fontWeight: 600,
    color: '#666',
    textAlign: 'left',
  },
  arrow: {
    fontSize: 10,
    transition: 'transform 0.15s',
  },
  body: {
    padding: '0 12px 10px',
    fontSize: 12,
    lineHeight: 1.5,
    color: '#555',
  },
};

interface EditorPageHeaderProps {
  storageKey: string;
  children: React.ReactNode;
}

export default function EditorPageHeader({ storageKey, children }: EditorPageHeaderProps) {
  const lsKey = `maker-help-${storageKey}`;
  const [collapsed, setCollapsed] = useState(() => {
    try { return localStorage.getItem(lsKey) === '1'; }
    catch { return false; }
  });

  const toggle = () => {
    const next = !collapsed;
    setCollapsed(next);
    try { localStorage.setItem(lsKey, next ? '1' : '0'); }
    catch { /* private browsing */ }
  };

  return (
    <div style={styles.wrapper}>
      <button style={styles.toggle} onClick={toggle}>
        <span style={{ ...styles.arrow, transform: collapsed ? 'rotate(-90deg)' : 'rotate(0deg)' }}>
          &#9660;
        </span>
        How This Works
      </button>
      {!collapsed && <div style={styles.body}>{children}</div>}
    </div>
  );
}
