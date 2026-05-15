
# NeoMud — Project Instructions

A multiplayer dungeon game (MUD) inspired by '90s text MUDs like MajorMUD, built with Kotlin Multiplatform, Ktor, Jetpack Compose, and a React-based world editor.

## Repository Layout

```
NeoMud/
├── shared/     KMP module — models, protocol, shared between client and server
├── server/     Ktor 3.x + Netty — WebSocket game server, SQLite persistence
├── client/     Jetpack Compose — Android client with sprite rendering
├── maker/      React 18 + Express — web-based world editor (Vite dev server)
├── docs/       Screenshots and assets
└── scripts/    Utility scripts
```

## Build & Run

**Prerequisites**: JDK 21 (Corretto), Android SDK platform 34+, Node.js 18+

```bash
# Server
./gradlew :server:run          # Starts on :8080, WebSocket at /game, health at /health

# Client
./gradlew :client:installDebug  # Android emulator connects to 10.0.2.2:8080

# Maker
cd maker && npm install && npm run dev  # http://localhost:5173

# Tests
./gradlew :shared:jvmTest :server:test  # All server + shared tests
./gradlew :client:compileDebugKotlin    # Client compile check (no runtime tests yet)
```

**Gradle notes**: Configuration cache is enabled. `JAVA_HOME` must be set (configured in `settings.local.json` env).

**CRITICAL — World Bundle**: The server loads world data from `server/build/worlds/default-world.nmd`, built by `./gradlew packageWorld` from `maker/default_world_src/`. **Always run `./gradlew packageWorld --rerun-tasks`** after ANY change to `default_world_src/` (skills, items, zones, assets, etc.) and before running the server, running tests, or doing any verification. Gradle's UP-TO-DATE check for this task is unreliable — never trust it, always force with `--rerun-tasks`.

## Tooling Sanity (read this before debugging env)

- **If anything looks off** (PATH, MCP, "command not found", "playwright not loading"), run `./scripts/doctor.sh` first. It prints PASS/FAIL for every tool, env var, and MCP server. Do NOT start spelunking through `~/.claude/`, `~/.mcp.json`, etc. — they don't exist on this machine. The project-scoped `.mcp.json` (repo root) and `.claude/settings.local.json` are authoritative.
- **MCP servers**: declared in `.mcp.json` at the repo root. Auto-loaded because `enableAllProjectMcpServers: true` is in `.claude/settings.local.json`. To inspect runtime state: `claude mcp list`. After editing `.mcp.json`, the session must be restarted — there is no live reload.
- **Bash tool environment**: runs as a non-login non-interactive `bash`, but PATH is built from a shell snapshot that includes Homebrew, JAVA_HOME, and `~/.local/bin`. `npx`, `uvx`, `node`, `java`, `gradle` all resolve without absolute paths. Don't add absolute paths in scripts unless you actually need cross-shell portability.
- **Waiting on long-running scripts/CI**: use `Bash` with `run_in_background: true` plus the `Monitor` tool to stream output line-by-line — each new line wakes the model. Do **not** poll in a `sleep` loop, do not run `gh run watch` in the foreground (it eats the whole turn). `RemoteTrigger` spawns a *new* session and cannot resume the current one — don't try.
- **Secrets**: API keys live in `.claude/settings.local.json` env block. That file is gitignored (line 49 of `.gitignore`) and never tracked. Don't print key values to logs or copy them into committed files.

## Architecture Overview

### Protocol
- `shared/.../protocol/ClientMessage.kt` — all client-to-server messages (sealed class)
- `shared/.../protocol/ServerMessage.kt` — all server-to-client messages (sealed class)
- `shared/.../protocol/MessageSerializer.kt` — kotlinx.serialization with `classDiscriminator = "type"`
- Wire format is JSON over WebSocket. Both sides share the same Kotlin types at compile time.

### Server Core Loop
The server runs a **1.5-second tick-based game loop** (`GameLoop.kt`). Each tick:

1. **Grace period tick-down** — newly-arrived players get a brief combat grace window
2. **Cooldown tick-down** — per-skill cooldown counters decrement each tick
3. **Non-combat skill resolution** — meditate and track resolve before combat
4. **Combat resolution** (`CombatManager.processCombatTick()`) — for each player in attack mode, resolves in priority order:
   - Pending Bash → Pending Kick → Readied Spell (auto-cast) → Melee attack
