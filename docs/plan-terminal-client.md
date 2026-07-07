# Terminal Client Plan: Telnet Server + Relay Interactive Mode

## Context

Issue #133 proposes adding a native telnet/TCP interface so MUD clients (Mudlet, TinTin++, raw telnet) can connect directly to NeoMud. The relay comment thread (May 2026) also deferred two relay features here: `--interactive` stdin/stdout mode and `--session-id` namespace isolation.

This plan covers both tracks and updates the issue with gaps identified from the actual codebase state:

- 70+ `ServerMessage` variants vs. the ~40 the issue accounts for
- Missing commands in the issue's command table (`drop`, `who`, `tell`, all party/follow/crafting commands)
- Authentication strategy (not resolved in issue)
- Port choice
- Specific MUD conventions and VTT compatibility requirements
- GMCP package specification for Mudlet

**What this plan is NOT covering**: The full split-pane TUI client (Ink/Blessed/Ratatui) described in `terminal-client-vision.md` — that's a separate track. The relay interactive mode here is classic scrolling MUD text, not a multi-panel TUI.

---

## Key Decisions

### Authentication: Login-only from telnet
No guest sessions, no registration wizard from telnet. Web registration is required. Reasons:
- Registration requires race/class/stat selection with visual feedback; interactive text wizard is significantly more complex
- Password handling over raw TCP is lower security than HTTPS registration
- Consistent with most modern MUDs (SKOTOS, Armageddon, etc. — register on web, connect by telnet)
- Defers the registration wizard to a future pass if there's demand

Telnet login flow: `username` prompt → `password` prompt (echo suppressed) → character select if account has multiple characters → `LoginOk` → room display.

### Port: 4000
Issue proposes 2323. Recommendation: **4000** — traditional MUD port (LPMud, DikuMUD, CircleMUD defaults), universally known by MUD client users, doesn't require root (>1024). Document it as `TELNET_PORT=4000`.

### Text mode: line-buffered
Negotiate `DO SUPPRESS-GO-AHEAD` + `WONT ECHO` → client handles local line editing and sends complete lines. This is the universal MUD convention. Avoid character-at-a-time mode; it's unnecessary complexity and breaks arrow-key editing in most clients.

### GMCP: implement as Phase 6 (not Phase 5)
GMCP is what makes Mudlet's mapper and health bars work. It's high-value but self-contained enough to defer until the core is stable. Phase 5 covers the minimum: NAWS + help + welcome banner.

---

## Protocol Coverage Gaps (vs. Issue #133)

### Missing ServerMessage renderers

The issue lists 40 messages. The actual protocol has 70+. These are missing from the issue's rendering table and must be added to `TextRenderer.kt`:

**Party system:**
- `PartyInviteReceived` → `"** {name} invites you to join their party. [accept/decline]"`
- `PartyFormed` → `"** Party formed with {members}."`
- `PartyMemberJoined` → `"** {name} joins the party."`
- `PartyMemberLeft` → `"** {name} leaves the party ({reason})."`
- `PartyDisbanded` → `"** The party has disbanded ({reason})."`
- `PartyMemberUpdate` → update party HP display in prompt (name cache only; full display on `party` command)
- `PartyChatMessage` → `"[Party] {name}: {msg}"` in cyan
- `PartyInfo` → formatted party listing
- `PartyLeaderChanged` → `"** {name} is now party leader."`

**Follow system:**
- `FollowUpdate` → `"** {follower} is now following {target}."` / `"** {follower} stops following {target}."`
- `RallyPing` → `"** {leader} calls a rally to {roomName}, {zoneName}!"`
- `FollowFailed` → `"** Could not follow: {reason}."`

**Boss mechanics:**
- `NpcPhaseShift` → `"*** {npcName} enters {phaseName}! ***"` + HP bar, prominent formatting (bold red)
- `NpcAbilityEffect` → render each `AbilityHitResult` hit line, save/resist outcomes

**Interactive prompts (modal input states):**
- `PlaceItemPrompt` → `"[{label}] {prompt} (type item name or 'cancel')"` — needs parser modal state
- `RiddlePrompt` → `"[{label}] {question}"` + optional hint display — needs `answer <text>` command
- `ChoicePrompt` → numbered menu `"[{label}] {question}\n  1. {option1}\n  2. {option2}"` — needs `choose <n>` command

**Crafting:**
- `CraftingMenu` → numbered recipe list with costs (mirrors VendorInfo format)
- `CraftResult` → `"{msg}"` in green/red

**Communication:**
- `TellReceived` → `"{name} tells you: '{message}'"` in yellow
- `TellSent` → `"You tell {name}: '{message}'"` in yellow
- `WhoList` → formatted player list with class/level/zone

**Session events:**
- `SessionDisplaced` → `"*** Your session has been displaced: {reason}. Disconnecting. ***"` then close
- `SessionConflict` → `"*** Another session for {characterName} is active. Use 'force' to displace it. ***"`

**Room state:**
- `RoomItemsUpdate` → used by NameResolver to update pickupable items list; no visible output unless items changed (optional: "The {item} disappears." / "A {item} appears.")

**Atlas:**
- `AtlasData` → zone-level ASCII overview, render on `atlas` command (cached; not auto-displayed)

**Handshake:**
- `ServerHello` → silent, cache `worldName`/`engineVersion` for display in banner
- `ConnectionRejected` → `"Connection refused: {reason}. Update URL: {updateUrl}"` then close

**Misc:**
- `NpcDialogue` → `'{npcName} says: "{content}"'`
- `Tutorial` → suppress entirely for telnet (no toast UI)
- `ActiveEffectsUpdate` → render on `effects` command (cached state); interrupt output only for `EffectTick`

### Missing commands (vs. issue's command table)

The issue's command table is missing:

