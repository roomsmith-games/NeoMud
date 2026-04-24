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
      assetUrl: vi.fn((path: string) => path),
      signAssetUrls: vi.fn().mockResolvedValue({}),
    },
    resolveUrl: vi.fn((path: string) => `/api${path}`),
    assetUrl: vi.fn((path: string) => `/api${path}`),
  }
})

import api from '../api'
import PcSpriteEditor from '../pages/PcSpriteEditor'

const mockApi = vi.mocked(api)

function makeSpriteRow(race: string, gender: string, cls: string) {
  const id = `${race.toLowerCase()}_${gender}_${cls.toLowerCase()}`
  return {
    id,
    race,
    gender,
    characterClass: cls,
    imagePrompt: `A ${gender} ${race.toLowerCase().replace('_', ' ')} ${cls.toLowerCase()}, fantasy RPG character portrait`,
    imageStyle: 'pixel art, 16-bit JRPG style',
    imageNegativePrompt: 'blurry',
    imageWidth: 384,
    imageHeight: 512,
  }
}

const RACES = ['HUMAN', 'DWARF', 'ELF', 'HALFLING', 'GNOME', 'HALF_ORC']
const GENDERS = ['male', 'female', 'neutral']
const CLASSES = [
  'WARRIOR', 'PALADIN', 'WITCHHUNTER', 'CLERIC', 'PRIEST', 'MISSIONARY',
  'MAGE', 'WARLOCK', 'DRUID', 'RANGER', 'THIEF', 'NINJA', 'MYSTIC', 'BARD', 'GYPSY',
]

function makeAll270(): ReturnType<typeof makeSpriteRow>[] {
  const all = []
  for (const race of RACES) {
    for (const gender of GENDERS) {
      for (const cls of CLASSES) {
        all.push(makeSpriteRow(race, gender, cls))
      }
    }
  }
  return all
}

function makeHumanSprites(): ReturnType<typeof makeSpriteRow>[] {
  const all = []
  for (const gender of GENDERS) {
    for (const cls of CLASSES) {
      all.push(makeSpriteRow('HUMAN', gender, cls))
    }
  }
  return all
}

const mockClasses = [
  {
    id: 'WARRIOR', name: 'Warrior', description: 'A mighty fighter',
    minimumStats: '{"strength":12,"agility":8,"intellect":5,"willpower":6,"health":10,"charm":5}',
    skills: '["BASH","KICK"]', magicSchools: '{}',
    hpPerLevelMin: 6, hpPerLevelMax: 12, mpPerLevelMin: 0, mpPerLevelMax: 0, xpModifier: 1.0,
  },
  {
    id: 'MAGE', name: 'Mage', description: 'A wielder of arcane magic',
    minimumStats: '{"strength":4,"agility":6,"intellect":14,"willpower":10,"health":5,"charm":6}',
    skills: '["MEDITATE"]', magicSchools: '{"mage":3}',
    hpPerLevelMin: 3, hpPerLevelMax: 6, mpPerLevelMin: 4, mpPerLevelMax: 10, xpModifier: 1.1,
  },
]

const mockRaces = [
  {
    id: 'HUMAN', name: 'Human', description: 'Versatile and adaptable.',
    statModifiers: '{"strength":0,"agility":0,"intellect":0,"willpower":0,"health":0,"charm":0}',
    xpModifier: 1.0,
  },
  {
    id: 'HALF_ORC', name: 'Half-Orc', description: 'Strong but brutish.',
    statModifiers: '{"strength":5,"agility":-2,"intellect":-3,"willpower":0,"health":3,"charm":-3}',
    xpModifier: 0.9,
  },
]

/**
 * The component makes 3 parallel API calls on mount:
 *   1. GET /pc-sprites (sprites list)
 *   2. GET /character-classes
 *   3. GET /races
 *
 * We route the mock by matching the URL path.
 */
function setupMockGet(sprites: ReturnType<typeof makeSpriteRow>[], ...extra: ReturnType<typeof makeSpriteRow>[][]) {
  let spriteCallIndex = 0
  const spriteSets = [sprites, ...extra]

  mockApi.get.mockImplementation((path: string) => {
    if (path === '/character-classes') return Promise.resolve(mockClasses) as any
    if (path === '/races') return Promise.resolve(mockRaces) as any
    // Sprite requests
    const result = spriteSets[spriteCallIndex] ?? []
    spriteCallIndex++
    return Promise.resolve(result) as any
  })
}

