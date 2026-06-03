# Terminal Client Vision

## Overview

Build a full terminal-based game client for NeoMud, evolving from the existing relay script (`scripts/game-relay.mjs`) into a rich TUI experience. The client would connect directly via WebSocket and render the game in a terminal, providing an authentic MUD experience alongside the existing Compose (mobile) and WASM (web) clients.

## Current State

The relay script (`scripts/game-relay.mjs`) is a WebSocket proxy that:
- Maintains a persistent connection to the game server
- Tracks full game state (player, room, NPCs, inventory, combat, party, effects)
- Exposes state via `relay-state.json` for AI agent consumption
- Accepts commands via `relay-command.json`
- Handles all ServerMessage types (21 handlers added in the relay coverage pass)
- Supports multi-instance via `--id` for multiplayer testing

This gives us a complete client-side state machine. What's missing is rendering and input.

## Architecture Options

### Option A: Node.js TUI (Ink/Blessed)

Extend the relay with a `--tui` flag that renders via a terminal UI framework.

- **Ink** (React for CLI) — declarative component model, familiar to React devs
- **Blessed** — lower-level, more control over terminal rendering, ncurses-like

Pros: Reuses existing relay code, single codebase, JS ecosystem
Cons: Node.js terminal libs are less mature than ncurses, performance ceiling for complex rendering

### Option B: Rust TUI (Ratatui)

New client in Rust using Ratatui (successor to tui-rs).

Pros: Fast, small binary, great terminal rendering, cross-platform, real ncurses-level control
Cons: New codebase, no code sharing with existing relay, needs WebSocket client library

### Option C: Kotlin Terminal Client

Extend the existing KMP shared module with a terminal frontend using Lanterna or Mordant.

Pros: Shares protocol types with server/client, single language across all clients
Cons: JVM startup overhead, terminal lib ecosystem less mature in Kotlin

## Proposed Layout

```
+--[NeoMud - Millhaven Town Square]--+--[Party]---+
|                                     | Thorgar  W |
| You stand in the bustling town     | HP ████░ 22|
| square of Millhaven. A weathered   | Elyndra  C |
| fountain sits at the center.       | HP ███░░ 16|
|                                     +------------+
| Exits: [N]orth [S]outh [E]ast     |             |
|                                     | [Minimap]  |
| NPCs: Guard Captain (friendly)     |   # . .    |
|       Merchant Giles (vendor)       |   . @ .    |
|                                     |   . . #    |
+-------------------------------------+------------+
| [Combat Log]                                     |
| Thorgar hits Shadow Wolf for 8 damage (14/22 HP) |
| Elyndra casts Smite on Shadow Wolf for 12 damage |
| Shadow Wolf was killed by Elyndra                |
| You gained 20 XP (party share)                   |
+--------------------------------------------------+
| > _                                              |
+--------------------------------------------------+
```

## Key Features

- Split-pane layout: room description, party panel, minimap, combat log, input
- Color-coded text (ANSI) for damage types, party chat, system messages
- Keyboard shortcuts for common actions (arrow keys for movement, number keys for targets)
- Slash command input (`/party invite Thorgar`, `/tell Elyndra hello`, `/attack`)
- Auto-follow visual indicator in party panel
- Scrollable combat log with search

## Prerequisites

Before starting the terminal client:
1. Complete relay protocol coverage (done)
2. Decide on architecture (A/B/C above)
3. Design the input command parser (slash commands → ClientMessage mapping)
4. Plan the rendering layout and responsive behavior for different terminal sizes

## Open Questions

- Should this be a standalone binary or stay as a Node.js script?
- What's the minimum terminal size to support?
- Should it support mouse input (clicking exits, NPCs) or keyboard-only?
- How to handle the minimap — ASCII art or Unicode box drawing?
- Sound support — terminal bell for alerts? External sound via a sidecar process?