| Command | Aliases | ClientMessage | Notes |
|---|---|---|---|
| `drop <item> [qty]` | | `DropItem(itemId, qty)` | Core MUD verb |
| `who` | | `RequestWho` (⚠️ see below) | Show online players |
| `tell <player> <msg>` | `t` | `Tell` (⚠️ see below) | Private message |
| `party invite <name>` | | `PartyInvite(targetName)` | |
| `party accept <name>` | | `PartyAccept(inviterName)` | |
| `party decline <name>` | | `PartyDecline(inviterName)` | |
| `party leave` | | `PartyLeave` | |
| `party kick <name>` | | `PartyKick(targetName)` | |
| `party say <msg>` | `ps`, `;` | `PartySay(message)` | |
| `party info` | `party` | local render cached `PartyInfo` | |
| `follow <name>` | | `Follow(targetName)` | |
| `unfollow` | `stopfollow` | `FollowStop` | |
| `rally` | | `Rally` | |
| `crafting` | `craft`, `recipes` | `InteractCrafter` | |
| `craft <recipe>` | | `CraftItem(recipeId)` | |
| `npc <name>` | `talk` | `InteractNpc(npcId)` | Talk to NPC |
| `answer <text>` | | `AnswerRiddle(featureId, text)` | Modal — only valid after `RiddlePrompt` |
| `choose <n>` | | `MakeChoice(featureId, choiceId)` | Modal — only valid after `ChoicePrompt` |
| `place <item>` | | `PlaceItem(featureId, itemId)` | Modal — only valid after `PlaceItemPrompt` |
| `atlas` | `world` | local render cached `AtlasData` | |
| `effects` | `buffs` | local render cached `ActiveEffectsUpdate` | |
| `hp` | `health` | local render player HP (from cache) | Common MUD alias |
| `get all` | `take all` | `PickupItem` for each room item | Iterate via NameResolver |
| `cancel` | | clear modal input state | Cancel riddle/choice/place prompt |
| `help` | `?` | local — render full command reference | First thing every new player types; implement in Phase 3, not deferred to polish |
| `help <command>` | | local — per-command description + aliases + example | Essential for discoverability |

⚠️ **Protocol gap — verify before implementation:**
- `Tell` (player-to-player DM): `TellReceived`/`TellSent` ServerMessages exist but no `Tell` ClientMessage in the protocol. Either it exists under a different name, or it needs to be added to shared/protocol.
- `RequestWho`: `WhoList` ServerMessage exists but no corresponding ClientMessage found. Verify or add.
- Check `CommandProcessor.kt` for how these are currently dispatched from the web client before assuming they need new ClientMessages.

---

## Phase 0: Relay Interactive Mode (1–2 days)

**Goal:** Quick win. Classic scrolling MUD text via `node scripts/game-relay.mjs --interactive`. Validates the text renderer logic in JS before the Kotlin build. Immediately useful for dev play and QA automation.

The relay already handles all ServerMessage types in its state machine. This phase adds rendering (state → text output) and a readline input loop.

### `scripts/game-relay.mjs` changes

**`--interactive` flag behavior:**
1. Start readline on stdin
2. On each input line: parse command text → ClientMessage (same table as Phase 3 below) → write to relay-command file (existing dispatch path)
3. On each ServerMessage: render to stdout via text renderer

**`--session-id <id>` flag:**
Namespace all relay files: `relay-state-<id>.json`, `relay-command-<id>.json`. Already partially designed (relay comment May 2026). This enables concurrent AI/QA sessions.

**Text renderer (JS, mirrors Phase 2 Kotlin):**
- Room display: name/description/exits/NPCs/players/ground items
- Combat: hit/miss/dodge/parry lines + inline HP bar via ASCII `[████░░]`
- Prompt: `<HP:{cur}/{max} MP:{cur}/{max}> `
- Chat: `{name} says: "{msg}"`
- System/error: prefix `** ` / `!! `
- Inventory: formatted list
- Everything else: one-line summary

**Output format:** Scrolling text, no escape codes by default. `--color` flag enables ANSI. This keeps it clean for piping and log files.

### Files modified
- `scripts/game-relay.mjs` — `--interactive`, `--session-id`, `--color` flags
- `scripts/game-relay.mjs` — text renderer module (inline or extracted to `scripts/mud-renderer.mjs`)

### Verify
- `node scripts/game-relay.mjs --interactive` connects, plays a session, quits cleanly
- `node scripts/game-relay.mjs --interactive --session-id abc` and `--session-id def` run concurrently without file collisions

---

## Phase 1: Transport Abstraction (1–2 days)

**Goal:** Decouple `PlayerSession` from `WebSocketSession`. Zero behavior change. All existing tests must pass.

### New files

**`server/src/main/kotlin/com/neomud/server/session/TransportSession.kt`**
```kotlin
interface TransportSession {
    suspend fun sendMessage(message: ServerMessage)
    suspend fun close(reason: String = "")
}
```

**`server/src/main/kotlin/com/neomud/server/session/WebSocketTransport.kt`**
```kotlin
class WebSocketTransport(private val ws: WebSocketSession) : TransportSession {
    override suspend fun sendMessage(message: ServerMessage) {
        ws.send(Frame.Text(MessageSerializer.encodeServerMessage(message)))
    }
    override suspend fun close(reason: String) {
        ws.close(CloseReason(CloseReason.Codes.NORMAL, reason))
    }
}
```

### Modified files

**`PlayerSession.kt`** — constructor changes from:
```kotlin
class PlayerSession(val webSocketSession: WebSocketSession)
```
to:
```kotlin
class PlayerSession(val transport: TransportSession)
```
All `webSocketSession.send(Frame.Text(...))` calls → `transport.sendMessage(...)`.

**`Routing.kt`** — wrap before passing:
```kotlin
val session = PlayerSession(WebSocketTransport(this))
```

**Test helpers** — `createTestSession()` becomes dramatically simpler:
```kotlin
fun createTestSession(): Pair<PlayerSession, MutableList<ServerMessage>> {
    val received = mutableListOf<ServerMessage>()
    val transport = object : TransportSession {
        override suspend fun sendMessage(message: ServerMessage) { received.add(message) }
        override suspend fun close(reason: String) {}
    }
    return PlayerSession(transport) to received
}
```
No more mocking 7 `WebSocketSession` interface members. Update all test files.

### Verify
All existing unit and integration tests pass. No functional change.

---

## Phase 2: ANSI Text Renderer (4–5 days)

**Goal:** `ServerMessage` → ANSI-formatted string(s). Complete coverage of all 70+ variants.

### Files

