// @vitest-environment jsdom
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect, beforeEach } from 'vitest'

vi.mock('../api', () => {
  return {
    default: {
      get: vi.fn().mockResolvedValue([]),
      post: vi.fn().mockResolvedValue({}),
      put: vi.fn().mockResolvedValue({}),
      del: vi.fn().mockResolvedValue({}),
    },
  }
})

// Mock MapCanvas — expose callbacks for testing
vi.mock('../components/MapCanvas', () => ({
  default: (props: any) => (
    <div data-testid="map-canvas">
      {props.rooms?.map((r: any) => (
        <button key={r.id} data-testid={`room-${r.id}`} onClick={() => props.onSelectRoom?.(r.id)}>
          {r.name}
        </button>
      ))}
      {/* Expose callbacks via data attrs and buttons for test interaction */}
      {props.onCreateRoom && (
        <button data-testid="create-room" onClick={() => props.onCreateRoom(3, 4)}>
          Create Room
        </button>
      )}
      {props.onCreateExit && (
        <button data-testid="create-exit" onClick={() => props.onCreateExit('town:square', 'town:market')}>
          Create Exit
        </button>
      )}
      {props.onMoveRoom && (
        <button data-testid="move-room" onClick={() => props.onMoveRoom('town:square', 2, 3)}>
          Move Room
        </button>
      )}
    </div>
  ),
  inferDirection: (dx: number, dy: number) => {
    if (dx === 0 && dy === 0) return null
    if (dx === 0 && dy > 0) return 'NORTH'
    if (dx === 0 && dy < 0) return 'SOUTH'
    if (dx > 0 && dy === 0) return 'EAST'
    if (dx < 0 && dy === 0) return 'WEST'
    if (dx > 0 && dy > 0) return 'NORTHEAST'
    if (dx < 0 && dy > 0) return 'NORTHWEST'
    if (dx > 0 && dy < 0) return 'SOUTHEAST'
    if (dx < 0 && dy < 0) return 'SOUTHWEST'
    return null
  },
}))

vi.mock('../components/ImagePreview', () => ({
  default: () => <div data-testid="image-preview" />,
}))
vi.mock('../components/AudioPreview', () => ({
  default: () => <div data-testid="audio-preview" />,
}))
vi.mock('../components/SfxPreview', () => ({
  default: (props: any) => <input data-testid={`sfx-${props.audioCategory || 'default'}`} value={props.soundId} readOnly />,
}))

import api from '../api'
import ZoneEditor from '../pages/ZoneEditor'

const mockApi = vi.mocked(api)

function makeRoom(id: string, name: string, x: number, y: number, exits: { fromRoomId: string; toRoomId: string; direction: string }[] = []) {
  return {
    id, name, description: `${name} description.`,
    x, y, backgroundImage: '', bgm: '', bgmPrompt: '', bgmDuration: 0,
    departSound: '', effects: '', lockedExits: '', lockResetTicks: '', hiddenExits: '',
    imagePrompt: '', imageStyle: '', imageNegativePrompt: '', interactables: '[]',
    unpickableExits: '', imageWidth: 1024, imageHeight: 576, maxHostileNpcs: null,
    exits,
  }
}

function makeZone(id: string, name: string, overrides: Record<string, any> = {}) {
  return {
    id, name, description: `${name} zone.`,
    safe: false, bgm: '', bgmPrompt: '', bgmDuration: 0,
    spawnRoom: null, spawnMaxEntities: 0, spawnMaxPerRoom: 0, spawnRateTicks: 0,
    imageStyle: '', imageNegativePrompt: '',
    ...overrides,
  }
}

const MOCK_ZONES = [
  makeZone('town', 'Millhaven', { safe: true }),
]

const MOCK_ZONE_DETAIL = {
  ...MOCK_ZONES[0],
  rooms: [
    makeRoom('town:square', 'Town Square', 0, 0, [{ fromRoomId: 'town:square', toRoomId: 'town:market', direction: 'EAST' }]),
    makeRoom('town:market', 'Market Street', 1, 0, [{ fromRoomId: 'town:market', toRoomId: 'town:square', direction: 'WEST' }]),
    makeRoom('town:inn', 'The Rusty Tankard', 0, 1),
  ],
}

