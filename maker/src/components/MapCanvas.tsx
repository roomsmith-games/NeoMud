import { useRef, useEffect, useCallback, useState } from 'react';

interface RoomNode {
  id: string;
  name: string;
  x: number;
  y: number;
}

interface ExitEdge {
  fromRoomId: string;
  toRoomId: string;
  direction: string;
  isLocked?: boolean;
  isHidden?: boolean;
}

interface VerticalExit {
  roomId: string;
  direction: string;
}

interface CrossZoneExit {
  roomId: string;
  direction: string;     // NORTH, SOUTH, EAST, WEST
  targetZone: string;    // display name, e.g. "Thornveil Marsh"
  targetZoneId: string;  // e.g. "marsh"
}

export interface MapOverlay {
  /** Rooms to tint with a semi-transparent color: roomId -> color */
  roomTints?: Map<string, string>;
  /** Start room gets a gold star marker */
  startRoomId?: string;
  /** Patrol route: ordered room IDs drawn as connected numbered path */
  patrolRoute?: string[];
  /** Spawn point rooms get cyan diamond markers */
  spawnPoints?: string[];
}

interface ZoneLabel {
  zoneName: string;
  cx: number;  // centroid x in grid coords
  cy: number;  // centroid y in grid coords
  color?: string;  // optional zone color for world map view
}

// Palette for distinguishing zones in the world map view
const ZONE_COLORS = [
  '#3f51b5', '#e91e63', '#009688', '#ff9800', '#673ab7',
  '#4caf50', '#f44336', '#00bcd4', '#795548', '#607d8b',
];

interface MovePreview {
  roomId: string;
  origX: number;
  origY: number;
  ghostX: number;
  ghostY: number;
  mouseX: number;
  mouseY: number;
}

interface MapCanvasProps {
  rooms: RoomNode[];
  exits: ExitEdge[];
  selectedRoomId: string | null;
  onSelectRoom: (id: string) => void;
  onCreateRoom: (x: number, y: number) => void;
  onCreateExit: (fromId: string, toId: string) => void;
  /** Called when a room is alt-dragged to a new grid position */
  onMoveRoom?: (roomId: string, newX: number, newY: number) => void;
  verticalExits?: VerticalExit[];
  crossZoneExits?: CrossZoneExit[];
  /** NPC behavior overlays (tints, patrol routes, markers) */
  overlay?: MapOverlay;
  /** If true, room creation and exit dragging are disabled (view + click-select only) */
  readOnly?: boolean;
  /** Room IDs to render at reduced opacity (other zones' rooms in world map view) */
  dimmedRoomIds?: Set<string>;
  /** Labels to draw at zone centroids for dimmed zones or world map zones */
  zoneLabels?: ZoneLabel[];
  /** Per-room zone color override (world map view) — roomId → color */
  roomZoneColors?: Map<string, string>;
}

const CELL_SIZE = 80;
const GAP = 20;
const STRIDE = CELL_SIZE + GAP; // 100
const ROOM_RADIUS = 8;

/** Infer compass direction from grid delta (dx = target.x - source.x, dy = target.y - source.y) */
function inferDirection(dx: number, dy: number): string | null {
  if (dx === 0 && dy === 0) return null;
  // Normalize to sign only for direction inference
  const sx = Math.sign(dx);
  const sy = Math.sign(dy);
  if (sy > 0 && sx === 0) return 'NORTH';
  if (sy < 0 && sx === 0) return 'SOUTH';
  if (sx > 0 && sy === 0) return 'EAST';
  if (sx < 0 && sy === 0) return 'WEST';
  if (sy > 0 && sx > 0) return 'NORTHEAST';
  if (sy > 0 && sx < 0) return 'NORTHWEST';
  if (sy < 0 && sx > 0) return 'SOUTHEAST';
  if (sy < 0 && sx < 0) return 'SOUTHWEST';
  return null;
}