describe('PcSpriteEditor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('renders race, gender, and class dropdown filters', async () => {
    setupMockGet([])
    render(<PcSpriteEditor />)
    await waitFor(() => {
      expect(screen.getByText('All Races')).toBeInTheDocument()
      expect(screen.getByText('All Genders')).toBeInTheDocument()
      expect(screen.getByText('All Classes')).toBeInTheDocument()
    })
  })

  it('shows all 270 sprites with no filter selected', async () => {
    setupMockGet(makeAll270())
    render(<PcSpriteEditor />)
    await waitFor(() => {
      expect(screen.getByText('270 sprites')).toBeInTheDocument()
    })
  })

  it('selecting race dropdown filters list', async () => {
    setupMockGet(makeAll270(), makeHumanSprites())

    const user = userEvent.setup()
    render(<PcSpriteEditor />)

    await waitFor(() => expect(screen.getByText('270 sprites')).toBeInTheDocument())

    const raceSelect = screen.getByDisplayValue('All Races') as HTMLSelectElement
    await user.selectOptions(raceSelect, 'HUMAN')

    await waitFor(() => expect(screen.getByText('45 sprites')).toBeInTheDocument())
  })

  it('combining race and gender filters narrows results', async () => {
    const humanMale = CLASSES.map((cls) => makeSpriteRow('HUMAN', 'male', cls))
    setupMockGet(makeAll270(), makeHumanSprites(), humanMale)

    const user = userEvent.setup()
    render(<PcSpriteEditor />)

    await waitFor(() => expect(screen.getByText('270 sprites')).toBeInTheDocument())

    await user.selectOptions(screen.getByDisplayValue('All Races') as HTMLSelectElement, 'HUMAN')
    await waitFor(() => expect(screen.getByText('45 sprites')).toBeInTheDocument())

    await user.selectOptions(screen.getByDisplayValue('All Genders') as HTMLSelectElement, 'male')
    await waitFor(() => expect(screen.getByText('15 sprites')).toBeInTheDocument())
  })

  it('combining all three filters shows single sprite', async () => {
    const single = [makeSpriteRow('HUMAN', 'male', 'WARRIOR')]
    setupMockGet(
      makeAll270(),
      makeHumanSprites(),
      CLASSES.map((cls) => makeSpriteRow('HUMAN', 'male', cls)),
      single,
    )

    const user = userEvent.setup()
    render(<PcSpriteEditor />)

    await waitFor(() => expect(screen.getByText('270 sprites')).toBeInTheDocument())

    await user.selectOptions(screen.getByDisplayValue('All Races') as HTMLSelectElement, 'HUMAN')
    await waitFor(() => expect(screen.getByText('45 sprites')).toBeInTheDocument())

    await user.selectOptions(screen.getByDisplayValue('All Genders') as HTMLSelectElement, 'male')
    await waitFor(() => expect(screen.getByText('15 sprites')).toBeInTheDocument())

    await user.selectOptions(screen.getByDisplayValue('All Classes') as HTMLSelectElement, 'WARRIOR')
    await waitFor(() => expect(screen.getByText('1 sprites')).toBeInTheDocument())
  })

  it('clicking a sprite opens detail panel with large image and character stats', async () => {
    const sprites = [makeSpriteRow('HUMAN', 'male', 'WARRIOR')]
    setupMockGet(sprites)

    const user = userEvent.setup()
    render(<PcSpriteEditor />)

    await waitFor(() => expect(screen.getByText('1 sprites')).toBeInTheDocument())

    // Click the sprite in the list
    await user.click(screen.getByText('Human Male Warrior'))

    // Detail panel shows identity + stats + prompt section
    await waitFor(() => {
      expect(screen.getByText('ID: human_male_warrior')).toBeInTheDocument()
      // Identity fields
      expect(screen.getByText('Identity')).toBeInTheDocument()
      // Class description from fetched class data
      expect(screen.getByText('A mighty fighter')).toBeInTheDocument()
      // Vitals section
      expect(screen.getByText('Vitals per Level')).toBeInTheDocument()
      expect(screen.getByText('6–12')).toBeInTheDocument() // HP per level for Warrior
      // Stats section
      expect(screen.getByText('Minimum Stats')).toBeInTheDocument()
      // Skills
      expect(screen.getByText('Skills')).toBeInTheDocument()
      expect(screen.getByText('Bash')).toBeInTheDocument()
      expect(screen.getByText('Kick')).toBeInTheDocument()
      // Image generation section
      expect(screen.getByText('Image Generation')).toBeInTheDocument()
    })
  })

  it('detail panel shows magic schools for magic classes', async () => {
    const sprites = [makeSpriteRow('HUMAN', 'male', 'MAGE')]
    setupMockGet(sprites)

    const user = userEvent.setup()
    render(<PcSpriteEditor />)

    await waitFor(() => expect(screen.getByText('1 sprites')).toBeInTheDocument())
    await user.click(screen.getByText('Human Male Mage'))

    await waitFor(() => {
      expect(screen.getByText('Magic Schools')).toBeInTheDocument()
      expect(screen.getByText('Mage (tier 3)')).toBeInTheDocument()
      expect(screen.getByText('3–6')).toBeInTheDocument() // HP for mage
      expect(screen.getByText('4–10')).toBeInTheDocument() // MP for mage
    })
  })

  it('detail panel shows race stat modifiers', async () => {
    const sprites = [makeSpriteRow('HALF_ORC', 'male', 'WARRIOR')]
    setupMockGet(sprites)

    const user = userEvent.setup()
    render(<PcSpriteEditor />)

    await waitFor(() => expect(screen.getByText('1 sprites')).toBeInTheDocument())
    await user.click(screen.getByText('Half orc Male Warrior'))

    await waitFor(() => {
      // Half-Orc has STR +5 modifier shown as "12 +5" below the effective value
      expect(screen.getByText('12 +5')).toBeInTheDocument()
      // Race description
      expect(screen.getByText('Strong but brutish.')).toBeInTheDocument()
      expect(screen.getByText('Race Lore')).toBeInTheDocument()
    })
  })

  it('validates prompt contains race, gender, and class', async () => {
    const sprites = [makeSpriteRow('HUMAN', 'male', 'WARRIOR')]
    setupMockGet(sprites)

    const user = userEvent.setup()
    render(<PcSpriteEditor />)

    await waitFor(() => expect(screen.getByText('1 sprites')).toBeInTheDocument())
    await user.click(screen.getByText('Human Male Warrior'))

    await waitFor(() => {
      // Default prompt includes race, gender, and class
      expect(screen.getByText('Prompt includes race, gender, and class')).toBeInTheDocument()
    })
  })

  it('editing prompt fires PUT via onUpdate', async () => {
    const sprite = makeSpriteRow('HUMAN', 'male', 'WARRIOR')
    setupMockGet([sprite])
    mockApi.put.mockResolvedValue({ ...sprite, imagePrompt: sprite.imagePrompt + 'X' })

    const user = userEvent.setup()
    render(<PcSpriteEditor />)

    await waitFor(() => expect(screen.getByText('1 sprites')).toBeInTheDocument())
    await user.click(screen.getByText('Human Male Warrior'))

    await waitFor(() => expect(screen.getByText('ID: human_male_warrior')).toBeInTheDocument())

    const promptTextarea = screen.getByDisplayValue(sprite.imagePrompt)
    await user.type(promptTextarea, 'X')

    await waitFor(() => {
      expect(mockApi.put).toHaveBeenCalledWith(
        '/pc-sprites/human_male_warrior',
        expect.objectContaining({ imagePrompt: sprite.imagePrompt + 'X' })
      )
    })
  })

  it('Copy All Prompts button is rendered and clickable', async () => {
    const sprites = [
      makeSpriteRow('HUMAN', 'male', 'WARRIOR'),
      makeSpriteRow('HUMAN', 'male', 'MAGE'),
    ]
    setupMockGet(sprites)

    const user = userEvent.setup()
    render(<PcSpriteEditor />)

    await waitFor(() => expect(screen.getByText('2 sprites')).toBeInTheDocument())

    const btn = screen.getByText('Copy All Prompts')
    expect(btn).toBeInTheDocument()
    await user.click(btn)
  })

  it('shows empty state when nothing selected', async () => {
    setupMockGet([makeSpriteRow('HUMAN', 'male', 'WARRIOR')])
    render(<PcSpriteEditor />)

    await waitFor(() => {
      expect(screen.getByText('Select a character from the list to view stats and edit image prompts.')).toBeInTheDocument()
    })
  })
})
