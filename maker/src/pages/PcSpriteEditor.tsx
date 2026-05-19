import { useEffect, useMemo, useState } from 'react';
import api, { resolveUrl } from '../api';
import { useSignedAssetUrls } from '../hooks/useSignedAssetUrl';
import ImagePreview from '../components/ImagePreview';
import EditorPageHeader from '../components/EditorPageHeader';
import type { CSSProperties } from 'react';

const RACES = ['HUMAN', 'DWARF', 'ELF', 'HALFLING', 'GNOME', 'HALF_ORC'];
const GENDERS = ['male', 'female', 'neutral'];
const CLASSES = [
  'WARRIOR', 'PALADIN', 'WITCHHUNTER', 'CLERIC', 'PRIEST', 'MISSIONARY',
  'MAGE', 'WARLOCK', 'DRUID', 'RANGER', 'THIEF', 'NINJA', 'MYSTIC', 'BARD', 'GYPSY',
];

interface PcSprite {
  id: string;
  race: string;
  gender: string;
  characterClass: string;
  imagePrompt: string;
  imageStyle: string;
  imageNegativePrompt: string;
  imageWidth: number;
  imageHeight: number;
}

interface CharacterClass {
  id: string;
  name: string;
  description: string;
  minimumStats: string;
  skills: string;
  magicSchools: string;
  hpPerLevelMin: number;
  hpPerLevelMax: number;
  mpPerLevelMin: number;
  mpPerLevelMax: number;
  xpModifier: number;
}

interface Race {
  id: string;
  name: string;
  description: string;
  statModifiers: string;
  xpModifier: number;
}

// --- helpers ---

function titleCase(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase().replace(/_/g, ' ');
}

function formatLabel(sprite: PcSprite): string {
  return `${titleCase(sprite.race)} ${titleCase(sprite.gender)} ${titleCase(sprite.characterClass)}`;
}

function safeJsonParse<T>(s: string | undefined | null, fallback: T): T {
  if (!s) return fallback;
  try { return JSON.parse(s); } catch { return fallback; }
}

// --- styles ---

