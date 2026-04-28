import { useEffect, useState, useCallback, useMemo } from 'react';
import api from '../api';
import MapCanvas from '../components/MapCanvas';
import type { MapOverlay } from '../components/MapCanvas';
import ImagePreview from '../components/ImagePreview';
import SfxPreview from '../components/SfxPreview';
import type { CSSProperties } from 'react';

interface Zone {
  id: string;
  name: string;
}

interface Exit {
  fromRoomId: string;
  toRoomId: string;
  direction: string;
}

interface Room {
  id: string;
  name: string;
  x: number;
  y: number;
  exits: Exit[];
}

interface NpcRecord {
  id: string;
  name: string;
  description: string;
  zoneId: string;
  startRoomId: string;
  behaviorType: string;
  patrolRoute: string;
  hostile: boolean;
  maxHp: number;
  damage: number;
  level: number;
  perception: number;
  xpReward: number;
  accuracy: number;
  defense: number;
  evasion: number;
  agility: number;
  vendorItems: string;
  crafterRecipes: string;
  trainerConfig: string;
  spawnPoints: string;
  lootItems: string;
  coinDrop: string;
  attackSound: string;
  missSound: string;
  deathSound: string;
  interactSound: string;
  exitSound: string;
  imagePrompt: string;
  imageStyle: string;
  imageNegativePrompt: string;
  imageWidth: number;
  imageHeight: number;
}

type MapMode = 'view' | 'start' | 'patrol' | 'spawns';

const BEHAVIOR_OPTIONS = [
  { value: 'idle', label: 'Idle' },
  { value: 'wander', label: 'Wander' },
  { value: 'patrol', label: 'Patrol' },
  { value: 'vendor', label: 'Vendor' },
  { value: 'trainer', label: 'Trainer' },
  { value: 'crafter', label: 'Crafter' },
  { value: 'quest', label: 'Quest Giver' },
];

const BEHAVIOR_DESCRIPTIONS: Record<string, string> = {
  idle: 'Stays in start room, never moves.',
  wander: 'Wanders randomly through connected rooms in the zone.',
  patrol: 'Walks a fixed route of rooms in sequence.',
  vendor: 'Stays in start room. Players can buy/sell items.',
  trainer: 'Stays in start room. Players can train skills/levels.',
  crafter: 'Stays in start room. Players can craft items from recipes.',
  quest: 'Stays in start room. Quest giver.',
};

const styles: Record<string, CSSProperties> = {
  container: { display: 'flex', height: '100%', overflow: 'hidden' },
  listPanel: {
    width: 220, minWidth: 220, borderRight: '1px solid #ddd',
    backgroundColor: '#fff', display: 'flex', flexDirection: 'column', overflow: 'hidden',
  },
  listTop: { padding: 12, borderBottom: '1px solid #eee' },
  newBtn: {
    width: '100%', padding: '8px', fontSize: 13, fontWeight: 600,
    backgroundColor: '#1a1a2e', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer',
  },
  listItems: { flex: 1, overflowY: 'auto', padding: '4px 0' },
  listItem: { padding: '8px 12px', cursor: 'pointer', fontSize: 13, borderBottom: '1px solid #f0f0f0' },
  listItemSelected: { backgroundColor: '#e8eaf6', fontWeight: 600 },
  centerPanel: { flex: 1, display: 'flex', flexDirection: 'column', overflow: 'hidden' },
  mapToolbar: {
    display: 'flex', gap: 4, padding: '6px 8px', backgroundColor: '#fff',
    borderBottom: '1px solid #ddd', alignItems: 'center', flexWrap: 'wrap',
  },
  modeBtn: {
    padding: '4px 10px', fontSize: 11, fontWeight: 600,
    borderWidth: 1, borderStyle: 'solid', borderColor: '#ccc', borderRadius: 4,
    cursor: 'pointer', backgroundColor: '#f5f5f5', color: '#333',
  },
  modeBtnActive: { backgroundColor: '#1a1a2e', color: '#fff', borderColor: '#1a1a2e' },
  mapArea: { flex: 1, position: 'relative' },
  rightPanel: {
    width: 340, minWidth: 340, borderLeft: '1px solid #ddd',
    backgroundColor: '#fafafa', overflowY: 'auto', padding: 16,
  },
  sectionTitle: { fontSize: 14, fontWeight: 700, marginBottom: 8, marginTop: 16, color: '#1a1a2e' },
  label: { display: 'block', fontSize: 11, fontWeight: 600, color: '#666', marginBottom: 3, marginTop: 8 },
  input: {
    width: '100%', padding: '6px 8px', fontSize: 13, border: '1px solid #ccc',
    borderRadius: 4, boxSizing: 'border-box' as const,
  },
  textarea: {
    width: '100%', padding: '6px 8px', fontSize: 13, border: '1px solid #ccc',
    borderRadius: 4, minHeight: 60, resize: 'vertical' as const, boxSizing: 'border-box' as const,
  },
  select: {
    width: '100%', padding: '6px 8px', fontSize: 13, border: '1px solid #ccc',
    borderRadius: 4, boxSizing: 'border-box' as const,
  },
  jsonTextarea: {
    width: '100%', padding: '6px 8px', fontSize: 12, fontFamily: 'monospace',
    border: '1px solid #ccc', borderRadius: 4, minHeight: 80, resize: 'vertical' as const,
    boxSizing: 'border-box' as const,
  },
  btnRow: { display: 'flex', gap: 8, marginTop: 16 },
  btnSmall: {
    padding: '8px 16px', fontSize: 13, fontWeight: 600,
    backgroundColor: '#1a1a2e', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer',
  },
  btnDanger: {
    padding: '8px 16px', fontSize: 13, fontWeight: 600,
    backgroundColor: '#d32f2f', color: '#fff', border: 'none', borderRadius: 4, cursor: 'pointer',
  },
  error: { color: '#d32f2f', fontSize: 12, marginTop: 4 },
  empty: { color: '#999', fontSize: 13, textAlign: 'center', marginTop: 40 },
  routeItem: {
    display: 'flex', alignItems: 'center', gap: 4, padding: '4px 8px',
    backgroundColor: '#f3e5f5', borderRadius: 4, fontSize: 12, marginBottom: 2,
  },
  spawnItem: {
    display: 'flex', alignItems: 'center', gap: 4, padding: '4px 8px',
    backgroundColor: '#e0f7fa', borderRadius: 4, fontSize: 12, marginBottom: 2,
  },
};

