import { useEffect, useState } from 'react';
import api from '../api';
import ImagePreview from './ImagePreview';
import SfxPreview from './SfxPreview';
import type { CSSProperties } from 'react';

// Letters whose names start with a vowel sound (e.g., N="en", M="em", S="es")
const VOWEL_SOUND_LETTERS = /^[AEFHILMNORSX]/i

function articleFor(word: string): string {
  if (/^[aeiou]/i.test(word)) return 'an'
  if (/^[A-Z]{2,}/.test(word) && VOWEL_SOUND_LETTERS.test(word)) return 'an'
  return 'a'
}

export interface FieldConfig {
  key: string;
  label: string;
  type: 'text' | 'textarea' | 'number' | 'checkbox' | 'select' | 'json' | 'radio' | 'sfx' | 'stat-grid' | 'checklist' | 'school-levels';
  options?: { value: string; label: string }[];
  placeholder?: string;
  disabled?: boolean;
  required?: boolean;
  rows?: number;
  help?: string;
  max?: number;
  /** Audio subdirectory for sfx fields: 'general', 'npcs', 'items', 'spells', 'rooms' */
  audioCategory?: string;
  visibleWhen?: (form: Record<string, any>) => boolean;
  /** stat-grid: stat keys to render (defaults to STAT_KEYS) */
  statKeys?: string[];
  /** stat-grid: whether negative values are allowed */
  allowNegative?: boolean;
  /** checklist: options for the checkbox grid */
  checklistOptions?: { value: string; label: string; group?: string }[];
}

interface GenericCrudEditorProps {
  entityName: string;
  apiPath: string;
  fields: FieldConfig[];
  idField?: string;
  imagePreview?: { entityType: string; maxWidth?: number; maxHeight?: number };
  disableCreate?: boolean;
  disableCreateMessage?: string;
}

const IMAGE_PREVIEW_KEYS = new Set(['imagePrompt', 'imageStyle', 'imageNegativePrompt', 'imageWidth', 'imageHeight']);

const STAT_KEYS = ['strength', 'agility', 'intellect', 'willpower', 'health', 'charm'];
const STAT_LABELS: Record<string, string> = {
  strength: 'Strength', agility: 'Agility', intellect: 'Intellect',
  willpower: 'Willpower', health: 'Health', charm: 'Charm',
};

const MAGIC_SCHOOLS = ['mage', 'priest', 'druid', 'kai', 'bard'];
const SCHOOL_LABELS: Record<string, string> = {
  mage: 'Mage', priest: 'Priest', druid: 'Druid', kai: 'Kai', bard: 'Bard',
};