5. **NPC behaviors** — wander, patrol, pursuit, attack (strategy pattern via `BehaviorNode`)
6. **Meditation regen** — MP restored for meditating players
7. **NPC spawning** — continuous spawn system per zone

### Command → Queue → Tick Pattern
**Commands are validation-only.** When a player sends a combat skill (bash, kick, spell, meditate, track), the corresponding command:
1. Validates prerequisites (cooldown, target exists, mana, etc.)
2. Queues the action on the session (`session.pendingSkill` or `session.readiedSpellId`)
3. Returns immediately — NO damage, NO kill handling, NO cooldown setting

The **game tick** resolves everything uniformly. This ensures:
- All kills flow through ONE handler in GameLoop (loot, XP, attack-mode-disable, broadcasts)
- No duplicate kill processing
- Consistent initiative ordering

### Key Server Types
- `PlayerSession` — per-connection state: player data, combat state, pending skill, readied spell, cooldowns, stealth, meditation
- `PendingSkill` — sealed class: `Bash(targetId)`, `Kick(targetId, direction)`, `Meditate`, `Track(targetId)`
- `CombatEvent` — sealed class: `Hit`, `NpcKilled`, `NpcKnockedBack`, `PlayerKilled`, `NpcPhaseShift`
- `CombatManager` — resolves combat actions each tick, broadcasts `SkillEffect`/`SpellEffect`/`CombatHit` to rooms
- `GameLoop` — orchestrates the tick, handles all `CombatEvent`s, manages NPC spawns and behaviors
- `CommandProcessor` — routes `ClientMessage` to command handlers
- `NpcManager` — NPC state, movement, spawning
- `WorldGraph` — in-memory room graph with BFS for minimap/nearby rooms
- `GameConfig` — all tuning constants (damage, cooldowns, thresholds, etc.)

### Data-Driven Design
Everything is JSON-defined, loaded into catalogs at startup:
- `ClassCatalog` — 15 character classes with stat minimums, allowed skills/spells
- `RaceCatalog` — 6 races with stat modifiers and XP scaling
- `ItemCatalog` — weapons, armor, consumables, crafting materials
- `SpellCatalog` — 20 spells across 5 schools (damage, heal, buff, DoT, HoT)
- `SkillCatalog` — 12 skills (active and passive)
- `LootTableCatalog` — drop tables per NPC type
- Zone JSON files — rooms, exits, coordinates, backgrounds

### Interactable System
Room interactables are JSON-defined features players can interact with (levers, traps, gates, puzzles). Each has an `actionType` that determines behavior:

- `EXIT_OPEN` — unlocks a locked exit direction
- `ROOM_EFFECT` — applies an effect to the room (heal, damage, buff)
- `TELEPORT` — moves player to a target room
- `DAMAGE_TRAP` — deals damage on entry (ON_ENTER trigger) with optional save check
- `PUZZLE_STEP` — increments a player flag key toward a target value, opening an exit on completion. Supports optional `requiredFlagKey` prerequisite gate and `completionFlagKey` per-player completion tracking.
- `PLACE_ITEM` — prompts player to place an inventory item (two-phase: server sends `PlaceItemPrompt`, client replies with `PlaceItem`). Supports optional `completionFlagKey`.
- `RIDDLE_PROMPT` — presents a riddle dialog (two-phase: server sends `RiddlePrompt`, client replies with `RiddleAnswer`). Supports optional `completionFlagKey`.
- `CONDITIONAL_TRIGGER` — evaluates a condition before opening a gated exit

**PUZZLE_STEP actionData schema:**
```json
{
  "puzzleGroupId": "tide_bells",
  "puzzleStepIndex": "0",
  "puzzleTotalSteps": "5",
  "advanceMessage": "Progress...",
  "successMessage": "Puzzle solved!",
  "resetMessage": "Wrong order.",
  "alreadySolvedMessage": "Already done.",
  "successDirection": "NORTH",
  "requiredFlagKey": "mara_hymn_given",      // optional — gate: player must have this flag to attempt
  "requiredFlagValue": "1",                   // optional — defaults to "true"
  "requiredFlagMessage": "You can't do this yet.", // optional — shown when gate fails
  "completionFlagKey": "puzzle:bells:complete",   // optional — set on solving player (per-party change point)
  "completionFlagValue": "true"                   // optional — defaults to "true"
}
```

