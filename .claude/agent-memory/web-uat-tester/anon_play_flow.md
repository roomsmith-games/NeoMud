---
name: Anonymous (guest) play flow shape on stg
description: What the post-#32 guest journey actually looks like screen-by-screen, plus a known asset-loading defect
type: project
---

End-to-end anon flow on stg as of 2026-04-24 (after #32 landed):

1. Marketplace landing → click 'NeoMud Default World' → world detail page
2. World detail shows green 'Server Online' and gold 'Enter World' button (no Sign In required)
3. Enter World → navigates to /client/index.html, WASM loading screen ('NeoMud Default World v1.0.0' with progress bar, ~10s on stg)
4. Loading complete → 'Guest Character' title (NOT 'Create Character') with 6-step wizard: Name → Gender → Race → Class → Stats → Review
5. Initial modal: 'Guest Play' disclaimer reading 'Guest characters are temporary. All progress, items, and XP will be permanently lost when you disconnect.' with Continue as Guest / Back / 'Sign in to save your progress' link
6. Each subsequent wizard step also repeats 'Guest characters are temporary. Your progress will not be saved.'
7. Review step shows assembled char card with 'Play as Guest' (green) button
8. Click → land in 'Temple of the Dawn' room with 'Welcome to NeoMud!' tutorial modal
9. Walking N reaches 'Town Square' (Millhaven) where Guildmaster Aldric is the trainer

**Known defect (issue #297):** the WASM client builds asset URLs by appending the path to its WSS URL string (which carries `?token={ws_jwt}`). Result: every asset HTTP GET goes to `/worlds/{id}/game?token={jwt}/assets/...` and 400s. Game logic/text fully works; only the visual layer is broken (black canvas, no music). The platform's working asset proxy is `/api/v1/worlds/{id}/bundle-assets/{path}` — the marketplace already uses it successfully.

**Why:** the play-token plumbing introduced for #32 leaked the WSS query string into the HTTP base URL.

**How to apply:** any future verification of #32-related fixes must check the asset network log, not just whether gameplay text is reachable. A passing 'guest can play' assertion is necessary but not sufficient — confirm at least one asset 200 (room background or NPC sprite).
