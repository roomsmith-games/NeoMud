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

This project is a tribute to that era, but it doesn't try to be a museum piece. It takes the core of what made MUDs great — exploration, combat, character progression, and shared worlds — and wraps it in a modern mobile client with room art, sprite overlays, and a real-time WebSocket backbone. The text log is still there. The direction pad is still there. But now you can *see* the tavern you're drinking in.

## Why Vibe Code a MUD?

Because MUDs were the original MMOs, and they got a lot of things right that modern games lost along the way:

- **Worlds driven by data, not code.** Rooms, items, NPCs, loot tables, spells, and skills are all JSON. A game master can reshape the world without recompiling anything.
- **Emergent multiplayer.** You share a room with other players. You see them arrive and leave. You talk. You fight the same monsters. No instancing, no sharding — just a shared world.
- **Mechanical transparency.** You know your stats. You know your weapon damage. You can reason about the systems, and that reasoning is the game.

This project is vibe-coded — built iteratively with AI assistance, following intuition over architecture docs, letting the design emerge from play. It's not production software. It's a playground.

## The World

The default world ships with **4 zones, 25 rooms, and 17 NPCs** across a progression from safe town to dangerous gorge:

| Zone | Level | Rooms | NPCs | Description |
|------|-------|-------|------|-------------|
| Millhaven | Safe | 7 | 5 | Starting town — vendors, trainers, temple, tavern |
| Whispering Forest | 1-3 | 7 | 4 | First combat zone — wolves, bandits, skeletons |
| Thornveil Marsh | 4-5 | 6 | 4 | Swamp zone — lizardfolk, bog horrors |
| Blackstone Gorge | 6-7 | 5 | 4 | End-game — gorge stalkers, cave trolls |

Every room has hand-prompted AI-generated background art. Every NPC and item has a sprite with proper alpha transparency. Every zone has background music and every action has sound effects — **460 assets** in total (357 images, 103 audio files).

## Features

### Characters
- **6 races** (Human, Dwarf, Elf, Halfling, Gnome, Half-Orc) with stat modifiers and XP scaling
- **15 classes** (Warrior, Paladin, Mage, Thief, Cleric, Druid, Ranger, Bard, and more) with unique skill/spell access
- 6-stat system with 60 CP allocation at creation and CP gains per level for ongoing training
- 9 equipment slots with paperdoll equip/unequip UI
- 270 unique player sprites (race/gender/class combinations)
- Starter equipment granted on character creation

### Combat
- **Tick-based** (1.5s) — all actions resolve in initiative order each tick
- Weapon damage = Strength + bonus + random roll; armor reduces incoming (min 1)
- **12 skills**: Bash (stun), Kick (knockback with direction picker), Backstab (from stealth), Parry, Dodge, Hide, Sneak, Meditate, Perception, Pick Lock, Track, Haggle
- **23 spells** across 5 schools (Mage, Priest, Druid, Kai, Bard) — damage, heal, buff, DoT, HoT
- Spell auto-cast — ready a spell once, it fires each tick until cancelled or out of MP
- Hostile NPC pursuit — engaged NPCs chase fleeing players
- Death respawns at temple with XP penalty

### NPCs & AI
- 5 behavior types: idle, wander, patrol, vendor, trainer
- Wander NPCs traverse connected rooms in their zone via random walks
- Patrol NPCs walk fixed routes (configurable in the maker with click-to-build route editor)
- Per-room and per-zone spawn caps with continuous respawn system
- Vendors with charm-based pricing and Haggle skill discounts
- Trainers for stat allocation and level-up

### Items & Economy
- 41 data-driven items: weapons, armor sets, consumables, scrolls, crafting materials
- 4-tier coin system: Copper, Silver, Gold, Platinum
- Loot tables per NPC type with weighted drop rates
- 3 town vendors: tavern (potions), blacksmith (weapons/armor), enchantress (scrolls/enchanted gear)
- Ground loot rendered as clickable sprites