function prettyJson(value: string): string {
  if (!value || value === '') return '';
  try { return JSON.stringify(JSON.parse(value), null, 2); } catch { return value; }
}

function parseJsonArray(val: string): string[] {
  if (!val) return [];
  try {
    const parsed = JSON.parse(val);
    return Array.isArray(parsed) ? parsed : [];
  } catch { return []; }
}

interface ItemRecord {
  id: string;
  name: string;
}

interface RecipeRecord {
  id: string;
  name: string;
}

interface LootEntry {
  itemId: string;
  chance: number;
  minQuantity: number;
  maxQuantity: number;
}

const COIN_FIELDS = [
  { key: 'minCopper', label: 'Min Copper' },
  { key: 'maxCopper', label: 'Max Copper' },
  { key: 'minSilver', label: 'Min Silver' },
  { key: 'maxSilver', label: 'Max Silver' },
  { key: 'minGold', label: 'Min Gold' },
  { key: 'maxGold', label: 'Max Gold' },
];

function parseLootItems(val: string): LootEntry[] {
  if (!val) return [];
  try {
    const parsed = JSON.parse(val);
    return Array.isArray(parsed) ? parsed : [];
  } catch { return []; }
}

function parseCoinDrop(val: string): Record<string, number> {
  if (!val) return {};
  try {
    const parsed = JSON.parse(val);
    return typeof parsed === 'object' && !Array.isArray(parsed) ? parsed : {};
  } catch { return {}; }
}

function parseObject(val: string): Record<string, any> {
  if (!val) return {};
  try {
    const parsed = JSON.parse(val);
    return typeof parsed === 'object' && !Array.isArray(parsed) && parsed !== null ? parsed : {};
  } catch { return {}; }
}

