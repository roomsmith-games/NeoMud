import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api';
import type { CSSProperties } from 'react';
import PublishModal, { type PublishSuccess, type PublishUpsell } from '../components/PublishModal';
import PublishSuccessModal from '../components/PublishSuccessModal';
import UpsellModal from '../components/UpsellModal';

interface PublishedInfo {
  worldId: string;
  slug: string;
  status: string;
  currentVersion: string | null;
  serverStatus: string;
}

interface ProjectInfo {
  name: string;
  readOnly: boolean;
  published?: PublishedInfo | null;
}

interface ProjectsResponse {
  projects: ProjectInfo[];
}

const styles: Record<string, CSSProperties> = {
  page: {
    minHeight: '100vh',
    backgroundColor: '#f5f5f5',
    display: 'flex',
    justifyContent: 'center',
    paddingTop: 80,
  },
  container: {
    width: 480,
  },
  title: {
    fontSize: 28,
    fontWeight: 700,
    marginBottom: 24,
    color: '#1a1a2e',
  },
  form: {
    display: 'flex',
    gap: 8,
    marginBottom: 32,
  },
  input: {
    flex: 1,
    padding: '10px 14px',
    fontSize: 14,
    border: '1px solid #ccc',
    borderRadius: 6,
    outline: 'none',
  },
  button: {
    padding: '10px 20px',
    fontSize: 14,
    fontWeight: 600,
    backgroundColor: '#1a1a2e',
    color: '#fff',
    border: 'none',
    borderRadius: 6,
    cursor: 'pointer',
  },
  list: {
    listStyle: 'none',
    padding: 0,
    margin: 0,
  },
  listItem: {
    padding: '14px 16px',
    backgroundColor: '#fff',
    borderRadius: 6,
    marginBottom: 8,
    fontSize: 15,
    fontWeight: 500,
    color: '#1a1a2e',
    border: '1px solid #e0e0e0',
    transition: 'background 0.15s',
    display: 'flex',
    alignItems: 'center',
    gap: 10,
  },
  empty: {
    color: '#888',
    fontSize: 14,
    textAlign: 'center',
    marginTop: 16,
  },
  error: {
    color: '#d32f2f',
    fontSize: 13,
    marginBottom: 12,
  },
  toast: {
    position: 'fixed',
    bottom: 24,
    left: '50%',
    transform: 'translateX(-50%)',
    padding: '10px 20px',
    backgroundColor: '#1a1a2e',
    color: '#fff',
    borderRadius: 6,
    fontSize: 13,
    fontWeight: 500,
    zIndex: 1000,
  },
  badge: {
    fontSize: 11,
    fontWeight: 600,
    padding: '2px 8px',
    borderRadius: 4,
    backgroundColor: '#e8eaf6',
    color: '#3949ab',
    whiteSpace: 'nowrap',
  },
  projectName: {
    flex: 1,
    cursor: 'pointer',
  },
  forkBtn: {
    padding: '6px 12px',
    fontSize: 12,
    fontWeight: 600,
    backgroundColor: '#3949ab',
    color: '#fff',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
  },
  publishBtn: {
    padding: '6px 12px',
    fontSize: 12,
    fontWeight: 600,
    backgroundColor: '#2e7d32',
    color: '#fff',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
  },
  publishedBadge: {
    fontSize: 11,
    fontWeight: 600,
    padding: '2px 8px',
    borderRadius: 4,
    backgroundColor: '#e8f5e9',
    color: '#2e7d32',
    whiteSpace: 'nowrap',
  },
  deleteBtn: {
    padding: '6px 12px',
    fontSize: 12,
    fontWeight: 600,
    backgroundColor: '#d32f2f',
    color: '#fff',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
  },
};