**CONDITIONAL_TRIGGER actionData schema:**
```json
{
  "conditionType": "ITEM" | "FLAG" | "LEVEL",
  "requiredItemId": "item:storm_key",       // ITEM only
  "requiredFlagKey": "puzzle:storm:step",    // FLAG only
  "requiredFlagValue": "5",                  // FLAG only
  "requiredLevel": "25",                     // LEVEL only
  "successDirection": "NORTH",
  "successMessage": "The gate opens.",
  "failureMessage": "You lack what is needed."
}
```

Interactables are stored in zone JSON files as `interactables` arrays on rooms. The server validates `actionType` against the allowlist in `WorldLoader.kt`. `InteractCommand.kt` dispatches to type-specific executor methods. Two-phase actions (PLACE_ITEM, RIDDLE_PROMPT) return early from execute(); self-managed actions (PUZZLE_STEP, CONDITIONAL_TRIGGER) call `markInteractableUsed` internally and return false.

### Boss Phase System
NPCs can define phase transitions that trigger at HP thresholds during combat. When an NPC's HP drops below a phase threshold, the system clamps HP (preventing burst-skip), applies stat overrides, swaps the sprite, and broadcasts a dramatic transition message.

**Zone JSON schema (on NPC definitions):**
```json
{
  "phases": [
    {
      "name": "Lightning Form",
      "hpThresholdPercent": 0.5,
      "spriteId": "npc:stormcrown_phase2",
      "damage": 85,
      "accuracy": 65,
      "defense": 40,
      "evasion": 20,
      "transitionMessage": "The Stormcrown's body cracks with lightning!",
      "transitionSound": "boss_phase_shift"
    }
  ]
}
```

**Key design decisions:**
- **HP clamped to threshold** — `max(thresholdHp, 1)` prevents one-shot kills from skipping phases
- **MaxHp stays constant** — no "new HP bar" complexity; phases modify combat stats only
- **Stat overrides are nullable** — only specified stats change; `null` keeps the base value
- **Single checkpoint method** — `CombatManager.checkPhaseTransition()` called at all 5 damage sites (melee, spell, bash, kick, guard combat), between damage application and kill check
- **Client sprite swap** — `Npc.spriteOverride` field; `SpriteOverlay.kt` checks it before falling back to `npc.id` for the sprite URL
- **Server message** — `ServerMessage.NpcPhaseShift` broadcasts to room with npcId, phaseName, spriteId, HP, message, and sound

**Files:**
- `server/.../world/Zone.kt` — `BossPhaseData` data class, `NpcData.phases`
- `server/.../npc/NpcManager.kt` — `NpcState` has mutable `damage`/`accuracy`/`defense`/`evasion`, plus `phases`/`currentPhase`/`spriteOverride`
- `server/.../combat/CombatManager.kt` — `checkPhaseTransition()` + `CombatEvent.NpcPhaseShift`
- `server/.../game/GameLoop.kt` — broadcasts `ServerMessage.NpcPhaseShift` on phase shift events
- `shared/.../protocol/ServerMessage.kt` — `NpcPhaseShift` message type
- `shared/.../model/Npc.kt` — `spriteOverride: String = ""`
- `client/.../viewmodel/GameViewModel.kt` — handles phase shift (log, sound, sprite update)
- `client/.../components/SpriteOverlay.kt` — resolves `spriteOverride` for sprite URL
- `maker/prisma/schema.prisma` — `phases String @default("[]")` on Npc model
- `maker/src/pages/NpcEditor.tsx` — Boss Phases UI section (visible when hostile)

### Observability (Sentry)
- **Game server (Kotlin)**: `io.sentry:sentry` initialized in `server/src/main/kotlin/com/neomud/server/Application.kt` (gated on `SENTRY_DSN` env). DSN forwarded into each game container by the orchestrator on the platform side — game server doesn't read it from any local config.
- **WASM client**: Sentry CDN loader script tag in `client/src/wasmJsMain/resources/index.html` — DSN baked into the CDN URL, rotation requires a code edit + redeploy.
- Both surfaces report into the `roomsmith-games` Sentry org. Tracing sample rate is `0.2` to match the platform-side SDKs (errors are 100%). Don't add new Sentry integrations that capture PII without a `beforeSend` scrubber.
- See NeoMud-platform `CLAUDE.md` §Observability and `project_sentry_observability.md` (auto-memory) for the full architecture.

