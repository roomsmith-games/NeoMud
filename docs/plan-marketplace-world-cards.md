# Marketplace World Card Enhancement Plan

## Goal

Three additions across the marketplace:
1. **Thumbnail images** — render each world's `coverImageUrl` on the listing cards
2. **Telnet discovery** — platform-level "Connect via MUD Client" section on the marketplace landing page (not per-world — the router handles all worlds)
3. **Social sharing** — share buttons on each world's detail page (world-specific)

---

## Decision Log

- **Thumbnails: top banner on card** (confirmed by UX review) — industry standard, works at all widths, degrades gracefully
- **Telnet: landing page, not detail page** — telnet connects to the platform router which presents a world menu interactively; putting it per-world would imply a 1:1 mapping that doesn't exist
- **Sharing: detail page only** — sharing is world-specific; cards stay clean (rating + featured badge only)
- **No action icons on world cards** — UX review confirmed cards should focus on "is this worth playing?", not "how do I play this?"

---

## Current State

- `WorldSummary.coverImageUrl: string | null` already exists in the API type — wired end-to-end (Prisma `coverImage`, API serialization, TypeScript type). Just needs a render site in `WorldCard.tsx`.
- `WorldCard.tsx` uses inline styles throughout (no Tailwind, no CSS modules).
- No icon library in `web/package.json` — need to add `lucide-react` and `react-icons`.
- Stone-frame aesthetic: dark leather/void backgrounds, burnished gold accents, `stone-frame` CSS class.

---

## Dependencies

Add to `web/package.json`:

```
lucide-react   ^0.475.0   # Terminal, Share2, Copy, Link, Check icons
react-icons    ^5.x       # Brand icons: FaXTwitter, FaFacebookF, FaRedditAlien
```

---

## Phase 1: World Thumbnails (WorldCard)

Add a banner image above card content when `coverImageUrl` is non-null. No other card changes.

```
┌─────────────────────────────────────┐  ← stone-frame border
│ [thumbnail — 16:9, full card width] │  ← new; 120px / 160px featured
│  or [gradient placeholder + ⚔ 0.25]│
├─────────────────────────────────────┤
│ Name                        v1.2.1  │  ← existing, unchanged
│ by Creator                          │
│ Description (2–3 lines)             │
│ ★★★★☆ (42)         ✦ Featured      │  ← existing, unchanged
└─────────────────────────────────────┘
```

- `<img>` with `objectFit: cover`, heights 120px (normal) / 160px (featured)
- Wrapper div with `overflow: hidden` to clip to card border-radius
- Placeholder: `deepVoid` → `frameDark` gradient + centered ⚔ at `burnishedGold` opacity 0.25
- No broken image state — `onError` swaps to the placeholder

**Files changed:**
- `web/src/components/WorldCard.tsx`

---

## Phase 2: Telnet Discovery (marketplace landing page)

A platform-level "Connect via MUD Client" panel on the main marketplace/landing page — positioned below the world listing, above the footer. It describes the telnet router which serves all worlds.

```
  ─── Also playable via MUD client ──────────────────────────────
  │                                                              │
  │  NeoMud supports any telnet-capable MUD client.             │
  │  Connect to the platform and choose your world interactively.│
  │                                                              │
  │  ┌──────────────────────────┐  [ ⎘ Copy ]                  │
  │  │  telnet neomud.app 4000  │  ✓ Copied! (2s confirmation) │
  │  └──────────────────────────┘                               │
  │                                                              │
  │  Recommended clients:                                        │
  │  [ Mudlet ↗ ]  [ TinTin++ ↗ ]  [ BlowTorch (Android) ↗ ]  │
  │                                                              │
  └──────────────────────────────────────────────────────────────┘
```

- Styled as an inset stone panel (recessed border, `frameDark` background)
- Copy button: `navigator.clipboard.writeText('telnet neomud.app 4000')` → 2s "✓ Copied" state, then resets
- Client links open in new tab: mudlet.org, tintin.sourceforge.net, play.google.com (BlowTorch)
- `Terminal` icon (lucide-react) as section header icon
- No modal needed — inline display on a full-width page section has room