function parseJsonObj(val: string | undefined): Record<string, any> {
  if (!val) return {};
  try {
    const parsed = JSON.parse(val);
    return typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch { return {}; }
}

function parseJsonArr(val: string | undefined): string[] {
  if (!val) return [];
  try {
    const parsed = JSON.parse(val);
    return Array.isArray(parsed) ? parsed : [];
  } catch { return []; }
}

const styles: Record<string, CSSProperties> = {
  container: {
    display: 'flex',
    height: '100%',
    overflow: 'hidden',
  },
  listPanel: {
    width: 240,
    minWidth: 240,
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
  newBtn: {
    width: '100%',
    padding: '8px',
    fontSize: 13,
    fontWeight: 600,
    backgroundColor: '#1a1a2e',
    color: '#fff',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
  },
  listItems: {
    flex: 1,
    overflowY: 'auto',
    padding: '4px 0',
  },
  listItem: {
    padding: '8px 12px',
    cursor: 'pointer',
    fontSize: 13,
    borderBottom: '1px solid #f0f0f0',
  },
  listItemSelected: {
    backgroundColor: '#e8eaf6',
    fontWeight: 600,
  },
  formPanel: {
    flex: 1,
    padding: 20,
    overflowY: 'auto',
    backgroundColor: '#fafafa',
  },
  sectionTitle: {
    fontSize: 16,
    fontWeight: 700,
    marginBottom: 16,
    color: '#1a1a2e',
  },
  field: {
    marginBottom: 12,
  },
  label: {
    display: 'block',
    fontSize: 11,
    fontWeight: 600,
    color: '#666',
    marginBottom: 3,
  },
  help: {
    fontSize: 10,
    color: '#999',
    marginTop: 2,
  },
  input: {
    width: '100%',
    padding: '6px 8px',
    fontSize: 13,
    border: '1px solid #ccc',
    borderRadius: 4,
    boxSizing: 'border-box' as const,
  },
  textarea: {
    width: '100%',
    padding: '6px 8px',
    fontSize: 13,
    border: '1px solid #ccc',
    borderRadius: 4,
    minHeight: 60,
    resize: 'vertical' as const,
    boxSizing: 'border-box' as const,
  },
  jsonTextarea: {
    width: '100%',
    padding: '6px 8px',
    fontSize: 12,
    fontFamily: 'monospace',
    border: '1px solid #ccc',
    borderRadius: 4,
    minHeight: 80,
    resize: 'vertical' as const,
    boxSizing: 'border-box' as const,
  },
  select: {
    width: '100%',
    padding: '6px 8px',
    fontSize: 13,
    border: '1px solid #ccc',
    borderRadius: 4,
    boxSizing: 'border-box' as const,
  },
  btnRow: {
    display: 'flex',
    gap: 8,
    marginTop: 16,
  },
  btnSmall: {
    padding: '8px 16px',
    fontSize: 13,
    fontWeight: 600,
    backgroundColor: '#1a1a2e',
    color: '#fff',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
  },
  btnDanger: {
    padding: '8px 16px',
    fontSize: 13,
    fontWeight: 600,
    backgroundColor: '#d32f2f',
    color: '#fff',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
  },
  error: {
    color: '#d32f2f',
    fontSize: 12,
    marginTop: 4,
  },
  empty: {
    color: '#999',
    fontSize: 13,
    textAlign: 'center',
    marginTop: 40,
  },
};

function prettyJson(value: string): string {
  if (!value || value === '') return '';
  try {
    return JSON.stringify(JSON.parse(value), null, 2);
  } catch {
    return value;
  }
}

function GenericCrudEditor({ entityName, apiPath, fields, idField = 'id', imagePreview, disableCreate, disableCreateMessage }: GenericCrudEditorProps) {
  const [items, setItems] = useState<any[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [form, setForm] = useState<Record<string, any>>({});
  const [isNew, setIsNew] = useState(false);
  const [error, setError] = useState('');
  const [jsonErrors, setJsonErrors] = useState<Record<string, string>>({});
  const [search, setSearch] = useState('');

  const loadList = () => {
    // Defense-in-depth: coerce to array at the setter boundary even
    // though api.ts now guarantees JSON-or-throw. A future regression
    // in either layer can't re-poison state with undefined.
    api
      .get<any[]>(apiPath)
      .then((data) => setItems(Array.isArray(data) ? data : []))
      .catch(() => {});
  };

  useEffect(() => {
    loadList();
  }, [apiPath]);

  const handleSelect = (id: string) => {
    const item = items.find((i) => i[idField] === id);
    if (!item) return;
    setSelectedId(id);
    setIsNew(false);
    setError('');
    setJsonErrors({});
    // Pretty-print JSON fields on load
    const formData: Record<string, any> = { ...item };
    for (const field of fields) {
      if (field.type === 'json' && typeof formData[field.key] === 'string') {
        formData[field.key] = prettyJson(formData[field.key]);
      }
    }
    setForm(formData);
  };

  const handleNew = () => {
    setSelectedId(null);
    setIsNew(true);
    setError('');
    setJsonErrors({});
    const defaults: Record<string, any> = {};
    for (const field of fields) {
      if (field.type === 'checkbox') defaults[field.key] = false;
      else if (field.type === 'number') defaults[field.key] = 0;
      else if (field.type === 'json') defaults[field.key] = '';
      else defaults[field.key] = '';
    }
    setForm(defaults);
  };

  const handleChange = (key: string, value: any) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    // Clear json error for this field when user edits
    if (jsonErrors[key]) {
      setJsonErrors((prev) => {
        const next = { ...prev };
        delete next[key];
        return next;
      });
    }
  };

  const handleSave = async () => {
    setError('');
    // Validate required fields
    for (const field of fields) {
      if (field.required && (!field.visibleWhen || field.visibleWhen(form))) {
        const val = form[field.key];
        if (val === undefined || val === null || val === '') {
          setError(`${field.label} is required`);
          return;
        }
      }
    }
    // Validate JSON fields
    const newJsonErrors: Record<string, string> = {};
    const submitData: Record<string, any> = { ...form };
    for (const field of fields) {
      if (field.type === 'json') {
        const val = submitData[field.key];
        if (val && val.trim() !== '') {
          try {
            JSON.parse(val);
          } catch {
            newJsonErrors[field.key] = 'Invalid JSON';
          }
        }
      }
    }
    if (Object.keys(newJsonErrors).length > 0) {
      setJsonErrors(newJsonErrors);
      return;
    }

    try {
      if (isNew) {
        const created = await api.post<any>(apiPath, submitData);
        setItems((prev) => [...prev, created]);
        setSelectedId(created[idField]);
        setIsNew(false);
        setForm(created);
        // Pretty-print JSON fields
        const formData: Record<string, any> = { ...created };
        for (const field of fields) {
          if (field.type === 'json' && typeof formData[field.key] === 'string') {
            formData[field.key] = prettyJson(formData[field.key]);
          }
        }
        setForm(formData);
      } else if (selectedId) {
        const { [idField]: _id, ...updateData } = submitData;
        const updated = await api.put<any>(`${apiPath}/${selectedId}`, updateData);
        setItems((prev) => prev.map((i) => (i[idField] === selectedId ? updated : i)));
        const formData: Record<string, any> = { ...updated };
        for (const field of fields) {
          if (field.type === 'json' && typeof formData[field.key] === 'string') {
            formData[field.key] = prettyJson(formData[field.key]);
          }
        }
        setForm(formData);
      }
    } catch (err: any) {
      setError(err.message || 'Save failed');
    }
  };

  const handleDelete = async () => {
    if (!selectedId) return;
    if (!confirm(`Delete this ${entityName}?`)) return;
    try {
      await api.del(`${apiPath}/${selectedId}`);
      setItems((prev) => prev.filter((i) => i[idField] !== selectedId));
      setSelectedId(null);
      setForm({});
      setIsNew(false);
    } catch (err: any) {
      setError(err.message || 'Delete failed');
    }
  };

  const showForm = isNew || selectedId !== null;

  return (
    <div style={styles.container}>
      <div style={styles.listPanel}>
        <div style={styles.listTop}>
          <button
            style={{ ...styles.newBtn, ...(disableCreate ? { opacity: 0.5, cursor: 'not-allowed' } : {}) }}
            onClick={disableCreate ? undefined : handleNew}
            disabled={disableCreate}
          >
            + New {entityName}
          </button>
          {disableCreate && disableCreateMessage && (
            <div style={{ fontSize: 11, color: '#999', marginTop: 6, textAlign: 'center' }}>
              {disableCreateMessage}
            </div>
          )}
          <input
            type="text"
            placeholder={`Search ${entityName.endsWith('s') || entityName.endsWith('x') || entityName.endsWith('sh') || entityName.endsWith('ch') ? entityName + 'es' : entityName + 's'}...`}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ ...styles.input, marginTop: 8, fontSize: 12 }}
          />
        </div>
        <div style={styles.listItems}>
          {items
            .filter((item) => {
              if (!search) return true;
              const q = search.toLowerCase();
              const name = (item.name || '').toLowerCase();
              const id = (item[idField] || '').toLowerCase();
              return name.includes(q) || id.includes(q);
            })
            .sort((a, b) => (a.name || a[idField] || '').localeCompare(b.name || b[idField] || ''))
            .map((item) => (
            <div
              key={item[idField]}
              style={{
                ...styles.listItem,
                ...(item[idField] === selectedId && !isNew ? styles.listItemSelected : {}),
              }}
              onClick={() => handleSelect(item[idField])}
            >
              {item.name || item[idField]}
            </div>
          ))}
        </div>
      </div>
      <div style={styles.formPanel}>
        {showForm ? (
          <>
            <div style={styles.sectionTitle}>
              {isNew ? `New ${entityName}` : `Edit ${entityName}`}
            </div>
            {imagePreview && !isNew && selectedId && (
              <ImagePreview
                entityType={imagePreview.entityType}
                entityId={selectedId}
                description={form.description}
                assetPath={imagePreview.entityType === 'room' ? `images/rooms/${selectedId.replace(':', '_')}.webp` : undefined}
                imagePrompt={form.imagePrompt}
                imageStyle={form.imageStyle}
                imageNegativePrompt={form.imageNegativePrompt}
                imageWidth={form.imageWidth}
                imageHeight={form.imageHeight}
                maxWidth={imagePreview.maxWidth}
                maxHeight={imagePreview.maxHeight}
                onUpdate={(fields) => setForm((f: any) => ({ ...f, ...fields }))}
              />
            )}
            {fields.filter((f) => {
              if (f.visibleWhen && !f.visibleWhen(form)) return false;
              // Hide image fields when ImagePreview is shown (it manages them)
              if (imagePreview && !isNew && selectedId && IMAGE_PREVIEW_KEYS.has(f.key)) return false;
              return true;
            }).map((field) => {
              const isIdDisabled = field.key === idField && !isNew;
              const disabled = field.disabled || isIdDisabled;
              return (
                <div key={field.key} style={styles.field}>
                  <label style={styles.label}>{field.label}</label>
                  {field.type === 'text' && (
                    <input
                      style={{ ...styles.input, ...(disabled ? { backgroundColor: '#eee' } : {}) }}
                      value={form[field.key] ?? ''}
                      placeholder={field.placeholder}
                      disabled={disabled}
                      onChange={(e) => handleChange(field.key, e.target.value)}
                    />
                  )}
                  {field.type === 'textarea' && (
                    <textarea
                      style={styles.textarea}
                      value={form[field.key] ?? ''}
                      placeholder={field.placeholder}
                      rows={field.rows ?? 3}
                      onChange={(e) => handleChange(field.key, e.target.value)}
                    />
                  )}
                  {field.type === 'number' && (
                    <input
                      style={styles.input}
                      type="number"
                      value={form[field.key] ?? 0}
                      max={field.max}
                      onChange={(e) => {
                        let v = parseFloat(e.target.value) || 0;
                        if (field.max != null) v = Math.min(v, field.max);
                        handleChange(field.key, v);
                      }}
                    />
                  )}
                  {field.type === 'checkbox' && (
                    <label style={{ fontSize: 13 }}>
                      <input
                        type="checkbox"
                        checked={form[field.key] ?? false}
                        onChange={(e) => handleChange(field.key, e.target.checked)}
                      />{' '}
                      {field.placeholder || 'Enabled'}
                    </label>
                  )}
                  {field.type === 'select' && (
                    <select
                      style={styles.select}
                      value={form[field.key] ?? ''}
                      onChange={(e) => handleChange(field.key, e.target.value)}
                    >
                      <option value="">-- Select --</option>
                      {(field.options || []).map((opt) => (
                        <option key={opt.value} value={opt.value}>
                          {opt.label}
                        </option>
                      ))}
                    </select>
                  )}
                  {field.type === 'json' && (
                    <>
                      <textarea
                        style={styles.jsonTextarea}
                        value={form[field.key] ?? ''}
                        placeholder={field.placeholder || '{}'}
                        rows={field.rows ?? 4}
                        onChange={(e) => handleChange(field.key, e.target.value)}
                      />
                      {jsonErrors[field.key] && (
                        <div style={styles.error}>{jsonErrors[field.key]}</div>
                      )}
                    </>
                  )}
                  {field.type === 'radio' && (
                    <div style={{ display: 'flex', gap: 12 }}>
                      {(field.options || []).map((opt) => (
                        <label key={opt.value} style={{ fontSize: 13, cursor: 'pointer' }}>
                          <input
                            type="radio"
                            name={field.key}
                            value={opt.value}
                            checked={form[field.key] === opt.value}
                            onChange={() => handleChange(field.key, opt.value)}
                          />{' '}{opt.label}
                        </label>
                      ))}
                    </div>
                  )}
                  {field.type === 'sfx' && (
                    <SfxPreview
                      soundId={form[field.key] ?? ''}
                      onSoundIdChange={(id) => handleChange(field.key, id)}
                      entityLabel={form.name || form.id || selectedId || ''}
                      audioCategory={field.audioCategory}
                    />
                  )}
                  {field.type === 'stat-grid' && (() => {
                    const keys = field.statKeys || STAT_KEYS;
                    const parsed = parseJsonObj(form[field.key]);
                    return (
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '6px 12px' }}>
                        {keys.map((stat) => (
                          <div key={stat}>
                            <label style={{ fontSize: 10, color: '#888' }}>{STAT_LABELS[stat] || stat}</label>
                            <input
                              style={styles.input}
                              type="number"
                              value={parsed[stat] ?? 0}
                              min={field.allowNegative ? undefined : 0}
                              onChange={(e) => {
                                const v = parseInt(e.target.value) || 0;
                                const next = { ...parsed, [stat]: v };
                                // Remove zero values for cleaner JSON
                                const clean: Record<string, number> = {};
                                for (const k of keys) {
                                  if (next[k] && next[k] !== 0) clean[k] = next[k];
                                }
                                handleChange(field.key, Object.keys(clean).length > 0 ? JSON.stringify(clean) : '');
                              }}
                            />
                          </div>
                        ))}
                      </div>
                    );
                  })()}
                  {field.type === 'checklist' && (() => {
                    const checked = new Set(parseJsonArr(form[field.key]));
                    const opts = field.checklistOptions || [];
                    // Group options if groups exist
                    const groups = new Map<string, typeof opts>();
                    for (const opt of opts) {
                      const g = opt.group || '';
                      if (!groups.has(g)) groups.set(g, []);
                      groups.get(g)!.push(opt);
                    }
                    return (
                      <div>
                        {Array.from(groups.entries()).map(([group, groupOpts]) => (
                          <div key={group}>
                            {group && <div style={{ fontSize: 10, fontWeight: 600, color: '#888', marginTop: 6, marginBottom: 2, textTransform: 'capitalize' }}>{group}</div>}
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: '2px 8px' }}>
                              {groupOpts.map((opt) => (
                                <label key={opt.value} style={{ fontSize: 12, cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 4 }}>
                                  <input
                                    type="checkbox"
                                    checked={checked.has(opt.value)}
                                    onChange={(e) => {
                                      const next = new Set(checked);
                                      if (e.target.checked) next.add(opt.value);
                                      else next.delete(opt.value);
                                      const arr = Array.from(next);
                                      handleChange(field.key, arr.length > 0 ? JSON.stringify(arr) : '');
                                    }}
                                  />
                                  {opt.label}
                                </label>
                              ))}
                            </div>
                          </div>
                        ))}
                      </div>
                    );
                  })()}
                  {field.type === 'school-levels' && (() => {
                    const parsed = parseJsonObj(form[field.key]);
                    return (
                      <div style={{ display: 'grid', gap: 4 }}>
                        {MAGIC_SCHOOLS.map((school) => (
                          <div key={school} style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                            <span style={{ fontSize: 12, width: 50, fontWeight: 500 }}>{SCHOOL_LABELS[school]}</span>
                            <select
                              style={{ ...styles.select, width: 80 }}
                              value={parsed[school] ?? 0}
                              onChange={(e) => {
                                const level = parseInt(e.target.value) || 0;
                                const next = { ...parsed };
                                if (level === 0) delete next[school];
                                else next[school] = level;
                                handleChange(field.key, Object.keys(next).length > 0 ? JSON.stringify(next) : '');
                              }}
                            >
                              <option value={0}>None</option>
                              <option value={1}>Level 1</option>
                              <option value={2}>Level 2</option>
                              <option value={3}>Level 3</option>
                            </select>
                          </div>
                        ))}
                      </div>
                    );
                  })()}
                  {field.help && <div style={styles.help}>{field.help}</div>}
                </div>
              );
            })}
            {error && <div style={styles.error}>{error}</div>}
            <div style={styles.btnRow}>
              <button style={styles.btnSmall} onClick={handleSave}>
                {isNew ? 'Create' : 'Save'}
              </button>
              {!isNew && selectedId && (
                <button style={styles.btnDanger} onClick={handleDelete}>
                  Delete
                </button>
              )}
            </div>
          </>
        ) : (
          <div style={styles.empty}>
            Select {articleFor(entityName)} {/^[A-Z]{2,}$/.test(entityName) ? entityName : entityName.toLowerCase()} to edit, or click "+ New {entityName}" to create one.
          </div>
        )}
      </div>
    </div>
  );
}

export default GenericCrudEditor;
