import { useEffect, useState } from 'react';
import api from '../api';
import EditorPageHeader from '../components/EditorPageHeader';
import type { CSSProperties } from 'react';

interface ProviderConfig {
  label: string;
  apiUrl?: string;
  apiKey?: string;
}

interface CustomProvider {
  id: string;
  label: string;
  apiUrl?: string;
  apiKey?: string;
}

interface ProviderStatus {
  ok: boolean;
  error?: string;
  testedAt: string;
}

interface Settings {
  providers: {
    'stable-diffusion': ProviderConfig;
    openai: ProviderConfig;
    elevenlabs: ProviderConfig;
  };
  customProviders: CustomProvider[];
  imageProvider: string;
  soundProvider: string;
  providerStatus: Record<string, ProviderStatus>;
}

const styles: Record<string, CSSProperties> = {
  page: {
    padding: 32,
    maxWidth: 640,
  },
  title: {
    fontSize: 22,
    fontWeight: 700,
    color: '#1a1a2e',
    marginBottom: 24,
  },
  card: {
    backgroundColor: '#fff',
    borderRadius: 8,
    border: '1px solid #e0e0e0',
    padding: 20,
    marginBottom: 20,
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: 600,
    color: '#1a1a2e',
    marginBottom: 16,
  },
  providerBlock: {
    marginBottom: 16,
    paddingBottom: 16,
    borderBottom: '1px solid #f0f0f0',
  },
  providerLabel: {
    fontSize: 14,
    fontWeight: 600,
    color: '#333',
    marginBottom: 8,
  },
  fieldRow: {
    display: 'flex',
    gap: 8,
    marginBottom: 6,
    alignItems: 'center',
  },
  fieldLabel: {
    fontSize: 12,
    color: '#666',
    width: 60,
    flexShrink: 0,
  },
  input: {
    flex: 1,
    padding: '6px 10px',
    fontSize: 13,
    border: '1px solid #ccc',
    borderRadius: 4,
    outline: 'none',
  },
  select: {
    flex: 1,
    padding: '6px 10px',
    fontSize: 13,
    border: '1px solid #ccc',
    borderRadius: 4,
  },
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
  btnDanger: {
    padding: '4px 10px',
    fontSize: 12,
    fontWeight: 600,
    backgroundColor: '#d32f2f',
    color: '#fff',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
  },
  btnSecondary: {
    padding: '8px 16px',
    fontSize: 13,
    fontWeight: 600,
    backgroundColor: '#3949ab',
    color: '#fff',
    border: 'none',
    borderRadius: 5,
    cursor: 'pointer',
  },
  btnTest: {
    padding: '4px 10px',
    fontSize: 12,
    fontWeight: 600,
    backgroundColor: '#fff',
    color: '#1a1a2e',
    border: '1px solid #1a1a2e',
    borderRadius: 4,
    cursor: 'pointer',
    whiteSpace: 'nowrap',
  },
  note: {
    fontSize: 12,
    color: '#888',
    marginTop: 12,
  },
  actions: {
    display: 'flex',
    gap: 8,
    marginTop: 16,
  },
  status: {
    fontSize: 13,
    marginLeft: 12,
    color: '#4caf50',
  },
  testResult: {
    fontSize: 11,
    marginLeft: 8,
    fontWeight: 600,
  },
  statusBadge: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 4,
    fontSize: 11,
    fontWeight: 500,
    marginLeft: 8,
  },
  statusDot: {
    width: 7,
    height: 7,
    borderRadius: '50%',
    display: 'inline-block',
    flexShrink: 0,
  },
  statusTime: {
    fontSize: 10,
    color: '#999',
    marginLeft: 4,
  },
};

