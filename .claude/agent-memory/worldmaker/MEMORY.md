# WorldMaker Agent Memory

## Maker Editor Notes

### Layout
- Three-panel layout: left sidebar (nav), middle (list or map), right (properties panel)
- Top toolbar: Save As, Switch Project, Validate, Export .nmd, Package .nmd, Quit Server
- Navigation: Zones, Items, NPCs, Classes, Races, Skills, Spells, Default Players, Default SFX, Settings
- Consistent list+detail pattern across all entity types
- Default Players has sprite gallery with filter dropdowns (race/gender/class)
- Default SFX has category-filtered list with colored status dots

### Known Issues (as of Session 9 - 2026-05-14)
- CRITICAL: Staging Maker .nmd import drops ALL exits -- 290 rooms x 0 exits (Issue #384)
- CRITICAL: No zone defines a spawnRoom on staging (Issue #358)
- 290 rooms missing background image assets (Issue #362)
- 206 items missing sprite assets (Issue #363)
- 113 NPCs missing sprite assets (Issue #365)
- 270 PC sprites missing assets (Issue #366)
- 747 missing SFX audio assets (Issue #368)
- 11 hostile NPCs in Glass Desert/Mirage Spire have no loot drops (Issue #370)
- 23 items use invalid slot 'none' (Issue #372)
- Patrol NPC charred_treant has empty patrol route (Issue #373)
- Room ashwood:wraith_hollow uses unknown effect type 'chill' (Issue #375)
- Marketplace description references nonexistent Millhaven zone (Issue #382)
- 11 hostile NPCs in Glass Desert/Mirage Spire have zero accuracy/defense/evasion (Issue #385)

### Known Issues (as of Session 8 - 2026-03-06)
- Canvas room hit-testing misaligned at ALL DPR values (Issue #106) -- regression from Issue #69 fix
- World map: clicking dimmed room tries to create new room instead of switching zones (Issue #125)
- World map: no way to deselect zone and return to unified view (Issue #126)
- World map: clicking rooms in unselected view does nothing (Issue #127)
- World map: room creation allowed at coordinates occupied by other zones (Issue #128) -- CRITICAL
- World map: no zone labels or color differentiation in unified view (Issue #129)
- World map: layer switch doesn't center on target layer rooms (Issue #130)
- NPC Behavior Type dropdown missing Wander and Trainer options (Issue #122)
- React CSS warning: borderColor/border shorthand conflict on NPC map mode changes (Issue #123)
- NPC Name field placeholder says "Goblin Guard" instead of generic text (Issue #124)
- Package .nmd uses native confirm() instead of proper modal (Issue #100)
- NPC list placeholder uses lowercase 'npc' (Issue #98)
- No server-side input validation/sanitization (Issue #90)
- Room creation allows overlapping coordinates within same zone (Issue #91)
- API key visible in accessibility tree + API response despite visual masking (Issue #82)
- Class/NPC editors use raw JSON for structured data (Issue #75)
- NPC creature image dimension labels show humanoid maximums (Issue #74)
- Search placeholder says "Search Classs..." on Classes page (Issue #114)

### Fixed Since Session 3
- Room ID double zone-prefix (Issue #87) -- FIXED
- Empty entity name validation (Issue #88) -- FIXED
- Grammar "A entity/item" in error messages (Issue #89) -- FIXED
- Validation results shown in browser alert() (Issue #81) -- FIXED (Validate button uses modal now)
- Grammar "a npc" in placeholder (Issue #83) -- FIXED in error messages but NOT in list placeholder
- Search/filter on entity lists (Issue #85) -- FIXED (all entity pages now have search box)
- Raw JSON error messages (Issue #86) -- FIXED (friendly messages like "Item ID is required")
- New Item form placeholder misleadingly shows 'Iron Sword' (Issue #97) -- FIXED (now "e.g. iron_sword")
- Validator checks old audio/sfx/ path (Issue #108) -- FIXED (now checks type-based subdirectories)

### Audio Subdirectory Mapping (Session 5)
- SfxPreview component correctly resolves audio paths by entity type:
  - NPC sounds (attackSound, missSound, deathSound, interactSound, exitSound) -> audio/npcs/
  - Item sounds (attackSound, missSound, useSound) -> audio/items/
  - Spell sounds (castSound, impactSound, missSound) -> audio/spells/
  - Room departSound -> audio/rooms/
  - Default SFX mapping: combat/loot -> audio/general/, item -> audio/items/, magic -> audio/spells/, movement -> audio/rooms/
- Asset-mgmt history requests also use the correct subdirectory paths
- Validator now correctly checks type-based subdirectories (Issue #108 FIXED)

### Fixed in Earlier Sessions
- Opening existing projects (Issue #79)
- Room creation on new projects (Issue #80)
- Loot Tables nav missing (Issue #84) -- intentionally removed, merged into NPC model
- DPI canvas hit-testing (Issue #69)
- Duplicate Image Prompt fields (Issue #72)
- Item image defaults (Issue #73)
- Room creation/deletion confirmations (Issues #66, #67)
- Deleted rooms in exit dropdown (Issue #68)
- Grammar "a item" in placeholder (Issue #71)
- Debug console.log spam (Issue #65)
- Missing favicon (Issue #64)
- Cross-zone exit names show raw IDs (Issue #70)

### NPC Editor Features (Session 7)
- 3-panel layout: NPC list (left), zone map (center), form (right)
- Map Mode toolbar: View, Set Start, Edit Patrol (patrol NPCs only), Edit Spawns
- Set Start mode: orange helper text, click room to set, Pick button highlights
- Edit Spawns mode: toggle rooms as spawn points, "Done Editing Spawns" button in form
- Edit Patrol mode: numbered route badges on map, dashed purple lines showing path
- Patrol Route list in form: numbered entries with reorder arrows and delete buttons
- Spawn Points section: shows list of spawn rooms or "defaults to start room"
- Start room shown with gold border + gold star on map
- Behavior-specific form sections: Patrol Route for patrol, Vendor Items for vendor
- NPC list shows behavior type + zone subtitle (e.g., "wander - Whispering Forest")
- NPC search filters list by name (case-insensitive)
- Room-level Max Hostile NPCs field in Zone Editor (after Depart Sound, before Effects)

### World Map View (Session 8)
- No zone selected: unified grid showing ALL zones' rooms (all same blue, no labels)
- Zone selected: selected zone at full opacity, others dimmed gray with zone name labels
- Cross-zone exit labels shown in orange text with orange marker arrows
- Exit arrows: green between rooms in selected zone, gray in world map view
- Zone deletion now has confirmation dialog (Issue #99 FIXED)
- New zone creation auto-selects and shows empty map with dimmed other zones
- Layer navigation only appears when selected zone has multiple layers
- Console stays clean -- no JS errors during zone switching, creation, deletion

### What Works Well
- Zone map rendering with room boxes, exit arrows, cross-zone labels
- Layer navigation (up/down buttons for multi-level zones like Tavern Cellar)
- Dynamic form fields based on item category (weapon fields appear when category set)
- Room interactable editor (lever/trapdoor with action types, stat checks, perception DC)
- Hidden exit system with perception DC, lock DC, re-hide/re-lock timers
- Exit lock system with DC, unpickable checkbox, re-lock timer
- Room rename feature (enables button when ID text changes)
- Forking read-only projects
- Default Players sprite gallery with 270 sprites and filtering
- Default SFX with color-coded category indicators
- Audio preview/player inline for BGM tracks
- Depart Sound dropdown with human-readable labels
- Export .nmd instant download
- Dependency upgrades (Express 5, jsdom 28, Prisma 7.4.2, react-router-dom 7.13.1) verified stable -- no regressions
- Item creation flow: create -> auto-switch to edit mode
- Validation modal dialog (improved from alert())
- Responsive layout degrades gracefully at narrow widths
- Room effects editor (HEAL/POISON/DAMAGE/MANA_REGEN/MANA_DRAIN/SANCTUARY)
- Start Room dropdown shows "Zone > Room" format for clarity
- Target Room dropdown in exits shows "Name (zone:id)" format

### Staging Maker (Session 9 - 2026-05-14)
- Staging URL: https://stage.neomud.app/maker
- Staging API base: /maker-api/ (NOT /api/ or /maker/api/)
- Platform auth: POST /api/v1/auth/login with {email, password} returns JWT
- Auth tokens in localStorage: neomud_access_token, neomud_refresh_token, neomud_user
- _default_world is Published v1.0.1 with 19 zones, 290 rooms, 206 items, 113 NPCs
- Root cause of zero exits: .nmd import to Maker DB drops exit data (room content intact)
- Game server loads .nmd directly, so players may not be affected by Maker DB issue
- Platform login rate limits aggressively -- wait 60s between failed attempts
- Validation modal shows 1,957 warnings across 8 categories

### API Endpoint Map
- `/api/projects` - GET list, POST create
- `/api/projects/:name/open` - POST to open/switch
- `/api/projects/:name/fork` - POST with {newName}
- `/api/zones` - GET list, POST create
- `/api/zones/:id/rooms` - GET rooms, POST create room
- `/api/rooms/:roomId/exits` - POST create exit (NOT nested under zones)
- `/api/items` - GET list, POST create
- `/api/items/:id` - PUT update, DELETE
- `/api/npcs` - GET list, POST create
- `/api/races` - GET list
- `/api/skills` - GET list
- `/api/spells` - GET list
- `/api/default-sfx` - GET list
- `/api/settings` - GET (exposes API keys!)
- `/api/export/nmd` - GET download .nmd bundle

### Interaction Tips
- Room boxes on zone map are canvas-rendered, not DOM elements
- DPI canvas hit-testing BROKEN at all DPR values (Issue #106) -- cannot click rooms on canvas map
- Right panel scrolls independently from map area
- Export endpoint is `/api/export/nmd` (not `/api/export`)
- Room creation now has a confirm dialog
- Forking _default_world is the best way to get a project with real data to test
- When creating rooms, send just the local ID (e.g., "hallway"), not "zone:hallway"
- Zone deletion cascades to rooms and NPCs correctly
- Layer navigation buttons auto-disable when at top/bottom layer
- Room properties panel persists last selected room when switching layers (not auto-cleared)
