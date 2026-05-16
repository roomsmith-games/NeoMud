---
name: qa-test
description: Launch a QA tester to systematically find bugs, test edge cases, and audit game systems. Use when you want thorough bug-hunting beyond what playtesting covers.
context: fork
agent: qa-tester
---

# QA Test NeoMud

Launch a QA testing session. The QA tester reads source code, analyzes data files, plays the game via relay, and files GitHub issues for every bug found.

## Instructions

$ARGUMENTS

If arguments were provided above, focus your QA session on that specific system (e.g., "combat", "vendors", "spells", "persistence", "protocol", "inventory", "economy"). Do a deep dive — read all related code, trace error paths, test boundaries, and file bugs.

If no arguments were provided, run a full audit across all major systems.

## Prerequisites

If you plan to do live testing (not just code audit), check if the game server is running:

```bash
curl -s http://localhost:8080/health
```

If the health check fails, inform the user that the game server needs to be running (`./gradlew :server:run`).

**Always rebuild the world bundle before testing:**

```bash
./gradlew packageWorld --rerun-tasks
```

## Session Flow

1. **Scope** — Determine what systems to test (from arguments or full audit)
2. **Read** — Study the source code for the target systems
3. **Analyze** — Identify suspicious code paths, missing validations, edge cases
4. **Test** — Verify bugs via relay play or by reading test coverage gaps
5. **File** — Create GitHub issues for every confirmed bug
6. **Shut down the relay** (MANDATORY if you started one):
   ```bash
   if [ -f scripts/relay.lock ]; then
     kill "$(cat scripts/relay.lock)" 2>/dev/null; sleep 1
     [ -f scripts/relay.lock ] && kill -9 "$(cat scripts/relay.lock)" 2>/dev/null && rm -f scripts/relay.lock scripts/relay-state.json
   fi
   ```
7. **Report** — Produce structured QA report with risk assessment
