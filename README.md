# NeoMud

A love letter to the MUDs of the '90s, built with modern tools and vibes. 100% vibe-coded with AI.

<p align="center">
  <img src="docs/screenshots/town_square.png" width="180" alt="Town Square — room art, NPC sprites, minimap, and game log" />
  <img src="docs/screenshots/whispering_forest.png" width="180" alt="Combat in the Whispering Forest" />
  <img src="docs/screenshots/splashscreen.png" width="400" alt="Login screen — forge splash with Stone & Torchlight UI" />
</p>
<p align="center">
  <img src="docs/screenshots/character_sheet.png" width="400" alt="Character sheet — stone-themed stats, vitals, skills, and equipment" />
  <img src="docs/screenshots/maker_zones.png" width="400" alt="Maker world map — all zones on a shared coordinate grid" />
</p>
<p align="center">
  <img src="docs/screenshots/maker_npcs.png" width="400" alt="Maker NPC editor with patrol route visualization" />
</p>

## What Is This?

NeoMud is a multiplayer dungeon game inspired by the text-based MUDs (Multi-User Dungeons) that consumed countless hours on dial-up connections in the 1990s. Games like MajorMUD, Legends of Kesmai, and the countless DikuMUD derivatives that ran on BBSes and early internet servers — where imagination filled in what pixels couldn't.

This project is a tribute to that era, but it doesn't try to be a museum piece. It takes the core of what made MUDs great — exploration, combat, character progression, and shared worlds — and wraps it in a modern client with room art, sprite overlays, and a real-time WebSocket backbone. The text log is still there. The direction pad is still there. But now you can *see* the tavern you're drinking in.

## Why Vibe Code a MUD?

Because MUDs were the original MMOs, and they got a lot of things right that modern games lost along the way:

- **Worlds driven by data, not code.** Rooms, items, NPCs, loot tables, spells, and skills are all JSON. A game master can reshape the world without recompiling anything.
- **Emergent multiplayer.** You share a room with other players. You see them arrive and leave. You talk. You fight the same monsters. No instancing, no sharding — just a shared world.
- **Mechanical transparency.** You know your stats. You know your weapon damage. You can reason about the systems, and that reasoning is the game.

This project is vibe-coded — built iteratively with AI assistance, following intuition over architecture docs, letting the design emerge from play. It's not production software. It's a playground.

## The World: Warden's Reckoning

The default world ships with **23 zones, 335 rooms, and 139 NPCs** across a full story arc from safe starting town to branching endgame finale:

| Zone | Level | Rooms | Theme |
|------|-------|-------|-------|
| Millhaven | Safe | 8 | Starting town — vendors, trainers, temple, tavern |
| Whispering Forest | 1–3 | 7 | First combat — wolves, bandits, skeletons |
| Thornveil Marsh | 4–5 | 6 | Swamp — lizardfolk, bog horrors |
| Blackstone Gorge | 6–7 | 5 | Gorge stalkers, cave trolls |
| Cracked Plains | 6–8 | 23 | Open wasteland with crafting vendor |
| Foothill Pass | 8–10 | 15 | Mountain approach |
| Iron Vein Mine | 9–11 | 13 | Underground mine with puzzles |
| Highmoor Steppes | 10–12 | 22 | Highland steppes |
| Watcher's Barrow | 11–12 | 10 | Burial chambers with mini-boss |
| Salt Coast | 13–15 | 20 | Coastal cliffs |
| Drowned Chapel | 14–15 | 10 | Sunken ruins with riddle puzzles |
| First Seal Approach | 15–17 | 16 | Dense forest approach |
| Cradle of the Seal | 16–17 | 10 | Ancient seal site |
| Skyveil Reach | 17–20 | 20 | High-altitude peaks |
| Ashwood Burn | 19–22 | 19 | Volcanic wasteland |
| Pyromancer's Folly | 20–22 | 9 | Fire dungeon |
| Glass Desert | 21–24 | 21 | Shimmering desert |
| Mirage Spire | 22–24 | 12 | Illusory tower |
| Bone Wastes | 22–25 | 23 | Undead wasteland |
| Necropolis of Vael | 23–25 | 17 | Necropolis dungeon |
| Warlord's Hold | 25–27 | 10 | Fortified stronghold |
| Stormcrown Keep | 25–27 | 17 | Boss keep with phase-shifting boss |
| The Sealed Threshold | 28–30 | 22 | **Branching finale** — permanent choice, two boss variants |

