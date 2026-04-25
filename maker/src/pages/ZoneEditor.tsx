import { useEffect, useState, useCallback, useMemo } from 'react';
import api, { resolveUrl } from '../api';
import MapCanvas, { inferDirection } from '../components/MapCanvas';
import ImagePreview from '../components/ImagePreview';
import AudioPreview from '../components/AudioPreview';
import SfxPreview from '../components/SfxPreview';
import type { CSSProperties } from 'react';

interface Zone {
  id: string;
  name: string;
  description: string;
  safe: boolean;
  bgm: string;
  bgmPrompt: string;
  bgmDuration: number;
  spawnRoom: string | null;
  spawnMaxEntities: number;
  spawnMaxPerRoom: number;
  spawnRateTicks: number;
  imageStyle: string;
  imageNegativePrompt: string;
}

interface Exit {
  fromRoomId: string;
  toRoomId: string;
  direction: string;
}

interface Room {
  id: string;
  name: string;
  description: string;
  x: number;
  y: number;
  backgroundImage: string;
  bgm: string;
  bgmPrompt: string;
  bgmDuration: number;
  departSound: string;
  effects: string;
  lockedExits: string;
  lockResetTicks: string;
  hiddenExits: string;
  imagePrompt: string;
  imageStyle: string;
  imageNegativePrompt: string;
  interactables: string;
  unpickableExits: string;
  imageWidth: number;
  imageHeight: number;
  maxHostileNpcs: number | null;
  exits: Exit[];
  _editSlug?: string;
  _editZone?: string;
}

interface HiddenExitData {
  perceptionDC: number;
  lockDifficulty: number;
  hiddenResetTicks: number;
  lockResetTicks: number;
}

interface Interactable {
  id: string;
  label: string;
  description: string;
  failureMessage: string;
  icon: string;
  actionType: string;
  actionData: Record<string, string>;
  difficulty: number;
  difficultyCheck: string;
  perceptionDC: number;
  cooldownTicks: number;
  resetTicks: number;
  sound: string;
}

interface ZoneWithRooms extends Zone {
  rooms: Room[];
}

const styles: Record<string, CSSProperties> = {
  container: {
    display: 'flex',
    height: '100%',
    overflow: 'hidden',
  },
  leftPanel: {
    width: 200,
    minWidth: 200,
    borderRight: '1px solid #ddd',
    backgroundColor: '#fff',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
  },
  leftTop: {
    padding: 12,
    borderBottom: '1px solid #eee',
  },
  zoneList: {
    flex: 1,
    overflowY: 'auto',
    padding: '4px 0',
  },
  zoneItem: {
    padding: '8px 12px',
    cursor: 'pointer',
    fontSize: 13,
    borderBottom: '1px solid #f0f0f0',
  },
  zoneItemSelected: {
    backgroundColor: '#e8eaf6',
    fontWeight: 600,
  },
  zoneProps: {
    borderTop: '1px solid #ddd',
    padding: 12,
    overflowY: 'auto',
    maxHeight: 320,
  },
  centerPanel: {
    flex: 1,
    position: 'relative',
    overflow: 'hidden',
  },
  rightPanel: {
    width: 250,
    minWidth: 250,
    borderLeft: '1px solid #ddd',
    backgroundColor: '#fff',
    padding: 12,
    overflowY: 'auto',
  },
  label: {
    display: 'block',
    fontSize: 11,
    fontWeight: 600,
    color: '#666',
    marginBottom: 2,
    marginTop: 8,
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
  btnSmall: {
    padding: '6px 12px',
    fontSize: 12,
    fontWeight: 600,
    backgroundColor: '#1a1a2e',
    color: '#fff',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
    marginTop: 8,
  },
  btnDanger: {
    padding: '6px 12px',
    fontSize: 12,
    fontWeight: 600,
    backgroundColor: '#d32f2f',
    color: '#fff',
    border: 'none',
    borderRadius: 4,
    cursor: 'pointer',
    marginTop: 8,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: 700,
    marginBottom: 8,
    color: '#1a1a2e',
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
};

const ALL_DIRECTIONS = [
  'NORTH', 'SOUTH', 'EAST', 'WEST',
  'NORTHEAST', 'NORTHWEST', 'SOUTHEAST', 'SOUTHWEST',
  'UP', 'DOWN',
];

function InteractablesEditor({ roomForm, setRoomForm }: {
  roomForm: Partial<Room>;
  setRoomForm: React.Dispatch<React.SetStateAction<Partial<Room>>>;
}) {
  const interactList: Interactable[] = (() => {
    try { return JSON.parse(roomForm.interactables || '[]'); } catch { return []; }
  })();
  const update = (updated: Interactable[]) =>
    setRoomForm((f) => ({ ...f, interactables: JSON.stringify(updated) }));
  const set = (i: number, patch: Partial<Interactable>) => {
    const u = [...interactList];
    u[i] = { ...u[i], ...patch };
    update(u);
  };
  const setData = (i: number, patch: Record<string, string>) => {
    const u = [...interactList];
    u[i] = { ...u[i], actionData: { ...u[i].actionData, ...patch } };
    update(u);
  };

  const hiddenMap: Record<string, HiddenExitData> = (() => {
    try { return JSON.parse(roomForm.hiddenExits || '{}'); } catch { return {}; }
  })();

  const actionSummary = (feat: Interactable): string => {
    let base: string;
    switch (feat.actionType) {
      case 'EXIT_OPEN': {
        const dir = feat.actionData?.direction;
        if (!dir) { base = 'Opens: (no direction)'; break; }
        const hidden = dir in hiddenMap;
        base = `Opens: ${dir}${hidden ? ' (hidden)' : ''}`; break;
      }
      case 'TREASURE_DROP':
        base = `Loot: ${feat.actionData?.lootTableId || '(none)'}`; break;
      case 'MONSTER_SPAWN':
        base = `Spawns: ${feat.actionData?.npcId || '(none)'} x${feat.actionData?.count || 1}`; break;
      case 'ROOM_EFFECT':
        base = `${feat.actionData?.effectType || 'Effect'}: ${feat.actionData?.value || 0}${(parseInt(feat.actionData?.durationTicks) || 0) > 0 ? ` (${feat.actionData.durationTicks}t)` : ''}`; break;
      case 'TELEPORT':
        base = `Teleport: ${feat.actionData?.targetRoomId || '(none)'}`; break;
      default:
        base = feat.actionType;
    }
    if (feat.difficultyCheck && feat.difficulty > 0) {
      base += ` [${feat.difficultyCheck} DC ${feat.difficulty}]`;
    }
    return base;
  };

  return (
    <>
      {interactList.map((feat, i) => (
        <div key={i} style={{ padding: '6px 8px', backgroundColor: '#f3f0ff', borderRadius: 4, marginBottom: 4, border: '1px solid #e0d8f0' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
            <div>
              <strong style={{ fontSize: 11 }}>{feat.label || feat.id || `#${i}`}</strong>
              <div style={{ fontSize: 9, color: '#888', marginTop: 1 }}>
                {actionSummary(feat)}
                {feat.description ? ` — "${feat.description.length > 40 ? feat.description.slice(0, 40) + '...' : feat.description}"` : ' — (no success message)'}
              </div>
            </div>
            <button
              onClick={() => update(interactList.filter((_, j) => j !== i))}
              style={{ background: 'none', border: 'none', color: '#d32f2f', cursor: 'pointer', fontWeight: 700, fontSize: 14, padding: '0 4px' }}
              title="Remove interactable"
            >x</button>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 4, fontSize: 10 }}>
            <div>
              <label style={{ color: '#666' }}>ID</label>
              <input style={{ ...styles.input, fontSize: 11 }} value={feat.id} onChange={(e) => set(i, { id: e.target.value })} />
            </div>
            <div>
              <label style={{ color: '#666' }}>Label</label>
              <input style={{ ...styles.input, fontSize: 11 }} value={feat.label} onChange={(e) => set(i, { label: e.target.value })} />
            </div>
          </div>
          <label style={{ fontSize: 10, color: '#d84315', fontWeight: 600, marginTop: 2, display: 'block' }}>Success Message (shown to player)</label>
          <textarea style={{ ...styles.textarea, minHeight: 30, fontSize: 11, borderColor: feat.description ? '#ccc' : '#e65100' }} placeholder="e.g. You pull the ancient lever and hear a grinding sound..." value={feat.description} onChange={(e) => set(i, { description: e.target.value })} />
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 4, fontSize: 10, marginTop: 2 }}>
            <div>
              <label style={{ color: '#666' }}>Stat Check</label>
              <select style={{ ...styles.input, fontSize: 11 }} value={feat.difficultyCheck || ''} onChange={(e) => set(i, { difficultyCheck: e.target.value, difficulty: e.target.value ? (feat.difficulty || 15) : 0 })}>
                <option value="">None (always succeeds)</option>
                <option value="STRENGTH">Strength</option>
                <option value="AGILITY">Agility</option>
                <option value="INTELLECT">Intellect</option>
                <option value="WILLPOWER">Willpower</option>
              </select>
            </div>
            <div>
              <label style={{ color: '#666' }}>Difficulty DC</label>
              <input type="number" style={{ ...styles.input, fontSize: 11, opacity: feat.difficultyCheck ? 1 : 0.4 }} value={feat.difficulty || 0} min={0} disabled={!feat.difficultyCheck} onChange={(e) => set(i, { difficulty: parseInt(e.target.value) || 0 })} />
            </div>
          </div>
          {feat.difficultyCheck && (
            <>
              <label style={{ fontSize: 10, color: '#c62828', fontWeight: 600, marginTop: 2, display: 'block' }}>Failure Message (shown to player)</label>
              <textarea style={{ ...styles.textarea, minHeight: 30, fontSize: 11, borderColor: feat.failureMessage ? '#ccc' : '#c62828' }} placeholder="e.g. The lever won't budge, you're not strong enough..." value={feat.failureMessage || ''} onChange={(e) => set(i, { failureMessage: e.target.value })} />
            </>
          )}
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 4, fontSize: 10, marginTop: 2 }}>
            <div>
              <label style={{ color: '#666' }}>Action Type</label>
              <select style={{ ...styles.input, fontSize: 11 }} value={feat.actionType} onChange={(e) => set(i, { actionType: e.target.value, actionData: {} })}>
                <option value="EXIT_OPEN">Open Exit</option>
                <option value="TREASURE_DROP">Drop Treasure</option>
                <option value="MONSTER_SPAWN">Spawn Monster</option>
                <option value="ROOM_EFFECT">Room Effect</option>
                <option value="TELEPORT">Teleport</option>
              </select>
            </div>
            <div>
              <label style={{ color: '#666' }}>Perception DC</label>
              <input type="number" style={{ ...styles.input, fontSize: 11 }} value={feat.perceptionDC} min={0} onChange={(e) => set(i, { perceptionDC: parseInt(e.target.value) || 0 })} />
            </div>
            <div>
              <label style={{ color: '#666' }}>Reset Ticks</label>
              <input type="number" style={{ ...styles.input, fontSize: 11 }} value={feat.resetTicks} min={0} onChange={(e) => set(i, { resetTicks: parseInt(e.target.value) || 0 })} />
            </div>
            <div>
              <label style={{ color: '#666' }}>Cooldown Ticks</label>
              <input type="number" style={{ ...styles.input, fontSize: 11 }} value={feat.cooldownTicks} min={0} onChange={(e) => set(i, { cooldownTicks: parseInt(e.target.value) || 0 })} />
            </div>
          </div>
          {/* Action-specific inputs */}
          {feat.actionType === 'EXIT_OPEN' && (
            <div style={{ marginTop: 4, fontSize: 10 }}>
              <label style={{ color: '#666' }}>Direction</label>
              <select style={{ ...styles.input, fontSize: 11 }} value={feat.actionData?.direction || ''} onChange={(e) => setData(i, { direction: e.target.value })}>
                <option value="">--</option>
                {ALL_DIRECTIONS.map((d) => <option key={d} value={d}>{d}{d in hiddenMap ? ' (hidden)' : ''}</option>)}
              </select>
            </div>
          )}
          {feat.actionType === 'TREASURE_DROP' && (
            <div style={{ marginTop: 4, fontSize: 10 }}>
              <label style={{ color: '#666' }}>Loot Table ID</label>
              <input style={{ ...styles.input, fontSize: 11 }} value={feat.actionData?.lootTableId || ''} onChange={(e) => setData(i, { lootTableId: e.target.value })} />
            </div>
          )}
          {feat.actionType === 'MONSTER_SPAWN' && (
            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 4, marginTop: 4, fontSize: 10 }}>
              <div>
                <label style={{ color: '#666' }}>NPC ID</label>
                <input style={{ ...styles.input, fontSize: 11 }} value={feat.actionData?.npcId || ''} onChange={(e) => setData(i, { npcId: e.target.value })} />
              </div>
              <div>
                <label style={{ color: '#666' }}>Count</label>
                <input type="number" style={{ ...styles.input, fontSize: 11 }} value={feat.actionData?.count || '1'} min={1} onChange={(e) => setData(i, { count: e.target.value })} />
              </div>
            </div>
          )}
          {feat.actionType === 'ROOM_EFFECT' && (
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 4, marginTop: 4, fontSize: 10 }}>
              <div>
                <label style={{ color: '#666' }}>Effect Type</label>
                <select style={{ ...styles.input, fontSize: 11 }} value={feat.actionData?.effectType || ''} onChange={(e) => setData(i, { effectType: e.target.value })}>
                  <option value="">--</option>
                  <option value="HEAL">HEAL — Restores HP</option>
                  <option value="DAMAGE">DAMAGE — Deals damage</option>
                  <option value="MANA_REGEN">MANA_REGEN — Restores mana</option>
                  <option value="BUFF_STRENGTH">BUFF_STRENGTH — Boosts strength</option>
                  <option value="BUFF_AGILITY">BUFF_AGILITY — Boosts agility</option>
                  <option value="BUFF_INTELLECT">BUFF_INTELLECT — Boosts intellect</option>
                  <option value="BUFF_WILLPOWER">BUFF_WILLPOWER — Boosts willpower</option>
                </select>
              </div>
              <div>
                <label style={{ color: '#666' }}>Value</label>
                <input type="number" style={{ ...styles.input, fontSize: 11 }} value={feat.actionData?.value || '0'} onChange={(e) => setData(i, { value: e.target.value })} />
              </div>
              <div>
                <label style={{ color: '#666' }}>Duration (ticks, 0=instant)</label>
                <input type="number" style={{ ...styles.input, fontSize: 11 }} value={feat.actionData?.durationTicks || '0'} onChange={(e) => setData(i, { durationTicks: e.target.value })} />
              </div>
              <div>
                <label style={{ color: '#666' }}>Player Message</label>
                <input style={{ ...styles.input, fontSize: 11 }} value={feat.actionData?.message || ''} placeholder="e.g. A warm glow fills you" onChange={(e) => setData(i, { message: e.target.value })} />
              </div>
            </div>
          )}
          {feat.actionType === 'TELEPORT' && (
            <div style={{ marginTop: 4, fontSize: 10 }}>
              <label style={{ color: '#666' }}>Target Room ID</label>
              <input style={{ ...styles.input, fontSize: 11 }} value={feat.actionData?.targetRoomId || ''} onChange={(e) => setData(i, { targetRoomId: e.target.value })} />
              <label style={{ color: '#666', marginTop: 2, display: 'block' }}>Message</label>
              <input style={{ ...styles.input, fontSize: 11 }} value={feat.actionData?.message || ''} onChange={(e) => setData(i, { message: e.target.value })} />
            </div>
          )}
          <div style={{ marginTop: 4, fontSize: 10 }}>
            <label style={{ color: '#666' }}>Icon (emoji, blank=default)</label>
            <input style={{ ...styles.input, fontSize: 11, width: 60 }} value={feat.icon} onChange={(e) => set(i, { icon: e.target.value })} />
          </div>
        </div>
      ))}
      <button
        style={{ ...styles.btnSmall, width: '100%' }}
        onClick={() => update([...interactList, { id: `feat_${interactList.length + 1}`, label: 'New Feature', description: '', failureMessage: '', icon: '', actionType: 'EXIT_OPEN', actionData: {}, difficulty: 0, difficultyCheck: '', perceptionDC: 0, cooldownTicks: 0, resetTicks: 0, sound: '' }])}
      >+ Add Interactable</button>
    </>
  );
}

