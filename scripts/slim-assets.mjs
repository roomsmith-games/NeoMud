#!/usr/bin/env node
/**
 * Slim default-world assets to spec dimensions and reasonable bitrates.
 *
 * Per-category targets (per CLAUDE.md "Asset Image Pipeline"):
 *   players  384×384  WebP q=80  alphaQ=90
 *   items    256×256  WebP q=80  alphaQ=90
 *   npcs     per-NPC from zone JSON imageWidth/imageHeight
 *            (humanoid 384×384, creature 512×384 for new gens; some older
 *            zones declare and ship 384×384 creatures — both are valid)
 *            WebP q=80  alphaQ=90
 *   coins    256×256  WebP q=85  alphaQ=90
 *   rooms    1024×576 WebP q=82
 *   bgm      96kbps stereo MP3 (mono if --mono)
 *
 * Sprite resize uses fit:'contain' + transparent bg to pad to exact spec dims
 * (the renderer ignores declared imageWidth/imageHeight and draws the file at
 * its natural aspect — see SpriteOverlay.kt). Backgrounds use fit:'inside'
 * because pillarbox bars on full-bleed scenes would be visible.
 *
 * Idempotency: sprites are skipped iff dimensions exactly match the target;
 * backgrounds and audio are skipped iff current dimensions are at-or-below
 * target (or, for audio, current bitrate ≤ MAX_AUDIO_BITRATE).
 * File size is never the gate — a 1024² sprite that compresses to 50KB still
 * wastes RAM at decode time.
 *
 * Atomicity: every write goes to a sibling .tmp file then rename(2)s onto
 * the target. A crash mid-encode leaves the original untouched.
 *
 * Usage:
 *   node scripts/slim-assets.mjs --category coins --dry-run
 *   node scripts/slim-assets.mjs --category all
 *   node scripts/slim-assets.mjs --category rooms --update-zones
 *
 * Requires: sharp (in scripts/node_modules), ffmpeg + ffprobe on PATH.
 */

import fs from 'node:fs';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import sharp from 'sharp';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = path.resolve(__dirname, '..');

const args = parseArgs(process.argv.slice(2));

const BASE_DIR = args['base-dir']
  ? path.resolve(args['base-dir'])
  : path.join(REPO_ROOT, 'maker/default_world_src');
const ASSETS_ROOT = path.join(BASE_DIR, 'assets');
const WORLD_DIR = path.join(BASE_DIR, 'world');

if (args.help) {
  console.log(`Usage: node scripts/slim-assets.mjs [options]
  --base-dir <path>    root directory containing assets/ and world/ subdirs
                       (default: maker/default_world_src)
  --category <name>    coins|items|rooms|npcs|players|audio|all (default: all)
  --only <names>       comma-separated basenames to process (others skipped)
  --dry-run            print projected changes, write nothing
  --verbose            per-file output
  --update-zones       (rooms only) rewrite zone JSON .jpg→.webp refs
  --mono               (audio only) downmix BGM to mono 96kbps
  --help

When --only is provided, only the listed files are processed. Names are
matched against the basename (e.g. "item_ale.webp,item_bread_loaf.webp").`);
  process.exit(0);
}

const onlySet = args.only
  ? new Set(String(args.only).split(',').map(s => s.trim()).filter(Boolean))
  : null;

const CATEGORIES = ['coins', 'items', 'rooms', 'npcs', 'players', 'audio'];
const requested = args.category === 'all' || !args.category ? CATEGORIES : [args.category];
for (const c of requested) {
  if (!CATEGORIES.includes(c)) {
    console.error(`unknown category: ${c}`);
    process.exit(2);
  }
}

const SPRITE_TARGETS = {
  players: { dir: 'images/players', w: 384, h: 384, quality: 80, alphaQuality: 90 },
  items:   { dir: 'images/items',   w: 256, h: 256, quality: 80, alphaQuality: 90 },
  coins:   { dir: 'images/coins',   w: 256, h: 256, quality: 85, alphaQuality: 90 },
  rooms:   { dir: 'images/rooms',   w: 1024, h: 576, quality: 82, alphaQuality: 80 },
};

const MAX_AUDIO_BITRATE = 100_000; // skip BGM tracks already ≤100kbps

const stats = { processed: 0, skipped: 0, oldBytes: 0, newBytes: 0, errors: 0 };
const zoneJsonRewrites = []; // { file, before, after } — only when --update-zones

for (const cat of requested) {
  if (cat === 'npcs') await runNpcs();
  else if (cat === 'audio') await runAudio();
  else await runSprites(cat, SPRITE_TARGETS[cat]);
}