function NpcEditor() {
  // Data state
  const [zones, setZones] = useState<Zone[]>([]);
  const [npcs, setNpcs] = useState<NpcRecord[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]); // rooms for the selected NPC's zone
  const [allZoneRooms, setAllZoneRooms] = useState<Map<string, Room[]>>(new Map());
  const [items, setItems] = useState<ItemRecord[]>([]);
  const [recipes, setRecipes] = useState<RecipeRecord[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [form, setForm] = useState<Record<string, any>>({});
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [mapMode, setMapMode] = useState<MapMode>('view');

  // Load zones, NPCs, and items
  useEffect(() => {
    // Array-coerce at the setter boundary — see src/api.ts for rationale.
    api.get<Zone[]>('/zones').then((d) => setZones(Array.isArray(d) ? d : [])).catch(() => {});
    api.get<NpcRecord[]>('/npcs').then((d) => setNpcs(Array.isArray(d) ? d : [])).catch(() => {});
    api.get<ItemRecord[]>('/items').then((d) => setItems(Array.isArray(d) ? d : [])).catch(() => {});
    api.get<RecipeRecord[]>('/recipes').then((d) => setRecipes(Array.isArray(d) ? d : [])).catch(() => {});
  }, []);

  // Load all zone rooms for the map (cached per zone)
  const loadZoneRooms = useCallback(async (zoneId: string) => {
    if (allZoneRooms.has(zoneId)) {
      setRooms(allZoneRooms.get(zoneId)!);
      return;
    }
    try {
      const zoneRooms = await api.get<Room[]>(`/zones/${zoneId}/rooms`);
      setAllZoneRooms((prev) => new Map(prev).set(zoneId, zoneRooms));
      setRooms(zoneRooms);
    } catch {
      setRooms([]);
    }
  }, [allZoneRooms]);

  // When selected NPC changes, load its zone's rooms
  useEffect(() => {
    const zoneId = form.zoneId;
    if (zoneId) {
      loadZoneRooms(zoneId);
    } else {
      setRooms([]);
    }
  }, [form.zoneId, loadZoneRooms]);

  // Build all room options for dropdowns (cross-zone)
  const allRoomOptions = useMemo(() => {
    const opts: { value: string; label: string }[] = [];
    for (const zone of zones) {
      const zoneRooms = allZoneRooms.get(zone.id) || [];
      for (const r of zoneRooms) {
        opts.push({ value: r.id, label: `${zone.name} > ${r.name}` });
      }
    }
    return opts;
  }, [zones, allZoneRooms]);

  // Eagerly load all zone rooms for dropdown options
  useEffect(() => {
    for (const zone of zones) {
      if (!allZoneRooms.has(zone.id)) {
        api.get<Room[]>(`/zones/${zone.id}/rooms`).then((zoneRooms) => {
          setAllZoneRooms((prev) => new Map(prev).set(zone.id, zoneRooms));
        }).catch(() => {});
      }
    }
  }, [zones, allZoneRooms]);

  const handleSelect = (id: string) => {
    const npc = npcs.find((n) => n.id === id);
    if (!npc) return;
    setSelectedId(id);
    setIsNew(false);
    setError('');
    setMapMode('view');
    setForm({
      ...npc,
      vendorItems: prettyJson(npc.vendorItems),
      crafterRecipes: prettyJson(npc.crafterRecipes),
      trainerConfig: prettyJson(npc.trainerConfig),
      lootItems: prettyJson(npc.lootItems),
      coinDrop: prettyJson(npc.coinDrop),
    });
  };

  const handleNew = () => {
    setSelectedId(null);
    setIsNew(true);
    setError('');
    setMapMode('view');
    setForm({
      id: '', name: '', description: '', zoneId: zones[0]?.id || '', startRoomId: '',
      behaviorType: 'idle', hostile: false, level: 1, maxHp: 10, damage: 1,
      accuracy: 0, defense: 0, evasion: 0, agility: 10, perception: 0, xpReward: 0,
      patrolRoute: '', vendorItems: '', crafterRecipes: '', trainerConfig: '', spawnPoints: '[]', lootItems: '', coinDrop: '',
      attackSound: '', missSound: '', deathSound: '', interactSound: '', exitSound: '',
      imagePrompt: '', imageStyle: '', imageNegativePrompt: '', imageWidth: 384, imageHeight: 512,
    });
  };

  const handleChange = (key: string, value: any) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const handleSave = async () => {
    setError('');
    if (!form.id?.trim()) { setError('ID is required'); return; }
    if (!form.name?.trim()) { setError('Name is required'); return; }
    if (!form.zoneId) { setError('Zone is required'); return; }

    // Validate JSON fields
    for (const key of ['vendorItems', 'crafterRecipes', 'trainerConfig', 'lootItems', 'coinDrop']) {
      const val = form[key];
      if (val && val.trim()) {
        try { JSON.parse(val); } catch { setError(`Invalid JSON in ${key}`); return; }
      }
    }

    try {
      if (isNew) {
        const created = await api.post<NpcRecord>('/npcs', form);
        setNpcs((prev) => [...prev, created]);
        setSelectedId(created.id);
        setIsNew(false);
        setForm({
          ...created,
          vendorItems: prettyJson(created.vendorItems),
          crafterRecipes: prettyJson(created.crafterRecipes),
          trainerConfig: prettyJson(created.trainerConfig),
          lootItems: prettyJson(created.lootItems),
          coinDrop: prettyJson(created.coinDrop),
        });
      } else if (selectedId) {
        const { id: _id, ...updateData } = form;
        const updated = await api.put<NpcRecord>(`/npcs/${selectedId}`, updateData);
        setNpcs((prev) => prev.map((n) => (n.id === selectedId ? updated : n)));
        setForm({
          ...updated,
          vendorItems: prettyJson(updated.vendorItems),
          crafterRecipes: prettyJson(updated.crafterRecipes),
          trainerConfig: prettyJson(updated.trainerConfig),
          lootItems: prettyJson(updated.lootItems),
          coinDrop: prettyJson(updated.coinDrop),
        });
      }
    } catch (err: any) {
      setError(err.message || 'Save failed');
    }
  };

  const handleDelete = async () => {
    if (!selectedId) return;
    if (!confirm('Delete this NPC?')) return;
    try {
      await api.del(`/npcs/${selectedId}`);
      setNpcs((prev) => prev.filter((n) => n.id !== selectedId));
      setSelectedId(null);
      setForm({});
      setIsNew(false);
    } catch (err: any) {
      setError(err.message || 'Delete failed');
    }
  };

  // Room name lookup
  const roomNameMap = useMemo(() => {
    const map = new Map<string, string>();
    for (const r of rooms) map.set(r.id, r.name);
    return map;
  }, [rooms]);

  // Item name lookup
  const itemNameMap = useMemo(() => {
    const map = new Map<string, string>();
    for (const item of items) map.set(item.id, item.name);
    return map;
  }, [items]);

  // Parse patrol route and spawn points from form
  const patrolRoute = useMemo(() => parseJsonArray(form.patrolRoute), [form.patrolRoute]);
  const spawnPoints = useMemo(() => parseJsonArray(form.spawnPoints), [form.spawnPoints]);

  // Build map overlay based on selected NPC's behavior
  const mapOverlay = useMemo((): MapOverlay | undefined => {
    if (!form.zoneId || rooms.length === 0) return undefined;
    const overlay: MapOverlay = {};

    // Start room
    if (form.startRoomId) overlay.startRoomId = form.startRoomId;

    // Spawn points
    if (spawnPoints.length > 0) overlay.spawnPoints = spawnPoints;

    const bt = form.behaviorType;
    if (bt === 'patrol' && patrolRoute.length >= 2) {
      // Patrol: highlight route rooms magenta, draw route lines
      overlay.patrolRoute = patrolRoute;
      const tints = new Map<string, string>();
      for (const rid of patrolRoute) tints.set(rid, '#e040fb');
      overlay.roomTints = tints;
    } else if (bt === 'wander') {
      // Wander: BFS from start room through same-zone exits to find reachable rooms
      const tints = new Map<string, string>();
      const roomMap = new Map<string, Room>();
      for (const r of rooms) roomMap.set(r.id, r);
      const zoneRoomIds = new Set(rooms.map((r) => r.id));

      if (form.startRoomId && roomMap.has(form.startRoomId)) {
        const visited = new Set<string>();
        const queue = [form.startRoomId];
        visited.add(form.startRoomId);
        while (queue.length > 0) {
          const cur = queue.shift()!;
          const room = roomMap.get(cur);
          if (!room) continue;
          for (const exit of room.exits || []) {
            if (!visited.has(exit.toRoomId) && zoneRoomIds.has(exit.toRoomId)) {
              visited.add(exit.toRoomId);
              queue.push(exit.toRoomId);
            }
          }
        }
        for (const rid of visited) tints.set(rid, '#42a5f5');
        // Unreachable rooms in zone get a muted tint
        for (const r of rooms) {
          if (!visited.has(r.id)) tints.set(r.id, '#e0e0e0');
        }
      } else {
        // No start room set — tint all rooms blue
        for (const r of rooms) tints.set(r.id, '#42a5f5');
      }
      overlay.roomTints = tints;
    }
    // idle/vendor/quest: no tint, just start room marker

    return overlay;
  }, [form.zoneId, form.startRoomId, form.behaviorType, rooms, patrolRoute, spawnPoints]);

  // Map exit data
  const mapExits = useMemo(() => {
    const exits: { fromRoomId: string; toRoomId: string; direction: string }[] = [];
    for (const room of rooms) {
      for (const exit of (room.exits || [])) {
        exits.push(exit);
      }
    }
    return exits;
  }, [rooms]);

  // Handle map room click based on mode
  const handleMapRoomClick = useCallback((roomId: string) => {
    if (mapMode === 'start') {
      handleChange('startRoomId', roomId);
      setMapMode('view');
    } else if (mapMode === 'patrol') {
      const current = parseJsonArray(form.patrolRoute);
      // Toggle: if last item is same room, remove it; otherwise append
      if (current.length > 0 && current[current.length - 1] === roomId) {
        current.pop();
      } else {
        current.push(roomId);
      }
      handleChange('patrolRoute', JSON.stringify(current));
    } else if (mapMode === 'spawns') {
      const current = parseJsonArray(form.spawnPoints);
      const idx = current.indexOf(roomId);
      if (idx >= 0) {
        current.splice(idx, 1);
      } else {
        current.push(roomId);
      }
      handleChange('spawnPoints', JSON.stringify(current));
    }
  }, [mapMode, form.patrolRoute, form.spawnPoints]);

  const noopCreate = useCallback(() => {}, []);
  const noopExit = useCallback((_f: string, _t: string) => {}, []);

  const showForm = isNew || selectedId !== null;
  const showMapToolbar = showForm && !isNew && rooms.length > 0;

  // Filtered NPC list
  const filteredNpcs = useMemo(() => {
    return npcs
      .filter((n) => {
        if (!search) return true;
        const q = search.toLowerCase();
        return n.name.toLowerCase().includes(q) || n.id.toLowerCase().includes(q);
      })
      .sort((a, b) => a.name.localeCompare(b.name));
  }, [npcs, search]);

  return (
    <div style={styles.container}>
      {/* Left: NPC List */}
      <div style={styles.listPanel}>
        <div style={styles.listTop}>
          <button
            style={{ ...styles.newBtn, ...(zones.length === 0 ? { opacity: 0.5, cursor: 'not-allowed' } : {}) }}
            onClick={zones.length === 0 ? undefined : handleNew}
            disabled={zones.length === 0}
          >
            + New NPC
          </button>
          {zones.length === 0 && (
            <div style={{ fontSize: 11, color: '#999', marginTop: 6, textAlign: 'center' }}>
              Create a zone first before adding NPCs.
            </div>
          )}
          <input
            type="text"
            placeholder="Search NPCs..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            style={{ ...styles.input, marginTop: 8, fontSize: 12 }}
          />
        </div>
        <div style={styles.listItems}>
          {filteredNpcs.map((npc) => (
            <div
              key={npc.id}
              style={{
                ...styles.listItem,
                ...(npc.id === selectedId && !isNew ? styles.listItemSelected : {}),
              }}
              onClick={() => handleSelect(npc.id)}
            >
              <div>{npc.name || npc.id}</div>
              <div style={{ fontSize: 10, color: '#999' }}>
                {npc.behaviorType} &middot; {zones.find((z) => z.id === npc.zoneId)?.name || npc.zoneId}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Center: Zone Map */}
      <div style={styles.centerPanel}>
        {showMapToolbar && (
          <div style={styles.mapToolbar}>
            <span style={{ fontSize: 11, color: '#666', marginRight: 4 }}>Map Mode:</span>
            {(['view', 'start', 'patrol', 'spawns'] as MapMode[]).map((mode) => {
              // Only show patrol mode for patrol behavior
              if (mode === 'patrol' && form.behaviorType !== 'patrol') return null;
              const labels: Record<MapMode, string> = {
                view: 'View', start: 'Set Start', patrol: 'Edit Patrol', spawns: 'Edit Spawns',
              };
              return (
                <button
                  key={mode}
                  style={{
                    ...styles.modeBtn,
                    ...(mapMode === mode ? styles.modeBtnActive : {}),
                  }}
                  onClick={() => setMapMode(mapMode === mode ? 'view' : mode)}
                >
                  {labels[mode]}
                </button>
              );
            })}
            {mapMode !== 'view' && (
              <span style={{ fontSize: 10, color: '#e65100', marginLeft: 8 }}>
                {mapMode === 'start' && 'Click a room to set as start room'}
                {mapMode === 'patrol' && 'Click rooms to add/remove from patrol route'}
                {mapMode === 'spawns' && 'Click rooms to toggle as spawn points'}
              </span>
            )}
          </div>
        )}
        <div style={styles.mapArea}>
          {showForm && rooms.length > 0 ? (
            <MapCanvas
              rooms={rooms.map((r) => ({ id: r.id, name: r.name, x: r.x, y: r.y }))}
              exits={mapExits}
              selectedRoomId={form.startRoomId || null}
              onSelectRoom={handleMapRoomClick}
              onCreateRoom={noopCreate}
              onCreateExit={noopExit}
              overlay={mapOverlay}
              readOnly
            />
          ) : (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', height: '100%', color: '#999' }}>
              {showForm ? 'Select a zone to see the map' : 'Select an NPC to visualize its behavior on the map'}
            </div>
          )}
        </div>
      </div>

      {/* Right: NPC Form */}
      <div style={styles.rightPanel}>
        {showForm ? (
          <>
            {/* Image preview for existing NPCs */}
            {!isNew && selectedId && (
              <ImagePreview
                entityType="npc"
                entityId={selectedId}
                description={form.description}
                imagePrompt={form.imagePrompt}
                imageStyle={form.imageStyle}
                imageNegativePrompt={form.imageNegativePrompt}
                imageWidth={form.imageWidth}
                imageHeight={form.imageHeight}
                maxWidth={384}
                maxHeight={512}
                onUpdate={(fields) => setForm((f: any) => ({ ...f, ...fields }))}
              />
            )}

            <div style={{ ...styles.sectionTitle, marginTop: 0 }}>
              {isNew ? 'New NPC' : `Edit: ${form.name || selectedId}`}
            </div>

            {/* Identity */}
            <label style={styles.label}>ID</label>
            <input
              style={{ ...styles.input, ...(selectedId && !isNew ? { backgroundColor: '#eee' } : {}) }}
              value={form.id ?? ''}
              placeholder="e.g. npc:goblin_guard"
              disabled={!!selectedId && !isNew}
              onChange={(e) => handleChange('id', e.target.value)}
            />

            <label style={styles.label}>Name</label>
            <input
              style={styles.input}
              value={form.name ?? ''}
              placeholder="NPC name"
              onChange={(e) => handleChange('name', e.target.value)}
            />

            <label style={styles.label}>Description</label>
            <textarea
              style={styles.textarea}
              value={form.description ?? ''}
              rows={3}
              onChange={(e) => handleChange('description', e.target.value)}
            />

            {/* Zone & Behavior */}
            <div style={styles.sectionTitle}>Location & Behavior</div>

            <label style={styles.label}>Zone</label>
            <select
              style={styles.select}
              value={form.zoneId ?? ''}
              onChange={(e) => {
                handleChange('zoneId', e.target.value);
                // Clear room-specific fields when zone changes
                handleChange('startRoomId', '');
                handleChange('patrolRoute', '');
                handleChange('spawnPoints', '[]');
              }}
            >
              <option value="">-- Select Zone --</option>
              {zones.map((z) => (
                <option key={z.id} value={z.id}>{z.name}</option>
              ))}
            </select>

            <label style={styles.label}>Start Room</label>
            <div style={{ display: 'flex', gap: 4 }}>
              <select
                style={{ ...styles.select, flex: 1 }}
                value={form.startRoomId ?? ''}
                onChange={(e) => handleChange('startRoomId', e.target.value)}
              >
                <option value="">-- Select --</option>
                {rooms.map((r) => (
                  <option key={r.id} value={r.id}>{r.name} ({r.id})</option>
                ))}
              </select>
              {rooms.length > 0 && (
                <button
                  style={{
                    ...styles.modeBtn,
                    ...(mapMode === 'start' ? styles.modeBtnActive : {}),
                    whiteSpace: 'nowrap',
                  }}
                  onClick={() => setMapMode(mapMode === 'start' ? 'view' : 'start')}
                  title="Click a room on the map to set start room"
                >
                  Pick
                </button>
              )}
            </div>

            <label style={styles.label}>Behavior Type</label>
            <select
              style={styles.select}
              value={form.behaviorType ?? 'idle'}
              onChange={(e) => handleChange('behaviorType', e.target.value)}
            >
              {BEHAVIOR_OPTIONS.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </select>
            <div style={{ fontSize: 10, color: '#888', marginTop: 2 }}>
              {BEHAVIOR_DESCRIPTIONS[form.behaviorType] || ''}
            </div>

            <label style={{ ...styles.label, marginTop: 4 }}>
              <input
                type="checkbox"
                checked={form.hostile ?? false}
                onChange={(e) => handleChange('hostile', e.target.checked)}
              />{' '}Hostile
            </label>

            {/* Patrol Route (visual editor) */}
            {form.behaviorType === 'patrol' && (
              <>
                <div style={styles.sectionTitle}>Patrol Route</div>
                {patrolRoute.length === 0 && (
                  <div style={{ fontSize: 11, color: '#999', marginBottom: 4 }}>
                    No patrol route. Click "Edit Patrol" and click rooms on the map to build a route.
                  </div>
                )}
                {patrolRoute.map((roomId, i) => (
                  <div key={`${roomId}-${i}`} style={styles.routeItem}>
                    <span style={{
                      display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
                      width: 18, height: 18, borderRadius: '50%', backgroundColor: '#e040fb',
                      color: '#fff', fontSize: 10, fontWeight: 700, flexShrink: 0,
                    }}>{i + 1}</span>
                    <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {roomNameMap.get(roomId) || roomId}
                    </span>
                    {i > 0 && (
                      <button
                        onClick={() => {
                          const arr = [...patrolRoute];
                          [arr[i - 1], arr[i]] = [arr[i], arr[i - 1]];
                          handleChange('patrolRoute', JSON.stringify(arr));
                        }}
                        style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 12, padding: '0 2px', color: '#666' }}
                        title="Move up"
                      >&uarr;</button>
                    )}
                    {i < patrolRoute.length - 1 && (
                      <button
                        onClick={() => {
                          const arr = [...patrolRoute];
                          [arr[i], arr[i + 1]] = [arr[i + 1], arr[i]];
                          handleChange('patrolRoute', JSON.stringify(arr));
                        }}
                        style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 12, padding: '0 2px', color: '#666' }}
                        title="Move down"
                      >&darr;</button>
                    )}
                    <button
                      onClick={() => {
                        const arr = patrolRoute.filter((_, j) => j !== i);
                        handleChange('patrolRoute', JSON.stringify(arr));
                      }}
                      style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, fontWeight: 700, padding: '0 4px', color: '#d32f2f' }}
                      title="Remove from route"
                    >x</button>
                  </div>
                ))}
                {rooms.length > 0 && (
                  <button
                    style={{
                      ...styles.modeBtn,
                      ...(mapMode === 'patrol' ? styles.modeBtnActive : {}),
                      width: '100%', marginTop: 4,
                    }}
                    onClick={() => setMapMode(mapMode === 'patrol' ? 'view' : 'patrol')}
                  >
                    {mapMode === 'patrol' ? 'Done Editing Route' : 'Edit Patrol on Map'}
                  </button>
                )}
              </>
            )}

            {/* Spawn Points (visual editor) */}
            <div style={styles.sectionTitle}>Spawn Points</div>
            <div style={{ fontSize: 10, color: '#888', marginBottom: 4 }}>
              Rooms where this NPC can respawn after death. If empty, uses start room only.
            </div>
            {spawnPoints.length === 0 && (
              <div style={{ fontSize: 11, color: '#999', marginBottom: 4 }}>
                No spawn points set (defaults to start room).
              </div>
            )}
            {spawnPoints.map((roomId, i) => (
              <div key={roomId} style={styles.spawnItem}>
                <span style={{
                  display: 'inline-block', width: 10, height: 10,
                  backgroundColor: '#00bcd4', transform: 'rotate(45deg)', flexShrink: 0,
                }} />
                <span style={{ flex: 1 }}>{roomNameMap.get(roomId) || roomId}</span>
                <button
                  onClick={() => {
                    const arr = spawnPoints.filter((_, j) => j !== i);
                    handleChange('spawnPoints', JSON.stringify(arr));
                  }}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, fontWeight: 700, padding: '0 4px', color: '#d32f2f' }}
                  title="Remove spawn point"
                >x</button>
              </div>
            ))}
            {rooms.length > 0 && (
              <button
                style={{
                  ...styles.modeBtn,
                  ...(mapMode === 'spawns' ? styles.modeBtnActive : {}),
                  width: '100%', marginTop: 4,
                }}
                onClick={() => setMapMode(mapMode === 'spawns' ? 'view' : 'spawns')}
              >
                {mapMode === 'spawns' ? 'Done Editing Spawns' : 'Edit Spawns on Map'}
              </button>
            )}

            {/* Combat Stats */}
            <div style={styles.sectionTitle}>Combat Stats</div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '4px 12px' }}>
              {[
                ['level', 'Level'], ['maxHp', 'Max HP'], ['damage', 'Damage'],
                ['accuracy', 'Accuracy'], ['defense', 'Defense'], ['evasion', 'Evasion'],
                ['agility', 'Agility'], ['perception', 'Perception'], ['xpReward', 'XP Reward'],
              ].map(([key, label]) => (
                <div key={key}>
                  <label style={{ ...styles.label, marginTop: 2 }}>{label}</label>
                  <input
                    style={styles.input}
                    type="number"
                    value={form[key] ?? 0}
                    onChange={(e) => handleChange(key, parseInt(e.target.value) || 0)}
                  />
                </div>
              ))}
            </div>

            {/* Vendor Items (only for vendor NPCs) */}
            {form.behaviorType === 'vendor' && (() => {
              const vendorList = parseJsonArray(form.vendorItems);
              return (
                <>
                  <div style={styles.sectionTitle}>Vendor Items</div>
                  {vendorList.length === 0 && (
                    <div style={{ fontSize: 11, color: '#999', marginBottom: 4 }}>
                      No vendor items. Add items this NPC sells.
                    </div>
                  )}
                  {vendorList.map((itemId, i) => (
                    <div key={`${itemId}-${i}`} style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '4px 8px', backgroundColor: '#e8f5e9', borderRadius: 4, fontSize: 12, marginBottom: 2 }}>
                      <span style={{ flex: 1 }}>{itemNameMap.get(itemId) || itemId}</span>
                      <button
                        onClick={() => {
                          const arr = vendorList.filter((_, j) => j !== i);
                          handleChange('vendorItems', arr.length > 0 ? JSON.stringify(arr) : '');
                        }}
                        style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, fontWeight: 700, padding: '0 4px', color: '#d32f2f' }}
                        title="Remove item"
                      >x</button>
                    </div>
                  ))}
                  <div style={{ display: 'flex', gap: 4, marginTop: 4 }}>
                    <select
                      id="vendor-item-add"
                      style={{ ...styles.select, flex: 1, fontSize: 12 }}
                      defaultValue=""
                    >
                      <option value="">-- Select item --</option>
                      {items.filter((it) => !vendorList.includes(it.id)).map((it) => (
                        <option key={it.id} value={it.id}>{it.name} ({it.id})</option>
                      ))}
                    </select>
                    <button
                      style={{ ...styles.modeBtn, whiteSpace: 'nowrap' }}
                      onClick={() => {
                        const sel = (document.getElementById('vendor-item-add') as HTMLSelectElement)?.value;
                        if (!sel) return;
                        const arr = [...vendorList, sel];
                        handleChange('vendorItems', JSON.stringify(arr));
                        (document.getElementById('vendor-item-add') as HTMLSelectElement).value = '';
                      }}
                    >Add</button>
                  </div>
                </>
              );
            })()}

            {/* Crafter Recipes (only for crafter NPCs) */}
            {form.behaviorType === 'crafter' && (() => {
              const recipeList = parseJsonArray(form.crafterRecipes);
              return (
                <>
                  <div style={styles.sectionTitle}>Crafter Recipes</div>
                  {recipeList.length === 0 && (
                    <div style={{ fontSize: 11, color: '#999', marginBottom: 4 }}>
                      No recipes assigned. Add recipes this NPC can craft.
                    </div>
                  )}
                  {recipeList.map((recipeId, i) => {
                    const recipe = recipes.find((r) => r.id === recipeId);
                    return (
                      <div key={`${recipeId}-${i}`} style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '4px 8px', backgroundColor: '#fff3e0', borderRadius: 4, fontSize: 12, marginBottom: 2 }}>
                        <span style={{ flex: 1 }}>{recipe?.name || recipeId}</span>
                        <button
                          onClick={() => {
                            const arr = recipeList.filter((_, j) => j !== i);
                            handleChange('crafterRecipes', arr.length > 0 ? JSON.stringify(arr) : '');
                          }}
                          style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, fontWeight: 700, padding: '0 4px', color: '#d32f2f' }}
                          title="Remove recipe"
                        >x</button>
                      </div>
                    );
                  })}
                  <div style={{ display: 'flex', gap: 4, marginTop: 4 }}>
                    <select
                      id="crafter-recipe-add"
                      style={{ ...styles.select, flex: 1, fontSize: 12 }}
                      defaultValue=""
                    >
                      <option value="">-- Select recipe --</option>
                      {recipes.filter((r) => !recipeList.includes(r.id)).map((r) => (
                        <option key={r.id} value={r.id}>{r.name} ({r.id})</option>
                      ))}
                    </select>
                    <button
                      style={{ ...styles.modeBtn, whiteSpace: 'nowrap' }}
                      onClick={() => {
                        const sel = (document.getElementById('crafter-recipe-add') as HTMLSelectElement)?.value;
                        if (!sel) return;
                        const arr = [...recipeList, sel];
                        handleChange('crafterRecipes', JSON.stringify(arr));
                        (document.getElementById('crafter-recipe-add') as HTMLSelectElement).value = '';
                      }}
                    >Add</button>
                  </div>
                </>
              );
            })()}

            {/* Trainer Config (only for trainer NPCs) */}
            {form.behaviorType === 'trainer' && (() => {
              const tc = parseObject(form.trainerConfig);
              const setTcField = (key: string, value: any) => {
                const next = { ...tc, [key]: value };
                // Strip empty/null values to keep JSON minimal.
                for (const k of Object.keys(next)) {
                  if (next[k] === '' || next[k] === null || next[k] === undefined) delete next[k];
                }
                handleChange('trainerConfig', Object.keys(next).length > 0 ? JSON.stringify(next, null, 2) : '');
              };
              return (
                <>
                  <div style={styles.sectionTitle}>Trainer Tier (Phase 8)</div>
                  <div style={{ fontSize: 11, color: '#666', marginBottom: 6 }}>
                    Caps how high this trainer can take a player. Leave blank for "no caps" (legacy behavior).
                  </div>
                  <label style={styles.label}>Tier (1–5)</label>
                  <input
                    type="number"
                    style={styles.input}
                    value={tc.tier ?? ''}
                    onChange={(e) => setTcField('tier', e.target.value === '' ? '' : (parseInt(e.target.value) || 0))}
                  />
                  <label style={styles.label}>Max Level</label>
                  <input
                    type="number"
                    style={styles.input}
                    value={tc.maxLevel ?? ''}
                    onChange={(e) => setTcField('maxLevel', e.target.value === '' ? '' : (parseInt(e.target.value) || 0))}
                  />
                  <label style={styles.label}>Max Stat Value</label>
                  <input
                    type="number"
                    style={styles.input}
                    value={tc.maxStatValue ?? ''}
                    onChange={(e) => setTcField('maxStatValue', e.target.value === '' ? '' : (parseInt(e.target.value) || 0))}
                  />
                  <label style={styles.label}>Next-Tier Trainer Name (optional)</label>
                  <input
                    type="text"
                    style={styles.input}
                    value={tc.nextTierTrainerName ?? ''}
                    onChange={(e) => setTcField('nextTierTrainerName', e.target.value)}
                    placeholder="e.g. Master Aldric"
                  />
                  <label style={styles.label}>Next-Tier Location Hint (optional)</label>
                  <input
                    type="text"
                    style={styles.input}
                    value={tc.nextTierLocationHint ?? ''}
                    onChange={(e) => setTcField('nextTierLocationHint', e.target.value)}
                    placeholder="e.g. the Foothill Pass"
                  />
                </>
              );
            })()}

            {/* Loot */}
            <div style={styles.sectionTitle}>Loot Items</div>
            {(() => {
              const lootList = parseLootItems(form.lootItems);
              return (
                <>
                  {lootList.length === 0 && (
                    <div style={{ fontSize: 11, color: '#999', marginBottom: 4 }}>
                      No loot entries. Add items this NPC can drop.
                    </div>
                  )}
                  {lootList.map((entry, i) => (
                    <div key={i} style={{ border: '1px solid #e0e0e0', borderRadius: 4, padding: 8, marginBottom: 6, backgroundColor: '#fff' }}>
                      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 4 }}>
                        <span style={{ fontSize: 11, fontWeight: 600, color: '#666' }}>Loot Entry {i + 1}</span>
                        <button
                          onClick={() => {
                            const arr = lootList.filter((_, j) => j !== i);
                            handleChange('lootItems', arr.length > 0 ? JSON.stringify(arr) : '');
                          }}
                          style={{ background: 'none', border: 'none', cursor: 'pointer', fontSize: 14, fontWeight: 700, color: '#d32f2f' }}
                        >x</button>
                      </div>
                      <label style={{ fontSize: 10, color: '#888' }}>Item</label>
                      <select
                        style={{ ...styles.select, fontSize: 12, marginBottom: 4 }}
                        value={entry.itemId}
                        onChange={(e) => {
                          const arr = [...lootList];
                          arr[i] = { ...arr[i], itemId: e.target.value };
                          handleChange('lootItems', JSON.stringify(arr));
                        }}
                      >
                        <option value="">-- Select --</option>
                        {items.map((it) => (
                          <option key={it.id} value={it.id}>{it.name} ({it.id})</option>
                        ))}
                      </select>
                      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr 1fr', gap: 4 }}>
                        <div>
                          <label style={{ fontSize: 10, color: '#888' }}>Chance</label>
                          <input
                            style={styles.input}
                            type="number"
                            step="0.05"
                            min="0"
                            max="1"
                            value={entry.chance}
                            onChange={(e) => {
                              const arr = [...lootList];
                              arr[i] = { ...arr[i], chance: parseFloat(e.target.value) || 0 };
                              handleChange('lootItems', JSON.stringify(arr));
                            }}
                          />
                        </div>
                        <div>
                          <label style={{ fontSize: 10, color: '#888' }}>Min Qty</label>
                          <input
                            style={styles.input}
                            type="number"
                            min="0"
                            value={entry.minQuantity}
                            onChange={(e) => {
                              const arr = [...lootList];
                              arr[i] = { ...arr[i], minQuantity: parseInt(e.target.value) || 0 };
                              handleChange('lootItems', JSON.stringify(arr));
                            }}
                          />
                        </div>
                        <div>
                          <label style={{ fontSize: 10, color: '#888' }}>Max Qty</label>
                          <input
                            style={styles.input}
                            type="number"
                            min="0"
                            value={entry.maxQuantity}
                            onChange={(e) => {
                              const arr = [...lootList];
                              arr[i] = { ...arr[i], maxQuantity: parseInt(e.target.value) || 0 };
                              handleChange('lootItems', JSON.stringify(arr));
                            }}
                          />
                        </div>
                      </div>
                    </div>
                  ))}
                  <button
                    style={{ ...styles.modeBtn, width: '100%', marginTop: 2 }}
                    onClick={() => {
                      const arr = [...lootList, { itemId: '', chance: 0.5, minQuantity: 1, maxQuantity: 1 }];
                      handleChange('lootItems', JSON.stringify(arr));
                    }}
                  >+ Add Loot Entry</button>
                </>
              );
            })()}

            {/* Coin Drop */}
            <div style={styles.sectionTitle}>Coin Drop</div>
            {(() => {
              const coins = parseCoinDrop(form.coinDrop);
              return (
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '4px 12px' }}>
                  {COIN_FIELDS.map(({ key, label }) => (
                    <div key={key}>
                      <label style={{ fontSize: 10, color: '#888' }}>{label}</label>
                      <input
                        style={styles.input}
                        type="number"
                        min="0"
                        value={coins[key] ?? 0}
                        onChange={(e) => {
                          const v = parseInt(e.target.value) || 0;
                          const next = { ...coins, [key]: v };
                          // Remove zero values
                          const clean: Record<string, number> = {};
                          for (const cf of COIN_FIELDS) {
                            if (next[cf.key] && next[cf.key] > 0) clean[cf.key] = next[cf.key];
                          }
                          handleChange('coinDrop', Object.keys(clean).length > 0 ? JSON.stringify(clean) : '');
                        }}
                      />
                    </div>
                  ))}
                </div>
              );
            })()}

            {/* Sounds */}
            <div style={styles.sectionTitle}>Sounds</div>
            {[
              ['attackSound', 'Attack Sound'],
              ['missSound', 'Miss Sound'],
              ['deathSound', 'Death Sound'],
              ['interactSound', 'Interact Sound'],
              ['exitSound', 'Exit Sound'],
            ].map(([key, label]) => (
              <div key={key} style={{ marginBottom: 8 }}>
                <label style={styles.label}>{label}</label>
                <SfxPreview
                  soundId={form[key] ?? ''}
                  onSoundIdChange={(id) => handleChange(key, id)}
                  entityLabel={form.name || form.id || ''}
                  audioCategory="npcs"
                />
              </div>
            ))}

            {/* Image (only show fields when ImagePreview is not handling them) */}
            {(isNew || !selectedId) && (
              <>
                <div style={styles.sectionTitle}>Image</div>
                <label style={styles.label}>Image Prompt</label>
                <textarea
                  style={styles.textarea}
                  value={form.imagePrompt ?? ''}
                  rows={3}
                  onChange={(e) => handleChange('imagePrompt', e.target.value)}
                />
                <label style={styles.label}>Image Style</label>
                <input style={styles.input} value={form.imageStyle ?? ''} onChange={(e) => handleChange('imageStyle', e.target.value)} />
                <label style={styles.label}>Negative Prompt</label>
                <input style={styles.input} value={form.imageNegativePrompt ?? ''} onChange={(e) => handleChange('imageNegativePrompt', e.target.value)} />
                <label style={styles.label}>Width</label>
                <input style={styles.input} type="number" value={form.imageWidth ?? 384} max={512} onChange={(e) => handleChange('imageWidth', Math.min(parseInt(e.target.value) || 0, 512))} />
                <label style={styles.label}>Height</label>
                <input style={styles.input} type="number" value={form.imageHeight ?? 512} max={512} onChange={(e) => handleChange('imageHeight', Math.min(parseInt(e.target.value) || 0, 512))} />
              </>
            )}

            {error && <div style={styles.error}>{error}</div>}
            <div style={styles.btnRow}>
              <button style={styles.btnSmall} onClick={handleSave}>
                {isNew ? 'Create' : 'Save'}
              </button>
              {!isNew && selectedId && (
                <button style={styles.btnDanger} onClick={handleDelete}>Delete</button>
              )}
            </div>
          </>
        ) : (
          <div style={styles.empty}>
            Select an NPC to edit, or click "+ New NPC" to create one.
          </div>
        )}
      </div>
    </div>
  );
}

export default NpcEditor;