function ProjectList() {
  const [projects, setProjects] = useState<ProjectInfo[]>([]);
  const [newName, setNewName] = useState('');
  const [importPath, setImportPath] = useState('');
  const [importName, setImportName] = useState('');
  const [error, setError] = useState('');
  const [toast, setToast] = useState('');
  const [publishName, setPublishName] = useState<string | null>(null);
  const [publishSuccess, setPublishSuccess] = useState<PublishSuccess | null>(null);
  const [publishUpsell, setPublishUpsell] = useState<PublishUpsell | null>(null);
  const navigate = useNavigate();

  const showToast = (msg: string) => {
    setToast(msg);
    setTimeout(() => setToast(''), 3000);
  };

  const loadProjects = () => {
    api
      .get<ProjectsResponse>('/projects/enriched')
      .then((data) => setProjects(data.projects))
      .catch(() => {
        api
          .get<ProjectsResponse>('/projects')
          .then((data) => setProjects(data.projects))
          .catch(() => {});
      });
  };

  useEffect(() => {
    loadProjects();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = newName.trim();
    if (!trimmed) return;
    setError('');
    try {
      await api.post('/projects', { name: trimmed });
      navigate(`/project/${encodeURIComponent(trimmed)}/zones`);
    } catch (err: any) {
      setError(err.message || 'Failed to create project');
    }
  };

  // Navigate directly — no "open" needed (per-request project context)
  const handleNavigate = (name: string) => {
    navigate(`/project/${encodeURIComponent(name)}/zones`);
  };

  const handleFork = async (sourceName: string) => {
    const forkName = prompt(`Fork "${sourceName}" as:`, `${sourceName}_copy`);
    if (!forkName || !forkName.trim()) return;
    setError('');
    try {
      await api.post(`/projects/${encodeURIComponent(sourceName)}/fork`, {
        newName: forkName.trim(),
      });
      loadProjects();
    } catch (err: any) {
      setError(err.message || 'Failed to fork project');
    }
  };

  const handleDelete = async (name: string) => {
    if (!confirm(`Delete project "${name}"? This cannot be undone.`)) return;
    setError('');
    try {
      await api.del(`/projects/${encodeURIComponent(name)}`);
      loadProjects();
    } catch (err: any) {
      setError(err.message || 'Failed to delete project');
    }
  };

  const handlePublish = (name: string) => {
    setPublishName(name);
  };

  const handleImport = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmedPath = importPath.trim();
    if (!trimmedPath) return;
    setError('');
    try {
      const result = await api.post<{ name: string }>('/projects/import', {
        path: trimmedPath,
        name: importName.trim() || undefined,
      });
      navigate(`/project/${encodeURIComponent(result.name)}/zones`);
    } catch (err: any) {
      setError(err.message || 'Import failed');
    }
  };

  return (
    <div style={styles.page}>
      <div style={styles.container}>
        <h1 style={styles.title}>NeoMUD Maker</h1>
        <form style={styles.form} onSubmit={handleCreate}>
          <input
            style={styles.input}
            type="text"
            placeholder="New project name..."
            value={newName}
            onChange={(e) => setNewName(e.target.value)}
          />
          <button style={styles.button} type="submit">
            New Project
          </button>
        </form>
        <form style={{ ...styles.form, marginBottom: 12 }} onSubmit={handleImport}>
          <input
            style={styles.input}
            type="text"
            placeholder="Path to .nmd file..."
            value={importPath}
            onChange={(e) => setImportPath(e.target.value)}
          />
          <input
            style={{ ...styles.input, maxWidth: 140 }}
            type="text"
            placeholder="Name (optional)"
            value={importName}
            onChange={(e) => setImportName(e.target.value)}
          />
          <button style={styles.button} type="submit">
            Import .nmd
          </button>
        </form>
        {error && <p style={styles.error}>{error}</p>}
        {projects.length === 0 ? (
          <p style={styles.empty}>No projects yet. Type a name above and click "Create" to start building your world. Zones are a good first step — they hold the rooms your players will explore.</p>
        ) : (
          <ul style={styles.list}>
            {projects.map((proj) => (
              <li
                key={proj.name}
                style={styles.listItem}
                onMouseEnter={(e) =>
                  ((e.currentTarget as HTMLElement).style.backgroundColor = '#f0f0ff')
                }
                onMouseLeave={(e) =>
                  ((e.currentTarget as HTMLElement).style.backgroundColor = '#fff')
                }
              >
                <span
                  style={styles.projectName}
                  onClick={() => handleNavigate(proj.name)}
                >
                  {proj.name}
                </span>
                {proj.readOnly && (
                  <>
                    <span style={styles.badge}>Read Only</span>
                    <button
                      style={styles.forkBtn}
                      onClick={(e) => {
                        e.stopPropagation();
                        handleFork(proj.name);
                      }}
                    >
                      Fork
                    </button>
                  </>
                )}
                {!proj.readOnly && proj.published && (
                  <span style={styles.publishedBadge}>
                    Published{proj.published.currentVersion ? ` v${proj.published.currentVersion}` : ''}
                  </span>
                )}
                {!proj.readOnly && (
                  <button
                    style={styles.publishBtn}
                    onClick={(e) => {
                      e.stopPropagation();
                      handlePublish(proj.name);
                    }}
                  >
                    {proj.published ? 'Update' : 'Publish'}
                  </button>
                )}
                {!proj.name.startsWith('_') && (
                  <button
                    style={styles.deleteBtn}
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDelete(proj.name);
                    }}
                  >
                    Delete
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
      {toast && <div style={styles.toast}>{toast}</div>}
      {publishName && (
        <PublishModal
          projectName={publishName}
          onClose={() => setPublishName(null)}
          onSuccess={(result) => {
            setPublishName(null);
            setPublishSuccess(result);
          }}
          onUpsell={(info) => {
            setPublishName(null);
            setPublishUpsell(info);
          }}
        />
      )}
      {publishSuccess && (
        <PublishSuccessModal
          slug={publishSuccess.slug}
          publicUrl={publishSuccess.publicUrl}
          onClose={() => setPublishSuccess(null)}
        />
      )}
      {publishUpsell && (
        <UpsellModal
          plan={publishUpsell.plan}
          upgradeUrl={publishUpsell.upgradeUrl}
          onClose={() => setPublishUpsell(null)}
        />
      )}
    </div>
  );
}

export default ProjectList;