**Files changed:**
- `web/src/components/TelnetConnectPanel.tsx` — new standalone component
- `web/src/pages/LandingPage.tsx` (or equivalent marketplace listing page) — add `<TelnetConnectPanel>` below world listing

---

## Phase 3: Social Sharing (world detail page)

Share buttons on the detail page for each world, below the Play button. World-specific — each world has its own shareable URL.

```
  World detail page:

  [ ▶  Play in Browser ]

  Share this world:
  [ 🔗 Copy link ]  [ 𝕏 X / Twitter ]  [ f Facebook ]  [ 👾 Reddit ]

  ─────────────────────────────────────────
  [description, ratings, comments...]
```

- Mobile: try `navigator.share({ title, text, url })` first; show button row if Web Share API unavailable
- **Share URL:** `https://neomud.app/worlds/{slug}`
- **Share text:** `"{World Name}" — a text MUD on NeoMud`
- Copy link → 2s "✓ Copied" confirmation
- X, Facebook, Reddit open platform share URLs in new tab
- Discord: no platform share URL — copy link covers it

**Platform URLs:**
| Button | Action |
|---|---|
| Copy link | `navigator.clipboard.writeText(url)` |
| X/Twitter | `https://x.com/intent/tweet?url={url}&text={text}` |
| Facebook | `https://www.facebook.com/sharer/sharer.php?u={url}` |
| Reddit | `https://www.reddit.com/submit?url={url}&title={text}` |

**Files changed:**
- `web/src/components/ShareButtons.tsx` — new
- `web/src/pages/WorldDetailPage.tsx` (or equivalent) — add `<ShareButtons>` below Play button

---

## Phase 4: Tests

| Test | File | Verifies |
|---|---|---|
| Thumbnail renders when `coverImageUrl` set | WorldCard.test.tsx | `<img>` present with correct src |
| Placeholder renders when no `coverImageUrl` | WorldCard.test.tsx | No `<img>`, placeholder element present |
| Copy button shows "✓ Copied" for 2s then resets | TelnetConnectPanel.test.tsx | State transition on click |
| Telnet copy writes correct string to clipboard | TelnetConnectPanel.test.tsx | `navigator.clipboard.writeText` called with `"telnet neomud.app 4000"` |
| Client links open in new tab | TelnetConnectPanel.test.tsx | `target="_blank"` on each anchor |
| Share copy link writes world URL to clipboard | ShareButtons.test.tsx | Clipboard called with `neomud.app/worlds/{slug}` |
| X/Twitter button opens correct URL | ShareButtons.test.tsx | href contains x.com/intent/tweet |
| Facebook button opens correct URL | ShareButtons.test.tsx | href contains facebook.com/sharer |
| Reddit button opens correct URL | ShareButtons.test.tsx | href contains reddit.com/submit |
| Mobile: `navigator.share()` called when available | ShareButtons.test.tsx | API called instead of showing buttons |

---

## Files Changed Summary

| File | Change |
|---|---|
| `web/package.json` | Add `lucide-react`, `react-icons` |
| `web/src/components/WorldCard.tsx` | Add thumbnail banner |
| `web/src/components/TelnetConnectPanel.tsx` | **NEW** — platform telnet section |
| `web/src/components/ShareButtons.tsx` | **NEW** — world share buttons |
| `web/src/pages/LandingPage.tsx` | Add `<TelnetConnectPanel>` |
| `web/src/pages/WorldDetailPage.tsx` | Add `<ShareButtons>` |
| `web/src/__tests__/WorldCard.test.tsx` | Thumbnail + placeholder tests |
| `web/src/__tests__/TelnetConnectPanel.test.tsx` | **NEW** |
| `web/src/__tests__/ShareButtons.test.tsx` | **NEW** |

---

## Out of Scope

- Thumbnail upload UI in the maker or marketplace — `coverImageUrl` is already set by the world publisher workflow; this plan only adds the render site
- WhatsApp, LinkedIn, Mastodon sharing — extensible later
- Analytics on share events — deferred
- Deep links that pre-select a world after telnet login — separate initiative