**`server/src/main/kotlin/com/neomud/server/telnet/AnsiColors.kt`**
```kotlin
object Ansi {
    const val RESET = "[0m"
    const val BOLD = "[1m"
    const val RED = "[31m"; const val BOLD_RED = "[1;31m"
    const val GREEN = "[32m"; const val BOLD_GREEN = "[1;32m"
    const val YELLOW = "[33m"; const val BOLD_YELLOW = "[1;33m"
    const val CYAN = "[36m"; const val BOLD_CYAN = "[1;36m"
    const val WHITE = "[37m"; const val BOLD_WHITE = "[1;37m"
    const val GRAY = "[90m"
    const val BLUE = "[34m"
    const val MAGENTA = "[35m"

    fun hpBar(current: Int, max: Int, width: Int = 10): String {
        val filled = ((current.toFloat() / max) * width).toInt().coerceIn(0, width)
        val color = when {
            current.toFloat() / max > 0.5f -> GREEN
            current.toFloat() / max > 0.25f -> YELLOW
            else -> RED
        }
        return "$color[${"█".repeat(filled)}${"░".repeat(width - filled)}]$RESET"
    }
}
```

**`server/src/main/kotlin/com/neomud/server/telnet/TextRenderer.kt`**

`TextRenderer` is a pure function `render(message: ServerMessage, state: TelnetSessionState): List<String>`. Returns a list of lines (without `\r\n` — the transport layer adds those). Returns empty list for silent messages (catalog syncs, Pong, Tutorial).