### Persistence
- SQLite + Exposed ORM
- Tables: `PlayersTable`, `InventoryTable`, `PlayerCoinsTable`, `PlayerDiscoveryTable`
- Repositories: `PlayerRepository`, `InventoryRepository`, `CoinRepository`, `DiscoveryRepository`
- Auth is passwordless — character name is the sole identity. Legacy password column retained (new registrations store a random UUID hash) for backward compatibility with old clients. Platform users authenticate via JWT on a separate code path.
- Discovery system tracks visited rooms, hidden/locked exits, interactables, and tutorials per player

### Admin Model

Admin promotion has two paths, both checked at every login (composable):

1. **Character name allowlist** — `NEOMUD_ADMINS` env var (or `--admins` CLI flag, or `adminUsernamesOverride` test param) is a comma-separated list of character names (case-insensitive). A character whose name matches is promoted to admin. The check also matches against the internal DB username for backward compatibility with legacy password-based accounts. **This is the local-dev / OSS path** — Gradle's `:server:run` injects `NEOMUD_ADMINS=bob` by default so a fresh `git clone` boot has a character named "Bob" as admin out of the box.
2. **World-owner platform JWT** — `WORLD_OWNER_PLATFORM_USER_ID` env var (or `worldOwnerPlatformUserIdOverride` test param) holds the platform `userId` of the world's owner. The platform user whose verified JWT `userId` claim matches gets admin in this world only. **This is the platform-marketplace path** — the orchestrator injects this per-container from `World.ownerUserId`.

Both env vars are optional and additive. Either being unset is silent (no warn log spam, no startup error). The code-path enforces a strict null guard on `worldOwnerPlatformUserId != null && session.platformUserId != null` to avoid the `null == null` trap that would otherwise grant admin to unauthenticated connections in a misconfigured setup. If `WORLD_OWNER_PLATFORM_USER_ID` is set but `PLATFORM_JWKS_URL`/`PLATFORM_JWT_SECRET` are not, the server emits a single startup warn (the world-owner path is inert without a verifier — pure misconfig signal).

Test fixtures calling `Application.module(jdbcUrl = …)` continue to work without supplying any override — both paths default to "no admin". See `AdminCommandTest` (username path), `AdminOwnershipTest` (world-owner path), `SecurityHardeningTest` (characterName path), `BootSmokeTest` (zero-override boot contract).

### Tutorial System
- `TutorialService` (`server/.../game/TutorialService.kt`) — centralized service with all tutorial definitions and `trySend()` for dedup + persist + send
- One-time tutorials tracked via `PlayerDiscoveryTable` with type `"tutorial"` and a key (e.g., `"welcome"`, `"tut_hostile_npc"`)
- `ServerMessage.Tutorial(key, title, content, blocking, targetElement?)` — `blocking=true` renders as modal dialog, `blocking=false` renders as auto-fading coach mark toast
- Tutorial state loaded from DB on login (`session.seenTutorials`), persisted via `DiscoveryRepository.markTutorialSeen()`
- Triggers wired into: `GameLoop` (combat, death, effects), `CommandProcessor` (welcome), `MoveCommand` (room-enter: hostiles, vendors, crafters, zones), `TrainerCommand` (CP spend)
- Client: `TutorialModal` (blocking stone-themed dialog), `CoachMarkOverlay` (passive fade-in/out banner, 6s), `coachMarkTarget()` Modifier for UI element targeting
- **Auth transition**: `Tutorial` messages arrive before `GameViewModel` is mounted. `AuthViewModel` captures them in `initialTutorial` and passes them through via `NeoMudApp.kt` → `GameViewModel.setInitialTutorial()`. Any new message types sent during the post-login sequence must follow this same pattern.
- Help panel ("Adventurer's Tome") with 10 accordion sections accessible via toolbar icon — static client-side content

