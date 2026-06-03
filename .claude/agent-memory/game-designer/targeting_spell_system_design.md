---
name: targeting-spell-system-design
description: Design decisions for #450/#451 party healing, group buffs, NPC AoE -- target types, spell balance ratios, threat model, NPC ability system
metadata:
  type: project
---

## Targeting Enhancement Design (June 2026)

**Decision**: 4 target types total: ENEMY, SELF, ALLY (single friendly in room), PARTY_ROOM (all party in room).
- ALLY includes non-party players (encourages social play)
- PARTY_ROOM degrades to SELF when solo (clean fallback)
- No ENEMY_ROOM for players (would trivialize multi-mob encounters)

**Why:** Minimal set that enables tank/healer/DPS role differentiation in 4-player parties.

**How to apply:** All new heals/buffs use these target types. NPC AoE uses separate ability system, NOT player spell catalog.

## Key Balance Ratios
- ALLY heals: same formula as SELF heals (no penalty -- targeting flexibility is the value)
- PARTY_ROOM heals: ~55% of single-target per person, 40% more mana, longer cooldown
- AoE boss damage: ~40-60% of single-target melee per player, with AGI/WIL/STR saves (DC scaling with level)
- Heal threat: adds healer to NPC's engagedPlayerIds (uses existing 80% engaged weight)

## 6 New Spells Proposed
1. Mend Wounds (priest T1, ALLY heal) -- bread-and-butter party heal
2. Rejuvenation (druid T2, ALLY HoT) -- Druid's distinct heal identity
3. Prayer of Mending (priest T3, PARTY_ROOM heal) -- group heal for boss AoE
4. War Hymn (bard T3, PARTY_ROOM STR buff) -- Bard party identity
5. Soothing Aura (bard T2, PARTY_ROOM HoT) -- Bard off-healer
6. Bark Skin (druid T2, ALLY AGI buff) -- Druid tank support

## NPC Ability System (Separate from Player Spells)
- JSON array on NPC definitions: `abilities` with damage, cooldown, phaseRequired, save mechanics
- Independent from player spell formula (no stat dependency on NPCs)
- Phase-gated: bosses unlock AoE in later phases
- Resolves in CombatManager NPC combat phase, after melee attack

## Implementation Priority
Phase 1: ALLY + Mend Wounds (highest impact)
Phase 2: PARTY_ROOM + group spells
Phase 3: Druid ALLY spells
Phase 4: NPC ability system + boss AoE

Related: [[party-system-design]]