### Client
- **Stone & Torchlight UI** — custom dark medieval forge aesthetic across all screens: stone-framed panels with beveled edges, corner rivets, runic inner glow, and torchlight-gold typography (no Material3 defaults)
- **Cross-platform icons** — all UI icons (skills, spells, toolbar, status effects) use Material Icons `ImageVector` objects via a centralized `MudIcons` registry — vector-drawn by Compose on every platform, no emoji or font dependencies
- Login/registration splash screen with embedded forge background art and cinematic intro BGM
- Equipment paperdoll with tap-to-inspect flow (stats, description, unequip) instead of instant unequip
- Room scene: background art + NPC sprites + item sprites + player sprites
- BFS-based minimap with fog-of-war, zone color-coding, locked/hidden/interactable exit indicators
- 10-direction navigation (cardinal, diagonal, up/down)
- **Landscape mode** — side-by-side room art and game log with compact controls
- Spell bar with drag-to-assign slots and tap-to-ready auto-cast
- Icon grid inventory with item sprites and tap-to-use
- Per-zone background music with crossfade on zone transitions
- Sound effects for combat, spells, movement, and interactions
- Configurable volume controls for BGM and SFX

### Audio
- AI-generated sound effects via ElevenLabs — 104 audio files across combat, spells, items, NPCs, and ambient categories
- AI-composed background music via ElevenLabs — 5 BGM tracks including cinematic login theme
- Per-zone background music with crossfade transitions
- Embedded intro theme plays on login screen without server connection
- Per-NPC attack, miss, death, and interaction sounds
- Per-weapon attack and miss sounds
- Per-spell cast, impact, and miss sounds

## The Maker

The Maker is a full-featured web-based world editor for building and managing game content. It's a standalone React + Express application with its own Prisma/SQLite database that imports and exports `.nmd` world bundles.

### Editors
- **Zone Editor** — visual room placement on a shared global coordinate grid, click-to-connect exits, room properties (effects, hidden exits, interactables, spawn caps), and a **world map view** showing all zones simultaneously with per-zone coloring
- **NPC Editor** — 3-panel layout with NPC list, zone map visualization (BFS-based wander reachability, patrol route rendering, spawn point markers), and full property editing
- **Item Editor** — weapons, armor, consumables, scrolls with type-specific fields
- **Class Editor** — stat minimums, allowed skills/spells per class
- **Race Editor** — stat modifiers and XP scaling
- **Spell Editor** — damage, heal, buff, DoT/HoT with school and level requirements
- **Skill Editor** — active/passive skills with class restrictions and cooldowns
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
├── client/     Compose Multiplatform — game client (Android + Desktop + iOS)
├── maker/      React 18 + Express — web-based world editor and GM toolkit
├── scripts/    Utility scripts (background removal, game relay, etc.)
└── .claude/    AI agents, skills, and memory for Claude Code tooling
```

**Server** runs a 1.5-second tick-based game loop. Combat actions queue on the player session and resolve each tick in initiative order: bash, kick, readied spell, then melee. NPC behaviors (wander, patrol, pursuit, attack) execute after combat. All NPC kills flow through a single handler for loot, XP, and state cleanup. The world is loaded from a `.nmd` bundle at startup — a self-contained ZIP archive, similar to DOOM's WAD files.

**Client** is a Compose Multiplatform application — 89% of the code (UI components, screens, viewmodels, networking) lives in a shared `commonMain` source set, with only platform-specific glue (entry point, audio, logging) in `androidMain`, `desktopMain`, and `iosMain`. Runs on Android, Desktop (JVM), and iOS today, with Web planned. All UI icons use Material Icons (`ImageVector`) for guaranteed cross-platform rendering — no emoji, no platform font dependencies. The client connects over WebSocket and renders the game as a layered scene. The protocol is type-safe sealed classes with `kotlinx.serialization` — client and server share the same Kotlin types at compile time via the shared module.

**Maker** is a separate web application for world authoring. It has its own database, its own API, and exports `.nmd` bundles that the server consumes. The zone editor renders all zones on a single shared coordinate grid, enforcing global spatial consistency.

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.3 (JVM 21) |
| Server | Ktor 3.4 + Netty |
| Database | SQLite + Exposed ORM |
| Client | Compose Multiplatform (Android + Desktop + iOS, Web planned) |
| Images | Coil 3 (WebP with transparency, multiplatform) |
| Audio | Android MediaPlayer + SoundPool; Desktop JavaFX Media; iOS AVFoundation (via expect/actual `PlatformAudioManager`) |
| Protocol | kotlinx.serialization over WebSocket |
| Navigation | JetBrains Navigation Compose (multiplatform) |
| Lifecycle | JetBrains Lifecycle ViewModel (multiplatform) |
| Shared Code | Kotlin Multiplatform |
| Build | Gradle 9.2 with configuration cache |
| Maker | React 18 + Express + Prisma + SQLite |

## By the Numbers

| Metric | Count |
|--------|-------|
| Lines of code | ~46,700 (35.8k Kotlin, 10.9k TypeScript) |
| Commits | 321 |
| Tests | 1,364 (439 server, 392 shared, 203 client, 330 maker) |
| Assets | 461 (357 images, 104 audio) |
| Player sprites | 270 (6 races x 3 genders x 15 classes) |
| World content | 4 zones, 25 rooms, 17 NPCs, 41 items, 23 spells, 12 skills, 15 classes, 6 races |

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
./gradlew :shared:jvmTest :server:test              # Server + shared tests (831 tests)
./gradlew :client:testDebugUnitTest :client:desktopTest  # Client tests — Android + Desktop (203 tests)
cd maker && npx vitest run                           # Maker tests (330 tests)
```