function SettingsEditor() {
  const [settings, setSettings] = useState<Settings | null>(null);
  const [saveStatus, setSaveStatus] = useState('');
  const [testResults, setTestResults] = useState<Record<string, { ok: boolean; error?: string } | null>>({});
  const [testing, setTesting] = useState<Record<string, boolean>>({});

  useEffect(() => {
    api
      .get<Settings>('/settings')
      .then(setSettings)
      .catch(() => {});
  }, []);

  const save = async () => {
    if (!settings) return;
    try {
      await api.put('/settings', settings);
      setSaveStatus('Saved');
      setTimeout(() => setSaveStatus(''), 2000);
    } catch (err: any) {
      alert(err.message || 'Failed to save settings');
    }
  };

  const updateProvider = (
    key: keyof Settings['providers'],
    field: 'apiUrl' | 'apiKey',
    value: string
  ) => {
    if (!settings) return;
    setSettings({
      ...settings,
      providers: {
        ...settings.providers,
        [key]: { ...settings.providers[key], [field]: value },
      },
    });
  };

  const addCustomProvider = () => {
    if (!settings) return;
    const id = `custom_${Date.now()}`;
    setSettings({
      ...settings,
      customProviders: [
        ...settings.customProviders,
        { id, label: '', apiUrl: '', apiKey: '' },
      ],
    });
  };

  const updateCustomProvider = (
    index: number,
    field: keyof CustomProvider,
    value: string
  ) => {
    if (!settings) return;
    const updated = [...settings.customProviders];
    updated[index] = { ...updated[index], [field]: value };
    setSettings({ ...settings, customProviders: updated });
  };

  const removeCustomProvider = (index: number) => {
    if (!settings) return;
    const updated = settings.customProviders.filter((_, i) => i !== index);
    setSettings({ ...settings, customProviders: updated });
  };

  const testProvider = async (providerId: string) => {
    setTesting((prev) => ({ ...prev, [providerId]: true }));
    setTestResults((prev) => ({ ...prev, [providerId]: null }));
    try {
      const result = await api.post<{ ok: boolean; error?: string; testedAt: string }>('/settings/test-provider', { providerId });
      setTestResults((prev) => ({ ...prev, [providerId]: result }));
      // Update persisted status in local state
      if (settings) {
        setSettings({
          ...settings,
          providerStatus: { ...settings.providerStatus, [providerId]: result },
        });
      }
    } catch (err: any) {
      setTestResults((prev) => ({ ...prev, [providerId]: { ok: false, error: err.message } }));
    } finally {
      setTesting((prev) => ({ ...prev, [providerId]: false }));
    }
  };

  const formatTimeAgo = (iso: string): string => {
    const diff = Date.now() - new Date(iso).getTime();
    const mins = Math.floor(diff / 60000);
    if (mins < 1) return 'just now';
    if (mins < 60) return `${mins}m ago`;
    const hrs = Math.floor(mins / 60);
    if (hrs < 24) return `${hrs}h ago`;
    const days = Math.floor(hrs / 24);
    return `${days}d ago`;
  };

  const renderStatusBadge = (providerId: string) => {
    const status = settings?.providerStatus?.[providerId];
    if (!status) {
      return (
        <span style={styles.statusBadge}>
          <span style={{ ...styles.statusDot, backgroundColor: '#bbb' }} />
          <span style={{ color: '#999' }}>Not tested</span>
        </span>
      );
    }
    return (
      <span style={styles.statusBadge}>
        <span style={{ ...styles.statusDot, backgroundColor: status.ok ? '#4caf50' : '#d32f2f' }} />
        <span style={{ color: status.ok ? '#4caf50' : '#d32f2f' }}>
          {status.ok ? 'Connected' : (status.error || 'Failed')}
        </span>
        <span style={styles.statusTime}>({formatTimeAgo(status.testedAt)})</span>
      </span>
    );
  };

  // Build provider options for dropdowns
  const imageProviderOptions = [
    { value: 'stable-diffusion', label: 'Stable Diffusion' },
    { value: 'openai', label: 'OpenAI DALL-E' },
    ...(settings?.customProviders || []).map((cp) => ({ value: cp.id, label: cp.label || cp.id })),
  ];

  const soundProviderOptions = [
    { value: 'elevenlabs', label: 'ElevenLabs' },
    ...(settings?.customProviders || []).map((cp) => ({ value: cp.id, label: cp.label || cp.id })),
  ];

  const renderTestButton = (providerId: string) => (
    <>
      <button
        style={{ ...styles.btnTest, opacity: testing[providerId] ? 0.5 : 1 }}
        onClick={() => testProvider(providerId)}
        disabled={testing[providerId]}
      >
        {testing[providerId] ? 'Testing...' : 'Test'}
      </button>
      {testResults[providerId] && (
        <span style={{ ...styles.testResult, color: testResults[providerId]!.ok ? '#4caf50' : '#d32f2f' }}>
          {testResults[providerId]!.ok ? 'Connected' : testResults[providerId]!.error || 'Failed'}
        </span>
      )}
    </>
  );

  if (!settings) return null;

  return (
    <form style={styles.page} onSubmit={(e) => { e.preventDefault(); save(); }} autoComplete="off">
      <h2 style={styles.title}>Settings</h2>
      <EditorPageHeader storageKey="settings">
        Configure AI providers for image and sound generation. Each provider needs an API URL and key.
        Use "Test" to verify connectivity. The active provider dropdowns control which service is used
        when generating assets from the Maker.
      </EditorPageHeader>

      {/* Active Providers */}
      <div style={styles.card}>
        <div style={styles.sectionTitle}>Active Providers</div>
        <div style={styles.fieldRow}>
          <span style={{ ...styles.fieldLabel, width: 100 }}>Image Provider</span>
          <select
            style={styles.select}
            value={settings.imageProvider}
            onChange={(e) => setSettings({ ...settings, imageProvider: e.target.value })}
          >
            {imageProviderOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
          {renderTestButton(settings.imageProvider)}
        </div>
        <div style={{ ...styles.fieldRow, marginTop: 8 }}>
          <span style={{ ...styles.fieldLabel, width: 100 }}>Sound Provider</span>
          <select
            style={styles.select}
            value={settings.soundProvider}
            onChange={(e) => setSettings({ ...settings, soundProvider: e.target.value })}
          >
            {soundProviderOptions.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </select>
          {renderTestButton(settings.soundProvider)}
        </div>
      </div>

      <div style={styles.card}>
        <div style={styles.sectionTitle}>Built-in Providers</div>

        <div style={styles.providerBlock}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <div style={styles.providerLabel}>Stable Diffusion{renderStatusBadge('stable-diffusion')}</div>
            {renderTestButton('stable-diffusion')}
          </div>
          <div style={styles.fieldRow}>
            <span style={styles.fieldLabel}>URL</span>
            <input
              style={styles.input}
              type="text"
              value={settings.providers['stable-diffusion'].apiUrl || ''}
              onChange={(e) =>
                updateProvider('stable-diffusion', 'apiUrl', e.target.value)
              }
              placeholder="http://localhost:7860"
            />
          </div>
          <div style={styles.fieldRow}>
            <span style={styles.fieldLabel}>Key</span>
            <input
              style={styles.input}
              type="password"
              autoComplete="new-password"
              value={settings.providers['stable-diffusion'].apiKey || ''}
              onChange={(e) =>
                updateProvider('stable-diffusion', 'apiKey', e.target.value)
              }
            />
          </div>
        </div>

        <div style={styles.providerBlock}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <div style={styles.providerLabel}>OpenAI{renderStatusBadge('openai')}</div>
            {renderTestButton('openai')}
          </div>
          <div style={styles.fieldRow}>
            <span style={styles.fieldLabel}>URL</span>
            <input
              style={styles.input}
              type="text"
              value={settings.providers.openai.apiUrl || ''}
              onChange={(e) =>
                updateProvider('openai', 'apiUrl', e.target.value)
              }
              placeholder="https://api.openai.com/v1"
            />
          </div>
          <div style={styles.fieldRow}>
            <span style={styles.fieldLabel}>Key</span>
            <input
              style={styles.input}
              type="password"
              autoComplete="new-password"
              value={settings.providers.openai.apiKey || ''}
              onChange={(e) =>
                updateProvider('openai', 'apiKey', e.target.value)
              }
            />
          </div>
        </div>

        <div style={{ ...styles.providerBlock, borderBottom: 'none', marginBottom: 0, paddingBottom: 0 }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <div style={styles.providerLabel}>ElevenLabs{renderStatusBadge('elevenlabs')}</div>
            {renderTestButton('elevenlabs')}
          </div>
          <div style={styles.fieldRow}>
            <span style={styles.fieldLabel}>URL</span>
            <input
              style={styles.input}
              type="text"
              value={settings.providers.elevenlabs.apiUrl || ''}
              onChange={(e) =>
                updateProvider('elevenlabs', 'apiUrl', e.target.value)
              }
              placeholder="https://api.elevenlabs.io/v1"
            />
          </div>
          <div style={styles.fieldRow}>
            <span style={styles.fieldLabel}>Key</span>
            <input
              style={styles.input}
              type="password"
              autoComplete="new-password"
              value={settings.providers.elevenlabs.apiKey || ''}
              onChange={(e) =>
                updateProvider('elevenlabs', 'apiKey', e.target.value)
              }
            />
          </div>
        </div>
      </div>

      <div style={styles.card}>
        <div style={styles.sectionTitle}>Custom Providers</div>
        {settings.customProviders.map((cp, i) => (
          <div key={cp.id} style={styles.providerBlock}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
              <div style={{ display: 'flex', alignItems: 'center' }}>
                <input
                  style={{ ...styles.input, fontWeight: 600, maxWidth: 200, marginBottom: 0 }}
                  type="text"
                  value={cp.label}
                  onChange={(e) => updateCustomProvider(i, 'label', e.target.value)}
                  placeholder="Provider name"
                />
                {renderStatusBadge(cp.id)}
              </div>
              <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
                {renderTestButton(cp.id)}
                <button style={styles.btnDanger} onClick={() => removeCustomProvider(i)}>
                  Remove
                </button>
              </div>
            </div>
            <div style={styles.fieldRow}>
              <span style={styles.fieldLabel}>URL</span>
              <input
                style={styles.input}
                type="text"
                value={cp.apiUrl || ''}
                onChange={(e) => updateCustomProvider(i, 'apiUrl', e.target.value)}
                placeholder="https://..."
              />
            </div>
            <div style={styles.fieldRow}>
              <span style={styles.fieldLabel}>Key</span>
              <input
                style={styles.input}
                type="password"
              autoComplete="new-password"
                value={cp.apiKey || ''}
                onChange={(e) => updateCustomProvider(i, 'apiKey', e.target.value)}
              />
            </div>
          </div>
        ))}
        <button style={styles.btnSecondary} onClick={addCustomProvider}>
          Add Provider
        </button>
      </div>

      <div style={styles.actions}>
        <button style={styles.btn} onClick={save}>
          Save Settings
        </button>
        {saveStatus && <span style={styles.status}>{saveStatus}</span>}
      </div>
      <p style={styles.note}>
        Keys are stored locally in maker/settings.json
      </p>
    </form>
  );
}

export default SettingsEditor;