### Client
- Jetpack Compose + Material 3
- Ktor OkHttp WebSocket client
- Room rendering: background art, NPC/item sprites, floating minimap with fog-of-war
- On connect: server sends `ClassCatalogSync` + `ItemCatalogSync`; on login: `LoginOk` → `Tutorial` (first login only) → `RoomInfo` → `MapData` → `InventoryUpdate` → `RoomItemsUpdate`

### Maker (World Editor)
- React 18 frontend + Express API backend
- Prisma ORM with SQLite
- Visual zone editor with drag-and-drop room placement
- Full CRUD for all entity types
- Import/export `.nmd` bundles (ZIP archives with zone data + catalogs + assets)
- Vite dev server on port 5173

## Testing Policy

**Every new feature or significant bugfix MUST include relevant tests — no exceptions, no need to ask.** If tests are forgotten, prompt the user before moving on.

### Test Locations
- **Shared**: `shared/src/commonTest/kotlin/com/neomud/shared/` — protocol serialization, model tests
- **Server**: `server/src/test/kotlin/com/neomud/server/` — unit tests, integration tests, catalog tests, combat tests

### Running Tests
```bash
./gradlew :shared:jvmTest :server:test
```

### Test Principles
- Test behavioral contracts, not trivial getters/setters
- Prefer focused tests that verify one thing
- Use `createTestSession()` helper for PlayerSession tests (mock WebSocketSession)
- `TestWorldSource` provides test fixtures for integration tests

## Conventions

### No Magic Numbers
- **All tuning constants belong in `GameConfig`** (or equivalent config object) — never hardcode numeric values inline in game logic or tests
- Tests must reference `GameConfig` constants (e.g., `GameConfig.Combat.DODGE_MAX_CHANCE`) rather than duplicating raw values
- If a hardcoded constant is truly necessary, it requires a comment explaining why and must be flagged for review
- When you encounter an existing magic number that should be in config, flag it to the user before proceeding

### Kotlin
- kotlinx.serialization for all wire types — `@Serializable`, `@SerialName("snake_case")`
- Sealed classes for protocol messages and internal events
- `object` for singletons and constants (`GameConfig`)
- Constructor injection — no DI framework, wired manually in `Application.kt`

### Naming
- Server messages use `snake_case` SerialNames: `combat_hit`, `spell_effect`, `skill_effect`, `npc_killed`
- Client messages use `snake_case` SerialNames: `move`, `attack`, `cast_spell`, `ready_spell`, `use_skill`
- Kotlin code uses standard `camelCase` for properties and functions
- Room IDs follow `zone:room` format (e.g., `millhaven:town_square`)

### Broadcasts
- Combat actions (bash, kick) broadcast `SkillEffect` to all players in the room — dedicated flavor text, NOT flags on `CombatHit`
- Spells broadcast `SpellEffect` to all players in the room
- Melee attacks broadcast `CombatHit` to all players in the room
- Room presence changes broadcast `NpcEntered`/`NpcLeft`/`PlayerEntered`/`PlayerLeft`
- Boss phase transitions broadcast `NpcPhaseShift` to all players in the room

### Server-Authoritative State
- **All game state is server-authoritative** — the client is a thin renderer
- Stealth/backstab: `session.isHidden` set only by server-side `SneakCommand`, checked by `CombatManager`
- Combat: damage, kills, loot, XP all resolved server-side in the game tick
- The client never determines whether an attack is a backstab, whether a skill succeeds, or what damage is dealt

## Asset Image Pipeline

**All sprite images (NPCs, items, coins, players) MUST go through background removal after generation.** AI image models cannot produce true alpha transparency — they render a visual checkerboard or white background that must be post-processed.

**Prerequisite**: `uv`/`uvx` must be installed — the script calls `rembg` (birefnet-general model) via `uvx` subprocess.

### Pipeline Steps (mandatory for every sprite generation)

