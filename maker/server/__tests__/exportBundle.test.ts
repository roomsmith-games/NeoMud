import { describe, it, expect, beforeAll, afterAll } from 'vitest'
import AdmZip from 'adm-zip'
import { createTestApp } from './helpers.js'
import { buildNmdBundle } from '../routes/export.js'
import { getProjectClient, assetsRoot } from '../projectContext.js'

const TEST_USER_ID = 'test-user'

/**
 * Exercises buildNmdBundle directly against a real project DB to
 * confirm the produced manifest.json validates against the platform's
 * bundleManifestSchema — specifically that every field marked required
 * by the platform has a sensible default, even for a fresh project
 * where the creator never filled in the settings editor.
 *
 * Caught in Phase 4 smoke testing: fresh projects were blowing up on
 * the platform's drafts/upsert route because formatVersion / name /
 * author / version were missing from the manifest.
 */

function readManifest(bundle: Buffer): Record<string, unknown> {
  const zip = new AdmZip(bundle)
  const entry = zip.getEntry('manifest.json')
  expect(entry).toBeTruthy()
  return JSON.parse(entry!.getData().toString('utf8'))
}

describe('buildNmdBundle — manifest defaults', () => {
  let cleanup: () => Promise<void>
  let projectName: string

  beforeAll(async () => {
    const ctx = await createTestApp()
    projectName = ctx.projectName
    cleanup = ctx.cleanup
  })
  afterAll(async () => { await cleanup() })

  it('produces a manifest whose every platform-required field is non-empty for a fresh project', async () => {
    const pooled = await getProjectClient(TEST_USER_ID, projectName)
    const bundle = await buildNmdBundle(pooled.client, assetsRoot(TEST_USER_ID, projectName))
    const manifest = readManifest(bundle)

    // Every field in the platform's bundleManifestSchema.
    expect(manifest.formatVersion).toBeTypeOf('number')
    expect(manifest.name).toBeTypeOf('string')
    expect((manifest.name as string).length).toBeGreaterThan(0)
    expect(manifest.author).toBeTypeOf('string')
    expect((manifest.author as string).length).toBeGreaterThan(0)
    expect(manifest.version).toBeTypeOf('string')
    expect((manifest.version as string).length).toBeGreaterThan(0)
    expect(manifest.engineVersion).toBeTypeOf('string')
    expect(manifest.engineVersionMin).toBeTypeOf('string')
    expect(manifest.worldId).toBeTypeOf('string')
    expect(manifest.createdWithMaker).toBe(true)
  })

  it('preserves user-supplied projectMeta values over defaults', async () => {
    const pooled = await getProjectClient(TEST_USER_ID, projectName)
    await pooled.client.projectMeta.upsert({
      where: { key: 'name' },
      create: { key: 'name', value: 'Custom World Name' },
      update: { value: 'Custom World Name' },
    })
    await pooled.client.projectMeta.upsert({
      where: { key: 'author' },
      create: { key: 'author', value: 'J Doe' },
      update: { value: 'J Doe' },
    })
    await pooled.client.projectMeta.upsert({
      where: { key: 'version' },
      create: { key: 'version', value: '2.3.4' },
      update: { value: '2.3.4' },
    })

    const bundle = await buildNmdBundle(pooled.client, assetsRoot(TEST_USER_ID, projectName))
    const manifest = readManifest(bundle)

    expect(manifest.name).toBe('Custom World Name')
    expect(manifest.author).toBe('J Doe')
    expect(manifest.version).toBe('2.3.4')
  })
})