Every room has AI-generated background art. Every NPC and item has a sprite with proper alpha transparency. Every zone has background music and every action has sound effects — **1,081 assets** in total (955 images, 124 audio files).

## Features

### Characters
- **6 races** (Human, Dwarf, Elf, Halfling, Gnome, Half-Orc) with stat modifiers and XP scaling
- **15 classes** (Warrior, Paladin, Mage, Thief, Cleric, Druid, Ranger, Bard, and more) with unique skill/spell access
- 6-stat system with 60 CP allocation at creation and CP gains per level for ongoing training
- 5-tier trainer system gating level caps: T1(5) → T2(10) → T3(18) → T4(25) → T5(30)
- 9 equipment slots with paperdoll equip/unequip UI
- 270 unique player sprites (race/gender/class combinations)
- Starter equipment granted on character creation

### Combat
- **Tick-based** (1.5s) — all actions resolve in initiative order each tick
- Weapon damage = Strength + bonus + random roll; armor reduces incoming (min 1)
- **12 skills**: Bash (stun), Kick (knockback with direction picker), Backstab (from stealth), Parry, Dodge, Hide, Sneak, Meditate, Perception, Pick Lock, Track, Haggle
- **26 spells** across 5 schools (Mage, Priest, Druid, Kai, Bard) — damage, heal, buff, DoT, HoT
- Spell auto-cast — ready a spell once, it fires each tick until cancelled or out of MP
- **Boss phase system** — HP-threshold phase transitions with stat changes, sprite swaps, and dramatic broadcasts
- Hostile NPC pursuit — engaged NPCs chase fleeing players (wander/patrol types only)
- Death respawns at temple with XP penalty

### NPCs & AI
- 6 behavior types: idle, stationary, wander, patrol, vendor, trainer
- Wander NPCs traverse connected rooms in their zone via random walks
- Patrol NPCs walk fixed routes (configurable in the maker with click-to-build route editor)
- Per-room and per-zone spawn caps with continuous respawn system
- Vendors with charm-based pricing, equipped gear comparison, and Haggle skill discounts
- Trainers for stat allocation and level-up (5 tiers)
- Boss encounters with multi-phase HP-threshold transitions

### Interactable System
Room interactables are JSON-defined features: levers, traps, gates, puzzles, and branching choices.

- **EXIT_OPEN** — strength/skill check to open a locked exit
- **DAMAGE_TRAP** — deals damage on entry with stat-based save checks
- **PUZZLE_STEP** — multi-step puzzles that unlock exits on completion
- **PLACE_ITEM** — exchange an inventory item to open a path
- **RIDDLE_PROMPT** — dialog riddle that must be answered correctly
- **CONDITIONAL_TRIGGER** — gates exits on items, flags, or level requirements
- **CHOICE_PROMPT** — permanent per-character branching dialog with path-specific consequences

### Items & Economy
- **206 data-driven items**: weapons, armor sets, consumables, scrolls, crafting materials, quest items
- **22 crafting recipes** with crafting station NPCs
- 4-tier coin system: Copper, Silver, Gold, Platinum
- Loot tables per NPC type with weighted drop rates
- Town vendors with buy/sell and equipped gear comparison indicators
- Ground loot rendered as clickable sprites

