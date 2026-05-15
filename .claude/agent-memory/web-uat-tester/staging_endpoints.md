---
name: Staging endpoints and asset proxy paths
description: Exact public URLs for stg environment and the working asset-proxy endpoint shape
type: reference
---

Public stg hosts (verified 2026-04-24):
- Marketplace: https://stage.neomud.app
- Maker: https://stage-maker.neomud.app/maker/  (note trailing /maker/ — Caddy strips that prefix)
- Platform API: https://stage-api.neomud.app  (health at /api/v1/health, anon auth at POST /api/v1/auth/anonymous)
- WASM client: https://stage.neomud.app/client/index.html (loaded after Enter World)

Asset proxy (confirmed working, no auth needed for public worlds):
- GET https://stage.neomud.app/api/v1/worlds/{worldId}/bundle-assets/{assetPath}
- Returns 200 for images, 206 (partial) for audio
- This is what the marketplace world-detail page uses for preview audio/images

Asset proxy that does NOT exist (any client trying this gets 400):
- /worlds/{worldId}/game/... — that path is the WSS upgrade endpoint only

Anonymous auth flow:
- POST https://stage-api.neomud.app/api/v1/auth/anonymous (no body needed)
- Returns {accessToken, ...} where accessToken is a JWT with role=GUEST, exp ~15 min
- IP-rate-limited; from a single IP you can exhaust the limit fast during UAT (60/min)
