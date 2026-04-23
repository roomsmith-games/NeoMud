import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import api from '../api';
import ValidationModal from './ValidationModal';
import type { CSSProperties } from 'react';

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
};

function MenuBar() {
  const { name } = useParams<{ name: string }>();
  const navigate = useNavigate();
  const [validation, setValidation] = useState<{ errors: string[]; warnings: string[]; actionLabel?: string; onAction?: () => void } | null>(null);

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