1. **Generate** via nano-banana MCP (`generate_image`), using the `imagePrompt` + `imageStyle` + `imageNegativePrompt` from the relevant JSON data file. nano-banana renders at 1024×1024 regardless of any size hint — do NOT skip the resize step in #4.
2. **Convert** from PNG to WebP: `npx sharp-cli -i input.png -o output.webp --format webp`
3. **Remove background**: `node scripts/remove-bg.mjs output.webp`
4. **Resize to spec dimensions** (see table below) — `node scripts/slim-assets.mjs --category <coins|items|...|all>` resizes any oversized files in place. Skipping this step regenerates the bloat that Phase 7Q-H eliminated (default-world.nmd grew to 113MB largely because the resize step was historically missing).
5. **Verify** the result visually — check that the subject is intact
6. **Clean up** intermediate PNGs and the `nanobanana-output/` directory

### Batch background removal
```bash
node scripts/remove-bg.mjs --batch maker/default_world_src/assets/images/npcs
node scripts/remove-bg.mjs --batch maker/default_world_src/assets/images/items
node scripts/remove-bg.mjs --batch maker/default_world_src/assets/images/coins
node scripts/remove-bg.mjs --batch maker/default_world_src/assets/images/players
```

Use `--suffix nobg` for non-destructive output (e.g., `npc_rat_nobg.webp` alongside the original).

### Asset naming conventions and directories

| Asset Type | Directory | Filename Pattern | Dimensions |
|---|---|---|---|
| NPC sprites (humanoid) | `images/npcs/` | `npc_{npc_id}.webp` | 384×384 |
| NPC sprites (creature) | `images/npcs/` | `npc_{npc_id}.webp` | 512×384 |
| Item sprites | `images/items/` | `item_{item_id}.webp` | 256×256 |
| Coin sprites | `images/coins/` | `coin_{denomination}.webp` | 256×256 |
| Player sprites | `images/players/` | `{race}_{gender}_{class}.webp` | 384×384 |
| Room backgrounds | `images/rooms/` | `{zone}_{room}.webp` | 1024×576 |

All paths are relative to `maker/default_world_src/assets/`. The `npc_id` and `item_id` strip the `npc:` / `item:` prefix (e.g., `npc:guildmaster` → `npc_guildmaster.webp`).

### Image prompts

Image generation prompts are stored in the data files:
- **NPCs**: `imagePrompt`, `imageStyle`, `imageNegativePrompt`, `imageWidth`, `imageHeight` fields in zone JSON files
- **Items**: `imagePrompt`, `imageStyle`, `imageNegativePrompt` fields in `items.json` (dimensions default to 256×256)
- **Players**: `imagePrompt`, `imageStyle`, `imageNegativePrompt`, `imageWidth`, `imageHeight` fields in `pc_sprites.json`
- **Rooms**: `imagePrompt`, `imageStyle`, `imageNegativePrompt`, `imageWidth`, `imageHeight` fields in zone JSON files

### Important notes
- **Room backgrounds do NOT need background removal** — they are full-bleed images, not sprites
- The script uses ML segmentation (rembg/birefnet-general) — it handles any background color including dark, gradient, and checkerboard
- No special prompt requirements for background color — the ML model segments the foreground regardless
- `nanobanana-output/` is gitignored — always clean it up after generation
- **Sprite resize uses `fit:'contain'` + transparent background, NOT `fit:'inside'` or `fit:'cover'`.** The renderer (`SpriteOverlay.kt`) draws WebPs at their natural on-disk aspect via `ContentScale.Fit`, ignoring declared `imageWidth`/`imageHeight`. So a 384×209 sprite renders 1.83× wider than a 384×384 one and overflows its slot. `fit:'inside'` (the historical bug) fits-without-padding → off-spec dims. `fit:'cover'` would crop the rembg-trimmed subject. `fit:'contain'` pads with transparent margins → exact spec dims, subject intact. Backgrounds use `fit:'inside'` because they're full-bleed and pillarboxing would be visible.

## Asset SFX Pipeline

**Sound effects are generated via ElevenLabs AI** using the `text_to_sound_effects` MCP tool or the maker's `/generate/sound` backend endpoint.

### Generation Methods

1. **MCP Tool (Claude Code direct)**: Use `mcp__elevenlabs-sfx__text_to_sound_effects` — saves MP3 to configured base path, needs conversion to OGG
2. **Maker Backend (recommended)**: `POST http://localhost:5173/api/generate/sound` with `{prompt, duration, assetPath}` — outputs OGG directly (requires maker dev server running)

### SFX Conventions