if (args['update-zones']) await applyZoneJsonRewrites();

reportTotals();

// -----------------------------------------------------------------------------

async function runSprites(category, target) {
  const dir = path.join(ASSETS_ROOT, target.dir);
  if (!fs.existsSync(dir)) {
    console.warn(`(${category}) directory missing: ${dir}`);
    return;
  }
  console.log(`\n=== ${category} → ${target.w}×${target.h} WebP q=${target.quality} ===`);
  let files = fs.readdirSync(dir).filter((f) => /\.(webp|jpg|jpeg|png)$/i.test(f));
  if (onlySet) files = files.filter((f) => onlySet.has(f));
  for (const name of files) {
    const file = path.join(dir, name);
    await processSprite(category, file, target);
  }
}

async function runNpcs() {
  console.log(`\n=== npcs → per-NPC dims from zone JSON, WebP q=80 ===`);
  const dimMap = buildNpcDimMap(); // { 'forest_spider' → { w: 512, h: 384 } }
  const dir = path.join(ASSETS_ROOT, 'images/npcs');
  if (!fs.existsSync(dir)) {
    console.warn(`(npcs) directory missing: ${dir}`);
    return;
  }
  let files = fs.readdirSync(dir).filter((f) => /\.webp$/i.test(f));
  if (onlySet) files = files.filter((f) => onlySet.has(f));
  for (const name of files) {
    // npc_forest_spider.webp → forest_spider
    const id = name.replace(/^npc_/, '').replace(/\.webp$/i, '');
    const dim = dimMap.get(id);
    const target = dim
      ? { dir: 'images/npcs', w: dim.w, h: dim.h, quality: 80, alphaQuality: 90 }
      : { dir: 'images/npcs', w: 512, h: 512, quality: 80, alphaQuality: 90 };
    if (!dim && args.verbose) console.log(`  ! no zone-JSON dims for ${id}, defaulting to 512×512`);
    await processSprite('npcs', path.join(dir, name), target);
  }
}

async function runAudio() {
  console.log(`\n=== audio/bgm → 96kbps ${args.mono ? 'mono' : 'stereo'} MP3 ===`);
  const dir = path.join(ASSETS_ROOT, 'audio/bgm');
  if (!fs.existsSync(dir)) {
    console.warn(`(audio) directory missing: ${dir}`);
    return;
  }
  let files = fs.readdirSync(dir).filter((f) => /\.mp3$/i.test(f));
  if (onlySet) files = files.filter((f) => onlySet.has(f));
  for (const name of files) {
    const file = path.join(dir, name);
    await processAudio(file);
  }
}

// -----------------------------------------------------------------------------