interface AllRoomsGroup {
  zoneId: string;
  zoneName: string;
  rooms: { id: string; name: string; x: number; y: number; exits: Exit[] }[];
}

function computeLayerMap(rooms: Room[], exits: Exit[]) {
  const downTargets = new Map<string, string[]>();
  const upTargets = new Map<string, string[]>();
  const roomIds = new Set(rooms.map((r) => r.id));

  for (const exit of exits) {
    if (exit.direction === 'DOWN' && roomIds.has(exit.toRoomId)) {
      const list = downTargets.get(exit.fromRoomId) || [];
      list.push(exit.toRoomId);
      downTargets.set(exit.fromRoomId, list);
    }
    if (exit.direction === 'UP' && roomIds.has(exit.toRoomId)) {
      const list = upTargets.get(exit.fromRoomId) || [];
      list.push(exit.toRoomId);
      upTargets.set(exit.fromRoomId, list);
    }
  }

  const layerMap = new Map<string, number>();
  for (const room of rooms) {
    if (layerMap.has(room.id)) continue;
    layerMap.set(room.id, 0);
    const queue = [room.id];
    while (queue.length > 0) {
      const current = queue.shift()!;
      const layer = layerMap.get(current)!;
      for (const target of downTargets.get(current) || []) {
        if (!layerMap.has(target)) {
          layerMap.set(target, layer - 1);
          queue.push(target);
        }
      }
      for (const target of upTargets.get(current) || []) {
        if (!layerMap.has(target)) {
          layerMap.set(target, layer + 1);
          queue.push(target);
        }
      }
    }
  }

  const layers = [...new Set(layerMap.values())].sort((a, b) => b - a);
  return { layerMap, layers };
}

let zoneCounter = 0;