### Client
- **Stone & Torchlight UI** — custom dark medieval forge aesthetic across all screens: stone-framed panels with beveled edges, corner rivets, runic inner glow, and torchlight-gold typography
- **Cross-platform** — Android, Desktop (JVM), iOS, and Web (WASM)
- Room scene: background art + NPC sprites + item sprites + player sprites
- BFS-based minimap with fog-of-war, zone color-coding, locked/hidden/interactable exit indicators
- 10-direction navigation (cardinal, diagonal, up/down)
- **Landscape mode** — side-by-side room art and game log with compact controls
- Spell bar with drag-to-assign slots and tap-to-ready auto-cast
- Icon grid inventory with item sprites and tap-to-use
- Per-zone background music with crossfade on zone transitions
- Sound effects for combat, spells, movement, and interactions
- **Tutorial system** — blocking modal dialogs and passive coach marks for new players
- **Help panel** — 10-section "Adventurer's Tome" accessible from toolbar

### Audio
- AI-generated sound effects via ElevenLabs — 124 audio files across combat, spells, items, NPCs, and ambient categories
- AI-composed background music — per-zone BGM with crossfade transitions
- Embedded intro theme plays on login screen without server connection
- Per-NPC attack, miss, death, and interaction sounds
- Per-weapon attack and miss sounds
- Per-spell cast, impact, and miss sounds

## The Maker

The Maker is a full-featured web-based world editor for building and managing game content. It's a standalone React + Express application with its own Prisma/SQLite database that imports and exports `.nmd` world bundles.

### Editors
- **Zone Editor** — visual room placement on a shared global coordinate grid, click-to-connect exits, room properties (effects, hidden exits, interactables, spawn caps), and a **world map view** showing all zones simultaneously with per-zone coloring
- **NPC Editor** — 3-panel layout with NPC list, zone map visualization (BFS-based wander reachability, patrol route rendering, spawn point markers), and full property editing including boss phases
- **Item Editor** — weapons, armor, consumables, scrolls with type-specific fields
- **Class Editor** — stat minimums, allowed skills/spells per class
- **Race Editor** — stat modifiers and XP scaling
- **Spell Editor** — damage, heal, buff, DoT/HoT with school and level requirements
- **Skill Editor** — active/passive skills with class restrictions and cooldowns
- **Recipe Editor** — crafting recipes with ingredient lists and station requirements
- **PC Sprite Editor** — manage 270 player sprites with race/gender/class filtering
- **Default SFX Editor** — assign and preview sounds across all entity types
- **Settings** — API keys for AI generation services

### Maker Features
- **Project system** — create, fork, open, and delete independent world projects
- **World map** — unified coordinate space across all zones; rooms can't overlap across zones
- **Import/export** — `.nmd` bundles (ZIP archives with JSON data + image/audio assets)
- **Validation** — server-side HTML rejection, name length limits, text sanitization on all entity routes
- **AI generation pipeline** — image generation (Nano Banana), background removal (rembg), sound effect generation (ElevenLabs) integrated into the editor workflow

## Architecture

```
NeoMud/
├── shared/     Kotlin Multiplatform — models and protocol shared between client and server
├── server/     Ktor 3.x + Netty — WebSocket game server with SQLite persistence
├── client/     Compose Multiplatform — game client (Android + Desktop + iOS + WASM)
├── maker/      React 18 + Express — web-based world editor and GM toolkit
├── scripts/    Utility scripts (background removal, game relay, etc.)
└── .claude/    AI agents, skills, and memory for Claude Code tooling
```

**Server** runs a 1.5-second tick-based game loop. Combat actions queue on the player session and resolve each tick in initiative order: bash, kick, readied spell, then melee. NPC behaviors (wander, patrol, pursuit, attack) execute after combat. All NPC kills flow through a single handler for loot, XP, and state cleanup. Boss encounters use an HP-threshold phase system that clamps HP, overrides stats, and swaps sprites at transition points. The world is loaded from a `.nmd` bundle at startup — a self-contained ZIP archive, similar to DOOM's WAD files.