async function processSprite(category, file, target) {
  const oldBytes = fs.statSync(file).size;
  let meta;
  try {
    meta = await sharp(file).metadata();
  } catch (err) {
    console.error(`✗ ${rel(file)} — sharp.metadata failed: ${err.message}`);
    stats.errors++;
    return;
  }

  const isJpg = /\.jpg$|\.jpeg$/i.test(file);
  // Backgrounds are full-bleed scenes — anything at-or-below target is fine,
  // the renderer scales them to fit. Sprites must be EXACTLY at spec because
  // the renderer respects file aspect ratio (a 384x209 NPC renders 1.83× wider
  // than a 384x384 one and overflows its slot). The original at-or-below
  // check across all categories silently shipped 343 off-spec sprite files.
  const dimsMatch = category === 'rooms'
    ? meta.width <= target.w && meta.height <= target.h
    : meta.width === target.w && meta.height === target.h;

  if (!isJpg && dimsMatch) {
    if (args.verbose) {
      console.log(`  ⊘ ${rel(file)} (${meta.width}×${meta.height}, ${kb(oldBytes)}) at spec`);
    }
    stats.skipped++;
    stats.oldBytes += oldBytes;
    stats.newBytes += oldBytes;
    return;
  }

  // Output is always .webp at the same basename (JPGs get a sibling .webp).
  const outFile = isJpg ? file.replace(/\.(jpg|jpeg)$/i, '.webp') : file;
  const tmpFile = outFile + '.slim.tmp';

  if (args['dry-run']) {
    // Estimate without writing. For sprites: contain pads to exact target dims,
    // so output px count is target.w * target.h (transparent pad is cheap).
    // For backgrounds: inside-fit preserves source aspect, so output stays at
    // source dims if smaller than target.
    const oldPx = meta.width * meta.height;
    const isBg = category === 'rooms';
    const newW = isBg ? Math.min(meta.width, target.w) : target.w;
    const newH = isBg ? Math.min(meta.height, target.h) : target.h;
    const newPx = newW * newH;
    // Quality factor: padded transparent pixels compress nearly free, so use a
    // weighted estimate: actual content area ≈ oldPx, pad ≈ 0.05× per pixel.
    const contentRatio = isBg ? newPx / oldPx : Math.min(1, oldPx / newPx);
    const projected = Math.round(oldBytes * contentRatio * 0.6);
    console.log(`  → ${rel(file)} ${meta.width}×${meta.height} ${kb(oldBytes)}` +
      ` → ${newW}×${newH} ~${kb(projected)}`);
    stats.processed++;
    stats.oldBytes += oldBytes;
    stats.newBytes += projected;
    return;
  }

  // Sprites (NPCs, items, coins, players) need EXACT spec dims — the renderer
  // uses ContentScale.Fit + height-only-constrained Modifier, so a 384x209
  // sprite renders 1.83× wider than a 384x384 one and overflows its slot.
  // Pad with transparent margins via fit:'contain' so the rembg-cropped subject
  // stays at its natural pixel size and the canvas grows to spec.
  // Backgrounds (rooms) are full-bleed scenes — fit:'inside' is correct
  // because cover/contain would either crop composition or leave pillarbox bars.
  const isBackground = category === 'rooms';
  const fit = isBackground ? 'inside' : 'contain';

  try {
    await sharp(file)
      .resize({
        width: target.w,
        height: target.h,
        fit,
        background: { r: 0, g: 0, b: 0, alpha: 0 },
        withoutEnlargement: isBackground,
      })
      .webp({
        quality: target.quality,
        alphaQuality: target.alphaQuality,
        effort: 6,
      })
      .toFile(tmpFile);
    const newBytes = fs.statSync(tmpFile).size;
    fs.renameSync(tmpFile, outFile);
    if (isJpg && outFile !== file) {
      // Track the .jpg→.webp filename change. Caller (via --update-zones)
      // will rewrite zone JSON refs and prompt for `git rm` of the .jpg.
      zoneJsonRewrites.push({ oldPath: file, newPath: outFile });
    }
    if (args.verbose || category === 'rooms') {
      console.log(`  ✓ ${rel(file)} ${meta.width}×${meta.height} ${kb(oldBytes)}` +
        ` → ${kb(newBytes)} (${pct(newBytes, oldBytes)})`);
    }
    stats.processed++;
    stats.oldBytes += oldBytes;
    stats.newBytes += newBytes;
  } catch (err) {
    if (fs.existsSync(tmpFile)) fs.unlinkSync(tmpFile);
    console.error(`✗ ${rel(file)} — ${err.message}`);
    stats.errors++;
  }
}

async function processAudio(file) {
  const oldBytes = fs.statSync(file).size;
  let bitrate;
  try {
    const out = execFileSync('ffprobe', [
      '-v', 'error',
      '-select_streams', 'a:0',
      '-show_entries', 'stream=bit_rate',
      '-of', 'default=noprint_wrappers=1:nokey=1',
      file,
    ], { encoding: 'utf8' });
    bitrate = parseInt(out.trim(), 10);
  } catch (err) {
    console.error(`✗ ${rel(file)} — ffprobe failed: ${err.message}`);
    stats.errors++;
    return;
  }

  if (bitrate && bitrate <= MAX_AUDIO_BITRATE) {
    if (args.verbose) {
      console.log(`  ⊘ ${rel(file)} (${Math.round(bitrate / 1000)}kbps, ${kb(oldBytes)}) already ≤ ${MAX_AUDIO_BITRATE / 1000}kbps`);
    }
    stats.skipped++;
    stats.oldBytes += oldBytes;
    stats.newBytes += oldBytes;
    return;
  }

  if (args['dry-run']) {
    const targetBitrate = 96_000;
    const projected = Math.round(oldBytes * (targetBitrate / (bitrate || targetBitrate)));
    console.log(`  → ${rel(file)} ${Math.round(bitrate / 1000)}kbps ${kb(oldBytes)}` +
      ` → 96kbps ~${kb(projected)}`);
    stats.processed++;
    stats.oldBytes += oldBytes;
    stats.newBytes += projected;
    return;
  }

  // Keep .mp3 extension so ffmpeg auto-detects the output muxer. The .slim
  // infix prevents re-globbing on a crashed partial run.
  const tmpFile = file.replace(/\.mp3$/i, '.slim.mp3');
  const channels = args.mono ? '1' : '2';
  try {
    execFileSync('ffmpeg', [
      '-y', '-loglevel', 'error',
      '-i', file,
      '-ac', channels,
      '-b:a', '96k',
      '-map_metadata', '-1',
      tmpFile,
    ]);
    const newBytes = fs.statSync(tmpFile).size;
    fs.renameSync(tmpFile, file);
    console.log(`  ✓ ${rel(file)} ${Math.round(bitrate / 1000)}kbps ${kb(oldBytes)}` +
      ` → 96kbps ${channels === '1' ? 'mono' : 'stereo'} ${kb(newBytes)} (${pct(newBytes, oldBytes)})`);
    stats.processed++;
    stats.oldBytes += oldBytes;
    stats.newBytes += newBytes;
  } catch (err) {
    if (fs.existsSync(tmpFile)) fs.unlinkSync(tmpFile);
    console.error(`✗ ${rel(file)} — ffmpeg failed: ${err.message}`);
    stats.errors++;
  }
}

