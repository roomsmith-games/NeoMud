// @vitest-environment jsdom
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { vi, describe, it, expect } from 'vitest'

vi.mock('../api', () => ({
  default: {
    get: vi.fn().mockResolvedValue({ depth: 0 }),
    post: vi.fn().mockResolvedValue({}),
    put: vi.fn().mockResolvedValue({}),
    del: vi.fn().mockResolvedValue({}),
    upload: vi.fn().mockResolvedValue({}),
    assetUrl: vi.fn((p: string) => `/api${p}`),
    signAssetUrls: vi.fn((paths: string[]) =>
      Promise.resolve(Object.fromEntries(paths.map((p) => [p, `${p}?t=signed-token`]))),
    ),
  },
  resolveUrl: vi.fn((p: string) => `/api${p}`),
  assetUrl: vi.fn((p: string) => `/api${p}`),
}))

import AudioPreview from '../components/AudioPreview'

describe('AudioPreview', () => {
  it('shows placeholder when no bgm track is set', () => {
    render(<AudioPreview entityType="zone" entityId="test_zone" />)
    expect(screen.getByText('Missing BGM')).toBeInTheDocument()
  })

  it('shows track ID when bgm is provided', () => {
    render(<AudioPreview entityType="zone" entityId="test_zone" bgm="town_peaceful" />)
    expect(screen.getByText('town_peaceful.mp3')).toBeInTheDocument()
  })

  it('renders audio element when bgm track is provided', async () => {
    render(<AudioPreview entityType="zone" entityId="test_zone" bgm="town_peaceful" />)
    await waitFor(() => {
      const audio = document.querySelector('audio')
      expect(audio).not.toBeNull()
      // The signed-URL hook resolves the `<audio src>` asynchronously
      // via api.signAssetUrls; assert the underlying path is preserved.
      expect(audio!.src).toContain('/api/assets/audio/bgm/town_peaceful.mp3')
      expect(audio!.src).toContain('t=signed-token')
    })
  })

  it('shows prompt fields with correct labels', () => {
    render(<AudioPreview entityType="zone" entityId="test_zone" />)
    expect(screen.getByText('BGM Prompt')).toBeInTheDocument()
    expect(screen.getByText('Duration (seconds)')).toBeInTheDocument()
    expect(screen.getByText('Copy Prompt')).toBeInTheDocument()
  })

  it('displays initial prop values', () => {
    render(
      <AudioPreview
        entityType="zone"
        entityId="test_zone"
        bgmPrompt="A peaceful town"
        bgmDuration={120}
      />
    )
    expect(screen.getByDisplayValue('A peaceful town')).toBeInTheDocument()
    expect(screen.getByDisplayValue('120')).toBeInTheDocument()
  })

  it('fires onUpdate when prompt changes', async () => {
    const user = userEvent.setup()
    const onUpdate = vi.fn()

    render(
      <AudioPreview
        entityType="zone"
        entityId="test_zone"
        onUpdate={onUpdate}
      />
    )

    const promptTextarea = screen.getByPlaceholderText('Describe the BGM atmosphere...')
    await user.type(promptTextarea, 'A')

    expect(onUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ bgmPrompt: 'A' })
    )
  })

  it('fires onUpdate when duration changes', async () => {
    const user = userEvent.setup()
    const onUpdate = vi.fn()

    render(
      <AudioPreview
        entityType="zone"
        entityId="test_zone"
        onUpdate={onUpdate}
      />
    )

    const durationInput = screen.getByRole('spinbutton')
    await user.type(durationInput, '60')

    expect(onUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ bgmDuration: expect.any(Number) })
    )
  })

  it('shows zone defaults as placeholders in room mode', () => {
    render(
      <AudioPreview
        entityType="room"
        entityId="test_room"
        defaultBgmPrompt="Zone atmosphere prompt"
        defaultBgmDuration={120}
      />
    )

    expect(screen.getByPlaceholderText('Zone default: Zone atmosphere prompt')).toBeInTheDocument()
    expect(screen.getByPlaceholderText('Zone default: 120s')).toBeInTheDocument()
  })
})