function MapCanvas({
  rooms: roomsProp,
  exits: exitsProp,
  selectedRoomId,
  onSelectRoom,
  onCreateRoom,
  onCreateExit,
  onMoveRoom,
  verticalExits = [],
  crossZoneExits = [],
  overlay,
  readOnly = false,
  dimmedRoomIds,
  zoneLabels = [],
  roomZoneColors,
}: MapCanvasProps) {
  // Defend against undefined array props at the boundary. The TS
  // interface marks these required but a 6E bug shipped an `undefined`
  // through (empty-project Zones view crash: Cannot read properties of
  // undefined (reading 'find')). Normalising here keeps every `.find`
  // / `.filter` / `.flatMap` below safe regardless of caller discipline.
  const rooms = roomsProp ?? []
  const exits = exitsProp ?? []
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);

  const [offset, setOffset] = useState({ x: 0, y: 0 });
  const [canvasSize, setCanvasSize] = useState({ w: 800, h: 600 });

  // Track current mouse position for drag preview (exit creation)
  const [dragPreview, setDragPreview] = useState<{
    fromId: string;
    mouseX: number;
    mouseY: number;
  } | null>(null);

  // Track room move drag preview
  const [movePreview, setMovePreview] = useState<MovePreview | null>(null);

  const dragState = useRef<{
    type: 'none' | 'pan' | 'exit' | 'move';
    startX: number;
    startY: number;
    offsetStart: { x: number; y: number };
    fromRoomId?: string;
    origGridX?: number;
    origGridY?: number;
  }>({ type: 'none', startX: 0, startY: 0, offsetStart: { x: 0, y: 0 } });

  // ResizeObserver
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const ro = new ResizeObserver((entries) => {
      for (const entry of entries) {
        const { width, height } = entry.contentRect;
        setCanvasSize({ w: Math.floor(width), h: Math.floor(height) });
      }
    });
    ro.observe(container);
    return () => ro.disconnect();
  }, []);

  // Center view on rooms' bounding-box center whenever the room set changes
  // When dimmedRoomIds is provided, center on non-dimmed (active zone) rooms only
  // When selectedRoomId changes alongside rooms, center on that specific room
  const prevRoomKey = useRef('');
  useEffect(() => {
    if (canvasSize.w <= 0 || canvasSize.h <= 0) return;
    // Build key from non-dimmed rooms + selectedRoomId so zone switches via room click trigger re-centering
    const activeRooms = dimmedRoomIds
      ? rooms.filter((r) => !dimmedRoomIds.has(r.id))
      : rooms;
    const key = activeRooms.map((r) => r.id).sort().join(',') + '|' + (selectedRoomId ?? '');
    if (key === prevRoomKey.current) return;
    prevRoomKey.current = key;

    // If a specific room is selected and exists in the room set, center on it
    const selectedRoom = selectedRoomId ? rooms.find((r) => r.id === selectedRoomId) : null;
    if (selectedRoom) {
      setOffset({
        x: Math.floor(canvasSize.w / 2 - selectedRoom.x * STRIDE - STRIDE / 2),
        y: Math.floor(canvasSize.h / 2 + selectedRoom.y * STRIDE - STRIDE / 2),
      });
      return;
    }

    const centerRooms = activeRooms.length > 0 ? activeRooms : rooms;
    if (centerRooms.length === 0) {
      setOffset({
        x: Math.floor(canvasSize.w / 2 - STRIDE / 2),
        y: Math.floor(canvasSize.h / 2 - STRIDE / 2),
      });
      return;
    }

    const xs = centerRooms.map((r) => r.x);
    const ys = centerRooms.map((r) => r.y);
    const cx = (Math.min(...xs) + Math.max(...xs)) / 2;
    const cy = (Math.min(...ys) + Math.max(...ys)) / 2;
    setOffset({
      x: Math.floor(canvasSize.w / 2 - cx * STRIDE - STRIDE / 2),
      y: Math.floor(canvasSize.h / 2 + cy * STRIDE - STRIDE / 2),
    });
  }, [rooms, canvasSize.w, canvasSize.h, dimmedRoomIds, selectedRoomId]);

  // Convert grid coords to pixel coords (center of cell, centered within stride)
  const gridToPixel = useCallback(
    (gx: number, gy: number) => {
      return {
        px: offset.x + gx * STRIDE + STRIDE / 2,
        py: offset.y + -gy * STRIDE + STRIDE / 2,
      };
    },
    [offset]
  );

  // Convert pixel to grid coords
  const pixelToGrid = useCallback(
    (px: number, py: number) => {
      const gx = Math.floor((px - offset.x) / STRIDE);
      const gy = -Math.floor((py - offset.y) / STRIDE);
      return { gx, gy };
    },
    [offset]
  );

  // Find room at pixel coords
  const roomAtPixel = useCallback(
    (px: number, py: number): RoomNode | null => {
      for (const room of rooms) {
        const { px: cx, py: cy } = gridToPixel(room.x, room.y);
        if (
          px >= cx - CELL_SIZE / 2 &&
          px <= cx + CELL_SIZE / 2 &&
          py >= cy - CELL_SIZE / 2 &&
          py <= cy + CELL_SIZE / 2
        ) {
          return room;
        }
      }
      return null;
    },
    [rooms, gridToPixel]
  );

  // Draw
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dpr = window.devicePixelRatio || 1;
    canvas.width = canvasSize.w * dpr;
    canvas.height = canvasSize.h * dpr;
    ctx.scale(dpr, dpr);

    // Clear
    ctx.fillStyle = '#fafafa';
    ctx.fillRect(0, 0, canvasSize.w, canvasSize.h);

    // Draw grid lines
    ctx.strokeStyle = '#e8e8e8';
    ctx.lineWidth = 1;

    const startGX = Math.floor(-offset.x / STRIDE) - 1;
    const endGX = startGX + Math.ceil(canvasSize.w / STRIDE) + 2;
    const startGY = Math.floor(-offset.y / STRIDE) - 1;
    const endGY = startGY + Math.ceil(canvasSize.h / STRIDE) + 2;

    for (let gx = startGX; gx <= endGX; gx++) {
      const px = offset.x + gx * STRIDE;
      ctx.beginPath();
      ctx.moveTo(px, 0);
      ctx.lineTo(px, canvasSize.h);
      ctx.stroke();
    }
    for (let gy = startGY; gy <= endGY; gy++) {
      const py = offset.y + gy * STRIDE;
      ctx.beginPath();
      ctx.moveTo(0, py);
      ctx.lineTo(canvasSize.w, py);
      ctx.stroke();
    }

    // Build room lookup
    const roomMap = new Map<string, RoomNode>();
    for (const r of rooms) roomMap.set(r.id, r);

    const isDimmed = dimmedRoomIds ? (id: string) => dimmedRoomIds.has(id) : () => false;

    // Compute exit color for reuse in line + arrowhead passes
    const exitColor = (exit: ExitEdge): string => {
      if (exit.isHidden && exit.isLocked) return '#7b1fa2'; // purple
      if (exit.isHidden) return '#388e3c'; // green
      if (exit.isLocked) return '#e65100'; // orange
      return '#888';
    };

    // --- Dimmed pass: draw other zones' exits and rooms as ghostly background ---
    if (dimmedRoomIds && dimmedRoomIds.size > 0) {
      // Dimmed exit lines
      ctx.save();
      ctx.globalAlpha = 0.2;
      for (const exit of exits) {
        const from = roomMap.get(exit.fromRoomId);
        const to = roomMap.get(exit.toRoomId);
        if (!from || !to) continue;
        if (!isDimmed(exit.fromRoomId) && !isDimmed(exit.toRoomId)) continue;
        const { px: x1, py: y1 } = gridToPixel(from.x, from.y);
        const { px: x2, py: y2 } = gridToPixel(to.x, to.y);
        ctx.strokeStyle = '#888';
        ctx.setLineDash([]);
        ctx.lineWidth = 2;
        ctx.beginPath();
        ctx.moveTo(x1, y1);
        ctx.lineTo(x2, y2);
        ctx.stroke();
      }
      ctx.restore();

      // Dimmed rooms
      ctx.save();
      ctx.globalAlpha = 0.25;
      for (const room of rooms) {
        if (!isDimmed(room.id)) continue;
        const { px, py } = gridToPixel(room.x, room.y);
        const x = px - CELL_SIZE / 2;
        const y = py - CELL_SIZE / 2;
        ctx.beginPath();
        ctx.roundRect(x, y, CELL_SIZE, CELL_SIZE, ROOM_RADIUS);
        ctx.closePath();
        ctx.fillStyle = '#9e9e9e';
        ctx.fill();
        // Room name
        ctx.fillStyle = '#fff';
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        const displayName = room.name.length > 10 ? room.name.substring(0, 9) + '...' : room.name;
        ctx.fillText(displayName, px, py);
      }
      ctx.restore();
    }

    // Zone labels at centroids (renders in both dimmed and world map views)
    if (zoneLabels.length > 0) {
      for (const label of zoneLabels) {
        const { px, py } = gridToPixel(label.cx, label.cy);
        ctx.save();
        ctx.fillStyle = label.color ? label.color + '99' : 'rgba(100,100,100,0.5)';
        ctx.font = 'bold 14px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        ctx.fillText(label.zoneName, px, py - CELL_SIZE / 2 - 14);
        ctx.restore();
      }
    }

    // --- Normal pass: draw active zone exits and rooms ---

    // Pass 1: Draw exit lines (under rooms) — skip dimmed exits
    for (const exit of exits) {
      if (isDimmed(exit.fromRoomId) || isDimmed(exit.toRoomId)) continue;
      const from = roomMap.get(exit.fromRoomId);
      const to = roomMap.get(exit.toRoomId);
      if (!from || !to) continue;
      const { px: x1, py: y1 } = gridToPixel(from.x, from.y);
      const { px: x2, py: y2 } = gridToPixel(to.x, to.y);

      ctx.strokeStyle = exitColor(exit);
      ctx.setLineDash(exit.isHidden ? [6, 4] : []);
      ctx.lineWidth = 2;
      ctx.beginPath();
      ctx.moveTo(x1, y1);
      ctx.lineTo(x2, y2);
      ctx.stroke();
    }
    ctx.setLineDash([]);

    // Draw drag preview line
    if (dragPreview) {
      const fromRoom = roomMap.get(dragPreview.fromId);
      if (fromRoom) {
        const { px: fx, py: fy } = gridToPixel(fromRoom.x, fromRoom.y);
        ctx.strokeStyle = '#ffc107';
        ctx.lineWidth = 2;
        ctx.setLineDash([6, 4]);
        ctx.beginPath();
        ctx.moveTo(fx, fy);
        ctx.lineTo(dragPreview.mouseX, dragPreview.mouseY);
        ctx.stroke();
        ctx.setLineDash([]);

        // Highlight target room if hovering over one
        const target = roomAtPixel(dragPreview.mouseX, dragPreview.mouseY);
        if (target && target.id !== dragPreview.fromId) {
          const { px: tx, py: ty } = gridToPixel(target.x, target.y);
          ctx.strokeStyle = '#4caf50';
          ctx.lineWidth = 3;
          ctx.beginPath();
          const rx = tx - CELL_SIZE / 2 - 2;
          const ry = ty - CELL_SIZE / 2 - 2;
          const rw = CELL_SIZE + 4;
          const rh = CELL_SIZE + 4;
          ctx.roundRect(rx, ry, rw, rh, ROOM_RADIUS + 2);
          ctx.stroke();
        }
      }
    }

    // Draw rooms — skip dimmed rooms (already drawn)
    for (const room of rooms) {
      if (isDimmed(room.id)) continue;
      const isMoving = movePreview && room.id === movePreview.roomId;
      const { px, py } = gridToPixel(room.x, room.y);
      const x = px - CELL_SIZE / 2;
      const y = py - CELL_SIZE / 2;

      ctx.save();
      if (isMoving) ctx.globalAlpha = 0.3;

      ctx.beginPath();
      ctx.roundRect(x, y, CELL_SIZE, CELL_SIZE, ROOM_RADIUS);
      ctx.closePath();

      ctx.fillStyle = roomZoneColors?.get(room.id) ?? '#3f51b5';
      ctx.fill();

      if (isMoving) {
        ctx.strokeStyle = '#999';
        ctx.lineWidth = 2;
        ctx.setLineDash([4, 4]);
        ctx.stroke();
        ctx.setLineDash([]);
      } else if (room.id === selectedRoomId) {
        ctx.strokeStyle = '#ffc107';
        ctx.lineWidth = 3;
        ctx.stroke();
      }

      // Room name
      ctx.fillStyle = '#fff';
      ctx.font = '11px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      const displayName =
        room.name.length > 10 ? room.name.substring(0, 9) + '...' : room.name;
      ctx.fillText(displayName, px, py);
      ctx.restore();
    }

    // Draw move preview ghost room + stretched exit lines
    if (movePreview) {
      const movingRoom = roomMap.get(movePreview.roomId);
      if (movingRoom) {
        const { px: ghostPx, py: ghostPy } = gridToPixel(movePreview.ghostX, movePreview.ghostY);
        const occupied = rooms.some(
          (r) => r.id !== movePreview.roomId && r.x === movePreview.ghostX && r.y === movePreview.ghostY
        );

        // Draw stretched exit lines from connected rooms to ghost position
        for (const exit of exits) {
          let connectedRoom: RoomNode | undefined;
          if (exit.fromRoomId === movePreview.roomId) connectedRoom = roomMap.get(exit.toRoomId);
          else if (exit.toRoomId === movePreview.roomId) connectedRoom = roomMap.get(exit.fromRoomId);
          if (!connectedRoom) continue;
          const { px: cx, py: cy } = gridToPixel(connectedRoom.x, connectedRoom.y);

          // Check if exit direction still matches geometry
          const dx = exit.fromRoomId === movePreview.roomId
            ? connectedRoom.x - movePreview.ghostX
            : movePreview.ghostX - connectedRoom.x;
          const dy = exit.fromRoomId === movePreview.roomId
            ? connectedRoom.y - movePreview.ghostY
            : movePreview.ghostY - connectedRoom.y;
          const newDir = inferDirection(dx, dy);
          const dirMatches = newDir === exit.direction;

          ctx.strokeStyle = dirMatches ? '#4caf50' : '#ff9800';
          ctx.lineWidth = 2;
          ctx.setLineDash([6, 4]);
          ctx.beginPath();
          ctx.moveTo(cx, cy);
          ctx.lineTo(ghostPx, ghostPy);
          ctx.stroke();
          ctx.setLineDash([]);
        }

        // Ghost room
        const gx = ghostPx - CELL_SIZE / 2;
        const gy = ghostPy - CELL_SIZE / 2;
        ctx.save();
        ctx.globalAlpha = 0.7;
        ctx.beginPath();
        ctx.roundRect(gx, gy, CELL_SIZE, CELL_SIZE, ROOM_RADIUS);
        ctx.closePath();
        ctx.fillStyle = occupied ? '#d32f2f' : '#4caf50';
        ctx.fill();
        // Ghost room name
        ctx.fillStyle = '#fff';
        ctx.font = '11px sans-serif';
        ctx.textAlign = 'center';
        ctx.textBaseline = 'middle';
        const ghostName = movingRoom.name.length > 10 ? movingRoom.name.substring(0, 9) + '...' : movingRoom.name;
        ctx.fillText(ghostName, ghostPx, ghostPy);
        ctx.restore();

        // Collision indicator border
        if (occupied) {
          ctx.strokeStyle = '#d32f2f';
          ctx.lineWidth = 3;
          ctx.beginPath();
          ctx.roundRect(gx - 2, gy - 2, CELL_SIZE + 4, CELL_SIZE + 4, ROOM_RADIUS + 2);
          ctx.stroke();
        }
      }
    }

    // NPC behavior overlay pass
    if (overlay) {
      // Room tints (semi-transparent color overlay on rooms)
      if (overlay.roomTints) {
        for (const [roomId, color] of overlay.roomTints) {
          const room = roomMap.get(roomId);
          if (!room) continue;
          const { px, py } = gridToPixel(room.x, room.y);
          const x = px - CELL_SIZE / 2;
          const y = py - CELL_SIZE / 2;
          ctx.save();
          ctx.globalAlpha = 0.35;
          ctx.fillStyle = color;
          ctx.beginPath();
          ctx.roundRect(x, y, CELL_SIZE, CELL_SIZE, ROOM_RADIUS);
          ctx.fill();
          ctx.restore();
        }
      }

      // Patrol route lines (dashed magenta connecting route rooms in sequence)
      if (overlay.patrolRoute && overlay.patrolRoute.length >= 2) {
        ctx.strokeStyle = '#e040fb';
        ctx.setLineDash([8, 4]);
        ctx.lineWidth = 3;
        for (let i = 0; i < overlay.patrolRoute.length - 1; i++) {
          const from = roomMap.get(overlay.patrolRoute[i]);
          const to = roomMap.get(overlay.patrolRoute[i + 1]);
          if (!from || !to) continue;
          const { px: x1, py: y1 } = gridToPixel(from.x, from.y);
          const { px: x2, py: y2 } = gridToPixel(to.x, to.y);
          ctx.beginPath();
          ctx.moveTo(x1, y1);
          ctx.lineTo(x2, y2);
          ctx.stroke();
        }
        ctx.setLineDash([]);

        // Numbered circles on patrol route rooms
        for (let i = 0; i < overlay.patrolRoute.length; i++) {
          const room = roomMap.get(overlay.patrolRoute[i]);
          if (!room) continue;
          const { px, py } = gridToPixel(room.x, room.y);
          const cx = px + CELL_SIZE / 2 - 6;
          const cy = py - CELL_SIZE / 2 + 6;
          ctx.beginPath();
          ctx.arc(cx, cy, 9, 0, Math.PI * 2);
          ctx.fillStyle = '#e040fb';
          ctx.fill();
          ctx.fillStyle = '#fff';
          ctx.font = 'bold 10px sans-serif';
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.fillText(String(i + 1), cx, cy);
        }
      }

      // Spawn point markers (cyan diamonds)
      if (overlay.spawnPoints) {
        for (const roomId of overlay.spawnPoints) {
          const room = roomMap.get(roomId);
          if (!room) continue;
          const { px, py } = gridToPixel(room.x, room.y);
          const dx = px - CELL_SIZE / 2 + 6;
          const dy = py - CELL_SIZE / 2 + 6;
          const s = 7;
          ctx.fillStyle = '#00bcd4';
          ctx.beginPath();
          ctx.moveTo(dx, dy - s);
          ctx.lineTo(dx + s, dy);
          ctx.lineTo(dx, dy + s);
          ctx.lineTo(dx - s, dy);
          ctx.closePath();
          ctx.fill();
        }
      }

      // Start room marker (gold star border)
      if (overlay.startRoomId) {
        const room = roomMap.get(overlay.startRoomId);
        if (room) {
          const { px, py } = gridToPixel(room.x, room.y);
          const x = px - CELL_SIZE / 2 - 3;
          const y = py - CELL_SIZE / 2 - 3;
          ctx.strokeStyle = '#ffc107';
          ctx.lineWidth = 3;
          ctx.beginPath();
          ctx.roundRect(x, y, CELL_SIZE + 6, CELL_SIZE + 6, ROOM_RADIUS + 2);
          ctx.stroke();
          // Star icon top-left
          ctx.fillStyle = '#ffc107';
          ctx.font = '14px sans-serif';
          ctx.textAlign = 'center';
          ctx.textBaseline = 'middle';
          ctx.fillText('\u2605', px - CELL_SIZE / 2 + 8, py + CELL_SIZE / 2 - 8);
        }
      }
    }

    // Pass 2: Draw exit arrowheads (on top of rooms so diagonals are visible) — skip dimmed
    for (const exit of exits) {
      if (isDimmed(exit.fromRoomId) || isDimmed(exit.toRoomId)) continue;
      const from = roomMap.get(exit.fromRoomId);
      const to = roomMap.get(exit.toRoomId);
      if (!from || !to) continue;
      const { px: x1, py: y1 } = gridToPixel(from.x, from.y);
      const { px: x2, py: y2 } = gridToPixel(to.x, to.y);

      const angle = Math.atan2(y2 - y1, x2 - x1);
      const absC = Math.abs(Math.cos(angle));
      const absS = Math.abs(Math.sin(angle));
      const half = CELL_SIZE / 2;
      // Distance from center to square edge along the line's angle
      const isCardinal = absC < 0.001 || absS < 0.001;
      const edgeDist = isCardinal
        ? half
        : Math.min(half / absC, half / absS) - 6; // pull diagonal tips 6px closer
      const endX = x2 - Math.cos(angle) * edgeDist;
      const endY = y2 - Math.sin(angle) * edgeDist;
      const headLen = 8;
      ctx.fillStyle = exitColor(exit);
      ctx.beginPath();
      ctx.moveTo(endX, endY);
      ctx.lineTo(
        endX - headLen * Math.cos(angle - Math.PI / 7),
        endY - headLen * Math.sin(angle - Math.PI / 7)
      );
      ctx.lineTo(
        endX - headLen * Math.cos(angle + Math.PI / 7),
        endY - headLen * Math.sin(angle + Math.PI / 7)
      );
      ctx.closePath();
      ctx.fill();
    }

    // Draw vertical exit indicators (▲/▼ triangles)
    ctx.fillStyle = '#00acc1';
    const TRI_SIZE = 8;
    for (const ve of verticalExits) {
      const room = roomMap.get(ve.roomId);
      if (!room) continue;
      const { px, py } = gridToPixel(room.x, room.y);

      if (ve.direction === 'UP') {
        // Triangle above room box
        const tipY = py - CELL_SIZE / 2 - 4;
        ctx.beginPath();
        ctx.moveTo(px, tipY - TRI_SIZE);
        ctx.lineTo(px - TRI_SIZE, tipY);
        ctx.lineTo(px + TRI_SIZE, tipY);
        ctx.closePath();
        ctx.fill();
      } else if (ve.direction === 'DOWN') {
        // Triangle below room box
        const tipY = py + CELL_SIZE / 2 + 4;
        ctx.beginPath();
        ctx.moveTo(px, tipY + TRI_SIZE);
        ctx.lineTo(px - TRI_SIZE, tipY);
        ctx.lineTo(px + TRI_SIZE, tipY);
        ctx.closePath();
        ctx.fill();
      }
    }

    // Draw cross-zone exit indicators (portal arrows with zone labels)
    const CHEVRON_SIZE = 7;
    const PORTAL_COLOR = '#ef6c00';
    for (const cze of crossZoneExits) {
      const room = roomMap.get(cze.roomId);
      if (!room) continue;
      const { px, py } = gridToPixel(room.x, room.y);
      const half = CELL_SIZE / 2;

      let tipX = px, tipY = py;
      let angle = 0; // radians, direction the chevron points
      let labelX = px, labelY = py;
      let labelAngle = 0;

      switch (cze.direction) {
        case 'NORTH':
          tipX = px; tipY = py - half - 6;
          angle = -Math.PI / 2;
          labelX = px; labelY = tipY - CHEVRON_SIZE - 4;
          labelAngle = 0;
          break;
        case 'SOUTH':
          tipX = px; tipY = py + half + 6;
          angle = Math.PI / 2;
          labelX = px; labelY = tipY + CHEVRON_SIZE + 10;
          labelAngle = 0;
          break;
        case 'EAST':
          tipX = px + half + 6; tipY = py;
          angle = 0;
          labelX = tipX + CHEVRON_SIZE + 4; labelY = py;
          labelAngle = -Math.PI / 2;
          break;
        case 'WEST':
          tipX = px - half - 6; tipY = py;
          angle = Math.PI;
          labelX = tipX - CHEVRON_SIZE - 4; labelY = py;
          labelAngle = Math.PI / 2;
          break;
      }

      // Draw chevron (double arrowhead)
      ctx.fillStyle = PORTAL_COLOR;
      for (let i = 0; i < 2; i++) {
        const offsetDist = i * (CHEVRON_SIZE + 1);
        const cx = tipX + Math.cos(angle) * offsetDist;
        const cy = tipY + Math.sin(angle) * offsetDist;
        ctx.beginPath();
        ctx.moveTo(cx + Math.cos(angle) * CHEVRON_SIZE, cy + Math.sin(angle) * CHEVRON_SIZE);
        ctx.lineTo(
          cx + Math.cos(angle + 2.4) * CHEVRON_SIZE,
          cy + Math.sin(angle + 2.4) * CHEVRON_SIZE
        );
        ctx.lineTo(
          cx + Math.cos(angle - 2.4) * CHEVRON_SIZE,
          cy + Math.sin(angle - 2.4) * CHEVRON_SIZE
        );
        ctx.closePath();
        ctx.fill();
      }

      // Draw zone label
      ctx.save();
      ctx.translate(labelX, labelY);
      ctx.rotate(labelAngle);
      ctx.fillStyle = PORTAL_COLOR;
      ctx.font = 'bold 9px sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(cze.targetZone, 0, 0);
      ctx.restore();
    }
  }, [rooms, exits, selectedRoomId, offset, canvasSize, gridToPixel, dragPreview, movePreview, roomAtPixel, verticalExits, crossZoneExits, overlay, dimmedRoomIds, zoneLabels, roomZoneColors]);

  // Mouse handlers
  const handleMouseDown = useCallback(
    (e: React.MouseEvent<HTMLCanvasElement>) => {
      const rect = canvasRef.current!.getBoundingClientRect();
      const px = e.clientX - rect.left;
      const py = e.clientY - rect.top;

      const room = roomAtPixel(px, py);

      if (readOnly || e.shiftKey || e.button === 1) {
        // In readOnly mode, all drags are pans; clicks on rooms handled in mouseUp
        if (readOnly && room) {
          dragState.current = {
            type: 'exit', // reuse 'exit' type to track click-on-room for select
            startX: px,
            startY: py,
            offsetStart: offset,
            fromRoomId: room.id,
          };
        } else {
          dragState.current = {
            type: 'pan',
            startX: e.clientX,
            startY: e.clientY,
            offsetStart: { ...offset },
          };
        }
        return;
      }

      if (room) {
        // Alt+click on room = move mode
        if (e.altKey && onMoveRoom) {
          dragState.current = {
            type: 'move',
            startX: px,
            startY: py,
            offsetStart: offset,
            fromRoomId: room.id,
            origGridX: room.x,
            origGridY: room.y,
          };
        } else {
          dragState.current = {
            type: 'exit',
            startX: px,
            startY: py,
            offsetStart: offset,
            fromRoomId: room.id,
          };
        }
      } else {
        dragState.current = {
          type: 'pan',
          startX: e.clientX,
          startY: e.clientY,
          offsetStart: { ...offset },
        };
      }
    },
    [offset, roomAtPixel, readOnly, onMoveRoom]
  );

  const handleMouseMove = useCallback(
    (e: React.MouseEvent<HTMLCanvasElement>) => {
      if (dragState.current.type === 'pan') {
        const dx = e.clientX - dragState.current.startX;
        const dy = e.clientY - dragState.current.startY;
        setOffset({
          x: dragState.current.offsetStart.x + dx,
          y: dragState.current.offsetStart.y + dy,
        });
      } else if (!readOnly && dragState.current.type === 'exit' && dragState.current.fromRoomId) {
        const rect = canvasRef.current!.getBoundingClientRect();
        const px = e.clientX - rect.left;
        const py = e.clientY - rect.top;
        const dx = Math.abs(px - dragState.current.startX);
        const dy = Math.abs(py - dragState.current.startY);
        // Only show preview after a small movement threshold
        if (dx > 5 || dy > 5) {
          setDragPreview({
            fromId: dragState.current.fromRoomId,
            mouseX: px,
            mouseY: py,
          });
        }
      } else if (dragState.current.type === 'move' && dragState.current.fromRoomId) {
        const rect = canvasRef.current!.getBoundingClientRect();
        const px = e.clientX - rect.left;
        const py = e.clientY - rect.top;
        const dx = Math.abs(px - dragState.current.startX);
        const dy = Math.abs(py - dragState.current.startY);
        if (dx > 5 || dy > 5) {
          const { gx, gy } = pixelToGrid(px, py);
          setMovePreview({
            roomId: dragState.current.fromRoomId,
            origX: dragState.current.origGridX!,
            origY: dragState.current.origGridY!,
            ghostX: gx,
            ghostY: gy,
            mouseX: px,
            mouseY: py,
          });
        }
      }
    },
    [pixelToGrid]
  );

  const handleMouseUp = useCallback(
    (e: React.MouseEvent<HTMLCanvasElement>) => {
      const rect = canvasRef.current!.getBoundingClientRect();
      const px = e.clientX - rect.left;
      const py = e.clientY - rect.top;
      const state = dragState.current;
      dragState.current = { type: 'none', startX: 0, startY: 0, offsetStart: { x: 0, y: 0 } };
      setDragPreview(null);
      setMovePreview(null);

      if (state.type === 'pan') {
        const dx = Math.abs(e.clientX - state.startX);
        const dy = Math.abs(e.clientY - state.startY);
        if (dx < 3 && dy < 3) {
          const room = roomAtPixel(px, py);
          if (room) {
            onSelectRoom(room.id);
          } else if (!readOnly) {
            const { gx, gy } = pixelToGrid(px, py);
            const occupied = rooms.some((r) => r.x === gx && r.y === gy);
            if (!occupied) {
              onCreateRoom(gx, gy);
            }
          }
        }
        return;
      }

      if (state.type === 'move' && state.fromRoomId && onMoveRoom) {
        const dx = Math.abs(px - state.startX);
        const dy = Math.abs(py - state.startY);
        if (dx < 3 && dy < 3) {
          // Tiny movement = click-to-select
          onSelectRoom(state.fromRoomId);
        } else {
          const { gx, gy } = pixelToGrid(px, py);
          // Don't move to same position
          if (gx === state.origGridX && gy === state.origGridY) return;
          // Don't move to occupied position
          const occupied = rooms.some((r) => r.id !== state.fromRoomId && r.x === gx && r.y === gy);
          if (occupied) return;
          onMoveRoom(state.fromRoomId, gx, gy);
        }
        return;
      }

      if (state.type === 'exit' && state.fromRoomId) {
        const dx = Math.abs(px - state.startX);
        const dy = Math.abs(py - state.startY);
        const targetRoom = roomAtPixel(px, py);

        if (dx < 3 && dy < 3) {
          onSelectRoom(state.fromRoomId);
        } else if (!readOnly && targetRoom && targetRoom.id !== state.fromRoomId) {
          onCreateExit(state.fromRoomId, targetRoom.id);
        }
      }
    },
    [roomAtPixel, pixelToGrid, rooms, onCreateRoom, onSelectRoom, onCreateExit, onMoveRoom, readOnly]
  );

  // Track Alt key for cursor hint
  const [altHeld, setAltHeld] = useState(false);
  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Alt') setAltHeld(true);
      if (e.key === 'Escape' && dragState.current.type === 'move') {
        dragState.current = { type: 'none', startX: 0, startY: 0, offsetStart: { x: 0, y: 0 } };
        setMovePreview(null);
      }
    };
    const handleKeyUp = (e: KeyboardEvent) => {
      if (e.key === 'Alt') setAltHeld(false);
    };
    window.addEventListener('keydown', handleKeyDown);
    window.addEventListener('keyup', handleKeyUp);
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
      window.removeEventListener('keyup', handleKeyUp);
    };
  }, []);

  return (
    <div
      ref={containerRef}
      style={{ width: '100%', height: '100%', overflow: 'hidden', cursor: movePreview ? 'grabbing' : altHeld && onMoveRoom ? 'grab' : readOnly ? 'default' : 'crosshair' }}
    >
      <canvas
        ref={canvasRef}
        style={{ width: canvasSize.w, height: canvasSize.h, display: 'block' }}
        onMouseDown={handleMouseDown}
        onMouseMove={handleMouseMove}
        onMouseUp={handleMouseUp}
        onContextMenu={(e) => e.preventDefault()}
      />
    </div>
  );
}

export default MapCanvas;
export { inferDirection };
export type { RoomNode, ExitEdge };