Client UI tests live in `commonTest` and run on both Android (via Robolectric) and Desktop (via Skiko). Paparazzi screenshot tests remain Android-only. iOS tests compile-check on all platforms and run on macOS with a simulator.

#### Creating a Release

Tag a version and push — GitHub Actions builds the fat JAR, runs tests, and publishes a release with the artifact attached.

```bash
git tag v0.1.0
git push origin v0.1.0
```

The version number bakes into the JAR automatically via `./gradlew updateVersion` (called by the workflow). Format: `NeoMud alpha 0.1.0.0`.

## AI Tooling

This project is built entirely with [Claude Code](https://claude.com/claude-code), using custom agents and skills in the `.claude/` directory:

| Skill | Purpose |
|-------|---------|
| `/game-designer` | RPG balance analysis — models combat math, audits data files, proposes tuning changes |
| `/playtest` | AI playtester — plays the game via WebSocket relay, files GitHub issues for bugs |
| `/worldmaker` | Browser-based QA agent — tests the Maker UI through Playwright interaction |
| `/bugfixer` | Automated issue triage — works through the GitHub backlog |
| `/elevenlabs-sfx` | Sound effect and BGM generation via ElevenLabs AI |
| `/rebuild-world` | Rebuilds the `.nmd` bundle after asset changes |

Agent memory in `.claude/agent-memory/` persists findings across sessions — the game designer remembers its balance audits, the worldmaker remembers UI patterns it's tested, and the playtester remembers what it's explored.

## Roadmap

### Near Term
- [ ] Game balance pass — L4 equipment tier, T3 damage spells for Priest/Kai/Bard, class viability fixes ([#188](https://github.com/roomsmith-games/NeoMud/issues/188)-[#196](https://github.com/roomsmith-games/NeoMud/issues/196))
- [ ] Fill dead-end rooms with content (Forest Cave, Marsh Island, Gorge Alcove) ([#194](https://github.com/roomsmith-games/NeoMud/issues/194))
- [ ] BGM seamless looping ([#113](https://github.com/roomsmith-games/NeoMud/issues/113))
- [x] Replace raw JSON fields in maker editors with structured UI controls
- [x] Multiplatform UI test infrastructure — client tests run on Android, Desktop, and iOS

### Medium Term
- [ ] Quest system — kill quests, fetch quests, quest log, NPC dialogue trees
- [ ] Crafting system — use the crafting materials that currently drop with no purpose
- [ ] Boss encounters with special mechanics
- [ ] Multiplayer stress testing — concurrent combat, reconnection, edge cases

### Multiplatform Clients
- [x] Desktop (JVM) client — JavaFX audio, Ktor CIO networking, full feature parity ([#140](https://github.com/roomsmith-games/NeoMud/issues/140))
- [x] iOS client — Kotlin/Native + AVFoundation audio, Material Icons for cross-platform icon rendering ([#141](https://github.com/roomsmith-games/NeoMud/issues/141))
- [ ] Web (Wasm) client — zero-install browser play ([#142](https://github.com/roomsmith-games/NeoMud/issues/142))

### Future Vision
- [ ] Party system with shared XP and group combat
- [ ] PvP — dueling, arenas, or PvP zones
- [ ] World events — timed spawns, invasions
- [ ] Player guilds and social features

## The Spirit of the Thing

This isn't a finished game. It's a living sketch — a place to experiment with what a MUD looks like when you can see it, when the protocol is type-safe, when the world data lives in version control alongside the code, and when an AI can playtest its own creation.

If you played MUDs in the '90s, you'll recognize the bones. If you didn't, maybe this will show you what all the fuss was about.
