# Multi-Target Spell System: Party Heals, Group Buffs, Boss AoE

## Context

The party system (#212) shipped with XP sharing, loot distribution, and follow mechanics, but all healing/buff spells remain self-only. Three issues address this gap: #450 (healers target party members), #451 (group heals/buffs), and #456 (boss/NPC AoE abilities). Additionally, #455 (bandit in healing room) is already fixed in this session (SANCTUARY added to Hermit's Outpost).

This plan extends the spell system with two new player target types (ALLY, PARTY_ROOM) and introduces a separate NPC ability system for boss AoE attacks with save mechanics.

## Design Decisions (from game-designer + UX review)

**Target types**: Four total — `ENEMY`, `SELF`, `ALLY` (single party member in room), `PARTY_ROOM` (all party in room). No `ENEMY_ROOM` for players (trivializes multi-mob encounters).

**ALLY heal balance**: Same formula as self-heals, no penalty. Targeting flexibility IS the power increase. Same room only.

**Group heal balance**: 55% of single-target power per person. Higher mana cost and cooldown baked into spell definitions. Superior with 3+ targets, break-even at 2, worse solo.

**Boss AoE**: Fixed damage (40-60% of boss melee), save mechanic (stat + level/2 + d20 vs DC, success halves), phase-gated. Separate NPC ability system — not shared with player spell catalog.

**Threat from healing**: Healing someone in combat adds the healer to those NPCs' `engagedPlayerIds`. Creates healer tension without formal aggro table.

**Auto-cast ALLY**: Targets lowest HP% party member in room. Self-priority override below 25% HP. Skip cast if all allies above max HP (save mana).

**PARTY_ROOM UX**: Like SELF — click spell slot = immediate cast, no target selection. Also supports readied auto-repeat via attack toggle.

**Player sprite targeting**: Tap player sprite with readied ALLY spell → cast. Long-press player sprite → context menu with heal/buff spell buttons (mirrors NPC context menu pattern).

**Party HUD targeting**: Individual member rows become tappable when ALLY spell is readied.

**Spell bar visual**: ALLY/PARTY_ROOM spells get green glow (vs purple for ENEMY readied).

---

## Phase 1: ALLY Targeting + Single-Target Party Heals (#450)

**Goal**: Healers can target and heal/buff/HoT a specific party member in the same room.

### 1A. Shared model

**`shared/.../model/SpellDef.kt`** — Add `ALLY` to `TargetType` enum.

**`shared/.../protocol/ServerMessage.kt`** — Add optional `targetName: String? = null` to `SpellCastResult` so caster sees who was healed. Existing `SpellEffect.isPlayerTarget` already supports ally heal broadcasts.

### 1B. Server: SpellCommand ALLY resolution

**`server/.../game/commands/SpellCommand.kt`** — Core changes:

- Add `partyService: PartyService?` constructor param
- New `resolveAllyTarget(session, targetId, roomId) -> PlayerSession?`: look up by name via sessionManager, validate same room + same party + alive. Self-fallback if no targetId.
- Refactor `handleHeal(session, spell, power, playerName)` → `handleHeal(casterSession, targetSession, spell, power, playerName)`: apply HP change to targetSession, broadcast SpellEffect with target info
- Same refactor for `handleBuff` and `handleHot` — apply ActiveEffect to target session
- In `resolve()`: branch on `spell.targetType == ALLY` before the `when(spellType)` block
- In `autoCast()`: for ALLY spells, find lowest HP% party member in room via `partyService.getMembersInRoom()`. Self-priority below 25% HP. Skip if no injured ally.
- **Threat**: when healing a target in combat, add caster name to `engagedPlayerIds` of NPCs that have the target engaged

### 1C. Server: CombatManager auto-cast branch

**`server/.../game/combat/CombatManager.kt`** (lines 188-214) — Before calling `resolveTarget()` for readied spells, check `spellCatalog.getSpell(readiedSpell)?.targetType`. If ALLY or SELF, call `spellCommand.autoCast()` directly without NPC target resolution. Skip kill check.

### 1D. Server: CommandProcessor + GameConfig

**`server/.../game/CommandProcessor.kt`** — Pass `partyService` to `SpellCommand` constructor.

**`server/.../game/GameConfig.kt`** — Add to `Skills`:
- `AUTO_HEAL_SELF_PRIORITY_THRESHOLD = 0.25`

### 1E. Client: ALLY spell targeting UX

**`client/.../viewmodel/GameViewModel.kt`**:
- `readySpell()`: for ALLY, set readiedSpellId but do NOT auto-select hostile or toggle attack mode
- New `castAllySpell(spellId, targetPlayerName)`: send `CastSpell(spellId, targetPlayerName)`

**`client/.../ui/components/SpriteOverlay.kt`**:
- PC sprite tap: if readied ALLY spell, cast on that player instead of selectTarget
- PC sprite long-press: show context menu with available ALLY spells from spell slots (mirror NPC context menu pattern)
- Add params: `readiedSpellId`, `spellCatalog`, `onCastAllySpell`

**`client/.../ui/components/PartyHudOverlay.kt`**:
- Add params: `readiedSpellId`, `spellCatalog`, `onCastAllySpell`
- Individual member rows `.clickable` when ALLY spell readied → cast on that member

**`client/.../ui/components/SpellBar.kt`**:
- Green glow for readied ALLY spells instead of purple

**`client/.../ui/theme/MudColors.kt`**:
- Add `val healTarget = Color(0xFF44CC44)`

### 1F. World data: new ALLY spells

**`maker/default_world_src/world/spells.json`** — Add:
- `MEND_WOUNDS`: priest, HEAL, ALLY, basePower=8, manaCost=7, cooldown=2, level=2
- `BARK_SKIN`: druid, BUFF, ALLY, BUFF_AGILITY, basePower=6, manaCost=10, cooldown=6, level=3

### 1G. Tests

- `SpellCommandAllyTest.kt`: ALLY heal on party member (HP up, MP down), reject non-party, reject different room, self-fallback, threat registration, auto-cast lowest HP%, auto-cast self-priority, full-HP rejection, ALLY buff on target
- Protocol serialization: SpellCastResult with targetName round-trip

---

## Phase 2: PARTY_ROOM Group Heals/Buffs (#451)

**Goal**: AoE heals/buffs hitting all party members in the same room at reduced per-target power.

### 2A. Shared model

**`shared/.../model/SpellDef.kt`** — Add `PARTY_ROOM` to `TargetType` enum.

### 2B. Server: SpellCommand multi-target

**`server/.../game/commands/SpellCommand.kt`**:
- New `resolvePartyRoomTargets(session, roomId) -> List<PlayerSession>`: all party members in room via partyService + sessionManager. Always includes caster. Solo fallback = just caster.
- New `handleGroupHeal(caster, spell, power, targets, name)`: apply `power * GROUP_HEAL_POWER_RATIO` (0.55) to each target, skip full-HP members, broadcast SpellEffect per target
- New `handleGroupBuff` and `handleGroupHot`: same pattern, apply ActiveEffect to all targets
- In `resolve()` and `autoCast()`: PARTY_ROOM branch → resolve targets → group handler
- Threat: add caster to engagedPlayerIds for all in-combat targets

### 2C. Server: GameConfig

**`server/.../game/GameConfig.kt`** — Add `GROUP_HEAL_POWER_RATIO = 0.55`

### 2D. Client

**`client/.../viewmodel/GameViewModel.kt`** — `readySpell()` for PARTY_ROOM: treat like SELF (immediate cast), or ready for auto-repeat.

### 2E. World data: new PARTY_ROOM spells

**`maker/default_world_src/world/spells.json`** — Add:
- `PRAYER_OF_MENDING`: priest, HEAL, PARTY_ROOM, basePower=6, manaCost=14, cooldown=4, level=4
- `WAR_HYMN`: bard, BUFF, PARTY_ROOM, BUFF_STRENGTH, basePower=4, manaCost=12, cooldown=8, level=4
- `SOOTHING_AURA`: bard, HOT, PARTY_ROOM, basePower=3, tickPower=3, duration=5, manaCost=14, cooldown=6, level=5
- `REJUVENATION`: druid, HOT, PARTY_ROOM, basePower=3, tickPower=3, duration=5, manaCost=12, cooldown=5, level=4

### 2F. Tests

- PARTY_ROOM heal hits all party in room, skips other rooms
- Power scaled by GROUP_HEAL_POWER_RATIO per target
- Solo caster gets reduced power (incentivizes parties)
- PARTY_ROOM buff applies ActiveEffect to all targets
- Threat registration for all in-combat targets
- Mixed HP targets: only injured healed, full-HP skipped

---

## Phase 3: NPC Ability System + Boss AoE (#456)

**Goal**: Bosses use special room-wide abilities separate from the player spell system.

### 3A. Shared model

**`shared/.../protocol/ServerMessage.kt`** — New messages:
```kotlin
@SerialName("npc_ability_effect")
data class NpcAbilityEffect(
    val npcName: String, val npcId: String, val abilityName: String,
    val message: String, val results: List<AbilityHitResult>, val sound: String = ""
) : ServerMessage()

@Serializable
data class AbilityHitResult(
    val targetName: String, val damage: Int, val saved: Boolean,
    val newHp: Int, val maxHp: Int
)
```

### 3B. Server: NPC ability data model

**`server/.../world/Zone.kt`** — New data class:
```kotlin
data class NpcAbility(
    val id: String, val name: String, val damage: Int,
    val damageVariance: Int = 0, val cooldownTicks: Int = 3,
    val saveStat: String = "", val saveDC: Int = 15, val saveHalves: Boolean = true,
    val phaseRequired: Int = 0, val message: String = "", val sound: String = ""
)
```
Add to `NpcData`: `val abilities: List<NpcAbility> = emptyList()`

### 3C. Server: NpcState ability tracking

**`server/.../npc/NpcManager.kt`** — Add to NpcState:
- `val abilities: List<NpcAbility> = emptyList()` (copied from NpcData at spawn)
- `val abilityCooldowns: MutableMap<String, Int> = mutableMapOf()`
- Copy abilities from template during spawn

### 3D. Server: CombatManager ability resolution

**`server/.../game/combat/CombatManager.kt`** — In NPC combat block (line 322):
- Before standard melee, check for usable abilities (off cooldown, phase met)
- Pick one eligible ability (random if multiple)
- Resolve against ALL visible players: `damage + random(1..variance)`, save check per player (`stat + level/2 + d20 >= saveDC` → halve), apply damage, build `AbilityHitResult` list
- Emit new `CombatEvent.NpcAbilityUsed`
- Set ability cooldown, skip melee this tick
- Respect combat grace and hidden players (exclude from targets)

New: `CombatEvent.NpcAbilityUsed(npcId, npcName, abilityName, message, roomId, results, sound)`

### 3E. Server: GameLoop

**`server/.../game/GameLoop.kt`**:
- Phase 2 event processing: handle `NpcAbilityUsed` → broadcast `NpcAbilityEffect` to room, check player kills
- Phase 3 cooldown tick: decrement all NPC ability cooldowns

### 3F. Server: GameConfig

**`server/.../game/GameConfig.kt`** — Add `NpcAbility` object:
- `SAVE_DICE_SIZE = 20`, `SAVE_LEVEL_DIVISOR = 2`

### 3G. Client: ability rendering

**`client/.../viewmodel/GameViewModel.kt`** — Handle `NpcAbilityEffect`:
- Display boss message in combat log (red/orange, dramatic)
- Per-result lines: "You take X damage from Ability!" or "PlayerName takes X damage! (saved)"
- Play ability sound

### 3H. World data: boss abilities

Add abilities to existing bosses:
- **Tidewarden Korlach** (drowned_chapel): Tidal Surge (dmg=22, var=8, cd=4, save=agi DC16, phase=0), Abyssal Crush (dmg=35, var=10, cd=6, save=wil DC20, phase=1)
- **Additional bosses**: Add 1-2 abilities each to bosses in pyromancers_folly, watchers_barrow, gorge, forest zones

### 3I. Tests

- NPC uses ability when off cooldown + phase met, damages all visible players
- Save mechanic: high stat → halved damage, low stat → full
- Phase-gated ability blocked before phase threshold
- Ability replaces melee for the tick (no double attack)
- Cooldown decrements correctly
- Player killed by ability triggers PlayerKilled
- Hidden/graced players excluded from AoE

---

## Phase 4: Maker Editor for NPC Abilities

**Goal**: World creators can edit NPC abilities in the Maker UI.

### 4A. Prisma schema

**`maker/prisma/schema.prisma`** — Add to Npc model: `abilities String @default("[]")`

### 4B. NPC Editor UI

**`maker/src/pages/NpcEditor.tsx`** — Follow the Boss Phases section pattern:
- "NPC Abilities" accordion section (visible when hostile checked)
- Per-ability row: id, name, damage, damageVariance, cooldownTicks, saveStat (dropdown), saveDC, saveHalves, phaseRequired, message, sound
- Add/remove buttons matching phase editor pattern

### 4C. Tests

- Abilities JSON parsing, add/remove, field validation

---

## Dependency Graph

```
Phase 1 (ALLY) ──→ Phase 2 (PARTY_ROOM)
Phase 3 (NPC AoE) ──→ Phase 4 (Maker)
```

Phases 1 and 3 are independent and can be developed in parallel. Phase 2 depends on Phase 1. Phase 4 depends on Phase 3.

## File Change Summary

| File | Phase | Change |
|------|-------|--------|
| `shared/.../model/SpellDef.kt` | 1, 2 | Add ALLY, PARTY_ROOM to TargetType |
| `shared/.../protocol/ServerMessage.kt` | 1, 3 | targetName on SpellCastResult; NpcAbilityEffect |
| `server/.../game/commands/SpellCommand.kt` | 1, 2 | ALLY/PARTY_ROOM resolution, multi-target heal/buff/hot |
| `server/.../game/combat/CombatManager.kt` | 1, 3 | ALLY auto-cast branch; NPC ability resolution |
| `server/.../game/GameLoop.kt` | 3 | NPC ability event handling, cooldown ticks |
| `server/.../game/GameConfig.kt` | 1, 2, 3 | AUTO_HEAL threshold, GROUP_HEAL ratio, save constants |
| `server/.../game/CommandProcessor.kt` | 1 | Wire partyService to SpellCommand |
| `server/.../world/Zone.kt` | 3 | NpcAbility data class, add to NpcData |
| `server/.../npc/NpcManager.kt` | 3 | NpcState abilities + cooldowns, spawn copy |
| `client/.../viewmodel/GameViewModel.kt` | 1, 3 | ALLY readySpell, castAllySpell, NpcAbilityEffect handler |
| `client/.../ui/components/SpriteOverlay.kt` | 1 | Player sprite ALLY casting + context menu |
| `client/.../ui/components/PartyHudOverlay.kt` | 1 | Tappable members for ALLY spells |
| `client/.../ui/components/SpellBar.kt` | 1 | Green glow for heal spells |
| `client/.../ui/theme/MudColors.kt` | 1 | healTarget color |
| `maker/default_world_src/world/spells.json` | 1, 2 | 6 new spells |
| Boss zone JSONs | 3 | abilities arrays on bosses |
| `maker/prisma/schema.prisma` | 4 | abilities field on Npc |
| `maker/src/pages/NpcEditor.tsx` | 4 | Abilities editor section |

## Verification

1. **Unit tests**: `./gradlew :shared:jvmTest :server:test` — all new + existing tests pass
2. **Client compile**: `./gradlew :client:compileKotlinWasmJs :client:compileDevDebugKotlinAndroid`
3. **Manual test (ALLY heal)**: Two players in party, same room. Healer readies Mend Wounds, taps party member sprite → target healed. Auto-cast mode heals lowest HP ally each tick.
4. **Manual test (PARTY_ROOM)**: Three players in party. Priest casts Prayer of Mending → all three healed at 55% power each.
5. **Manual test (Boss AoE)**: Fight Tidewarden Korlach with a party. At phase 0, Tidal Surge hits all party members. At phase 1, Abyssal Crush unlocks with higher damage.
6. **Manual test (threat)**: Healer heals a fighter engaged with NPC → NPC starts targeting healer occasionally.
7. **World rebuild**: `./gradlew packageWorld --rerun-tasks` after spell/boss data changes.