const styles: Record<string, CSSProperties> = {
  container: {
    display: 'flex',
    height: '100%',
    overflow: 'hidden',
  },
  listPanel: {
    width: 260,
    minWidth: 260,
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
  filterBar: {
    display: 'flex',
    flexDirection: 'column',
    gap: 6,
  },
  filterRow: {
    display: 'flex',
    gap: 6,
  },
  select: {
    flex: 1,
    padding: '5px 6px',
    fontSize: 12,
    border: '1px solid #ccc',
    borderRadius: 3,
    backgroundColor: '#fff',
  },
  listCount: {
    fontSize: 11,
    color: '#999',
    textAlign: 'center',
    padding: '4px 0',
    borderBottom: '1px solid #f0f0f0',
  },
  listItems: {
    flex: 1,
    overflowY: 'auto',
    padding: '2px 0',
  },
  listItem: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '6px 10px',
    cursor: 'pointer',
    fontSize: 12,
    borderBottom: '1px solid #f5f5f5',
  },
  listItemSelected: {
    backgroundColor: '#e8eaf6',
    fontWeight: 600,
  },
  listThumb: {
    width: 32,
    height: 32,
    objectFit: 'contain',
    borderRadius: 3,
    backgroundColor: '#f0f0f0',
    flexShrink: 0,
  },
  btnOutline: {
    padding: '5px 10px',
    fontSize: 11,
    fontWeight: 600,
    backgroundColor: '#fff',
    color: '#1a1a2e',
    border: '1px solid #1a1a2e',
    borderRadius: 3,
    cursor: 'pointer',
    width: '100%',
  },
  detailPanel: {
    flex: 1,
    padding: 20,
    overflowY: 'auto',
    backgroundColor: '#fafafa',
  },
  detailTitle: {
    fontSize: 18,
    fontWeight: 700,
    color: '#1a1a2e',
    marginBottom: 4,
  },
  detailSubtitle: {
    fontSize: 13,
    color: '#666',
    marginBottom: 16,
  },
  detailGrid: {
    display: 'flex',
    gap: 24,
    marginBottom: 20,
    flexWrap: 'wrap',
  },
  imageCol: {
    flexShrink: 0,
  },
  largeImage: {
    width: 256,
    height: 340,
    objectFit: 'contain',
    backgroundColor: '#e8e8e8',
    borderRadius: 8,
    border: '1px solid #ddd',
    display: 'block',
  },
  statsCol: {
    flex: 1,
    minWidth: 240,
  },
  sectionLabel: {
    fontSize: 11,
    fontWeight: 700,
    color: '#999',
    textTransform: 'uppercase',
    letterSpacing: 0.5,
    marginBottom: 6,
    marginTop: 12,
  },
  statsGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(3, 1fr)',
    gap: 6,
  },
  statCell: {
    padding: '6px 8px',
    backgroundColor: '#fff',
    borderRadius: 4,
    border: '1px solid #e0e0e0',
    textAlign: 'center',
  },
  statName: {
    fontSize: 10,
    fontWeight: 600,
    color: '#888',
    marginBottom: 2,
  },
  statValue: {
    fontSize: 16,
    fontWeight: 700,
    color: '#1a1a2e',
  },
  statMod: {
    fontSize: 10,
    fontWeight: 600,
  },
  infoRow: {
    display: 'flex',
    gap: 12,
    marginBottom: 4,
  },
  infoLabel: {
    fontSize: 12,
    fontWeight: 600,
    color: '#888',
    width: 100,
    flexShrink: 0,
  },
  infoValue: {
    fontSize: 12,
    color: '#333',
  },
  tagRow: {
    display: 'flex',
    gap: 4,
    flexWrap: 'wrap',
  },
  tag: {
    display: 'inline-block',
    padding: '2px 8px',
    fontSize: 11,
    fontWeight: 600,
    borderRadius: 3,
    backgroundColor: '#e8eaf6',
    color: '#3949ab',
  },
  tagMagic: {
    display: 'inline-block',
    padding: '2px 8px',
    fontSize: 11,
    fontWeight: 600,
    borderRadius: 3,
    backgroundColor: '#f3e5f5',
    color: '#7b1fa2',
  },
  promptSection: {
    marginTop: 20,
    padding: 16,
    borderTop: '1px solid #e0e0e0',
  },
  promptSectionTitle: {
    fontSize: 14,
    fontWeight: 700,
    color: '#1a1a2e',
    marginBottom: 12,
  },
  empty: {
    color: '#999',
    fontSize: 13,
    textAlign: 'center',
    marginTop: 60,
  },
  validationOk: {
    fontSize: 11,
    color: '#4caf50',
    fontWeight: 600,
    marginTop: 4,
  },
  validationWarn: {
    fontSize: 11,
    color: '#f57c00',
    fontWeight: 600,
    marginTop: 4,
  },
};

