# Plan: Playtester & UX Feedback Sprint

## Context

The party system shipped recently and generated 15 issues from playtesters, game designers, and UX reviewers. The feedback clusters into three categories: a server bug (#472), balance (#468), and party UI polish (10 issues). Additionally, the loot system (#463, #467) needs simplification per design direction: **no loot assignments, everything drops to ground, players should be quick.**

**Dropped:** #468 (Gnome Mage HP) — defer to game balance. #472 (post-death commands) — client already handles via PlayerDied; relay-specific artifact. #481 (XP powerleveling) — dedicated game design session.

## Phase 1: Protocol & Config (shared, must land first) ✅

- 1A: `PartyInviteReceived` extended with `inviterLevel`/`inviterClass` ✅
- 1B: `PartyPromote` client message added ✅
- 1C: `PartyLeaderChanged` server message added ✅

## Phase 2: Server Changes ✅

### 2C. Richer disband/leave reasons (#469) ✅
**Problem:** "Party disbanded" gives no context — who left? Why?

- **PartyCommand.kt** `handleLeave()`: Change reason to `"$playerName left — party too small to continue."`
- **PartyCommand.kt** `handleKick()`: Include kicker name in reason strings

**Tests:** Add to `PartyCommandTest.kt` — verify reason strings contain the departing player's name

### 2D. Simplify loot — remove all assignments (#463, #467) ✅
**Design direction:** No round-robin assignment. All items AND coins drop to the ground. Players pick up manually, first-come-first-served.

- **GameLoop.kt** (~lines 1176-1269): Remove the party loot branch entirely:
  - Remove round-robin `nextLootRecipient()` calls
  - Remove `"$member's turn: $itemName"` messages
  - Remove coin auto-split logic (the `membersInRoom > 1` branch that divides coins)
  - All loot (items + coins) goes through the same ground-drop path as solo kills
- **PartyService.kt**: Remove `nextLootRecipient()` method and `lootRotation` map (dead code after this change)
- **GameConfig.kt**: Remove `LOOT_PRIORITY_TICKS` constant (no longer used)
- **RoomItemManager** (if it has assignment tracking): Remove assignment/priority fields from ground items

**Tests:** Update `LootDistributionTest.kt` — verify party kills drop all items and coins to ground; verify no assignment messages sent; verify any player can pick up immediately

### 2E. Populate party invite with class/level (#475 server side) ✅
- **PartyCommand.kt** `handleInvite()` (~line 37): Populate `inviterLevel` and `inviterClass` from `session.player`

**Tests:** Add to `PartyCommandTest.kt` — verify `PartyInviteReceived` contains inviter's level and class

### 2F. Party promote command (#476 server side) ✅
- **PartyService.kt**: Add `promoteLeader(promoterName, targetName)` method — validates leader-only, updates `party.leaderId`
- **PartyCommand.kt**: Add `handlePromote()` — broadcasts `PartyLeaderChanged` + system message to all members
- **CommandProcessor.kt**: Add routing for `ClientMessage.PartyPromote`

**Tests:** Add to `PartyCommandTest.kt` — promote success, not-leader rejection, target-not-in-party rejection

## Phase 3: Client UI Polish (all client-only, can parallelize) ✅

### 3A. Numeric HP/MP values in party panel (#477) ✅
- **PartyPanel.kt**: Added `Text("${member.currentHp}/${member.maxHp}")` beside HP bar, same for MP
- 9sp gray text in a Row with the bar

### 3B. HUD overlay: fix truncation, add MP bar (#478) ✅
- **PartyHudOverlay.kt**: Changed `take(8)` to `take(12)`
- Added thin MP bar (3dp tall, blue) below the HP bar (5dp tall) when `maxMp > 0`

### 3C. Party chat visual distinction (#479) ✅
- **MudColors.kt**: Changed `partyChat` from `0xFF55AAFF` to `0xFF00FFCC` (bright cyan-green)
- **GameScreen.kt** SayBar: Shows `"Say... (/p for party)"` hint when in party
- **GameScreen.kt** SayBar: Shows `"PARTY"` chip/badge in cyan-green when `/p ` prefix detected

### 3D. Different-room member indicator (#480) ✅
- **PartyPanel.kt**: Threaded `playerRoomId`. Dims members in different rooms (gray text), shows `"(elsewhere)"` label
- **PartyHudOverlay.kt**: Threaded `playerRoomId`. Applies `alpha(0.4f)` to rows in different rooms

### 3E. Leave Party confirmation dialog (#482) ✅
- **PartyPanel.kt**: Added `showLeaveConfirmation` state with confirmation overlay following SettingsPanel guest-logout pattern

### 3F. Party panel action buttons (#476 client side) ✅
- **PartyPanel.kt**: Added Follow and Promote buttons for each non-self member
- **GameViewModel.kt**: Added `promoteToLeader(targetName)` method sending `ClientMessage.PartyPromote`
- **GameViewModel.kt**: Already handles `PartyLeaderChanged` (done in Phase 2)

### 3G. Party invite modal: show class/level (#475 client side) ✅
- **PartyInviteDialog.kt**: Shows `"Lv.${invite.inviterLevel} ${invite.inviterClass}"` below inviter name when class is not empty

## Verification

1. `./gradlew :shared:jvmTest :server:test` — all new + existing tests pass
2. `./gradlew :client:compileDebugKotlin` — client compiles with protocol changes
3. Playtest party formation, invite (verify class/level shown), promote, leave (verify confirmation), disband (verify reason text)
4. Kill NPC in party — verify items AND coins drop to ground, no assignment messages, any member can pick up
5. Die in forest, verify no stale commands execute at Temple after respawn
6. Create Gnome Mage — verify HP >= 15
7. Visual check: party overlay shows MP bars, 12-char names, dims remote members; party panel shows numeric HP/MP; party chat is cyan-green with input hint

## Issue Tracker

| Issue | Phase | Scope |
|-------|-------|-------|
| #472 post-death commands | 2A | Server |
| #468 Gnome Mage HP | 2B | Server |
| #469 disband messaging | 2C | Server |
| #463 loot enforcement | 2D | Server (remove) |
| #467 loot auto-pickup | 2D | Server (remove) |
| #475 invite modal info | 1A + 2E + 3G | Full stack |
| #476 action buttons + promote | 1B-C + 2F + 3F | Full stack |
| #477 numeric HP/MP | 3A | Client |
| #478 overlay truncation/MP | 3B | Client |
| #479 party chat distinction | 3C | Client |
| #480 different room indicator | 3D | Client |
| #482 leave confirmation | 3E | Client |
| #481 XP powerleveling | **DEFERRED** | Game design session |