// -----------------------------------------------------------------------------

function buildNpcDimMap() {
  const map = new Map();
  if (!fs.existsSync(WORLD_DIR)) return map;
  for (const f of fs.readdirSync(WORLD_DIR).filter((x) => x.endsWith('.zone.json'))) {
    const data = JSON.parse(fs.readFileSync(path.join(WORLD_DIR, f), 'utf8'));
    for (const npc of (data.npcs || [])) {
      const id = String(npc.id || '').replace(/^npc:/, '');
      if (!id) continue;
      if (npc.imageWidth && npc.imageHeight) {
        map.set(id, { w: npc.imageWidth, h: npc.imageHeight });
      }
    }
  }
  return map;
}

async function applyZoneJsonRewrites() {
  if (zoneJsonRewrites.length === 0) {
    if (args.verbose) console.log('\n(no zone JSON rewrites needed)');
    return;
  }
  console.log(`\n=== zone JSON rewrites (${zoneJsonRewrites.length} files) ===`);
  // Discover which zone JSONs reference each old path.
  const zones = fs.readdirSync(WORLD_DIR).filter((x) => x.endsWith('.zone.json'));
  const removeCommands = [];
  for (const { oldPath, newPath } of zoneJsonRewrites) {
    const oldRef = '/' + path.relative(path.join(REPO_ROOT, 'maker/default_world_src'), oldPath);
    const newRef = '/' + path.relative(path.join(REPO_ROOT, 'maker/default_world_src'), newPath);
    let foundIn = [];
    for (const z of zones) {
      const zPath = path.join(WORLD_DIR, z);
      let txt = fs.readFileSync(zPath, 'utf8');
      if (txt.includes(oldRef)) {
        const updated = txt.split(oldRef).join(newRef);
        if (args['dry-run']) {
          console.log(`  → would rewrite ${z}: ${oldRef} → ${newRef}`);
        } else {
          fs.writeFileSync(zPath, updated);
          console.log(`  ✓ ${z}: ${oldRef} → ${newRef}`);
        }
        foundIn.push(z);
      }
    }
    if (foundIn.length === 0) {
      console.log(`  ! no zone JSON references ${oldRef}`);
    }
    removeCommands.push(`git rm ${path.relative(REPO_ROOT, oldPath)}`);
  }
  console.log(`\nNext: run these to drop the original .jpg files (verify each):`);
  for (const cmd of removeCommands) console.log(`  ${cmd}`);
}

function reportTotals() {
  const delta = stats.oldBytes - stats.newBytes;
  console.log(`\n--- summary ---`);
  console.log(`processed:  ${stats.processed}`);
  console.log(`skipped:    ${stats.skipped}`);
  console.log(`errors:     ${stats.errors}`);
  console.log(`old bytes:  ${mb(stats.oldBytes)}`);
  console.log(`new bytes:  ${mb(stats.newBytes)}` + (args['dry-run'] ? ' (projected)' : ''));
  console.log(`delta:      ${mb(delta)} saved (${pct(stats.newBytes, stats.oldBytes)} of original)`);
  if (stats.errors > 0) process.exit(1);
}

// -----------------------------------------------------------------------------

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (!a.startsWith('--')) continue;
    const key = a.slice(2);
    const next = argv[i + 1];
    if (next && !next.startsWith('--')) {
      out[key] = next;
      i++;
    } else {
      out[key] = true;
    }
  }
  return out;
}

function rel(p) {
  return path.relative(REPO_ROOT, p);
}

function kb(b) {
  return `${(b / 1024).toFixed(1)}KB`;
}

function mb(b) {
  return `${(b / 1024 / 1024).toFixed(2)}MB`;
}

function pct(a, b) {
  if (b === 0) return '-';
  return `${Math.round((a / b) * 100)}%`;
}
