---
name: Platform 429 body shape on stg
description: Confirmed JSON schema for rate-limited responses across the platform
type: reference
---

Platform rate-limit responses (verified on stg 2026-04-24, post-#33A fix):

```
HTTP/2 429
content-type: application/json
{"error":"Too many requests, please try again later","retryAfterSeconds":<int>}
```

`retryAfterSeconds` is a positive integer — the client can read it without parsing strings. Same shape returned across:
- POST /api/v1/auth/anonymous
- GET  /api/v1/worlds
- Generic 404 paths that fall through the general bucket (e.g., /health on the public host)

The marketplace consumes this on the world-list 429 path and renders 'The marketplace is busy right now. Please wait about N minutes and try again.' with a Retry button — that's #34 working as designed.

Note: the IP-based limit on the anon-auth endpoint can be exhausted during a single UAT session if you also probe with curl. After that you cannot mint another guest token from that IP for several minutes — re-test from a different IP or wait ~10 min.
