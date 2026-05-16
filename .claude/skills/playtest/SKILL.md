---
name: playtest
description: Launch an AI playtester to play NeoMud via the WebSocket relay and report findings
context: fork
agent: playtester
---

# Playtest NeoMud

Launch a playtesting session using the WebSocket game relay. The playtester agent will read game state from text, send commands via JSON files, and provide feedback from a real player's perspective.

## Instructions

$ARGUMENTS

If arguments were provided above, focus your testing session on that specific area (e.g., "combat system", "character creation", "shop interactions"). Play naturally but concentrate your effort and feedback on the requested system.

If no arguments were provided, run an exploratory session: create a character, explore the world, fight monsters, check inventory, try skills — play as a curious new player would.

## Prerequisites

First, check if the game server is running:

```bash
curl -s http://localhost:8080/health
```

If the health check fails, inform the user that the game server needs to be running (`./gradlew :server:run`).

Then check if a relay is already running by reading `scripts/relay-state.json`. If the relay is running and logged in, you can use the existing session.

If no relay is running, **ask the user** how they'd like to connect:

1. **Login with existing account** — ask for username and password
2. **Register a new character** — ask for username, password, character name, class, race, and gender
3. **Use an existing relay-state.json** — if the user already has a relay running externally

Start the relay accordingly:

```bash
# Login (local server)
node scripts/game-relay.mjs <username> <password> &

# Register (local server)
node scripts/game-relay.mjs --register <username> <password> <charName> <class> [race] [gender] &

# Login (staging — specify world by ID)
node scripts/game-relay.mjs --url "wss://stage.neomud.app/worlds/<worldId>/game" <username> <password> &

# Register (staging)
node scripts/game-relay.mjs --url "wss://stage.neomud.app/worlds/<worldId>/game" --register <username> <password> <charName> <class> [race] [gender] &
```

**Staging world IDs** (look up current IDs via `curl -s https://stage-api.neomud.app/api/v1/worlds`):
- Default World: `cmno7e7ha000216nwbzly1q8n`
- Shattered Reach: `cmnqrhljj000016n1f15es4rl`

Wait a few seconds for connection, then verify:

```bash
sleep 3
```

Read `scripts/relay-state.json` to confirm `connected` and `loggedIn` are true.

## Session Flow

1. Read the relay state to see the current game state
2. Play the game following your methodology — read state, send commands, evaluate results
3. Document bugs, UX issues, and impressions as you go
4. **Shut down the relay** (MANDATORY — see "Session Cleanup" in the agent definition):
   ```bash
   if [ -f scripts/relay.lock ]; then
     kill "$(cat scripts/relay.lock)" 2>/dev/null; sleep 1
     [ -f scripts/relay.lock ] && kill -9 "$(cat scripts/relay.lock)" 2>/dev/null && rm -f scripts/relay.lock scripts/relay-state.json
   fi
   ```
5. End with a structured playtest report
