/**
 * Rebuild the default world: zip source → .nmd → delete old DB → re-import.
 *
 * Usage:  cd maker && npm run rebuild-world
 *
 * Self-contained: does NOT rely on the dev server to re-import.
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'
import { execSync } from 'child_process'
import AdmZip from 'adm-zip'
import { deleteProject } from '../server/db.js'
import { importNmd } from '../server/import.js'

const __dirname = path.dirname(fileURLToPath(import.meta.url))
const SRC_DIR = path.resolve(__dirname, '..', 'default_world_src')
const NMD_PATH = path.resolve(__dirname, '..', 'default_world.nmd')
const PROJECTS_DIR = path.resolve(__dirname, '..', 'projects')

function addDirToZip(zip: AdmZip, dirPath: string, zipPrefix: string) {
  for (const entry of fs.readdirSync(dirPath, { withFileTypes: true })) {
    const fullPath = path.join(dirPath, entry.name)
    if (entry.isDirectory()) {
      addDirToZip(zip, fullPath, zipPrefix ? `${zipPrefix}/${entry.name}` : entry.name)
    } else {
      zip.addLocalFile(fullPath, zipPrefix || '')
    }
  }
}

async function main() {
  if (!fs.existsSync(SRC_DIR)) {
    console.error(`Source directory not found: ${SRC_DIR}`)
    process.exit(1)
  }

  // 0. Regenerate Prisma client (in case schema changed)
  const makerRoot = path.resolve(__dirname, '..')
  console.log('Regenerating Prisma client...')
  execSync('npx prisma generate', { cwd: makerRoot, stdio: 'pipe' })

  // 1. Zip source into .nmd
  console.log(`Zipping ${SRC_DIR} ...`)
  const zip = new AdmZip()
  addDirToZip(zip, SRC_DIR, '')
  zip.writeZip(NMD_PATH)
  const stats = fs.statSync(NMD_PATH)
  const sizeMB = (stats.size / (1024 * 1024)).toFixed(1)
  console.log(`Wrote ${sizeMB} MB → ${NMD_PATH}`)

  // 2. Force-delete old default world (DB, assets, mtime)
  console.log('Deleting old _default_world...')
  try { await deleteProject('_shared', '_default_world') } catch { /* wasn't open */ }
  const sharedDir = path.join(PROJECTS_DIR, '_shared')
  const dbPath = path.join(sharedDir, '_default_world.db')
  const assetsDir = path.join(sharedDir, '_default_world_assets')
  const mtimePath = path.join(sharedDir, '_default_world.mtime')
  if (fs.existsSync(dbPath)) fs.unlinkSync(dbPath)
  if (fs.existsSync(assetsDir)) fs.rmSync(assetsDir, { recursive: true, force: true })
  if (fs.existsSync(mtimePath)) fs.unlinkSync(mtimePath)

  // 3. Import fresh
  console.log('Importing _default_world from .nmd...')
  await importNmd(NMD_PATH, '_shared', '_default_world', true)
  const nmdMtime = fs.statSync(NMD_PATH).mtimeMs.toString()
  fs.writeFileSync(mtimePath, nmdMtime)

  console.log('Done. _default_world is ready.')
  process.exit(0)
}

main()