Complete rendering spec (additions/corrections to issue's table):

| ServerMessage | Output | Color |
|---|---|---|
| LoginOk | `"Welcome back, {name}! [{race} {class} Lv.{level}]"` | Cyan bold |
| PlatformAuthOk | Same as LoginOk (using characterName) | Cyan bold |
| AuthError | `"Error: {reason}"` | Red |
| SessionConflict | `"A session for {name} is already active. Login with 'force' to displace it."` | Yellow |
| SessionDisplaced | `"*** Session displaced: {reason}. Goodbye. ***"` | Red bold |
| RoomInfo | Full room display (box header + desc + exits + NPCs + players + ground items) | Mixed |
| MoveOk | `"You head {direction}."` + room display | White + mixed |
| MoveError | `"{reason}"` | Yellow |
| MapData | ASCII minimap (see AsciiMap.kt) | Cyan |
| AtlasData | Zone overview grid (render on demand, not auto-displayed) | Cyan |
| PlayerEntered | `"{name} has arrived."` | Cyan |
| PlayerLeft | `"{name} heads {direction}."` / `"{name} has left."` | Cyan |
| NpcEntered | `"{name} has arrived."` | Yellow |
| NpcLeft | `"{name} heads {direction}."` | Yellow |
| PlayerSays | `'{name} says: "{msg}"'` | Bold white |
| TellReceived | `"{name} tells you: '{msg}'"` | Yellow |
| TellSent | `"You tell {name}: '{msg}'"` | Yellow |
| WhoList | Table: name, class, level, zone — right-pad for alignment | White |
| NpcDialogue | `'{npcName} says: "{content}"'` | Yellow |
| CombatHit | Per-variant line + HP bar for defender | Red/green |
| NpcDied | `"{killer} has slain {npc}!"` | Bold red |
| PlayerDied | `"You have been slain by {killer}! Respawning..."` | Bold red |
| AttackModeUpdate | `"[ATTACK MODE ON]"` / `"[ATTACK MODE OFF]"` | Red/green |
| NpcPhaseShift | `"*** {npcName} ENTERS {phaseName}! ***"` + HP bar | Bold red |
| NpcAbilityEffect | Header line + one line per hit result (hit/save/resist) | Red/yellow |
| ActiveEffectsUpdate | Rendered on demand only (cached) | — |
| EffectTick | `"({effectName}) {message}"` | Magenta |
| InventoryUpdate | Full inventory display (issue's format) | White |
| RoomItemsUpdate | Silent (update state cache only) | — |
| LootReceived | `"You receive from {npc}: {items}"` | Green |
| LootDropped | `"{npc} drops: {items}"` | Yellow |
| PickupResult | `"You pick up {qty}x {name}."` / `"You pick up the {coinType}."` | Green |
| ItemUsed | `"{msg}"` | Green |
| EquipUpdate | `"Equipped {name} in {slot}."` / `"Unequipped {slot}."` | White |
| StealthUpdate | `"{msg}"` | Gray |
| MeditateUpdate | `"{msg}"` | Blue |
| RestUpdate | `"{msg}"` | Blue |
| TrackResult | `"{msg}"` | Green/yellow |
| XpGained | `"You gain {amount} XP. ({current}/{toNext})"` | Yellow |
| LevelUp | `"*** LEVEL UP! You are now level {level}! ***"` then stat summary | Bold yellow |
| TrainerInfo | Numbered menu of trainable stats with costs | Cyan |
| StatTrained | `"{stat} increased to {val}. ({remaining} CP remaining)"` | Cyan |
| SpellCastResult | `"{msg}"` | Blue/red |
| SkillEffect | Per-type flavor text + damage line | Yellow |
| SpellEffect | `"{caster} casts {spell} on {target} for {amount}."` + HP bar | Blue |
| VendorInfo | Numbered shop listing (issue's format) | Cyan |
| BuyResult | `"{msg}"` | Green/red |
| SellResult | `"{msg}"` | Green/red |
| InteractResult | `"{msg}"` | Yellow |
| PlaceItemPrompt | `"[{label}] {prompt}"` then `"  Type 'place <item>' or 'cancel'."` | Cyan |
| RiddlePrompt | `"[{label}] {question}"` + optional `"  Hint: {hint}"` + `"  Type 'answer <text>' or 'cancel'."` | Cyan |
| ChoicePrompt | `"[{label}] {question}"` then numbered options then `"  Type 'choose <n>' or 'cancel'."` | Cyan |
| CraftingMenu | Numbered recipe list with material costs + player coin balance | Cyan |
| CraftResult | `"{msg}"` | Green/red |
| PartyInviteReceived | `"** {name} invites you to join their party. Type 'party accept {name}' or 'party decline {name}'."` | Cyan bold |
| PartyFormed | `"** Party formed: {members}"` | Cyan |
| PartyMemberJoined | `"** {name} joins the party."` | Cyan |
| PartyMemberLeft | `"** {name} leaves the party ({reason})."` | Cyan |
| PartyDisbanded | `"** The party has disbanded ({reason})."` | Cyan |
| PartyMemberUpdate | Silent (update state cache only; reflected in prompt HP display) | — |
| PartyChatMessage | `"[Party] {name}: {msg}"` | Cyan |
| PartyInfo | Formatted party listing with HP bars | Cyan |
| PartyLeaderChanged | `"** {name} is now party leader."` | Cyan |
| FollowUpdate | `"** {follower} is now following {target}."` / `"** {follower} stops following {target}."` | Gray |
| RallyPing | `"** {leader} calls a rally to {roomName} ({zoneName})!"` | Cyan bold |
| FollowFailed | `"** Follow failed: {reason}."` | Yellow |
| SystemMessage | `"{msg}"` | Yellow |
| ServerShutdown | `"*** SERVER SHUTDOWN: {msg} ({seconds}s) ***"` | Bold red |
| Error | `"ERROR: {msg}"` | Red |
| ServerHello | Silent (cache worldName for banner) | — |
| ConnectionRejected | `"Connection refused: {reason}"` | Red |
| ClassCatalogSync | Silent (cache for name resolution) | — |
| ItemCatalogSync | Silent (cache for name resolution) | — |
| SkillCatalogSync | Silent | — |
| SpellCatalogSync | Silent | — |
| RaceCatalogSync | Silent | — |
| Pong | Silent | — |
| Tutorial | Silent (suppress for telnet) | — |

**`server/src/main/kotlin/com/neomud/server/telnet/AsciiMap.kt`**

Takes `MapData` (rooms, playerRoomId, visitedRooms). `MapRoom` carries `x: Int`, `y: Int`, `z: Int` — confirmed integer grid coords from the shared model — so grid layout is direct with no fallback needed.

**Algorithm:**

1. Filter to player's z-level (`room.z == playerRoom.z`)
2. Define a viewport centered on the player: radius 2 = 5×5 rooms when terminal ≥ 80 cols, radius 1 = 3×3 for narrow terminals
3. Build a sparse `Map<Pair<Int,Int>, MapRoom?>` for the viewport
4. Iterate y from **high to low** (NeoMud uses screen-y-down coords; reversing puts NORTH at the top — standard MUD convention)
5. For each row, emit two lines: a **room row** and a **connector row** (except after the last row)

**Room cell symbols (3 chars each):**
- `[+]` — current player's room
- `[*]` — visited room with other players present
- `[ ]` — visited, empty room
- `   ` — unvisited (fog of war — blank, not a box)
- `[^]` / `[v]` / `[±]` — visited room with up/down exit (overrides `[ ]`, not `[+]`/`[*]`)

**Connectors:**
- East/West exits: `--` between adjacent cells (only rendered if source room is visited)
- North/South exits: ` | ` on the connector row between room rows (only if source room is visited)
- Diagonal exits (NE/NW/SE/SW): omit from grid — diagonals don't render cleanly at 3-char cell width; note them in room description text instead

**Example output (5×5 viewport, 80-col terminal):**
```
     [ ]--[ ]
      |    |
[+]--[ ]--[ ]
      |
     [*]

[+]You  [ ]Room  [*]Players  [^]Up  [v]Down
```

**`AtlasData` rendering** (for the `atlas` command) — the atlas covers the whole world, potentially hundreds of rooms. Don't attempt a world grid; render a zone index:
```
=== World Atlas ===
  Millhaven          (12 rooms)
  The Foothills       (8 rooms)
  Drowned Chapel      (6 rooms)
  ...
Type 'map' for your local minimap.
```

### Prompt line

`<HP:{cur}/{max} MP:{cur}/{max}{flags}> ` where flags: ` [Attack]` in red, ` [Hidden]` in gray, ` [Med]` / ` [Rest]` in blue. MP section omitted for non-caster classes. Prompt is re-displayed after every async message while in command loop.

### VTT compatibility requirements

Every rendered line must:
1. End with `RESET` if any color code was opened — no "color bleed" between lines
2. Use only VT100/ANSI standard codes (no xterm-256color, no OSC sequences)
3. Never assume terminal height — no paging, no clear-screen
4. Wrap at configured terminal width (default 80, overridden by NAWS)
5. Not emit raw 0xFF bytes — the Telnet framing layer handles IAC escaping

### Tests

`TextRendererTest.kt` — one test per `ServerMessage` variant. Test that:
- Correct color codes present for each type
- RESET appears after every colored segment
- HP bar thresholds (>50% green, 25–50% yellow, <25% red)
- Empty/edge cases (0 HP, empty inventory, no exits)
- Line count within expected bounds for complex renders (room with 5 NPCs, vendor with 10 items)

---

## Phase 3: Command Parser + Name Resolution (2–3 days)

### `server/src/main/kotlin/com/neomud/server/telnet/CommandParser.kt`

Maps raw text input → `ClientMessage` or `LocalCommand`. Has access to `TelnetSessionState` for context-dependent parsing (modal state, name resolution).

**Modal input states** — certain messages put the session in a modal state:
- After `PlaceItemPrompt`: `answer`/`cancel` bypass normal parser, respond to riddle
- After `RiddlePrompt`: same
- After `ChoicePrompt`: `choose <n>` / `cancel`
- After `PlaceItemPrompt`: `place <item>` / `cancel`

`help` is implemented here in Phase 3 as a `LocalCommand` — it renders the command table it lives alongside, so it must exist in the same file. Phase 5 adds per-command detail text and categories, but the handler itself belongs in Phase 3. Every new telnet player's first input is `help`; it cannot wait until polish.

Full command table (issue's table + additions from gap analysis above). Key additions:

```
tell <player> <msg>   | t <player> <msg>   | Tell(player, msg)            -- ⚠️ verify ClientMessage exists
who                   |                    | RequestWho                   -- ⚠️ verify ClientMessage exists
drop <item> [qty]     |                    | DropItem(itemId, qty)
party invite <name>   |                    | PartyInvite(name)
party accept <name>   |                    | PartyAccept(name)
party decline <name>  |                    | PartyDecline(name)
party leave           |                    | PartyLeave
party kick <name>     |                    | PartyKick(name)
party say <msg>       | ps <msg>, ; <msg>  | PartySay(msg)
party info            | party              | local — render cached PartyInfo
follow <name>         |                    | Follow(name)
unfollow              | stopfollow         | FollowStop
rally                 |                    | Rally
crafting              | craft, recipes     | InteractCrafter
craft <recipe>        |                    | CraftItem(recipeId via NameResolver)
talk <npc>            | npc                | InteractNpc(npcId via NameResolver)
answer <text>         |                    | AnswerRiddle (modal only)
choose <n>            |                    | MakeChoice (modal only)
place <item>          |                    | PlaceItem (modal only)
cancel                |                    | local — clear modal state
atlas                 | world              | local — render cached AtlasData
effects               | buffs              | local — render cached ActiveEffectsUpdate
hp                    | health             | local — show HP/MP from cache
get all               | take all           | PickupItem for each room item
```

### `server/src/main/kotlin/com/neomud/server/telnet/NameResolver.kt`

Resolves player-typed names to entity IDs using cached state. Resolution order:
1. Exact match (case-insensitive)
2. Prefix match ("iron sw" → `item:iron_sword`)
3. Substring match ("sword" → first item containing "sword")
4. Numbered index (`2.rat` → second entity matching "rat" in current room)

Context determines which catalog to search: room NPCs for `target`/`attack`/`select`, room ground items for `get`/`pickup`, player inventory for `drop`/`equip`/`use`/`sell`, vendor inventory for `buy`, **room interactables for `interact`** (matched by `label`, returns `featureId`).

Ambiguity handling: if >1 match with no number qualifier, output `"Which one? 1. {match1} 2. {match2} — type '2.{name}' to specify."` and return null (no ClientMessage sent).

---

## Interactables: Full Coverage

`RoomInteractable` has `id`, `label`, `description`, `actionType`, `triggerType` (ON_ACTION / ON_ENTER), `perceptionDC`, `cooldownTicks`, `failureMessage`. All eight action types need specific handling.

### Room display

Only show `ON_ACTION` interactables in the room description (ON_ENTER traps are invisible to the player). Format:
```
  Features: Ancient Altar, Rusted Lever, Whispering Stone
```
Each feature's `label` is the clickable name. If `description` is non-empty, show it on a sub-line:
```
  Features:
    Ancient Altar — An altar of smooth black stone, stained with old offerings.
    Rusted Lever  — A large iron lever set into the wall.
```

Locked exits show `(locked)` in the exits line:
```
  [Exits: north south east(locked) west]
```
`hiddenExits` (discovered secret passages) show normally — server only sends them if already discovered.

### `interact` command

`interact <feature>` and `activate <feature>` → `InteractFeature(featureId)` via NameResolver on `state.roomInteractables` (cached from `RoomInfo`).

`use` maps to inventory items only (`UseItem`) — no overlap. If a player types `use lever`, the NameResolver checks inventory first; if not found there, suggest `"Did you mean 'interact lever'?"`.

If only one `ON_ACTION` interactable in the room: `interact` with no argument targets it automatically.

### Per-type handling

| Action Type | Flow | Modal State | Notes |
|---|---|---|---|
| `EXIT_OPEN` | `interact` → `InteractResult` | None | Lever, switch, button — one-shot |
| `ROOM_EFFECT` | `interact` → `InteractResult` | None | Healing spring, arcane pool |
| `TELEPORT` | `interact` → `InteractResult` + `MoveOk`/`RoomInfo` | None | Portal, magic gate |
| `DAMAGE_TRAP` | Fires automatically on room entry (ON_ENTER) | None | Player never types `interact`; `InteractResult` arrives async |
| `PUZZLE_STEP` | `interact` → `InteractResult` (with progress) | None | May require multiple steps in sequence; server tracks state |
| `PLACE_ITEM` | `interact` → `PlaceItemPrompt` → `place <item>` → `InteractResult` | `PlaceItemActive(featureId)` | Offering stone, altar |
| `RIDDLE_PROMPT` | `interact` → `RiddlePrompt` → `answer <text>` → `InteractResult` | `RiddleActive(featureId)` | Sphinx riddle, rune puzzle |
| `CONDITIONAL_TRIGGER` | `interact` → `InteractResult` (pass or fail with `failureMessage`) | None | Item/flag/level gate |

`ChoicePrompt` (NPC dialogue trees and any choice-based feature): `interact` → `ChoicePrompt` → `choose <n>` → `InteractResult`. Modal state: `ChoiceActive(featureId, options)`.

### Modal state machine

When a two-phase interactable fires, the CommandParser enters a modal state. Normal commands are suspended; only the modal response commands and `cancel` are accepted.

```
PlaceItemActive:
  "place <item>"  → PlaceItem(featureId, itemId via inventory NameResolver) → clear modal
  "cancel"        → print "Cancelled." → clear modal
  anything else   → print "You must place an item or type 'cancel'."

RiddleActive:
  "answer <text>" → AnswerRiddle(featureId, answer) → clear modal
  "cancel"        → print "You step away from the riddle." → clear modal
  anything else   → print "Type 'answer <text>' or 'cancel'."

ChoiceActive:
  "choose <n>"    → MakeChoice(featureId, choiceId from options[n-1]) → clear modal
  "cancel"        → print "You step back." → clear modal
  anything else   → print "Type 'choose <n>' or 'cancel'."
```

`InteractResult` always clears modal state (server either accepted or rejected the response).

### Cooldown handling

If a player `interact`s an interactable on cooldown, the server returns `InteractResult(success=false, message=failureMessage)`. No special client handling — just render the failure message. The client doesn't track cooldowns independently.

### ON_ENTER traps

`DAMAGE_TRAP` with `triggerType = ON_ENTER` fires when `TrapManager` detects room entry. The player receives one of: `InteractResult`, `CombatHit`, or `SystemMessage` depending on how the trap resolves. All arrive async with no pending modal — render them normally. No special client state needed.

### `TelnetSessionState` additions for interactables

```kotlin
var roomInteractables: List<RoomInteractableInfo> = emptyList()
// Updated from RoomInfo / MoveOk — only ON_ACTION interactables
```

`RoomInteractableInfo` caches `id`, `label`, `description` from `RoomInteractable` (enough for NameResolver and display; no need to cache full `actionData`).

---

## Phase 4: Telnet Protocol + TCP Infrastructure (3–4 days)

### Dependency

Add to `server/build.gradle.kts`:
```kotlin
implementation(libs.ktor.network)         // TCP server
implementation(libs.ktor.network.tls)     // optional, for STARTTLS later
```
Add to `gradle/libs.versions.toml` under `[libraries]`:
```toml
ktor-network = { module = "io.ktor:ktor-network", version.ref = "ktor" }
```

### `TelnetProtocol.kt`

IAC byte constants and negotiation frame builders:
```kotlin
object Telnet {
    const val IAC: Byte = 0xFF.toByte()
    const val WILL: Byte = 0xFB.toByte()
    const val WONT: Byte = 0xFC.toByte()
    const val DO: Byte = 0xFD.toByte()
    const val DONT: Byte = 0xFE.toByte()
    const val SB: Byte = 0xFA.toByte()   // subnegotiation begin
    const val SE: Byte = 0xF0.toByte()   // subnegotiation end
    const val ECHO: Byte = 0x01
    const val SGA: Byte = 0x03           // suppress go-ahead
    const val NAWS: Byte = 0x1F          // negotiate about window size
    const val GMCP: Byte = 0xC9.toByte()
    const val MSDP: Byte = 0x45
}
```

Helper: `negotiationFrame(command: Byte, option: Byte): ByteArray` and `subNegotiationFrame(option: Byte, data: ByteArray): ByteArray`.

### `TelnetSessionState.kt`

Cached game state for prompt rendering and name resolution. Updated by processing every `ServerMessage` before rendering. Fields:

```kotlin
data class TelnetSessionState(
    var playerName: String? = null,
    var currentHp: Int = 0, var maxHp: Int = 0,
    var currentMp: Int = 0, var maxMp: Int = 0,
    var playerClass: String? = null,
    var playerLevel: Int = 1,
    var inAttackMode: Boolean = false,
    var isHidden: Boolean = false,
    var isMeditating: Boolean = false,
    var isResting: Boolean = false,
    var currentRoomId: String? = null,
    var currentRoomName: String? = null,
    var roomNpcs: List<NpcInfo> = emptyList(),
    var roomPlayers: List<PlayerInfo> = emptyList(),
    var roomGroundItems: List<GroundItem> = emptyList(),
    var inventory: Inventory? = null,
    var equipment: Equipment? = null,
    var coins: Coins? = null,
    var activeEffects: List<ActiveEffect> = emptyList(),
    var mapData: MapData? = null,
    var atlasData: AtlasData? = null,
    var partyInfo: PartyInfo? = null,
    var itemCatalog: Map<String, Item> = emptyMap(),      // id → Item
    var skillCatalog: Map<String, SkillDef> = emptyMap(),
    var spellCatalog: Map<String, SpellDef> = emptyMap(),
    var classCatalog: Map<String, CharacterClassDef> = emptyMap(),
    var terminalWidth: Int = 80,
    var modalState: ModalState = ModalState.None
)

sealed class ModalState {
    object None : ModalState()
    data class RiddleActive(val featureId: String) : ModalState()
    data class ChoiceActive(val featureId: String, val options: List<ChoiceOption>) : ModalState()
    data class PlaceItemActive(val featureId: String) : ModalState()
}
```

### `TelnetTransport.kt`

Implements `TransportSession`. Uses a `Channel<ServerMessage>` so writer coroutine is the sole socket writer.

```kotlin
class TelnetTransport(
    private val socket: Socket,
    private val state: TelnetSessionState,
    private val renderer: TextRenderer
) : TransportSession {
    private val outChannel = Channel<ServerMessage>(Channel.UNLIMITED)
    private val writer = socket.openWriteChannel(autoFlush = true)
    private val reader = socket.openReadChannel()

    override suspend fun sendMessage(message: ServerMessage) {
        outChannel.send(message)
    }
    override suspend fun close(reason: String) {
        outChannel.close()
        socket.close()
    }

    suspend fun runWriterLoop() {
        for (message in outChannel) {
            state.update(message)
            val lines = renderer.render(message, state)
            if (lines.isNotEmpty()) {
                clearCurrentLine()
                lines.forEach { writer.writeStringUtf8("$it\r\n") }
                redisplayPrompt()
            }
        }
    }
}
```

`clearCurrentLine()` emits `\r` + spaces + `\r` to overwrite any partially-typed input before async output. `redisplayPrompt()` emits the prompt line.

### `TelnetConnectionHandler.kt`

Per-connection lifecycle. Runs two coroutines: reader and writer.

```kotlin
class TelnetConnectionHandler(
    private val socket: Socket,
    private val commandProcessor: CommandProcessor,
    private val sessionManager: SessionManager,
    // ... other dependencies
) {
    suspend fun handle() {
        val state = TelnetSessionState()
        val transport = TelnetTransport(socket, state, TextRenderer())
        
        coroutineScope {
            // Writer coroutine
            launch { transport.runWriterLoop() }
            
            // Negotiate options
            sendNegotiation(DO, SGA)
            sendNegotiation(WILL, SGA)
            sendNegotiation(WILL, ECHO)  // we echo, suppress client echo
            sendNegotiation(DO, NAWS)
            
            // Welcome banner
            transport.sendRaw(welcomeBanner(state))
            
            // Login flow
            val session = runLoginFlow(transport, state) ?: return@coroutineScope
            
            // Command loop
            runCommandLoop(session, transport, state)
        }
    }
}
```

**Login flow:**
1. `"Username: "` prompt (no newline)
2. Read line → set echo `WONT ECHO` (still echoed by default until password)
3. `"\r\nPassword: "` prompt
4. Send `WONT ECHO` (suppress password echo)
5. Read line (password)
6. Send `WILL ECHO` (restore echo)
7. Send `Login(username, password, characterName=null, force=false)` to CommandProcessor
8. Wait for `LoginOk` or `AuthError` from transport
9. On `AuthError`: up to 3 retries then close
10. On `LoginOk`: if account has multiple characters, show select menu
11. On `SessionConflict`: show conflict message, ask `"Force login? (y/n):"` then re-send with `force=true`

**Command loop:**
1. Display prompt
2. Read line (strip IAC bytes, trim, normalize to LF)
3. If empty, loop
4. Try `commandProcessor.process(session, commandParser.parse(line, state))`
5. Rate limit check (same token bucket as WebSocket path)
6. Loop

**IAC byte stripping in reader:** Any `IAC` byte in the data stream must be handled:
- `IAC IAC` → literal `0xFF` (unescape)
- `IAC WILL/WONT/DO/DONT <option>` → handle negotiation, strip from line
- `IAC SB ... IAC SE` → subnegotiation, parse NAWS, strip
- Bare `IAC` at stream end → ignore

### `TelnetServer.kt`

```kotlin
class TelnetServer(
    private val port: Int,
    private val handler: TelnetConnectionHandler,
    private val maxConnections: Int = 100
) {
    private val activeConnections = AtomicInteger(0)
    
    suspend fun start() {
        val server = aSocket(ActorSelectorManager(Dispatchers.IO)).tcp().bind("0.0.0.0", port)
        logger.info("Telnet server listening on port $port")
        while (true) {
            val socket = server.accept()
            if (activeConnections.get() >= maxConnections) {
                socket.openWriteChannel().writeStringUtf8("Server full. Try again later.\r\n")
                socket.close()
                continue
            }
            activeConnections.incrementAndGet()
            launch(Dispatchers.IO) {
                try {
                    TelnetConnectionHandler(socket, ...).handle()
                } finally {
                    activeConnections.decrementAndGet()
                    socket.close()
                }
            }
        }
    }
}
```

### `GameConfig.kt` additions

```kotlin
object Telnet {
    const val ENABLED = true          // override with NEOMUD_TELNET_ENABLED=false
    const val PORT = 4000
    const val MAX_CONNECTIONS = 100
    const val TERMINAL_WIDTH_DEFAULT = 80
    const val WELCOME_BANNER = """
        
         _   _            __  __           _
        | \ | | ___  ___ |  \/  |_   _  __| |
        |  \| |/ _ \/ _ \| |\/| | | | |/ _` |
        | |\  |  __/ (_) | |  | | |_| | (_| |
        |_| \_|\___|\___/|_|  |_|\__,_|\__,_|
        
        Connect. Explore. Survive.
        Type 'help' for commands.
        
    """.trimIndent()
}
```

### `Application.kt` changes

In `Application.module()`, after `launch { gameLoop.run() }`:
```kotlin
if (GameConfig.Telnet.ENABLED) {
    val telnetServer = TelnetServer(
        port = GameConfig.Telnet.PORT,
        commandProcessor = commandProcessor,
        sessionManager = sessionManager,
        // ...
    )
    launch(Dispatchers.IO) { telnetServer.start() }
}
```

Also parse `--telnet-port` CLI arg (default `GameConfig.Telnet.PORT`).

### Integration tests

`TelnetIntegrationTest.kt` — use `ServerSocket` + coroutines (not Ktor test infra, since this is raw TCP):
- Connect → receive banner → login → receive room → send `look` → receive room description
- Send `n` → receive move response or error
- Disconnect mid-session → verify session cleanup
- Two concurrent connections → verify session isolation
- Invalid commands → receive `ERROR:` line
- Rate limit → receive throttle message after burst

---

## Phase 5: Polish (2–3 days)

### Help system

`help` command → local rendering, no server round-trip. Categories: Movement, Combat, Communication, Inventory, Skills, Social, Other.
`help <command>` → one-line description + aliases + usage example.

Stored as a static map in `CommandParser.kt` alongside the command table.

### Welcome banner + MOTD

Banner from `GameConfig.Telnet.WELCOME_BANNER`. Include `worldName` (from cached `ServerHello`), engine version, player count from `SessionManager`.

### NAWS handling

When client sends `IAC SB NAWS <width-hi> <width-lo> <height-hi> <height-lo> IAC SE`:
- Update `state.terminalWidth`
- `TextRenderer` uses `state.terminalWidth` for wrapping long descriptions and room boxes
- Minimum 40 columns, maximum 200 columns

### `--no-telnet` / env var

`NEOMUD_TELNET_ENABLED=false` env var skips launching `TelnetServer`. CLI: `--no-telnet` flag. Useful for local dev when only testing the web client.

### Connection limits per IP

Mirror the WebSocket IP limit (`GameConfig.Security.MAX_CONNECTIONS_PER_IP`). Maintain a `ConcurrentHashMap<String, AtomicInteger>` in `TelnetServer`.

---

## Phase 6: Protocol Extensions — GMCP (2–3 days)

**Status: ✅ implemented (2026-07-07).** `Gmcp.kt` (Core.Hello, Core.Supports.Set, Char.Stats,
Char.Vitals, Room.Info, Char.Items.List, Map.Info) and `Msdp.kt` (HEALTH/HEALTH_MAX/MANA/MANA_MAX/
LEVEL/ROOM_NAME/ROOM_EXITS) encoders, wired into `TelnetConnectionHandler.handleNegotiation`
(`DO GMCP`/`DO MSDP` → `WILL …` + handshake) and the `TelnetTransport` writer loop (out-of-band
push after each rendered message). `TelnetSessionState` now caches `playerStats` + room name/zone/
exits and absorbs HP/MP from `ItemUsed`/`EffectTick`/`StatTrained` (also fixes stale prompt vitals
after heals). Covered by `GmcpTest`, `MsdpTest`, expanded `TelnetSessionStateUpdateTest`, and the
Phase 4 `TelnetIntegrationTest` (live GMCP handshake + snapshot over real TCP).

**Snapshot-on-enable + write serialization (2026-07-07):** a client's `IAC DO GMCP` isn't processed
until the command loop starts — after the login burst — so `gmcpEnabled` flips late and the initial
LoginOk/RoomInfo would carry no GMCP. The transport now flushes a full snapshot
(Char.Stats/Vitals/Room.Info/Items) the moment GMCP/MSDP is enabled, from the writer coroutine so
`state` is already populated. Building the integration test surfaced a latent bug: telnet
negotiation frames were written from the reader coroutine concurrently with the writer loop, and
Ktor's `ByteWriteChannel` is not concurrency-safe — an overlap threw and killed the writer loop.
All socket writes now serialize through a `Mutex`.

GMCP enables Mudlet's built-in mapper, health bars, and inventory panel. It's a subnegotiation channel: `IAC SB GMCP "Package.Name" <json> IAC SE` runs alongside text output.

### Negotiation

If client sends `IAC DO GMCP` (Mudlet does this on connect): respond `IAC WILL GMCP`. If client doesn't send it: don't offer GMCP (graceful degradation).

After `WILL GMCP` exchange: send `Core.Hello` with server identification.

### Packages to implement

**`Core.Hello`** (send after GMCP negotiated):
```json
{ "client": "NeoMud", "version": "1.0" }
```

**`Core.Supports.Set`** (send after `Core.Hello`):
```json
["Char 1", "Char.Vitals 1", "Char.Stats 1", "Room 1", "Room.Info 1", "Char.Items 1", "Map 1"]
```

**`Char.Vitals`** (send on every HP/MP change — `CombatHit`, `EffectTick`, `ItemUsed`, `SpellEffect`, `SkillEffect`, `LevelUp`, `StatTrained`):
```json
{ "hp": 45, "maxhp": 60, "mp": 20, "maxmp": 30 }
```

**`Char.Stats`** (send on `LoginOk`, `LevelUp`, `StatTrained`):
```json
{ "name": "Aldric", "class": "Warrior", "level": 5, "str": 14, "agi": 10, "int": 8, "wil": 9, "hea": 12 }
```

**`Room.Info`** (send on `RoomInfo`, `MoveOk`):
```json
{ "id": "millhaven:town_square", "name": "Town Square", "zone": "Millhaven", "exits": ["north", "south", "east", "west"] }
```
This drives Mudlet's automapper — room ID + exits are enough for it to build a map graph.

**`Char.Items.List`** (send on `InventoryUpdate`):
```json
{ "location": "inv", "items": [{ "id": "item:iron_sword", "name": "Iron Sword", "attrib": "w" }] }
```

**`Map.Info`** (send on `MapData`):
```json
{ "id": "millhaven:town_square", "roomData": { ... rooms ... } }
```

### MSDP (basic, for TinTin++)

If client sends `IAC DO MSDP`: respond `IAC WILL MSDP`. Send `HEALTH`, `HEALTH_MAX`, `MANA`, `MANA_MAX`, `LEVEL`, `ROOM_NAME`, `ROOM_EXITS` variables on state changes.

---

## File Inventory

```
server/src/main/kotlin/com/neomud/server/
├── session/
│   ├── TransportSession.kt          NEW — Phase 1
│   ├── WebSocketTransport.kt        NEW — Phase 1
│   └── PlayerSession.kt             MODIFIED — Phase 1
├── telnet/
│   ├── AnsiColors.kt                NEW — Phase 2
│   ├── TextRenderer.kt              NEW — Phase 2
│   ├── AsciiMap.kt                  NEW — Phase 2
│   ├── CommandParser.kt             NEW — Phase 3
│   ├── NameResolver.kt              NEW — Phase 3
│   ├── TelnetProtocol.kt            NEW — Phase 4
│   ├── TelnetSessionState.kt        NEW — Phase 4
│   ├── TelnetTransport.kt           NEW — Phase 4
│   ├── TelnetConnectionHandler.kt   NEW — Phase 4
│   └── TelnetServer.kt              NEW — Phase 4
└── plugins/
    └── Routing.kt                   MODIFIED — Phase 1

scripts/
└── game-relay.mjs                   MODIFIED — Phase 0 (--interactive, --session-id)
```

**Test files:**
```
server/src/test/kotlin/com/neomud/server/
├── telnet/
│   ├── TextRendererTest.kt          NEW — Phase 2 (70+ cases)
│   ├── CommandParserTest.kt         NEW — Phase 3 (all commands + aliases + edge cases)
│   ├── NameResolverTest.kt          NEW — Phase 3 (exact, prefix, partial, numbered)
│   └── TelnetIntegrationTest.kt     NEW — Phase 4 (TCP-level)
```

**Shared protocol changes (if gaps confirmed):**
```
shared/src/commonMain/kotlin/com/neomud/shared/protocol/
└── ClientMessage.kt                 POSSIBLY MODIFIED — verify Tell / RequestWho exist
```

---

## Effort Estimate

| Phase | Estimate | Notes |
|---|---|---|
| 0: Relay interactive mode | 1–2 days | JS, quick iteration |
| 1: Transport abstraction | 1–2 days | Mechanical, test-verifiable |
| 2: Text rendering engine | 4–5 days | 70+ message types, VTT compliance |
| 3: Command parser | 2–3 days | 45+ commands, NameResolver |
| 4: Telnet infrastructure | 3–4 days | TCP, IAC, async I/O, state cache |
| 5: Polish | 2–3 days | Help, banner, NAWS, env flags |
| 6: GMCP | 2–3 days | Mudlet integration |
| **Total** | **~3–4 weeks** | |

Phase 0 can ship independently. Phases 1–4 are the core deliverable. Phases 5–6 are polish/compatibility.

---

## Open Questions Before Starting

1. **Do `Tell` and `RequestWho` ClientMessages exist?** Check `CommandProcessor.kt` for how `tell` and `who` are dispatched from the web client. If they exist under different names, update the command table. If missing, add them to `shared/protocol/ClientMessage.kt`.

2. **Character select from telnet**: When a player account has multiple characters (PlatformAuthOk returns `characterNames`), how to present the picker? Numbered list + `"Enter number:"` is the obvious choice. Decide whether this is in scope for Phase 4 or Phase 5.

3. **Guest login from telnet**: Confirm this is out of scope. Guests need no account — a wizard to choose race/class/stats is significant extra work and low value for the MUD community we're targeting.

4. **Port 4000 vs 2323**: Final confirmation. 4000 is the traditional MUD port; 2323 avoids any conflict with legitimate MUD services using 4000. Either works. 4000 is more recognizable to MUD players.