- **Format**: MP3 (`.mp3`), 0.5–3 seconds for SFX, 30–120 seconds for BGM
- **BGM bitrate**: 96kbps stereo (44.1kHz). ElevenLabs ships at 128kbps; `scripts/slim-assets.mjs --category audio` re-encodes to 96k stereo. Don't go below 96k or below stereo without checking with the user — spatial cues in tracks like `gorge_danger` and `town_peaceful` matter.
- **Directory structure**:
  ```
  maker/default_world_src/assets/audio/
  ├── bgm/        Background music
  ├── npcs/       NPC attack, miss, death, interact, exit sounds
  ├── items/      Weapon attack/miss, item use sounds
  ├── spells/     Spell cast, impact, miss sounds
  ├── rooms/      Room depart sounds, room effect sounds
  └── general/    Shared sounds: dodge, parry, backstab, miss, loot_drop, coin_pickup, item_pickup, enemy_death
  ```
- **Sound IDs**: Referenced by filename without extension or path prefix. The client derives the subdirectory from context (e.g., NPC sounds → `audio/npcs/`, item sounds → `audio/items/`)

### Where Sounds Are Referenced

| Entity Type | JSON Fields | Location |
|---|---|---|
| NPC | `attackSound`, `missSound`, `deathSound`, `interactSound` | Zone JSON files |
| Item | `attackSound`, `missSound`, `useSound` | `items.json` |
| Spell | `castSound`, `impactSound`, `missSound` | `spells.json` |
| Room | `departSound` | Zone JSON files |
| Room Effect | `sound` | Zone JSON files |
| Zone | `bgm` | Zone JSON files |

### After generation
- Run `cd maker && npm run rebuild-world` if the Vite server isn't running
- Run `./gradlew packageWorld` to rebuild the `.nmd` bundle

## Maintenance Scripts

`scripts/` holds standalone Node scripts with their own deps in `scripts/package.json` (separate from maker/server runtime). Run from repo root with `node scripts/<name>.mjs`.

**Prerequisites** (one-time setup): `cd scripts && npm install` for `sharp`, plus `brew install ffmpeg` for the audio re-encode path of `slim-assets.mjs`. The `scripts/node_modules/` directory is gitignored.

- **`slim-assets.mjs`** — resize/re-encode `default_world_src/assets/` files to spec dimensions (per the Asset Image Pipeline table) and BGM to 96kbps stereo. Idempotent: skips files already at-or-below target dims. Atomic writes via tmp-then-rename. Flags: `--category {coins|items|rooms|npcs|players|audio|all}`, `--dry-run`, `--verbose`, `--update-zones` (rooms only — rewrites `.jpg` zone JSON refs to `.webp`), `--mono` (audio only — downmix to mono 96kbps). Always `--dry-run` first to preview byte deltas. Run any time the bundle grows unexpectedly.
- **`remove-bg.mjs`** — strip backgrounds from generated sprite WebPs via rembg (ML segmentation) or `--white` flood-fill mode for solid-white backgrounds.
- **`doctor.sh`** — environment health check (PATH, MCP servers, JAVA_HOME). Run when anything looks off; never spelunk through `~/.claude/` or `~/.mcp.json` directly.

## Maker Dev Server Gotchas

- **ALWAYS kill stale Vite dev server before rebuilding/testing maker UI changes**
  - Check: `lsof -ti:5173`
  - Kill: `lsof -ti:5173 | xargs kill -9` (no-op if nothing listening)
- **After any change to `default_world_src/` or `prisma/schema.prisma`**: kill Vite first, then `cd maker && npm run rebuild-world`
- SQLite DB will be EBUSY if Vite is running during rebuild

## Environment Notes

- macOS Darwin (Apple Silicon), bash 5+ as default shell — use Unix shell syntax
- Homebrew at `/opt/homebrew/bin` is first on PATH; `node`, `npm`, `npx`, `uv`, `uvx`, `jq` all installed there
- `JAVA_HOME` is set in `.claude/settings.local.json` env (Corretto 21) and also exported by `~/.bashrc`
- `.nmd` bundles are ZIP files — inspect with `unzip -l file.nmd`
- `packageWorld` Gradle task builds `default_world.nmd` from `maker/default_world_src/` into `server/build/worlds/default-world.nmd`