**Client** is a Compose Multiplatform application — 89% of the code (UI components, screens, viewmodels, networking) lives in a shared `commonMain` source set, with only platform-specific glue (entry point, audio, logging) in `androidMain`, `desktopMain`, `iosMain`, and `wasmJsMain`. Runs on Android, Desktop (JVM), iOS, and Web (WASM/Kotlin). All UI icons use Material Icons (`ImageVector`) for guaranteed cross-platform rendering. The client connects over WebSocket and renders the game as a layered scene. The protocol is type-safe sealed classes with `kotlinx.serialization` — client and server share the same Kotlin types at compile time via the shared module.

**Maker** is a separate web application for world authoring. It has its own database, its own API, and exports `.nmd` bundles that the server consumes. The zone editor renders all zones on a single shared coordinate grid, enforcing global spatial consistency.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.3 (JVM 21) |
| Server | Ktor 3.4 + Netty |
| Database | SQLite + Exposed ORM |
| Client | Compose Multiplatform (Android + Desktop + iOS + WASM) |
| Images | Coil 3 (WebP with transparency, multiplatform) |
| Audio | Android MediaPlayer + SoundPool; Desktop JavaFX Media; iOS AVFoundation; WASM Howler.js (via expect/actual `PlatformAudioManager`) |
| Protocol | kotlinx.serialization over WebSocket |
| Navigation | JetBrains Navigation Compose (multiplatform) |
| Lifecycle | JetBrains Lifecycle ViewModel (multiplatform) |
| Shared Code | Kotlin Multiplatform |
| Build | Gradle 9.2 with configuration cache |
| Maker | React 18 + Express + Prisma + SQLite |
| CI/CD | GitHub Actions — test, build Docker images, deploy to staging |
| Observability | Sentry (server + WASM client) |

## By the Numbers

| Metric | Count |
|--------|-------|
| Lines of code | ~67,000 (53k Kotlin, 14k TypeScript) |
| Commits | 602 |
| Assets | 1,081 (955 images, 124 audio) |
| Player sprites | 270 (6 races x 3 genders x 15 classes) |
| World content | 23 zones, 335 rooms, 139 NPCs, 206 items, 26 spells, 12 skills, 22 recipes, 15 classes, 6 races |

## Running It

There are two ways to play: **download the fat JAR** (easiest — just need Java) or **build from source** (for development).

---

### Quick Start: Fat JAR

The server ships as a self-contained fat JAR with the default world bundled inside. No cloning, no Gradle, no build steps.

**Prerequisites:** JDK 21+ (e.g., [Amazon Corretto](https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html))