function PcSpriteEditor() {
  const [sprites, setSprites] = useState<PcSprite[]>([]);
  const [filterRace, setFilterRace] = useState('');
  const [filterGender, setFilterGender] = useState('');
  const [filterClass, setFilterClass] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [classes, setClasses] = useState<CharacterClass[]>([]);
  const [races, setRaces] = useState<Race[]>([]);

  const fetchSprites = () => {
    const params = new URLSearchParams();
    if (filterRace) params.set('race', filterRace);
    if (filterGender) params.set('gender', filterGender);
    if (filterClass) params.set('class', filterClass);
    const qs = params.toString();
    // Array-coerce at the setter boundary — see src/api.ts for rationale.
    api.get<PcSprite[]>(`/pc-sprites${qs ? `?${qs}` : ''}`)
      .then((d) => setSprites(Array.isArray(d) ? d : []))
      .catch(console.error);
  };

  useEffect(() => { fetchSprites(); }, [filterRace, filterGender, filterClass]);

  useEffect(() => {
    api.get<CharacterClass[]>('/character-classes')
      .then((d) => setClasses(Array.isArray(d) ? d : []))
      .catch(() => {});
    api.get<Race[]>('/races')
      .then((d) => setRaces(Array.isArray(d) ? d : []))
      .catch(() => {});
  }, []);

  const selected = sprites.find((s) => s.id === selectedId) || null;
  const selectedClass = selected ? classes.find((c) => c.id === selected.characterClass) : null;
  const selectedRace = selected ? races.find((r) => r.id === selected.race) : null;

  // Bulk-sign all sprite thumbnails + the large preview in one request.
  // Include the selected sprite's path too so the detail pane isn't
  // subject to a second round-trip.
  const spriteApiPaths = useMemo(
    () => sprites.map((s) => resolveUrl(`/assets/images/players/${s.id}.webp`)),
    [sprites],
  );
  const signedSpriteUrls = useSignedAssetUrls(spriteApiPaths);

  const handleUpdate = (fields: { imagePrompt: string; imageStyle: string; imageNegativePrompt: string; imageWidth: number; imageHeight: number }) => {
    if (!selectedId) return;
    api.put<PcSprite>(`/pc-sprites/${selectedId}`, fields).then((updated) => {
      setSprites((prev) => prev.map((s) => (s.id === updated.id ? updated : s)));
    }).catch(console.error);
  };

  const handleCopyAllPrompts = () => {
    const prompts = sprites.map((s) => {
      return [s.imagePrompt, s.imageStyle && `Style: ${s.imageStyle}`, s.imageNegativePrompt && `Negative: ${s.imageNegativePrompt}`]
        .filter(Boolean)
        .join('\n');
    }).join('\n---\n');
    navigator.clipboard.writeText(prompts).catch(console.error);
  };

  // Parse class/race JSON fields
  const minStats = safeJsonParse<Record<string, number>>(selectedClass?.minimumStats, {});
  const raceMods = safeJsonParse<Record<string, number>>(selectedRace?.statModifiers, {});
  const skills: string[] = safeJsonParse(selectedClass?.skills, []);
  const magicSchools: Record<string, number> = safeJsonParse(selectedClass?.magicSchools, {});
  const statNames = ['strength', 'agility', 'intellect', 'willpower', 'health', 'charm'];

  // Prompt validation
  const promptValid = selected ? selected.imagePrompt.trim().length > 0 : false;
  const promptHasRace = selected ? selected.imagePrompt.toLowerCase().includes(selected.race.toLowerCase().replace('_', ' ')) : false;
  const promptHasClass = selected ? selected.imagePrompt.toLowerCase().includes(selected.characterClass.toLowerCase()) : false;
  const promptHasGender = selected ? selected.imagePrompt.toLowerCase().includes(selected.gender) : false;

  return (
    <div style={styles.container}>
      {/* Left: filterable list */}
      <div style={styles.listPanel}>
        <div style={styles.listTop}>
          <div style={styles.filterBar}>
            <select style={styles.select} value={filterRace} onChange={(e) => setFilterRace(e.target.value)}>
              <option value="">All Races</option>
              {RACES.map((r) => <option key={r} value={r}>{titleCase(r)}</option>)}
            </select>
            <div style={styles.filterRow}>
              <select style={styles.select} value={filterGender} onChange={(e) => setFilterGender(e.target.value)}>
                <option value="">All Genders</option>
                {GENDERS.map((g) => <option key={g} value={g}>{titleCase(g)}</option>)}
              </select>
              <select style={styles.select} value={filterClass} onChange={(e) => setFilterClass(e.target.value)}>
                <option value="">All Classes</option>
                {CLASSES.map((c) => <option key={c} value={c}>{titleCase(c)}</option>)}
              </select>
            </div>
            <button style={styles.btnOutline} onClick={handleCopyAllPrompts}>
              Copy All Prompts
            </button>
          </div>
        </div>
        <div style={styles.listCount}>{sprites.length} sprites</div>
        <div style={styles.listItems}>
          {sprites.map((sprite) => (
            <div
              key={sprite.id}
              style={{ ...styles.listItem, ...(selectedId === sprite.id ? styles.listItemSelected : {}) }}
              onClick={() => setSelectedId(sprite.id)}
            >
              <img
                src={signedSpriteUrls[resolveUrl(`/assets/images/players/${sprite.id}.webp`)] || ''}
                alt={sprite.id}
                style={styles.listThumb as any}
                onError={(e) => { (e.target as HTMLImageElement).style.display = 'none'; }}
              />
              <span>{formatLabel(sprite)}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Right: detail panel */}
      <div style={styles.detailPanel}>
        {selected ? (
          <>
            <div style={styles.detailTitle}>{formatLabel(selected)}</div>
            <div style={styles.detailSubtitle}>ID: {selected.id}</div>

            <div style={styles.detailGrid}>
              {/* Large sprite image */}
              <div style={styles.imageCol}>
                <img
                  src={signedSpriteUrls[resolveUrl(`/assets/images/players/${selected.id}.webp`)] || ''}
                  alt={selected.id}
                  style={styles.largeImage}
                  onError={(e) => { (e.target as HTMLImageElement).style.opacity = '0.2'; }}
                />
              </div>

              {/* Character stats */}
              <div style={styles.statsCol}>
                {/* Identity */}
                <div style={{ ...styles.sectionLabel, marginTop: 0 }}>Identity</div>
                <div style={styles.infoRow}>
                  <span style={styles.infoLabel}>Race</span>
                  <span style={styles.infoValue}>{selectedRace?.name || titleCase(selected.race)}</span>
                </div>
                <div style={styles.infoRow}>
                  <span style={styles.infoLabel}>Gender</span>
                  <span style={styles.infoValue}>{titleCase(selected.gender)}</span>
                </div>
                <div style={styles.infoRow}>
                  <span style={styles.infoLabel}>Class</span>
                  <span style={styles.infoValue}>{selectedClass?.name || titleCase(selected.characterClass)}</span>
                </div>
                {selectedClass?.description && (
                  <div style={{ fontSize: 12, color: '#666', marginTop: 4, fontStyle: 'italic' }}>
                    {selectedClass.description}
                  </div>
                )}

                {/* Vitals */}
                <div style={styles.sectionLabel}>Vitals per Level</div>
                <div style={styles.infoRow}>
                  <span style={styles.infoLabel}>HP / level</span>
                  <span style={{ ...styles.infoValue, color: '#4caf50', fontWeight: 600 }}>
                    {selectedClass ? `${selectedClass.hpPerLevelMin}–${selectedClass.hpPerLevelMax}` : '—'}
                  </span>
                </div>
                <div style={styles.infoRow}>
                  <span style={styles.infoLabel}>MP / level</span>
                  <span style={{ ...styles.infoValue, color: '#448aff', fontWeight: 600 }}>
                    {selectedClass ? (selectedClass.mpPerLevelMax > 0 ? `${selectedClass.mpPerLevelMin}–${selectedClass.mpPerLevelMax}` : 'None') : '—'}
                  </span>
                </div>
                {((selectedClass?.xpModifier ?? 1) !== 1 || (selectedRace?.xpModifier ?? 1) !== 1) && (
                  <div style={styles.infoRow}>
                    <span style={styles.infoLabel}>XP Rate</span>
                    <span style={{ ...styles.infoValue, color: '#f57c00', fontWeight: 600 }}>
                      {Math.round((selectedClass?.xpModifier ?? 1) * (selectedRace?.xpModifier ?? 1) * 100)}%
                    </span>
                  </div>
                )}

                {/* Minimum Stats + Race Mods */}
                <div style={styles.sectionLabel}>Minimum Stats</div>
                <div style={styles.statsGrid}>
                  {statNames.map((stat) => {
                    const base = minStats[stat] ?? 0;
                    const mod = raceMods[stat] ?? 0;
                    const effective = Math.max(1, base + mod);
                    return (
                      <div key={stat} style={styles.statCell}>
                        <div style={styles.statName}>{stat.slice(0, 3).toUpperCase()}</div>
                        <div style={styles.statValue}>{effective}</div>
                        {mod !== 0 && (
                          <div style={{ ...styles.statMod, color: mod > 0 ? '#4caf50' : '#f44336' }}>
                            {base} {mod > 0 ? '+' : ''}{mod}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>

                {/* Skills */}
                {skills.length > 0 && (
                  <>
                    <div style={styles.sectionLabel}>Skills</div>
                    <div style={styles.tagRow}>
                      {skills.map((s) => <span key={s} style={styles.tag}>{titleCase(s)}</span>)}
                    </div>
                  </>
                )}

                {/* Magic Schools */}
                {Object.keys(magicSchools).length > 0 && (
                  <>
                    <div style={styles.sectionLabel}>Magic Schools</div>
                    <div style={styles.tagRow}>
                      {Object.entries(magicSchools).map(([school, tier]) => (
                        <span key={school} style={styles.tagMagic}>
                          {titleCase(school)} (tier {tier})
                        </span>
                      ))}
                    </div>
                  </>
                )}

                {/* Race description */}
                {selectedRace?.description && (
                  <>
                    <div style={styles.sectionLabel}>Race Lore</div>
                    <div style={{ fontSize: 12, color: '#555', lineHeight: 1.5 }}>
                      {selectedRace.description}
                    </div>
                  </>
                )}
              </div>
            </div>

            {/* Prompt fields — same as NPC/Item/Room editors */}
            <div style={styles.promptSection}>
              <div style={styles.promptSectionTitle}>Image Generation</div>

              {/* Validation indicators */}
              {promptValid ? (
                <div style={promptHasRace && promptHasClass && promptHasGender ? styles.validationOk : styles.validationWarn}>
                  {promptHasRace && promptHasClass && promptHasGender
                    ? 'Prompt includes race, gender, and class'
                    : `Prompt missing: ${[!promptHasRace && 'race', !promptHasGender && 'gender', !promptHasClass && 'class'].filter(Boolean).join(', ')}`
                  }
                </div>
              ) : (
                <div style={styles.validationWarn}>Prompt is empty</div>
              )}

              <div style={{ marginTop: 12 }}>
                <ImagePreview
                  entityType="pc-sprite"
                  entityId={selected.id}
                  assetPath={`images/players/${selected.id}.webp`}
                  description={`A ${selected.gender} ${titleCase(selected.race)} ${titleCase(selected.characterClass)}, fantasy RPG character`}
                  imagePrompt={selected.imagePrompt}
                  imageStyle={selected.imageStyle}
                  imageNegativePrompt={selected.imageNegativePrompt}
                  imageWidth={selected.imageWidth}
                  imageHeight={selected.imageHeight}
                  maxWidth={384}
                  maxHeight={512}
                  onUpdate={handleUpdate}
                />
              </div>
            </div>
          </>
        ) : (
          <div style={styles.empty}>
            <EditorPageHeader storageKey="pc-sprites">
              Default player sprites are auto-generated for every race/gender/class combination.
              Select a character to view its class stats and edit the AI image generation prompt.
              Good prompts mention the race, gender, and class for consistent results.
            </EditorPageHeader>
            Select a character from the list to view stats and edit image prompts.
          </div>
        )}
      </div>
    </div>
  );
}

export default PcSpriteEditor;
