---
name: Driving Compose for Web text inputs from Playwright
description: How to type into Compose canvas inputs when there is no DOM input element
type: reference
---

The NeoMud WASM client uses Compose for Web rendering to canvas. There are NO DOM input elements -- `document.querySelectorAll('input')` returns empty. Standard Playwright `browser_type` won't work because there is no ref to target.

Working pattern for game commands:
1. Click the "Say..." input area at the bottom of the screen via `browser_mouse_click_xy(870, 650)` (approximate center of the input field)
2. Type the command using `browser_run_code_unsafe` with `page.keyboard.type('/command args', { delay: 30 })`
3. Click the "Say" button at `browser_mouse_click_xy(1163, 650)` to submit

Important: The input field is a "Say" (chat) input. To send game commands (as opposed to chat messages), prefix with `/`. For example:
- `/teleport bone_wastes:glass_edge` -- admin teleport
- `/godmode` -- toggle invincibility
- `/help` -- list admin commands
- `/spawn npc:bone_stalker` -- spawn NPC

Without the `/` prefix, text is sent as a chat message (`BoneWalker says: "..."`).

Single-character `browser_press_key` works for typing but is very slow. The `browser_run_code_unsafe` + `page.keyboard.type()` approach is much faster. Use `page.waitForTimeout(300)` for delays between steps (not `setTimeout`).

For Tab/Enter/Escape navigation between fields and submission, plain `browser_press_key` works as expected, but Enter may not always submit in the Compose canvas -- clicking the "Say" button is more reliable.

Note: the admin command is `/teleport` (full word), not `/tp`.