function setupMockApi() {
  mockApi.get.mockImplementation((path: string) => {
    if (path === '/zones') return Promise.resolve(MOCK_ZONES)
    if (path === '/zones/town') return Promise.resolve(MOCK_ZONE_DETAIL)
    if (path.startsWith('/default-sfx')) return Promise.resolve([])
    return Promise.resolve([])
  })
}

async function selectZone(user: ReturnType<typeof userEvent.setup>) {
  await waitFor(() => expect(screen.getByText('Millhaven')).toBeInTheDocument())
  await user.click(screen.getByText('Millhaven'))
  await waitFor(() => expect(screen.getByTestId('map-canvas')).toBeInTheDocument())
}

describe('ZoneEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    setupMockApi()
    vi.spyOn(window, 'confirm').mockReturnValue(true)
    vi.spyOn(window, 'alert').mockImplementation(() => {})
  })

  it('renders zone list and empty state', async () => {
    render(<ZoneEditor />)

    await waitFor(() => {
      expect(screen.getByText('Millhaven')).toBeInTheDocument()
    })
  })

  it('selecting a zone loads map with rooms', async () => {
    const user = userEvent.setup()
    render(<ZoneEditor />)

    await selectZone(user)

    await waitFor(() => {
      expect(screen.getByTestId('room-town:square')).toBeInTheDocument()
      expect(screen.getByTestId('room-town:market')).toBeInTheDocument()
      expect(screen.getByTestId('room-town:inn')).toBeInTheDocument()
    })
  })

  it('selecting a room shows edit form', async () => {
    const user = userEvent.setup()
    render(<ZoneEditor />)

    await selectZone(user)

    await user.click(screen.getByTestId('room-town:square'))

    await waitFor(() => {
      expect(screen.getByDisplayValue('Town Square')).toBeInTheDocument()
      expect(screen.getByDisplayValue('Town Square description.')).toBeInTheDocument()
    })
  })

  it('handleCreateRoom calls api.post with coordinates', async () => {
    const user = userEvent.setup()
    const newRoom = {
      id: 'town:town_room_3_4', name: 'New Room (3,4)', description: '',
      x: 3, y: 4, exits: [],
    }
    mockApi.post.mockResolvedValue(newRoom)

    render(<ZoneEditor />)
    await selectZone(user)

    await user.click(screen.getByTestId('create-room'))

    // #299: clicking an empty cell creates a room directly — no native confirm
    expect(window.confirm).not.toHaveBeenCalled()

    await waitFor(() => {
      expect(mockApi.post).toHaveBeenCalledWith(
        '/zones/town/rooms',
        expect.objectContaining({ x: 3, y: 4, name: 'New Room (3,4)' })
      )
    })
  })

  it('handleCreateExit calls api.post with direction', async () => {
    const user = userEvent.setup()
    // After creating exit, zone reloads
    mockApi.post.mockResolvedValue({})
    const reloadData = { ...MOCK_ZONE_DETAIL }
    // Second call to /zones/town returns updated data
    let callCount = 0
    mockApi.get.mockImplementation((path: string) => {
      if (path === '/zones') return Promise.resolve(MOCK_ZONES)
      if (path === '/zones/town') {
        callCount++
        return Promise.resolve(reloadData)
      }
      if (path.startsWith('/default-sfx')) return Promise.resolve([])
      return Promise.resolve([])
    })

    render(<ZoneEditor />)
    await selectZone(user)

    await user.click(screen.getByTestId('create-exit'))

    await waitFor(() => {
      expect(mockApi.post).toHaveBeenCalledWith(
        '/rooms/town:square/exits',
        expect.objectContaining({ toRoomId: 'town:market' })
      )
    })
  })

  it('handleMoveRoom calls api.put to update coordinates', async () => {
    const user = userEvent.setup()
    mockApi.put.mockResolvedValue({})
    // After move, zone reloads
    mockApi.get.mockImplementation((path: string) => {
      if (path === '/zones') return Promise.resolve(MOCK_ZONES)
      if (path === '/zones/town') return Promise.resolve(MOCK_ZONE_DETAIL)
      if (path.startsWith('/default-sfx')) return Promise.resolve([])
      return Promise.resolve([])
    })

    render(<ZoneEditor />)
    await selectZone(user)

    await user.click(screen.getByTestId('move-room'))

    // Confirmation dialog should mention the room and coordinates
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('Town Square'))
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('(2, 3)'))

    await waitFor(() => {
      expect(mockApi.put).toHaveBeenCalledWith(
        '/zones/town/rooms/square',
        expect.objectContaining({ x: 2, y: 3 })
      )
    })
  })

  it('handleMoveRoom is cancelled when user declines confirm', async () => {
    const user = userEvent.setup()
    vi.spyOn(window, 'confirm').mockReturnValue(false)

    render(<ZoneEditor />)
    await selectZone(user)

    await user.click(screen.getByTestId('move-room'))

    expect(window.confirm).toHaveBeenCalled()
    expect(mockApi.put).not.toHaveBeenCalled()
  })

  it('handleSaveRoom calls api.put with room fields', async () => {
    const user = userEvent.setup()
    mockApi.put.mockResolvedValue({
      ...MOCK_ZONE_DETAIL.rooms[0],
      name: 'Updated Square',
    })

    render(<ZoneEditor />)
    await selectZone(user)

    // Select a room
    await user.click(screen.getByTestId('room-town:square'))

    await waitFor(() => {
      expect(screen.getByDisplayValue('Town Square')).toBeInTheDocument()
    })

    // Change the name
    const nameInput = screen.getByDisplayValue('Town Square')
    await user.clear(nameInput)
    await user.type(nameInput, 'Updated Square')

    // Click save
    await user.click(screen.getByText('Save Room'))

    await waitFor(() => {
      expect(mockApi.put).toHaveBeenCalledWith(
        '/zones/town/rooms/square',
        expect.objectContaining({ name: 'Updated Square' })
      )
    })
  })

  it('handleDeleteRoom calls api.del after confirm', async () => {
    const user = userEvent.setup()
    mockApi.del.mockResolvedValue({})

    render(<ZoneEditor />)
    await selectZone(user)

    await user.click(screen.getByTestId('room-town:square'))

    await waitFor(() => {
      expect(screen.getByText('Delete Room')).toBeInTheDocument()
    })

    await user.click(screen.getByText('Delete Room'))

    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('Delete room'))

    await waitFor(() => {
      expect(mockApi.del).toHaveBeenCalledWith('/zones/town/rooms/square')
    })
  })

  it('handleDeleteExit calls api.del and reloads zone', async () => {
    const user = userEvent.setup()
    mockApi.del.mockResolvedValue({})

    render(<ZoneEditor />)
    await selectZone(user)

    // Select town:square which has an EAST exit
    await user.click(screen.getByTestId('room-town:square'))

    await waitFor(() => {
      expect(screen.getByTitle('Delete exit')).toBeInTheDocument()
    })

    await user.click(screen.getByTitle('Delete exit'))

    await waitFor(() => {
      expect(mockApi.del).toHaveBeenCalledWith('/rooms/town:square/exits/EAST')
    })
  })

  it('interactable validation blocks save when success message is missing', async () => {
    const user = userEvent.setup()
    // Room with interactable missing description (success message)
    const zoneWithBadInteractable = {
      ...MOCK_ZONE_DETAIL,
      rooms: [
        {
          ...MOCK_ZONE_DETAIL.rooms[0],
          interactables: JSON.stringify([
            { id: 'feat_1', label: 'Broken Lever', description: '', failureMessage: '', icon: '', actionType: 'EXIT_OPEN', actionData: {}, difficulty: 0, difficultyCheck: '', perceptionDC: 0, cooldownTicks: 0, resetTicks: 0, sound: '' },
          ]),
        },
        ...MOCK_ZONE_DETAIL.rooms.slice(1),
      ],
    }
    mockApi.get.mockImplementation((path: string) => {
      if (path === '/zones') return Promise.resolve(MOCK_ZONES)
      if (path === '/zones/town') return Promise.resolve(zoneWithBadInteractable)
      if (path.startsWith('/default-sfx')) return Promise.resolve([])
      return Promise.resolve([])
    })

    render(<ZoneEditor />)
    await selectZone(user)

    await user.click(screen.getByTestId('room-town:square'))
    await waitFor(() => expect(screen.getByText('Save Room')).toBeInTheDocument())

    await user.click(screen.getByText('Save Room'))

    expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('Broken Lever'))
    expect(window.alert).toHaveBeenCalledWith(expect.stringContaining('success message'))
    expect(mockApi.put).not.toHaveBeenCalled()
  })

  it('shows hint text mentioning Alt+drag', async () => {
    const user = userEvent.setup()
    render(<ZoneEditor />)

    await selectZone(user)

    // With no room selected, the hint text should be visible
    expect(screen.getByText(/Alt\+drag/)).toBeInTheDocument()
  })

  it('handleMoveRoom fixes cross-zone exit directions (#136)', async () => {
    const user = userEvent.setup()

    // Set up two zones with cross-zone exits:
    // town:square (0,0) <-> forest:edge (0,2) via NORTH/SOUTH
    // Moving town:square from (0,0) to (2,3) should update forest:edge's SOUTH exit
    // since forest:edge is at (0,2) and town:square moves to (2,3):
    //   dx = 2-0 = 2, dy = 3-2 = 1 => negative dy from forest perspective => SOUTHEAST
    //   (forest:edge looking at new town:square position)
    const forestZone = makeZone('forest', 'Whispering Forest')
    const forestDetail = {
      ...forestZone,
      rooms: [
        makeRoom('forest:edge', 'Forest Edge', 0, 2, [
          { fromRoomId: 'forest:edge', toRoomId: 'town:square', direction: 'SOUTH' },
        ]),
      ],
    }
    const townWithCrossExit = {
      ...MOCK_ZONES[0],
      rooms: [
        makeRoom('town:square', 'Town Square', 0, 0, [
          { fromRoomId: 'town:square', toRoomId: 'town:market', direction: 'EAST' },
          { fromRoomId: 'town:square', toRoomId: 'forest:edge', direction: 'NORTH' },
        ]),
        makeRoom('town:market', 'Market Street', 1, 0, [
          { fromRoomId: 'town:market', toRoomId: 'town:square', direction: 'WEST' },
        ]),
      ],
    }
    const zones = [makeZone('town', 'Millhaven', { safe: true }), forestZone]

    mockApi.get.mockImplementation((path: string) => {
      if (path === '/zones') return Promise.resolve(zones)
      if (path === '/zones/town') return Promise.resolve(townWithCrossExit)
      if (path === '/zones/forest') return Promise.resolve(forestDetail)
      if (path.startsWith('/default-sfx')) return Promise.resolve([])
      return Promise.resolve([])
    })
    mockApi.put.mockResolvedValue({})
    mockApi.del.mockResolvedValue({})
    mockApi.post.mockResolvedValue({})

    render(<ZoneEditor />)
    await selectZone(user)

    // The mock move-room button moves town:square to (2, 3)
    await user.click(screen.getByTestId('move-room'))

    // Should have been confirmed
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('Town Square'))

    // The cross-zone exit from forest:edge SOUTH -> town:square needs fixing
    // forest:edge is at (0,2), town:square moves to (2,3)
    // From forest:edge's perspective: dx=2, dy=3-2=1 => SOUTHEAST (positive dx, positive dy = NORTHEAST... wait)
    // Actually inferDirection(dx, dy) where dx = target.x - source.x, dy = target.y - source.y
    // For forest:edge -> town:square: dx = 2-0 = 2, dy = 3-2 = 1 => positive dx, positive dy => NORTHEAST
    // But the current direction is SOUTH, so it needs to change
    // The fix should delete the old SOUTH exit and create a new one with the correct direction

    await waitFor(() => {
      // Should have deleted the cross-zone exit (forest:edge SOUTH)
      expect(mockApi.del).toHaveBeenCalledWith(
        expect.stringMatching(/\/rooms\/forest:edge\/exits\/SOUTH|\/rooms\/town:square\/exits\//)
      )
    })
  })
})