1. Download `neomud-server-vX.Y.Z.jar` from the [latest release](https://github.com/roomsmith-games/NeoMud/releases/latest)
2. Run it:

```bash
java -jar neomud-server-vX.Y.Z.jar
```

The server starts on port 8080 with defaults:
- WebSocket: `ws://localhost:8080/game`
- Health check: `http://localhost:8080/health`
- Database: `neomud.db` in the current directory
- World: extracted from the bundled classpath resource

#### CLI Options

```
Usage: java -jar neomud-server.jar [options]

Options:
  --port, -p <port>       Server port (default: 8080, env: NEOMUD_PORT)
  --world, -w <path>      World bundle .nmd file (default: bundled world, env: NEOMUD_WORLD)
  --db <path>             SQLite database path (default: neomud.db, env: NEOMUD_DB)
  --admins <users>        Comma-separated admin usernames (env: NEOMUD_ADMINS)
  --help, -h              Show this help message
```

Examples:
```bash
java -jar neomud-server.jar --port 9090 --admins alice,bob
java -jar neomud-server.jar --world custom-world.nmd --db /data/neomud.db
NEOMUD_PORT=9090 java -jar neomud-server.jar   # env vars work too
```

---

### Development Setup

For building from source, running clients, or working on the Maker.

#### Prerequisites

| Component | Requirement |
|-----------|-------------|
| Server | JDK 21 (e.g., Amazon Corretto) |
| Android client | JDK 21 + Android SDK (platform 34+) + emulator or device (min SDK 26) |
| Desktop client | JDK 21 (no extra dependencies) |
| WASM client | JDK 21 (built by Gradle, served as static files) |
| Maker | Node.js 18+ |

#### macOS

```bash
# JDK 21
brew install --cask corretto@21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Android SDK (or install Android Studio which bundles it)
brew install --cask android-commandlinetools
sdkmanager "platforms;android-34" "build-tools;34.0.0"
export ANDROID_HOME=$HOME/Library/Android/sdk

# Persist in shell profile
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
echo 'export ANDROID_HOME=$HOME/Library/Android/sdk' >> ~/.zshrc

# Node.js (for the Maker)
brew install node@18
```

#### Windows

```powershell
# Install JDK 21 from https://docs.aws.amazon.com/corretto/latest/corretto-21-ug/downloads-list.html
# Set JAVA_HOME in System Environment Variables to the JDK install path

# Install Android Studio from https://developer.android.com/studio
# Set ANDROID_HOME in System Environment Variables (typically %LOCALAPPDATA%\Android\Sdk)

# Install Node.js from https://nodejs.org/ (LTS 18+)
```

#### Linux

```bash
# JDK 21 (Ubuntu/Debian)
sudo apt install -y openjdk-21-jdk
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Android SDK (or install Android Studio)
sudo apt install -y android-sdk
export ANDROID_HOME=$HOME/Android/Sdk

# Node.js 18+
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt install -y nodejs
```

#### Server (Dev Mode)

```bash
./gradlew packageWorld --rerun-tasks   # Build the .nmd world bundle from source
./gradlew :server:run                  # Starts on :8080, WebSocket at /game
```

The dev server loads the world from `server/build/worlds/default-world.nmd` (built by `packageWorld` from `maker/default_world_src/`). You must re-run `packageWorld --rerun-tasks` after any change to world data files.

##### Admin promotion

Admins can run privileged slash commands like `/teleport <roomId>`, `/give`, etc. Two paths grant admin status, both checked at every login:

- **Username allowlist** — set `NEOMUD_ADMINS` to a comma-separated list of usernames (or pass `--admins`). Matching characters are auto-promoted on login. The `:server:run` task injects `NEOMUD_ADMINS=bob` by default, so a freshly-cloned, freshly-registered character named `bob` is admin out of the box.
- **World ownership (platform-managed)** — when running under the NeoMud-platform marketplace, the orchestrator injects `WORLD_OWNER_PLATFORM_USER_ID`. The platform user whose JWT `userId` matches is auto-promoted in their own world. **Self-hosted servers can ignore this** — the env var is optional and inert if unset.

Both paths compose: a player can be promoted by either match. Either env var being unset is silent — no warn spam, no startup error.

#### Client (Android)

```bash
./gradlew :client:installDebug   # Build and install on connected device/emulator
```

From an Android emulator, connect to `10.0.2.2:8080`. From a physical device on the same network, use your machine's local IP (e.g., `192.168.1.x:8080`). The host and port are configurable on the login screen.

#### Client (Desktop)

```bash
./gradlew :client:run   # Launches a desktop window (JVM)
```

Defaults to `127.0.0.1:8080`. The host and port are configurable on the login screen. Works on Windows, macOS, and Linux — no Android SDK required.

To build native installers:

```bash
./gradlew :client:packageMsi    # Windows .msi
./gradlew :client:packageDmg    # macOS .dmg
./gradlew :client:packageDeb    # Linux .deb
```

#### Client (Web/WASM)

```bash
./gradlew :client:wasmJsBrowserDistribution   # Build production WASM bundle
```

Output goes to `client/build/dist/wasmJs/productionExecutable/`. Serve with any static file server. The WASM client is also deployed automatically to staging via CI.

#### Client (iOS)

Requires macOS with Xcode 15+ installed. The iOS client is built via Kotlin Multiplatform's iOS framework embedding.

```bash
./gradlew :client:linkDebugFrameworkIosSimulatorArm64   # Build the framework
```

Then open the Xcode project in `iosApp/`, select a simulator, and run. The iOS client shares 89% of its code with Android/Desktop — only the entry point, audio (`AVFoundation`), and logging are platform-specific.

#### Maker (World Editor)

```bash
cd maker
npm install          # postinstall runs prisma generate automatically
npm run dev          # http://localhost:5173
```

On first run, it auto-imports the default world. The Maker is a standalone web application — it has its own database and exports `.nmd` bundles that the server consumes.

#### Tests

```bash
./gradlew :shared:jvmTest :server:test                   # Server + shared tests
./gradlew :client:testDebugUnitTest :client:desktopTest   # Client tests — Android + Desktop
cd maker && npx vitest run                                # Maker tests
```

Client UI tests live in `commonTest` and run on both Android (via Robolectric) and Desktop (via Skiko). Paparazzi screenshot tests remain Android-only.

#### Creating a Release

Tag a version and push — GitHub Actions builds the fat JAR, WASM client, and world bundle, runs tests, publishes a GitHub release with artifacts, and pushes Docker images to GHCR.

```bash
# 1. Bump version in maker/default_world_src/manifest.json
# 2. Commit and push to master (deploys to staging automatically)
# 3. Tag and push
git tag v1.0.1
git push origin v1.0.1
```

## AI Tooling

This project is built entirely with [Claude Code](https://claude.com/claude-code), using custom agents and skills in the `.claude/` directory:

| Skill | Purpose |
|-------|---------|
| `/game-designer` | RPG balance analysis — models combat math, audits data files, proposes tuning changes |
| `/playtest` | AI playtester — plays the game via WebSocket relay, files GitHub issues for bugs |
| `/worldmaker` | Browser-based QA agent — tests the Maker UI through Playwright interaction |
| `/web-uat-test` | End-to-end browser testing of the WASM client and marketplace |
| `/bugfixer` | Automated issue triage — works through the GitHub backlog |
| `/elevenlabs-sfx` | Sound effect and BGM generation via ElevenLabs AI |
| `/rebuild-world` | Rebuilds the `.nmd` bundle after asset changes |

Agent memory in `.claude/agent-memory/` persists findings across sessions — the game designer remembers its balance audits, the worldmaker remembers UI patterns it's tested, and the playtester remembers what it's explored.

## Roadmap

### Completed
- [x] **Warden's Reckoning** — full story arc across 23 zones, 335 rooms (L1–30)
- [x] Boss phase system with HP-threshold transitions
- [x] Interactable system (traps, puzzles, riddles, branching choices)
- [x] Crafting system with 22 recipes
- [x] WASM web client — zero-install browser play
- [x] Desktop (JVM) and iOS clients with full feature parity
- [x] Tutorial system for new players
- [x] CI/CD pipeline with automated staging deployment
- [x] Sentry observability (server + WASM client)

### Future
- [ ] Endless Spire — procedural endgame dungeon
- [ ] Party system with shared XP and group combat
- [ ] PvP — dueling, arenas, or PvP zones
- [ ] World events — timed spawns, invasions
- [ ] Player guilds and social features
- [ ] Disarm skill
- [ ] Chat UI improvements

## The Spirit of the Thing

This isn't a finished game. It's a living sketch — a place to experiment with what a MUD looks like when you can see it, when the protocol is type-safe, when the world data lives in version control alongside the code, and when an AI can playtest its own creation.

If you played MUDs in the '90s, you'll recognize the bones. If you didn't, maybe this will show you what all the fuss was about.