function ZoneEditor() {
  const [zones, setZones] = useState<Zone[]>([]);
  const [selectedZoneId, setSelectedZoneId] = useState<string | null>(null);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [selectedRoomId, setSelectedRoomId] = useState<string | null>(null);

  const [zoneForm, setZoneForm] = useState<Partial<Zone>>({});
  const [roomForm, setRoomForm] = useState<Partial<Room>>({});

  // Layer navigation state
  const [currentLayer, setCurrentLayer] = useState(0);

  // Manual exit creation state
  const [newExitDir, setNewExitDir] = useState('');
  const [newExitTarget, setNewExitTarget] = useState('');
  const [allRooms, setAllRooms] = useState<AllRoomsGroup[]>([]);
  const [movementSfx, setMovementSfx] = useState<{ id: string; label: string }[]>([]);

  // Flat map of roomId → roomName for efficient exit display
  const roomNameMap = useMemo(() => {
    const map = new Map<string, string>();
    for (const group of allRooms) {
      for (const r of group.rooms) map.set(r.id, r.name);
    }
    for (const r of rooms) map.set(r.id, r.name); // current zone rooms override
    return map;
  }, [allRooms, rooms]);

  // Load zones list + all rooms for exit picker
  useEffect(() => {
    api.get<Zone[]>('/zones').then((zoneList) => {
      const safeZones = Array.isArray(zoneList) ? zoneList : [];
      setZones(safeZones);
      // Load all rooms across all zones (with coordinates and exits for world map)
      const promises = safeZones.map((z) =>
        api.get<ZoneWithRooms>(`/zones/${z.id}`).then((data) => ({
          zoneId: z.id,
          zoneName: z.name,
          rooms: (data?.rooms || []).map((r) => ({ id: r.id, name: r.name, x: r.x, y: r.y, exits: r.exits || [] })),
        }))
      );
      Promise.all(promises)
        .then((groups) => setAllRooms(Array.isArray(groups) ? groups : []))
        .catch(() => {});
    }).catch(() => {});
    api.get<{ id: string; label: string }[]>('/default-sfx?category=movement')
      .then((sfx) => setMovementSfx(Array.isArray(sfx) ? sfx : []))
      .catch(() => {});
  }, []);

  // Load zone detail when selected
  useEffect(() => {
    if (!selectedZoneId) {
      setRooms([]);
      setSelectedRoomId(null);
      return;
    }
    api
      .get<ZoneWithRooms>(`/zones/${selectedZoneId}`)
      .then((data) => {
        setRooms(data.rooms || []);
        setZoneForm({
          id: data.id,
          name: data.name,
          description: data.description,
          safe: data.safe,
          bgm: data.bgm,
          bgmPrompt: data.bgmPrompt,
          bgmDuration: data.bgmDuration,
          spawnRoom: data.spawnRoom,
          spawnMaxEntities: data.spawnMaxEntities,
          spawnMaxPerRoom: data.spawnMaxPerRoom,
          spawnRateTicks: data.spawnRateTicks,
          imageStyle: data.imageStyle,
          imageNegativePrompt: data.imageNegativePrompt,
        });
      })
      .catch(() => {});
  }, [selectedZoneId]);

  // Sync room form when selection changes
  useEffect(() => {
    if (!selectedRoomId) {
      setRoomForm({});
      return;
    }
    const room = rooms.find((r) => r.id === selectedRoomId);
    if (room) setRoomForm({ ...room });
  }, [selectedRoomId, rooms]);

  // Collect all exits from rooms
  const allExits: Exit[] = rooms.flatMap((r) => r.exits || []);

  // Compute layers from UP/DOWN exit topology
  const { layerMap, layers } = useMemo(
    () => {
      const result = computeLayerMap(rooms, allExits);
      return result;
    },
    [rooms, allExits]
  );

  // Reset layer to 0 when zone changes (if 0 exists), otherwise pick highest
  useEffect(() => {
    if (layers.length === 0) {
      setCurrentLayer(0);
    } else if (!layers.includes(currentLayer)) {
      setCurrentLayer(layers.includes(0) ? 0 : layers[0]);
    }
  }, [layers]);

  // Filter rooms and exits to the current layer
  const layerRoomIds = useMemo(() => {
    const ids = new Set<string>();
    for (const room of rooms) {
      if ((layerMap.get(room.id) ?? 0) === currentLayer) ids.add(room.id);
    }
    return ids;
  }, [rooms, layerMap, currentLayer]);

  const filteredRooms = useMemo(
    () => rooms.filter((r) => layerRoomIds.has(r.id)),
    [rooms, layerRoomIds]
  );

  const filteredExits = useMemo(
    () => {
      // Build lookup maps for locked/hidden status per room
      const roomById = new Map(rooms.map((r) => [r.id, r]));
      return allExits
        .filter(
          (e) =>
            layerRoomIds.has(e.fromRoomId) &&
            layerRoomIds.has(e.toRoomId) &&
            e.direction !== 'UP' &&
            e.direction !== 'DOWN'
        )
        .map((e) => {
          const room = roomById.get(e.fromRoomId);
          let isLocked = false;
          let isHidden = false;
          if (room) {
            try {
              const locked: Record<string, number> = JSON.parse(room.lockedExits || '{}');
              isLocked = e.direction in locked;
            } catch {}
            try {
              const hidden: Record<string, any> = JSON.parse(room.hiddenExits || '{}');
              if (e.direction in hidden) {
                isHidden = true;
                // Hidden exits store their own lockDifficulty (not in lockedExits)
                if (hidden[e.direction]?.lockDifficulty > 0) {
                  isLocked = true;
                }
              }
            } catch {}
          }
          return { ...e, isLocked, isHidden };
        });
    },
    [allExits, layerRoomIds, rooms]
  );

  // Vertical exit indicators for rooms on the current layer
  const verticalExits = useMemo(() => {
    const result: { roomId: string; direction: string }[] = [];
    for (const exit of allExits) {
      if (
        (exit.direction === 'UP' || exit.direction === 'DOWN') &&
        layerRoomIds.has(exit.fromRoomId)
      ) {
        result.push({ roomId: exit.fromRoomId, direction: exit.direction });
      }
    }
    return result;
  }, [allExits, layerRoomIds]);

  // Cross-zone exit indicators for rooms on the current layer
  const crossZoneExits = useMemo(() => {
    if (!selectedZoneId) return [];
    const zoneNameMap = new Map(zones.map((z) => [z.id, z.name]));
    const result: { roomId: string; direction: string; targetZone: string; targetZoneId: string }[] = [];
    for (const exit of allExits) {
      if (!layerRoomIds.has(exit.fromRoomId)) continue;
      const targetZoneId = exit.toRoomId.split(':')[0];
      if (targetZoneId === selectedZoneId) continue;
      // Only cardinal directions (not UP/DOWN)
      if (exit.direction === 'UP' || exit.direction === 'DOWN') continue;
      result.push({
        roomId: exit.fromRoomId,
        direction: exit.direction,
        targetZone: zoneNameMap.get(targetZoneId) || targetZoneId,
        targetZoneId,
      });
    }
    return result;
  }, [allExits, layerRoomIds, selectedZoneId, zones]);

  // Compute dimmedRoomIds: rooms from non-selected zones (for world map overlay)
  const dimmedRoomIds = useMemo(() => {
    if (!selectedZoneId) return undefined;
    const set = new Set<string>();
    for (const g of allRooms) {
      if (g.zoneId !== selectedZoneId) {
        for (const r of g.rooms) set.add(r.id);
      }
    }
    return set.size > 0 ? set : undefined;
  }, [selectedZoneId, allRooms]);

  // Compute zone labels at centroids for dimmed zones
  const zoneLabels = useMemo(() => {
    if (!selectedZoneId) return [];
    return allRooms
      .filter((g) => g.zoneId !== selectedZoneId && g.rooms.length > 0)
      .map((g) => {
        const cx = g.rooms.reduce((sum, r) => sum + r.x, 0) / g.rooms.length;
        const cy = g.rooms.reduce((sum, r) => sum + r.y, 0) / g.rooms.length;
        return { zoneName: g.zoneName, cx, cy };
      });
  }, [selectedZoneId, allRooms]);

  // Combined rooms for MapCanvas when a zone is selected (current zone full data + other zones from allRooms)
  const combinedMapRooms = useMemo(() => {
    if (!selectedZoneId) return [];
    const currentZoneRooms = filteredRooms.map((r) => ({ id: r.id, name: r.name, x: r.x, y: r.y }));
    const otherZoneRooms = allRooms
      .filter((g) => g.zoneId !== selectedZoneId)
      .flatMap((g) => g.rooms.map((r) => ({ id: r.id, name: r.name, x: r.x, y: r.y })));
    return [...currentZoneRooms, ...otherZoneRooms];
  }, [selectedZoneId, filteredRooms, allRooms]);

  // Combined exits for MapCanvas when a zone is selected
  const combinedMapExits = useMemo(() => {
    if (!selectedZoneId) return [];
    const otherExits = allRooms
      .filter((g) => g.zoneId !== selectedZoneId)
      .flatMap((g) => g.rooms.flatMap((r) => (r.exits || []).filter(
        (e) => e.direction !== 'UP' && e.direction !== 'DOWN'
      )));
    return [...filteredExits, ...otherExits];
  }, [selectedZoneId, filteredExits, allRooms]);

  // World map rooms/exits (when no zone is selected)
  const worldMapRooms = useMemo(() => {
    if (selectedZoneId) return [];
    return allRooms.flatMap((g) =>
      g.rooms.map((r) => ({ id: r.id, name: r.name, x: r.x, y: r.y }))
    );
  }, [selectedZoneId, allRooms]);

  const worldMapExits = useMemo(() => {
    if (selectedZoneId) return [];
    return allRooms.flatMap((g) =>
      g.rooms.flatMap((r) => (r.exits || []).filter(
        (e) => e.direction !== 'UP' && e.direction !== 'DOWN'
      ))
    );
  }, [selectedZoneId, allRooms]);

  // Stable zone-to-color mapping (index based on sorted zone IDs)
  const zoneColorIndex = useMemo(() => {
    const ZONE_COLORS = [
      '#3f51b5', '#e91e63', '#009688', '#ff9800', '#673ab7',
      '#4caf50', '#f44336', '#00bcd4', '#795548', '#607d8b',
    ];
    const map = new Map<string, string>();
    const sortedIds = allRooms.map((g) => g.zoneId).sort();
    for (let i = 0; i < sortedIds.length; i++) {
      map.set(sortedIds[i], ZONE_COLORS[i % ZONE_COLORS.length]);
    }
    return map;
  }, [allRooms]);

  const worldMapZoneLabels = useMemo(() => {
    if (selectedZoneId) return [];
    return allRooms
      .filter((g) => g.rooms.length > 0)
      .map((g) => {
        const cx = g.rooms.reduce((sum, r) => sum + r.x, 0) / g.rooms.length;
        const cy = g.rooms.reduce((sum, r) => sum + r.y, 0) / g.rooms.length;
        return { zoneName: g.zoneName, cx, cy, color: zoneColorIndex.get(g.zoneId) };
      });
  }, [selectedZoneId, allRooms, zoneColorIndex]);

  // Per-room zone color map for world map view
  const worldMapRoomColors = useMemo(() => {
    if (selectedZoneId) return undefined;
    const map = new Map<string, string>();
    for (const g of allRooms) {
      const color = zoneColorIndex.get(g.zoneId) || '#3f51b5';
      for (const r of g.rooms) map.set(r.id, color);
    }
    return map.size > 0 ? map : undefined;
  }, [selectedZoneId, allRooms, zoneColorIndex]);

  // Handle clicking a room in world map view (find zone and select it)
  const handleWorldMapSelectRoom = useCallback((roomId: string) => {
    for (const g of allRooms) {
      const room = g.rooms.find((r) => r.id === roomId);
      if (room) {
        setSelectedZoneId(g.zoneId);
        setSelectedRoomId(roomId);
        return;
      }
    }
  }, [allRooms]);

  // Handle clicking a dimmed room from another zone (switch zones)
  const handleCombinedSelectRoom = useCallback((roomId: string) => {
    if (dimmedRoomIds?.has(roomId)) {
      // Clicked a dimmed room — switch to that zone
      for (const g of allRooms) {
        const room = g.rooms.find((r) => r.id === roomId);
        if (room) {
          setSelectedZoneId(g.zoneId);
          setSelectedRoomId(roomId);
          return;
        }
      }
    } else {
      // Clicked a room in the current zone
      setSelectedRoomId(roomId);
    }
  }, [dimmedRoomIds, allRooms]);

  const layerLabel = (layer: number) => {
    if (layer === 0) return 'Ground';
    if (layer > 0) return `Above +${layer}`;
    return `Below ${layer}`;
  };

  const canGoUp = layers.some((l) => l > currentLayer);
  const canGoDown = layers.some((l) => l < currentLayer);

  const handleNewZone = async () => {
    const id = `zone_${++zoneCounter}_${Date.now()}`;
    try {
      const zone = await api.post<Zone>('/zones', {
        id,
        name: 'New Zone',
        description: '',
      });
      setZones((prev) => [...prev, zone]);
      setSelectedZoneId(zone.id);
    } catch {}
  };

  const handleSaveZone = async () => {
    if (!selectedZoneId) return;
    try {
      const { id: _, ...fields } = zoneForm;
      const updated = await api.put<Zone>(`/zones/${selectedZoneId}`, fields);
      setZones((prev) => prev.map((z) => (z.id === updated.id ? updated : z)));
      setZoneForm(updated);
    } catch {}
  };

  const handleDeleteZone = async () => {
    if (!selectedZoneId) return;
    const zone = zones.find((z) => z.id === selectedZoneId);
    const zoneName = zone?.name || selectedZoneId;
    const roomCount = rooms.length;
    const msg = roomCount > 0
      ? `Delete zone "${zoneName}" and all ${roomCount} room(s) in it? This cannot be undone.`
      : `Delete zone "${zoneName}"? This cannot be undone.`;
    if (!window.confirm(msg)) return;
    try {
      await api.del(`/zones/${selectedZoneId}`);
      setZones((prev) => prev.filter((z) => z.id !== selectedZoneId));
      setAllRooms((prev) => prev.filter((g) => g.zoneId !== selectedZoneId));
      setSelectedZoneId(null);
    } catch {}
  };

  const handleCreateRoom = useCallback(
    async (x: number, y: number) => {
      if (!selectedZoneId) return;
      // Global coordinate collision check (fast-fail before API call)
      const globalOccupied = allRooms.some((g) =>
        g.rooms.some((r) => r.x === x && r.y === y)
      );
      if (globalOccupied) return;
      const roomSlug = `${selectedZoneId}_room_${x}_${y}`;
      const roomName = `New Room (${x},${y})`;
      try {
        const room = await api.post<Room>(`/zones/${selectedZoneId}/rooms`, {
          id: roomSlug,
          name: roomName,
          description: '',
          x,
          y,
        });
        setRooms((prev) => [...prev, { ...room, exits: room.exits || [] }]);
        setSelectedRoomId(room.id);
        // Update allRooms so the world map and exit target dropdown include the new room
        setAllRooms((prev) => prev.map((g) =>
          g.zoneId === selectedZoneId
            ? { ...g, rooms: [...g.rooms, { id: room.id, name: room.name, x: room.x, y: room.y, exits: room.exits || [] }] }
            : g
        ));
      } catch {}
    },
    [selectedZoneId, allRooms]
  );

  const handleSelectRoom = useCallback((id: string) => {
    setSelectedRoomId(id);
  }, []);

  const handleCreateExit = useCallback(
    async (fromId: string, toId: string) => {
      // Determine direction from grid positions
      const fromRoom = rooms.find((r) => r.id === fromId);
      const toRoom = rooms.find((r) => r.id === toId);
      if (!fromRoom || !toRoom) return;

      const dx = toRoom.x - fromRoom.x;
      const dy = toRoom.y - fromRoom.y;
      let direction = '';
      if (dy < 0 && dx === 0) direction = 'NORTH';
      else if (dy > 0 && dx === 0) direction = 'SOUTH';
      else if (dx > 0 && dy === 0) direction = 'EAST';
      else if (dx < 0 && dy === 0) direction = 'WEST';
      else if (dy < 0 && dx > 0) direction = 'NORTHEAST';
      else if (dy < 0 && dx < 0) direction = 'NORTHWEST';
      else if (dy > 0 && dx > 0) direction = 'SOUTHEAST';
      else if (dy > 0 && dx < 0) direction = 'SOUTHWEST';
      else return;

      try {
        await api.post(`/rooms/${fromId}/exits`, { direction, toRoomId: toId });
        // Reload zone to get updated exits
        const data = await api.get<ZoneWithRooms>(`/zones/${selectedZoneId}`);
        setRooms(data.rooms || []);
      } catch {}
    },
    [rooms, selectedZoneId]
  );

  const handleMoveRoom = useCallback(
    async (roomId: string, newX: number, newY: number) => {
      if (!selectedZoneId) return;

      const room = rooms.find((r) => r.id === roomId);
      if (!room) return;

      // Pre-compute which exits need direction changes
      // Scan ALL rooms (including cross-zone) to catch exits pointing to the moved room
      // Collect only FORWARD exits (fromRoomId side) to avoid double-processing
      // since deleting an exit also deletes its reverse
      const exitsToFix: { fromRoomId: string; oldDir: string; newDir: string; toRoomId: string }[] = [];
      const exitWarnings: string[] = [];
      const processed = new Set<string>(); // "fromId:dir" keys to avoid duplicates

      // Build a flat list of all rooms across all zones for coordinate/exit lookup
      const allRoomsList = allRooms.flatMap((g) => g.rooms);
      // Include current zone rooms (which may have fresher data than allRooms)
      const roomById = new Map<string, { x: number; y: number; exits: Exit[] }>();
      for (const r of allRoomsList) roomById.set(r.id, r);
      for (const r of rooms) roomById.set(r.id, r); // current zone overrides

      for (const [, r] of roomById) {
        for (const exit of r.exits || []) {
          if (exit.direction === 'UP' || exit.direction === 'DOWN') continue;

          let sourceX: number, sourceY: number, targetX: number, targetY: number;
          if (exit.fromRoomId === roomId) {
            const target = roomById.get(exit.toRoomId);
            if (!target) continue;
            sourceX = newX; sourceY = newY;
            targetX = target.x; targetY = target.y;
          } else if (exit.toRoomId === roomId) {
            sourceX = r.x; sourceY = r.y;
            targetX = newX; targetY = newY;
          } else {
            continue;
          }

          const dx = targetX - sourceX;
          const dy = targetY - sourceY;
          const newDir = inferDirection(dx, dy);
          if (!newDir || newDir === exit.direction) continue;

          const key = `${exit.fromRoomId}:${exit.direction}`;
          if (processed.has(key)) continue;
          processed.add(key);

          // Check if target direction slot is free
          const fromRoom = roomById.get(exit.fromRoomId);
          const fromExits = fromRoom?.exits || [];
          const slotTaken = fromExits.some((e) => e.direction === newDir && e.fromRoomId === exit.fromRoomId);

          if (slotTaken) {
            exitWarnings.push(`${exit.direction} exit from ${exit.fromRoomId} → ${newDir} slot occupied`);
          } else {
            exitsToFix.push({ fromRoomId: exit.fromRoomId, oldDir: exit.direction, newDir, toRoomId: exit.toRoomId });
          }
        }
      }

      // Confirmation dialog
      const roomName = room.name || roomId;
      let confirmMsg = `Move "${roomName}" from (${room.x}, ${room.y}) to (${newX}, ${newY})?`;
      if (exitsToFix.length > 0) {
        confirmMsg += `\n\n${exitsToFix.length} exit(s) will be updated to match new directions.`;
      }
      if (exitWarnings.length > 0) {
        confirmMsg += `\n\n${exitWarnings.length} exit(s) cannot be auto-fixed:\n${exitWarnings.join('\n')}`;
      }
      if (!window.confirm(confirmMsg)) return;

      const slug = roomId.startsWith(`${selectedZoneId}:`) ? roomId.slice(selectedZoneId.length + 1) : roomId;

      try {
        // Step 1: Move the room coordinates
        await api.put(`/zones/${selectedZoneId}/rooms/${slug}`, { x: newX, y: newY });

        // Step 2: Fix exit directions (delete old, create new)
        // Deleting an exit also deletes the reverse, and creating re-creates the reverse.
        // So we only process one side of each bidirectional pair.
        const fixedPairs = new Set<string>(); // track "roomA:roomB" pairs we've already fixed
        for (const fix of exitsToFix) {
          const pairKey = [fix.fromRoomId, fix.toRoomId].sort().join('|');
          if (fixedPairs.has(pairKey)) continue;
          fixedPairs.add(pairKey);

          try {
            await api.del(`/rooms/${fix.fromRoomId}/exits/${fix.oldDir}`);
            await api.post(`/rooms/${fix.fromRoomId}/exits`, {
              direction: fix.newDir,
              toRoomId: fix.toRoomId,
            });
          } catch {
            exitWarnings.push(`Failed to update ${fix.oldDir} → ${fix.newDir} from ${fix.fromRoomId}`);
          }
        }

        // Step 3: Reload zone to get fresh data
        const data = await api.get<ZoneWithRooms>(`/zones/${selectedZoneId}`);
        setRooms(data.rooms || []);

        // Update allRooms
        setAllRooms((prev) => prev.map((g) =>
          g.zoneId === selectedZoneId
            ? { ...g, rooms: (data.rooms || []).map((r) => ({ id: r.id, name: r.name, x: r.x, y: r.y, exits: r.exits || [] })) }
            : g
        ));

        // Sync roomForm if the moved room is selected
        if (selectedRoomId === roomId) {
          const updated = (data.rooms || []).find((r) => r.id === roomId);
          if (updated) setRoomForm(updated);
        }

        if (exitWarnings.length > 0) {
          alert(`Room moved. Some exits could not be auto-fixed:\n${exitWarnings.join('\n')}`);
        }
      } catch (err: any) {
        alert(err.message || 'Failed to move room');
        // Reload to ensure consistent state
        try {
          const data = await api.get<ZoneWithRooms>(`/zones/${selectedZoneId}`);
          setRooms(data.rooms || []);
        } catch {}
      }
    },
    [rooms, allRooms, selectedZoneId, selectedRoomId]
  );

  const handleSaveRoom = async () => {
    if (!selectedZoneId || !selectedRoomId) return;
    // Validate interactables have success messages
    try {
      const interactables: Interactable[] = JSON.parse(roomForm.interactables || '[]');
      const missingSuccess = interactables.filter((f) => !f.description?.trim());
      if (missingSuccess.length > 0) {
        alert(`Cannot save: interactable${missingSuccess.length > 1 ? 's' : ''} ${missingSuccess.map((f) => `"${f.label || f.id}"`).join(', ')} missing a success message.`);
        return;
      }
      const missingFailure = interactables.filter((f) => f.difficultyCheck && !f.failureMessage?.trim());
      if (missingFailure.length > 0) {
        alert(`Cannot save: interactable${missingFailure.length > 1 ? 's' : ''} ${missingFailure.map((f) => `"${f.label || f.id}"`).join(', ')} missing a failure message (required when difficulty is set).`);
        return;
      }
    } catch {}
    try {
      const roomSuffix = selectedRoomId.split(':').slice(1).join(':');
      const { exits: _, _editSlug: __, _editZone: ___, ...fields } = roomForm as any;
      const updated = await api.put<Room>(
        `/zones/${selectedZoneId}/rooms/${roomSuffix}`,
        fields
      );
      setRooms((prev) =>
        prev.map((r) =>
          r.id === updated.id ? { ...updated, exits: r.exits } : r
        )
      );
      setRoomForm((prev) => ({ ...prev, ...updated }));
      // Sync allRooms so world map and exit target dropdown reflect the update
      if (selectedZoneId) {
        setAllRooms((prev) => prev.map((g) =>
          g.zoneId === selectedZoneId
            ? { ...g, rooms: g.rooms.map((r) => r.id === updated.id ? { ...r, name: updated.name, x: updated.x, y: updated.y } : r) }
            : g
        ));
      }
    } catch {}
  };

  const handleDeleteRoom = async () => {
    if (!selectedZoneId || !selectedRoomId) return;
    if (!window.confirm(`Delete room "${selectedRoomId}"? This will also remove all exits to/from this room.`)) return;
    try {
      const roomSuffix = selectedRoomId.split(':').slice(1).join(':');
      await api.del(`/zones/${selectedZoneId}/rooms/${roomSuffix}`);
      const deletedId = selectedRoomId;
      setRooms((prev) => prev.filter((r) => r.id !== deletedId));
      setSelectedRoomId(null);
      // Remove from allRooms so exit target dropdown stays in sync
      setAllRooms((prev) => prev.map((g) =>
        g.zoneId === selectedZoneId
          ? { ...g, rooms: g.rooms.filter((r) => r.id !== deletedId) }
          : g
      ));
    } catch {}
  };

  const handleDeleteExit = async (roomId: string, direction: string) => {
    try {
      await api.del(`/rooms/${roomId}/exits/${direction}`);
      const data = await api.get<ZoneWithRooms>(`/zones/${selectedZoneId}`);
      setRooms(data.rooms || []);
    } catch {}
  };

  const handleAddExitManual = async () => {
    if (!selectedRoomId || !newExitDir || !newExitTarget) return;
    try {
      await api.post(`/rooms/${selectedRoomId}/exits`, {
        direction: newExitDir,
        toRoomId: newExitTarget,
      });
      // Reload zone data to get updated exits
      if (selectedZoneId) {
        const data = await api.get<ZoneWithRooms>(`/zones/${selectedZoneId}`);
        setRooms(data.rooms || []);
      }
      setNewExitDir('');
      setNewExitTarget('');
      // Refresh all rooms list too
      const zoneList = await api.get<Zone[]>('/zones');
      const safeZones = Array.isArray(zoneList) ? zoneList : [];
      const promises = safeZones.map((z) =>
        api.get<ZoneWithRooms>(`/zones/${z.id}`).then((data) => ({
          zoneId: z.id,
          zoneName: z.name,
          rooms: (data?.rooms || []).map((r) => ({ id: r.id, name: r.name, x: r.x, y: r.y, exits: r.exits || [] })),
        }))
      );
      Promise.all(promises)
        .then((groups) => setAllRooms(Array.isArray(groups) ? groups : []))
        .catch(() => {});
    } catch {}
  };

  // 6E-bis: Render-time crash "Cannot read properties of undefined
  // (reading 'find')" trips here when either state is unexpectedly
  // undefined. useState<T[]>([]) should guarantee arrays, but a prod
  // crash was reliably reproducible on stg. Optional chain keeps the
  // render alive; the warn surfaces which state flipped for future
  // root-causing.
  if (!Array.isArray(zones) || !Array.isArray(rooms)) {
    // eslint-disable-next-line no-console
    console.warn('[ZoneEditor] unexpected non-array state', {
      zonesIsArray: Array.isArray(zones),
      roomsIsArray: Array.isArray(rooms),
      zonesType: typeof zones,
      roomsType: typeof rooms,
    })
  }
  const selectedZone = zones?.find?.((z) => z.id === selectedZoneId);
  const selectedRoom = rooms?.find?.((r) => r.id === selectedRoomId);

  return (
    <div style={styles.container}>
      {/* Left Panel */}
      <div style={styles.leftPanel}>
        <div style={styles.leftTop}>
          <button style={styles.newBtn} onClick={handleNewZone}>
            + New Zone
          </button>
        </div>
        <div style={styles.zoneList}>
          {zones.map((z) => (
            <div
              key={z.id}
              style={{
                ...styles.zoneItem,
                ...(z.id === selectedZoneId ? styles.zoneItemSelected : {}),
              }}
              onClick={() => setSelectedZoneId(z.id === selectedZoneId ? null : z.id)}
            >
              {z.name}
            </div>
          ))}
        </div>
        {selectedZone && (
          <div style={styles.zoneProps}>
            <div style={styles.sectionTitle}>Zone Properties</div>
            <label style={styles.label}>Name</label>
            <input
              style={styles.input}
              value={zoneForm.name || ''}
              onChange={(e) => setZoneForm((f) => ({ ...f, name: e.target.value }))}
            />
            <label style={styles.label}>Description</label>
            <textarea
              style={styles.textarea}
              value={zoneForm.description || ''}
              onChange={(e) => setZoneForm((f) => ({ ...f, description: e.target.value }))}
            />
            <label style={styles.label}>
              <input
                type="checkbox"
                checked={zoneForm.safe || false}
                onChange={(e) => setZoneForm((f) => ({ ...f, safe: e.target.checked }))}
              />{' '}
              Safe Zone
            </label>
            <label style={styles.label}>BGM Track ID</label>
            <input
              style={styles.input}
              value={zoneForm.bgm || ''}
              placeholder="e.g. town_peaceful"
              onChange={(e) => setZoneForm((f) => ({ ...f, bgm: e.target.value }))}
            />
            <AudioPreview
              entityType="zone"
              entityId={selectedZoneId!}
              bgm={zoneForm.bgm}
              bgmPrompt={zoneForm.bgmPrompt}
              bgmDuration={zoneForm.bgmDuration}
              onUpdate={(fields) => setZoneForm((f) => ({ ...f, ...fields }))}
            />
            <label style={styles.label}>Spawn Room</label>
            <input
              style={styles.input}
              value={zoneForm.spawnRoom || ''}
              onChange={(e) => setZoneForm((f) => ({ ...f, spawnRoom: e.target.value }))}
            />
            <label style={styles.label}>Max Entities</label>
            <input
              style={styles.input}
              type="number"
              value={zoneForm.spawnMaxEntities ?? 0}
              onChange={(e) =>
                setZoneForm((f) => ({ ...f, spawnMaxEntities: parseInt(e.target.value) || 0 }))
              }
            />
            <label style={styles.label}>Max Per Room</label>
            <input
              style={styles.input}
              type="number"
              value={zoneForm.spawnMaxPerRoom ?? 0}
              onChange={(e) =>
                setZoneForm((f) => ({ ...f, spawnMaxPerRoom: parseInt(e.target.value) || 0 }))
              }
            />
            <label style={styles.label}>Spawn Rate (ticks)</label>
            <input
              style={styles.input}
              type="number"
              value={zoneForm.spawnRateTicks ?? 0}
              onChange={(e) =>
                setZoneForm((f) => ({ ...f, spawnRateTicks: parseInt(e.target.value) || 0 }))
              }
            />
            <label style={styles.label}>Image Style (zone default)</label>
            <input
              style={styles.input}
              value={zoneForm.imageStyle || ''}
              onChange={(e) => setZoneForm((f) => ({ ...f, imageStyle: e.target.value }))}
              placeholder="e.g. pixel art, watercolor"
            />
            <label style={styles.label}>Image Negative Prompt (zone default)</label>
            <input
              style={styles.input}
              value={zoneForm.imageNegativePrompt || ''}
              onChange={(e) => setZoneForm((f) => ({ ...f, imageNegativePrompt: e.target.value }))}
              placeholder="e.g. blurry, low quality"
            />
            <div style={{ display: 'flex', gap: 8 }}>
              <button style={styles.btnSmall} onClick={handleSaveZone}>
                Save Zone
              </button>
              <button style={styles.btnDanger} onClick={handleDeleteZone}>
                Delete
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Center Panel */}
      <div style={styles.centerPanel}>
        {selectedZoneId ? (
          <>
            {/* Layer navigation bar */}
            {layers.length > 1 && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  gap: 12,
                  padding: '6px 12px',
                  backgroundColor: '#fff',
                  borderBottom: '1px solid #ddd',
                  fontSize: 13,
                  fontWeight: 600,
                  color: '#1a1a2e',
                  userSelect: 'none',
                }}
              >
                <button
                  onClick={() => {
                    const above = layers.filter((l) => l > currentLayer);
                    if (above.length > 0) setCurrentLayer(above[above.length - 1]);
                  }}
                  disabled={!canGoUp}
                  style={{
                    padding: '2px 10px',
                    fontSize: 14,
                    fontWeight: 700,
                    border: '1px solid #ccc',
                    borderRadius: 4,
                    backgroundColor: canGoUp ? '#e8eaf6' : '#f5f5f5',
                    color: canGoUp ? '#1a1a2e' : '#bbb',
                    cursor: canGoUp ? 'pointer' : 'default',
                  }}
                  title="Go up one layer"
                >
                  &#9650;
                </button>
                <span>Layer {currentLayer} ({layerLabel(currentLayer)})</span>
                <button
                  onClick={() => {
                    const below = layers.filter((l) => l < currentLayer);
                    if (below.length > 0) setCurrentLayer(below[0]);
                  }}
                  disabled={!canGoDown}
                  style={{
                    padding: '2px 10px',
                    fontSize: 14,
                    fontWeight: 700,
                    border: '1px solid #ccc',
                    borderRadius: 4,
                    backgroundColor: canGoDown ? '#e8eaf6' : '#f5f5f5',
                    color: canGoDown ? '#1a1a2e' : '#bbb',
                    cursor: canGoDown ? 'pointer' : 'default',
                  }}
                  title="Go down one layer"
                >
                  &#9660;
                </button>
              </div>
            )}
            <MapCanvas
              rooms={combinedMapRooms}
              exits={combinedMapExits}
              selectedRoomId={selectedRoomId}
              onSelectRoom={handleCombinedSelectRoom}
              onCreateRoom={handleCreateRoom}
              onCreateExit={handleCreateExit}
              onMoveRoom={handleMoveRoom}
              verticalExits={verticalExits}
              crossZoneExits={crossZoneExits}
              dimmedRoomIds={dimmedRoomIds}
              zoneLabels={zoneLabels}
            />
          </>
        ) : (
          <MapCanvas
            rooms={worldMapRooms}
            exits={worldMapExits}
            selectedRoomId={null}
            onSelectRoom={handleWorldMapSelectRoom}
            onCreateRoom={() => {}}
            onCreateExit={() => {}}
            readOnly
            zoneLabels={worldMapZoneLabels}
            roomZoneColors={worldMapRoomColors}
          />
        )}
      </div>

      {/* Right Panel */}
      <div style={styles.rightPanel}>
        {selectedRoom ? (
          <>
            <ImagePreview
              entityType="room"
              entityId={selectedRoom.id}
              description={roomForm.description}
              assetPath={roomForm.backgroundImage}
              imagePrompt={roomForm.imagePrompt}
              imageStyle={roomForm.imageStyle}
              imageNegativePrompt={roomForm.imageNegativePrompt}
              imageWidth={roomForm.imageWidth}
              imageHeight={roomForm.imageHeight}
              onUpdate={(fields) => setRoomForm((f) => ({ ...f, ...fields }))}
            />
            <div style={styles.sectionTitle}>Room Properties</div>
            <label style={styles.label}>Room ID</label>
            {(() => {
              const currentZone = roomForm._editZone ?? selectedRoom.id.split(':')[0];
              const currentSlug = roomForm._editSlug ?? selectedRoom.id.split(':').slice(1).join(':');
              const origZone = selectedRoom.id.split(':')[0];
              const origSlug = selectedRoom.id.split(':').slice(1).join(':');
              const hasChanges = currentZone !== origZone || currentSlug !== origSlug;
              return (
                <div style={{ display: 'flex', gap: 0, alignItems: 'center' }}>
                  <select
                    style={{
                      fontSize: 13,
                      color: '#555',
                      padding: '6px 4px 6px 8px',
                      backgroundColor: '#f0f0f0',
                      border: '1px solid #ccc',
                      borderRight: 'none',
                      borderRadius: '4px 0 0 4px',
                      lineHeight: '1.2',
                      cursor: 'pointer',
                      appearance: 'auto' as const,
                    }}
                    value={currentZone}
                    onChange={(e) => setRoomForm((f) => ({ ...f, _editZone: e.target.value }))}
                  >
                    {zones.map((z) => (
                      <option key={z.id} value={z.id}>{z.id}</option>
                    ))}
                  </select>
                  <span style={{
                    fontSize: 13,
                    color: '#999',
                    padding: '6px 0',
                    backgroundColor: '#f0f0f0',
                    borderTop: '1px solid #ccc',
                    borderBottom: '1px solid #ccc',
                    lineHeight: '1.2',
                  }}>:</span>
                  <input
                    style={{
                      ...styles.input,
                      flex: 1,
                      borderRadius: '0 4px 4px 0',
                      borderLeft: 'none',
                    }}
                    value={currentSlug}
                    onChange={(e) => setRoomForm((f) => ({ ...f, _editSlug: e.target.value }))}
                  />
                  <button
                    style={{
                      ...styles.btnSmall,
                      marginTop: 0,
                      marginLeft: 4,
                      opacity: hasChanges ? 1 : 0.4,
                      whiteSpace: 'nowrap',
                    }}
                    disabled={!hasChanges}
                    onClick={async () => {
                      const newSlug = currentSlug.trim();
                      if (!newSlug) { alert('ID cannot be empty'); return; }
                      if (!/^[a-zA-Z0-9_]+$/.test(newSlug)) { alert('ID must contain only letters, numbers, and underscores'); return; }
                      const oldSlug = origSlug;
                      const targetZoneId = currentZone !== origZone ? currentZone : undefined;
                      try {
                        // Save any pending form changes (name, description, etc.) before renaming
                        const { exits: _e, _editSlug: _s, _editZone: _z, ...saveFields } = roomForm as any;
                        await api.put<Room>(`/zones/${selectedZoneId}/rooms/${oldSlug}`, saveFields);
                        // Now rename (change slug and/or zone)
                        const renamed = await api.put<Room>(`/zones/${selectedZoneId}/rooms/${oldSlug}/rename`, {
                          newId: newSlug,
                          ...(targetZoneId ? { targetZoneId } : {}),
                        });
                        const newFullId = renamed.id;
                        // Reload current zone data
                        const data = await api.get<ZoneWithRooms>(`/zones/${selectedZoneId}`);
                        // Update rooms + selection together so the useEffect doesn't flash
                        setRooms(data.rooms || []);
                        if (targetZoneId) {
                          // Room moved to a different zone — deselect it (it's no longer in this zone)
                          setSelectedRoomId(null);
                          setRoomForm({});
                        } else {
                          setSelectedRoomId(newFullId);
                          setRoomForm((f) => ({ ...f, id: newFullId, _editSlug: undefined, _editZone: undefined }));
                        }
                        // Refresh allRooms across all zones (fire-and-forget)
                        api.get<Zone[]>('/zones').then((zoneList) => {
                          const safeZones = Array.isArray(zoneList) ? zoneList : [];
                          const promises = safeZones.map((z) =>
                            api.get<ZoneWithRooms>(`/zones/${z.id}`).then((data) => ({
                              zoneId: z.id,
                              zoneName: z.name,
                              rooms: (data?.rooms || []).map((r) => ({ id: r.id, name: r.name, x: r.x, y: r.y, exits: r.exits || [] })),
                            }))
                          );
                          Promise.all(promises)
                            .then((groups) => setAllRooms(Array.isArray(groups) ? groups : []))
                            .catch(() => {});
                        }).catch(() => {});
                      } catch (err: any) {
                        alert(err?.message || 'Rename failed');
                      }
                    }}
                  >
                    {(currentZone !== origZone) ? 'Move' : 'Rename'}
                  </button>
                </div>
              );
            })()}
            <label style={styles.label}>Name</label>
            <input
              style={styles.input}
              value={roomForm.name || ''}
              onChange={(e) => setRoomForm((f) => ({ ...f, name: e.target.value }))}
            />
            <label style={styles.label}>Description</label>
            <textarea
              style={styles.textarea}
              value={roomForm.description || ''}
              onChange={(e) => setRoomForm((f) => ({ ...f, description: e.target.value }))}
            />
            <label style={styles.label}>Background Image</label>
            <input
              style={styles.input}
              value={roomForm.backgroundImage || ''}
              onChange={(e) => setRoomForm((f) => ({ ...f, backgroundImage: e.target.value }))}
            />
            <label style={styles.label}>
              BGM Track ID <span style={{fontWeight:400,color:'#999',fontSize:10}}>(leave blank to use zone: {zoneForm.bgm || 'none'})</span>
            </label>
            <input
              style={styles.input}
              value={roomForm.bgm || ''}
              placeholder={`Zone default: ${zoneForm.bgm || 'none'}`}
              onChange={(e) => setRoomForm((f) => ({ ...f, bgm: e.target.value }))}
            />
            <AudioPreview
              entityType="room"
              entityId={selectedRoom!.id}
              bgm={roomForm.bgm || zoneForm.bgm}
              bgmPrompt={roomForm.bgmPrompt}
              bgmDuration={roomForm.bgmDuration}
              defaultBgmPrompt={zoneForm.bgmPrompt}
              defaultBgmDuration={zoneForm.bgmDuration}
              onUpdate={(fields) => setRoomForm((f) => ({ ...f, ...fields }))}
            />
            <label style={styles.label}>Depart Sound</label>
            <div style={{ display: 'flex', gap: 4, alignItems: 'center' }}>
              <select
                style={{ ...styles.input, flex: 1 }}
                value={roomForm.departSound || ''}
                onChange={(e) => setRoomForm((f) => ({ ...f, departSound: e.target.value }))}
              >
                <option value="">None</option>
                {movementSfx.map((sfx) => (
                  <option key={sfx.id} value={sfx.id}>{sfx.label}</option>
                ))}
              </select>
              <button
                style={{ padding: '4px 8px', fontSize: 11, fontWeight: 600, backgroundColor: '#fff', color: '#1a1a2e', border: '1px solid #ccc', borderRadius: 3, cursor: 'pointer', whiteSpace: 'nowrap' }}
                disabled={!roomForm.departSound}
                onClick={async () => {
                  if (!roomForm.departSound) return;
                  const apiPath = resolveUrl(`/assets/audio/rooms/${roomForm.departSound}.mp3`);
                  const urls = await api.signAssetUrls([apiPath]).catch(() => ({} as Record<string, string>));
                  const src = urls[apiPath];
                  if (!src) return;
                  const audio = new Audio(src);
                  audio.play().catch(() => {});
                }}
              >
                Play
              </button>
            </div>
            {/* Max hostile NPCs per room */}
            <label style={styles.label}>
              Max Hostile NPCs{' '}
              <span style={{ fontWeight: 400, color: '#999', fontSize: 10 }}>
                (blank = zone default: {zoneForm.spawnMaxPerRoom || 'unlimited'})
              </span>
            </label>
            <input
              style={styles.input}
              type="number"
              min={0}
              placeholder={`Zone default: ${zoneForm.spawnMaxPerRoom || 'unlimited'}`}
              value={roomForm.maxHostileNpcs ?? ''}
              onChange={(e) => {
                const val = e.target.value.trim();
                setRoomForm((f) => ({
                  ...f,
                  maxHostileNpcs: val === '' ? null : Math.max(0, parseInt(val) || 0),
                }));
              }}
            />
            {roomForm.maxHostileNpcs != null && zoneForm.spawnMaxPerRoom > 0 && roomForm.maxHostileNpcs > zoneForm.spawnMaxPerRoom && (
              <div style={{ color: '#e65100', fontSize: 11, marginTop: 2 }}>
                Warning: exceeds zone default ({zoneForm.spawnMaxPerRoom})
              </div>
            )}

            {/* Effects */}
            <div style={{ ...styles.sectionTitle, marginTop: 12 }}>Effects</div>
            {(() => {
              const effectsList: { type: string; value: number; message: string; sound: string }[] = (() => {
                try { return JSON.parse(roomForm.effects || '[]'); } catch { return []; }
              })();
              const updateEffects = (updated: typeof effectsList) =>
                setRoomForm((f) => ({ ...f, effects: JSON.stringify(updated) }));
              return (
                <>
                  {effectsList.map((eff, i) => (
                    <div key={i} style={{ padding: '6px 8px', backgroundColor: '#f9f9f9', borderRadius: 4, marginBottom: 4, border: '1px solid #eee' }}>
                      <div style={{ display: 'flex', gap: 4, alignItems: 'center', marginBottom: 4 }}>
                        <select
                          style={{ ...styles.input, flex: 1 }}
                          value={eff.type}
                          onChange={(e) => { const u = [...effectsList]; u[i] = { ...u[i], type: e.target.value }; updateEffects(u); }}
                        >
                          <option value="HEAL">HEAL</option>
                          <option value="POISON">POISON</option>
                          <option value="DAMAGE">DAMAGE</option>
                          <option value="MANA_REGEN">MANA_REGEN</option>
                          <option value="MANA_DRAIN">MANA_DRAIN</option>
                          <option value="SANCTUARY">SANCTUARY</option>
                        </select>
                        <input
                          style={{ ...styles.input, width: 50 }}
                          type="number"
                          title="Value"
                          value={eff.value}
                          onChange={(e) => { const u = [...effectsList]; u[i] = { ...u[i], value: parseInt(e.target.value) || 0 }; updateEffects(u); }}
                        />
                        <button
                          onClick={() => { const u = effectsList.filter((_, j) => j !== i); updateEffects(u); }}
                          style={{ background: 'none', border: 'none', color: '#d32f2f', cursor: 'pointer', fontWeight: 700, fontSize: 14, padding: '0 4px' }}
                          title="Remove effect"
                        >x</button>
                      </div>
                      <input
                        style={{ ...styles.input, marginBottom: 2 }}
                        placeholder="Message (optional)"
                        value={eff.message}
                        onChange={(e) => { const u = [...effectsList]; u[i] = { ...u[i], message: e.target.value }; updateEffects(u); }}
                      />
                      <SfxPreview
                        soundId={eff.sound || ''}
                        onSoundIdChange={(id) => { const u = [...effectsList]; u[i] = { ...u[i], sound: id }; updateEffects(u); }}
                        entityLabel={`${eff.type.toLowerCase()} effect`}
                        audioCategory="rooms"
                      />
                    </div>
                  ))}
                  <button
                    style={{ ...styles.btnSmall, width: '100%' }}
                    onClick={() => updateEffects([...effectsList, { type: 'HEAL', value: 0, message: '', sound: '' }])}
                  >+ Add Effect</button>
                </>
              );
            })()}
            {/* ─── Interactables ──────────────────────────── */}
            <div style={{ ...styles.sectionTitle, marginTop: 12 }}>Interactables</div>
            <InteractablesEditor roomForm={roomForm} setRoomForm={setRoomForm} />
            <div style={{ display: 'flex', gap: 8 }}>
              <button style={styles.btnSmall} onClick={handleSaveRoom}>
                Save Room
              </button>
              <button style={styles.btnDanger} onClick={handleDeleteRoom}>
                Delete Room
              </button>
            </div>

            {/* Exits section */}
            <div style={{ ...styles.sectionTitle, marginTop: 20 }}>Exits</div>
            {selectedRoom.exits && selectedRoom.exits.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                {selectedRoom.exits.map((exit) => {
                  const targetName = roomNameMap.get(exit.toRoomId);
                  const exitZone = exit.toRoomId.split(':')[0];
                  const isOtherZone = selectedZoneId && exitZone !== selectedZoneId;
                  const displayName = targetName
                    ? (isOtherZone ? `${targetName} (${exitZone})` : targetName)
                    : exit.toRoomId;
                  const lockedMap: Record<string, number> = (() => {
                    try { return JSON.parse(roomForm.lockedExits || '{}'); } catch { return {}; }
                  })();
                  const resetMap: Record<string, number> = (() => {
                    try { return JSON.parse(roomForm.lockResetTicks || '{}'); } catch { return {}; }
                  })();
                  const hiddenMap: Record<string, HiddenExitData> = (() => {
                    try { return JSON.parse(roomForm.hiddenExits || '{}'); } catch { return {}; }
                  })();
                  const lockDc = lockedMap[exit.direction] ?? 0;
                  const resetTicks = resetMap[exit.direction] ?? 0;
                  const isHidden = exit.direction in hiddenMap;
                  return (
                    <div
                      key={exit.direction}
                      style={{
                        padding: '4px 8px',
                        backgroundColor: isHidden ? '#e8f5e9' : lockDc > 0 ? '#fff8e1' : '#f5f5f5',
                        borderRadius: 4,
                        fontSize: 12,
                      }}
                    >
                      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                        <span style={{ flex: 1, minWidth: 0 }}>
                          <strong>{exit.direction}</strong>{' '}
                          <span style={{ color: '#666' }}>
                            {displayName}
                          </span>
                          {isHidden && <span style={{ color: '#388e3c', fontSize: 10, marginLeft: 4 }}>(hidden)</span>}
                        </span>
                        <button
                          onClick={() => {
                            const updated = { ...lockedMap };
                            if (lockDc > 0) {
                              delete updated[exit.direction];
                              // Also remove reset ticks
                              const updReset = { ...resetMap };
                              delete updReset[exit.direction];
                              setRoomForm((f) => ({ ...f, lockedExits: JSON.stringify(updated), lockResetTicks: JSON.stringify(updReset) }));
                            } else {
                              updated[exit.direction] = 10;
                              setRoomForm((f) => ({ ...f, lockedExits: JSON.stringify(updated) }));
                            }
                          }}
                          title={lockDc > 0 ? 'Remove lock' : 'Add lock'}
                          style={{
                            background: 'none',
                            border: 'none',
                            cursor: 'pointer',
                            fontSize: 14,
                            padding: '0 2px',
                            color: lockDc > 0 ? '#e65100' : '#bbb',
                          }}
                        >
                          {lockDc > 0 ? '\uD83D\uDD12' : '\uD83D\uDD13'}
                        </button>
                        {lockDc > 0 && (
                          <input
                            type="number"
                            title="Lock DC"
                            value={lockDc}
                            min={1}
                            onChange={(e) => {
                              const val = parseInt(e.target.value) || 0;
                              const updated = { ...lockedMap };
                              if (val > 0) updated[exit.direction] = val;
                              else delete updated[exit.direction];
                              setRoomForm((f) => ({ ...f, lockedExits: JSON.stringify(updated) }));
                            }}
                            style={{ width: 40, padding: '2px 4px', fontSize: 11, border: '1px solid #ccc', borderRadius: 3, textAlign: 'center' as const }}
                          />
                        )}
                        <button
                          onClick={() => handleDeleteExit(selectedRoomId!, exit.direction)}
                          style={{
                            background: 'none',
                            border: 'none',
                            color: '#d32f2f',
                            cursor: 'pointer',
                            fontWeight: 700,
                            fontSize: 14,
                            padding: '0 4px',
                          }}
                          title="Delete exit"
                        >
                          x
                        </button>
                      </div>
                      {/* Lock options (shown when locked) */}
                      {lockDc > 0 && (() => {
                        const unpickableList: string[] = (() => {
                          try { return JSON.parse(roomForm.unpickableExits || '[]'); } catch { return []; }
                        })();
                        const isUnpickable = unpickableList.includes(exit.direction);
                        return (
                          <div style={{ marginTop: 2, marginLeft: 8 }}>
                            <label style={{ fontSize: 10, color: '#888', cursor: 'pointer' }}>
                              <input
                                type="checkbox"
                                checked={isUnpickable}
                                onChange={(e) => {
                                  const updated = e.target.checked
                                    ? [...unpickableList, exit.direction]
                                    : unpickableList.filter((d) => d !== exit.direction);
                                  setRoomForm((f: any) => ({ ...f, unpickableExits: JSON.stringify(updated) }));
                                }}
                              />{' '}Unpickable (interaction only)
                            </label>
                          </div>
                        );
                      })()}
                      {lockDc > 0 && (
                        <div style={{ display: 'flex', alignItems: 'center', gap: 4, marginTop: 2, marginLeft: 8 }}>
                          <span style={{ fontSize: 10, color: '#888' }}>Re-lock after</span>
                          <input
                            type="number"
                            title="Lock reset ticks (0 = permanent unlock)"
                            value={resetTicks}
                            min={0}
                            onChange={(e) => {
                              const val = parseInt(e.target.value) || 0;
                              const updated = { ...resetMap };
                              if (val > 0) updated[exit.direction] = val;
                              else delete updated[exit.direction];
                              setRoomForm((f) => ({ ...f, lockResetTicks: JSON.stringify(updated) }));
                            }}
                            style={{ width: 40, padding: '2px 4px', fontSize: 10, border: '1px solid #ccc', borderRadius: 3, textAlign: 'center' as const }}
                          />
                          <span style={{ fontSize: 10, color: '#888' }}>ticks</span>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            ) : (
              <div style={{ fontSize: 12, color: '#999' }}>
                No exits. Drag on the map or use the form below to add one.
              </div>
            )}

            {/* Hidden Exits section */}
            <div style={{ ...styles.sectionTitle, marginTop: 16 }}>Hidden Exits</div>
            {(() => {
              const hiddenMap: Record<string, HiddenExitData> = (() => {
                try { return JSON.parse(roomForm.hiddenExits || '{}'); } catch { return {}; }
              })();
              const interactList: Interactable[] = (() => {
                try { return JSON.parse(roomForm.interactables || '[]'); } catch { return []; }
              })();
              const updateHidden = (updated: Record<string, HiddenExitData>) =>
                setRoomForm((f) => ({ ...f, hiddenExits: JSON.stringify(updated) }));
              const exitDirs = selectedRoom.exits?.map((e) => e.direction) || [];
              const hiddenDirs = Object.keys(hiddenMap);

              return (
                <>
                  {hiddenDirs.length > 0 ? (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      {hiddenDirs.map((dir) => {
                        const data = hiddenMap[dir];
                        const linkedInteractables = interactList.filter(
                          (f) => f.actionType === 'EXIT_OPEN' && f.actionData?.direction === dir
                        );
                        return (
                          <div key={dir} style={{ padding: '6px 8px', backgroundColor: '#e8f5e9', borderRadius: 4, border: '1px solid #c8e6c9' }}>
                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 4 }}>
                              <div>
                                <strong style={{ fontSize: 12 }}>{dir}</strong>
                                {linkedInteractables.length > 0 ? (
                                  <span style={{ fontSize: 9, color: '#388e3c', marginLeft: 6 }}>
                                    Opened by: {linkedInteractables.map((f) => f.label || f.id).join(', ')}
                                  </span>
                                ) : (
                                  <span style={{ fontSize: 9, color: '#e65100', marginLeft: 6 }}>
                                    No interactable linked
                                  </span>
                                )}
                              </div>
                              <button
                                onClick={() => {
                                  const updated = { ...hiddenMap };
                                  delete updated[dir];
                                  updateHidden(updated);
                                }}
                                style={{ background: 'none', border: 'none', color: '#d32f2f', cursor: 'pointer', fontWeight: 700, fontSize: 14, padding: '0 4px' }}
                                title="Remove hidden exit"
                              >x</button>
                            </div>
                            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 4, fontSize: 10 }}>
                              <div>
                                <label style={{ color: '#666' }}>Perception DC</label>
                                <input
                                  type="number"
                                  value={data.perceptionDC}
                                  min={1}
                                  onChange={(e) => {
                                    const updated = { ...hiddenMap, [dir]: { ...data, perceptionDC: parseInt(e.target.value) || 0 } };
                                    updateHidden(updated);
                                  }}
                                  style={{ width: '100%', padding: '2px 4px', fontSize: 11, border: '1px solid #ccc', borderRadius: 3, boxSizing: 'border-box' as const }}
                                />
                              </div>
                              <div>
                                <label style={{ color: '#666' }}>Lock DC</label>
                                <input
                                  type="number"
                                  value={data.lockDifficulty}
                                  min={0}
                                  onChange={(e) => {
                                    const updated = { ...hiddenMap, [dir]: { ...data, lockDifficulty: parseInt(e.target.value) || 0 } };
                                    updateHidden(updated);
                                  }}
                                  style={{ width: '100%', padding: '2px 4px', fontSize: 11, border: '1px solid #ccc', borderRadius: 3, boxSizing: 'border-box' as const }}
                                />
                              </div>
                              <div>
                                <label style={{ color: '#666' }}>Re-hide ticks</label>
                                <input
                                  type="number"
                                  value={data.hiddenResetTicks}
                                  min={0}
                                  onChange={(e) => {
                                    const updated = { ...hiddenMap, [dir]: { ...data, hiddenResetTicks: parseInt(e.target.value) || 0 } };
                                    updateHidden(updated);
                                  }}
                                  style={{ width: '100%', padding: '2px 4px', fontSize: 11, border: '1px solid #ccc', borderRadius: 3, boxSizing: 'border-box' as const }}
                                />
                              </div>
                              <div>
                                <label style={{ color: '#666' }}>Re-lock ticks</label>
                                <input
                                  type="number"
                                  value={data.lockResetTicks}
                                  min={0}
                                  onChange={(e) => {
                                    const updated = { ...hiddenMap, [dir]: { ...data, lockResetTicks: parseInt(e.target.value) || 0 } };
                                    updateHidden(updated);
                                  }}
                                  style={{ width: '100%', padding: '2px 4px', fontSize: 11, border: '1px solid #ccc', borderRadius: 3, boxSizing: 'border-box' as const }}
                                />
                              </div>
                            </div>
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <div style={{ fontSize: 11, color: '#999', marginBottom: 4 }}>
                      No hidden exits. Mark an existing exit as hidden below.
                    </div>
                  )}
                  {/* Toggle: mark an existing exit as hidden */}
                  {exitDirs.filter((d) => !hiddenDirs.includes(d)).length > 0 && (
                    <div style={{ marginTop: 4 }}>
                      <select
                        style={{ ...styles.input, fontSize: 11 }}
                        value=""
                        onChange={(e) => {
                          const dir = e.target.value;
                          if (!dir) return;
                          const updated = { ...hiddenMap, [dir]: { perceptionDC: 15, lockDifficulty: 0, hiddenResetTicks: 0, lockResetTicks: 0 } };
                          updateHidden(updated);
                        }}
                      >
                        <option value="">-- Mark exit as hidden --</option>
                        {exitDirs.filter((d) => !hiddenDirs.includes(d)).map((d) => (
                          <option key={d} value={d}>{d}</option>
                        ))}
                      </select>
                    </div>
                  )}
                </>
              );
            })()}

            {/* Add Exit form */}
            <div style={{ marginTop: 12, padding: '10px', backgroundColor: '#f9f9f9', borderRadius: 4, border: '1px solid #eee' }}>
              <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6, color: '#444' }}>Add Exit</div>
              <label style={{ ...styles.label, marginTop: 0 }}>Direction</label>
              <select
                style={styles.input}
                value={newExitDir}
                onChange={(e) => setNewExitDir(e.target.value)}
              >
                <option value="">-- Direction --</option>
                {ALL_DIRECTIONS.map((d) => (
                  <option key={d} value={d}>{d}</option>
                ))}
              </select>
              <label style={styles.label}>Target Room</label>
              <select
                style={styles.input}
                value={newExitTarget}
                onChange={(e) => setNewExitTarget(e.target.value)}
              >
                <option value="">-- Target Room --</option>
                {allRooms.map((group) => (
                  <optgroup key={group.zoneId} label={group.zoneName}>
                    {group.rooms.map((r) => (
                      <option key={r.id} value={r.id}>{r.name} ({r.id})</option>
                    ))}
                  </optgroup>
                ))}
              </select>
              <button
                style={{ ...styles.btnSmall, marginTop: 8, width: '100%', opacity: newExitDir && newExitTarget ? 1 : 0.5 }}
                disabled={!newExitDir || !newExitTarget}
                onClick={handleAddExitManual}
              >
                Create Exit
              </button>
            </div>
          </>
        ) : (
          <div style={{ color: '#999', fontSize: 13, textAlign: 'center', marginTop: 40 }}>
            {selectedZoneId
              ? 'Click a room to edit, click an empty cell to create one, or Alt+drag a room to move it'
              : 'Select a zone first'}
          </div>
        )}
      </div>
    </div>
  );
}

export default ZoneEditor;
