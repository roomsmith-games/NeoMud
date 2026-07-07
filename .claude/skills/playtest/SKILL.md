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

## Connection Mode

Determine which connection mode to use:

- **If testing the telnet client** (text rendering, command parsing, help system, prompt display): use **Telnet mode**
- **For general gameplay testing** (combat, inventory, quests, balance): use **WebSocket relay mode**
- **If the user specified "telnet"** in their arguments: use Telnet mode
- **If unsure**: ask the user — "Should I connect via telnet (tests the text client) or via the WebSocket relay (JSON state, more visibility into game data)?"

---

### Telnet Mode

Check if already running:
```bash
[ -f scripts/telnet.lock ] && echo "Already running" || echo "Not running"
```

If not running, ask the user for credentials (username + password for an existing character — no registration via telnet). Then start:

```bash
# Local server (default port 4000)
node scripts/telnet-client.mjs &

# Staging
node scripts/telnet-client.mjs stage.neomud.app 4000 &
```

Wait a few seconds, then read `scripts/telnet-recent.txt` to confirm the welcome banner appeared.

The login flow is text-driven — follow the prompts:
```bash
sleep 3
# Read telnet-recent.txt — should see the welcome banner
echo -n "<username>" > scripts/telnet-command.txt
sleep 1
# Read telnet-recent.txt — should see "Password: "
echo -n "<password>" > scripts/telnet-command.txt
sleep 2
# Read telnet-recent.txt — should see room description and prompt
```

---

### WebSocket Relay Mode

Check if already running by reading `scripts/relay-state.json`. If running and logged in, you can use the existing session.

If no relay is running, **ask the user** how they'd like to connect:
1. **Login with existing account** — ask for username and password
2. **Register a new character** — ask for username, password, character name, class, race, and gender
3. **Use staging** — connects to `stage.neomud.app`

```bash
# Login (local server)
node scripts/game-relay.mjs <username> <password> &

# Register (local server)
node scripts/game-relay.mjs --register <username> <password> <charName> <class> [race] [gender] &

# Login (staging)
node scripts/game-relay.mjs --staging default-world &

# Register (staging)
node scripts/game-relay.mjs --staging default-world --register TestChar WARRIOR HUMAN male &
```

Wait a few seconds, then read `scripts/relay-state.json` to confirm `connected` and `loggedIn` are true.

---

## Session Flow

1. Connect using the appropriate mode above
2. Play the game following your methodology — read state/output, send commands, evaluate results
3. Document bugs, UX issues, and impressions as you go
4. **Shut down the client** (MANDATORY — see "Session Cleanup" in the agent definition)
5. End with a structured playtest report
