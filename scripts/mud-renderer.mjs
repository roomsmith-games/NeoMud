#!/usr/bin/env node
/**
 * mud-renderer.mjs — Renders ServerMessages to scrolling text for --interactive mode.
 *
 * All exported functions take a `useColor` flag. When false, output is plain
 * ASCII suitable for piping and log files. When true, VT100/ANSI escape codes
 * are emitted (compatible with all MUD clients and standard terminals).
 *
 * renderMessage(msg, state, useColor) → string[]   (lines, no trailing \n)
 * renderPrompt(state, useColor)       → string      (prompt line)
 * renderHelp(useColor)                → string[]
 * renderPartyState(party, useColor)   → string[]
 */

const A = {
  RESET: '\x1b[0m',
  BOLD: '\x1b[1m',
  RED: '\x1b[31m', BOLD_RED: '\x1b[1;31m',
  GREEN: '\x1b[32m', BOLD_GREEN: '\x1b[1;32m',
  YELLOW: '\x1b[33m', BOLD_YELLOW: '\x1b[1;33m',
  CYAN: '\x1b[36m', BOLD_CYAN: '\x1b[1;36m',
  WHITE: '\x1b[37m', BOLD_WHITE: '\x1b[1;37m',
  GRAY: '\x1b[90m',
  BLUE: '\x1b[34m',
  MAGENTA: '\x1b[35m',
};

function c(text, color, uc) {
  if (!uc || !color) return text;
  return `${color}${text}${A.RESET}`;
}

function hpBar(current, max, width = 10, uc = false) {
  if (!max || max <= 0) return '';
  const ratio = Math.max(0, Math.min(1, current / max));
  const filled = Math.round(ratio * width);
  const bar = '█'.repeat(filled) + '░'.repeat(width - filled);
  if (!uc) return `[${bar}]`;
  const color = ratio > 0.5 ? A.GREEN : ratio > 0.25 ? A.YELLOW : A.RED;
  return `${color}[${bar}]${A.RESET}`;
}

function coinStr(coins) {
  if (!coins) return '';
  const parts = [];
  if (coins.gold) parts.push(`${coins.gold}g`);
  if (coins.silver) parts.push(`${coins.silver}s`);
  if (coins.copper) parts.push(`${coins.copper}c`);
  return parts.join(' ');
}

function renderRoom(room, npcsInRoom, playersInRoom, groundItems, groundCoins, uc) {
  const lines = [];
  lines.push('');
  lines.push(c(`=== ${room.name} ===`, A.BOLD_CYAN, uc));
  if (room.description) lines.push(room.description);
  lines.push('');

  // Exits (locked exits shown with marker)
  const exits = room.exits || {};
  const lockedSet = new Set(Object.keys(room.lockedExits || {}).map(d => d.toUpperCase()));
  const exitKeys = Object.keys(exits);
  if (exitKeys.length > 0) {
    const exitStr = exitKeys.map(dir => {
      const d = dir.toLowerCase();
      return lockedSet.has(dir.toUpperCase()) ? c(`${d}(locked)`, A.YELLOW, uc) : d;
    }).join(' ');
    lines.push(c(`  [Exits: ${exitStr}]`, A.CYAN, uc));
  } else {
    lines.push(c('  [Exits: none]', A.GRAY, uc));
  }

  // NPCs
  if (npcsInRoom && npcsInRoom.length > 0) {
    const npcList = npcsInRoom.map(n => {
      const hostile = n.hostile ? c('*', A.RED, uc) : '';
      const hp = n.maxHp > 0 ? ` ${hpBar(n.hp, n.maxHp, 6, uc)}` : '';
      return `${hostile}${n.name}${hp}`;
    }).join(', ');
    lines.push(`  NPCs: ${npcList}`);
  }

  // Other players
  if (playersInRoom && playersInRoom.length > 0) {
    const playerList = playersInRoom.map(p => {
      const cls = p.class || p.characterClass || '';
      return `${p.name}${cls ? ' (' + cls + ' Lv' + p.level + ')' : ''}`;
    }).join(', ');
    lines.push(`  Players: ${playerList}`);
  }

  // Ground items (may be empty on initial room_info — populated by room_items_update)
  const items = groundItems || [];
  if (items.length > 0) {
    const itemList = items.map(i => i.quantity > 1 ? `${i.name} x${i.quantity}` : i.name).join(', ');
    lines.push(c(`  Items: ${itemList}`, A.WHITE, uc));
  }
  const coins = groundCoins || {};
  const coinAmt = coinStr(coins);
  if (coinAmt) lines.push(c(`  Coins: ${coinAmt}`, A.YELLOW, uc));

  // Interactables (ON_ACTION only — traps are invisible)
  const features = (room.interactables || []).filter(i => i.triggerType !== 'ON_ENTER');
  if (features.length > 0) {
    const featList = features.map(f => f.label).join(', ');
    lines.push(`  Features: ${c(featList, A.YELLOW, uc)}`);
  }

  return lines;
}

export function renderMessage(msg, state, useColor = false) {
  const uc = useColor;

  switch (msg.type) {
    // --- Auth / Handshake ---
    case 'server_hello':
    case 'platform_auth_ok':
    case 'name_check_result':
    case 'pong':
      return [];

    case 'register_ok':
      return [c('Registration successful.', A.CYAN, uc)];

    case 'login_ok': {
      const p = msg.player;
      return [
        '',
        c(`Welcome back, ${p.name}! [${p.race || ''} ${p.characterClass} Lv.${p.level}]`, A.BOLD_CYAN, uc),
        c(`  HP: ${p.currentHp}/${p.maxHp}  MP: ${p.currentMp || 0}/${p.maxMp || 0}  XP: ${p.currentXp || 0}/${p.xpToNextLevel || 0}`, A.CYAN, uc),
      ];
    }

    case 'auth_error':
      return [c(`ERROR: ${msg.reason}`, A.RED, uc)];

    case 'session_conflict':
      return [c(`A session for ${msg.characterName} is already active. Login with 'force' to displace it.`, A.YELLOW, uc)];

    case 'session_displaced':
      return [c(`*** Session displaced: ${msg.reason || 'Another session logged in'}. Goodbye. ***`, A.BOLD_RED, uc)];

    case 'connection_rejected':
      return [c(`Connection refused: ${msg.reason}`, A.RED, uc)];

    // --- Catalog syncs (silent) ---
    case 'class_catalog_sync':
    case 'item_catalog_sync':
    case 'skill_catalog_sync':
    case 'race_catalog_sync':
    case 'spell_catalog_sync':
      return [];

    // --- Room / Movement ---
    case 'room_info':
      return renderRoom(msg.room, state.npcsInRoom, state.playersInRoom, state.groundItems, state.groundCoins, uc);

    case 'move_ok': {
      const dir = (msg.direction || '').toLowerCase();
      const lines = [c(`You head ${dir}.`, A.WHITE, uc)];
      lines.push(...renderRoom(msg.room, state.npcsInRoom, state.playersInRoom, state.groundItems, state.groundCoins, uc));
      return lines;
    }

    case 'move_error':
      return [c(msg.reason, A.YELLOW, uc)];

    case 'map_data':
    case 'atlas_data':
      return []; // rendered on demand via local commands

    // --- Presence ---
    case 'player_entered':
      return [c(`${msg.playerName} has arrived.`, A.CYAN, uc)];

    case 'player_left': {
      const dir = msg.direction && msg.direction !== 'NONE' ? ` heading ${msg.direction.toLowerCase()}` : '';
      return [c(`${msg.playerName} has left${dir}.`, A.CYAN, uc)];
    }

    case 'npc_entered': {
      const hostile = msg.hostile ? c(' [hostile]', A.RED, uc) : '';
      const spawned = msg.spawned ? ' appears!' : ' has arrived.';
      return [c(`${msg.npcName}${spawned}${hostile}`, A.YELLOW, uc)];
    }

    case 'npc_left': {
      const dir = msg.direction && msg.direction !== 'NONE' ? ` heading ${msg.direction.toLowerCase()}` : '';
      return [c(`${msg.npcName} leaves${dir}.`, A.YELLOW, uc)];
    }

    // --- Chat ---
    case 'player_says':
      return [c(`${msg.playerName} says: "${msg.message}"`, A.BOLD_WHITE, uc)];

    case 'tell_received':
      return [c(`${msg.senderName} tells you: '${msg.message}'`, A.YELLOW, uc)];

    case 'tell_sent':
      return [c(`You tell ${msg.targetName}: '${msg.message}'`, A.YELLOW, uc)];

    case 'who_list': {
      const players = msg.players || [];
      if (players.length === 0) return [c('No players online.', A.WHITE, uc)];
      const lines = [c(`--- ${players.length} player(s) online ---`, A.CYAN, uc)];
      for (const p of players) {
        const zone = p.zone ? ` — ${p.zone}` : '';
        lines.push(`  ${p.name} (${p.characterClass || ''} Lv${p.level})${zone}`);
      }
      return lines;
    }

    // --- Combat ---
    case 'combat_hit': {
      let line;
      if (msg.isMiss) {
        line = `${msg.attackerName} misses ${msg.defenderName}.`;
      } else if (msg.isDodge) {
        line = `${msg.defenderName} dodges ${msg.attackerName}'s attack!`;
      } else if (msg.isParry) {
        line = `${msg.defenderName} parries ${msg.attackerName}'s attack!`;
      } else if (msg.isBackstab) {
        line = `${msg.attackerName} BACKSTABS ${msg.defenderName} for ${msg.damage} damage!`;
      } else {
        line = `${msg.attackerName} hits ${msg.defenderName} for ${msg.damage} damage.`;
      }
      const bar = hpBar(msg.defenderHp, msg.defenderMaxHp, 8, uc);
      const hpLine = `  ${msg.defenderName}: ${msg.defenderHp}/${msg.defenderMaxHp} ${bar}`;
      const lineColor = msg.isPlayerDefender ? A.RED : (msg.isMiss || msg.isDodge || msg.isParry ? A.GRAY : A.GREEN);
      return [c(line, lineColor, uc), c(hpLine, A.GRAY, uc)];
    }

    case 'skill_effect': {
      const line = `${msg.userName} uses ${msg.skillName} on ${msg.targetName}: ${msg.message} (${msg.damage} dmg)`;
      const bar = hpBar(msg.targetHp, msg.targetMaxHp, 8, uc);
      const hpLine = `  ${msg.targetName}: ${msg.targetHp}/${msg.targetMaxHp} ${bar}`;
      return [c(line, A.YELLOW, uc), c(hpLine, A.GRAY, uc)];
    }

    case 'spell_effect': {
      const line = `${msg.casterName} casts ${msg.spellName} on ${msg.targetName} for ${msg.effectAmount}.`;
      const bar = hpBar(msg.targetNewHp, msg.targetMaxHp, 8, uc);
      const hpLine = `  ${msg.targetName}: ${msg.targetNewHp}/${msg.targetMaxHp} ${bar}`;
      const lineColor = msg.isPlayerTarget ? A.RED : A.BLUE;
      return [c(line, lineColor, uc), c(hpLine, A.GRAY, uc)];
    }

    case 'spell_cast_result':
      return [c(`${msg.spellName}: ${msg.message}`, msg.success ? A.BLUE : A.YELLOW, uc)];

    case 'npc_died':
      return [c(`${msg.killerName} has slain ${msg.npcName}!`, A.BOLD_GREEN, uc)];

    case 'player_died':
      return [c(`You have been slain by ${msg.killerName}! Respawning...`, A.BOLD_RED, uc)];

    case 'attack_mode_update':
      return [msg.enabled ? c('[ATTACK MODE ON]', A.RED, uc) : c('[ATTACK MODE OFF]', A.GREEN, uc)];

    case 'npc_phase_shift':
      return [
        c(`*** ${msg.npcName} ENTERS ${msg.phaseName?.toUpperCase()}! ***`, A.BOLD_RED, uc),
        c(`  ${msg.message}`, A.RED, uc),
        c(`  ${msg.npcName}: ${msg.currentHp}/${msg.maxHp} ${hpBar(msg.currentHp, msg.maxHp, 10, uc)}`, A.GRAY, uc),
      ];

    case 'npc_ability_effect': {
      const lines = [c(`${msg.npcName} uses ${msg.abilityName}!`, A.BOLD_RED, uc)];
      for (const r of (msg.results || [])) {
        const saved = r.saved ? ' (saved)' : '';
        const bar = hpBar(r.newHp, r.maxHp, 6, uc);
        lines.push(c(`  ${r.targetName}: ${r.damage} dmg${saved} — ${r.newHp}/${r.maxHp} ${bar}`, A.RED, uc));
      }
      return lines;
    }

    // --- Effects ---
    case 'active_effects_update':
      return []; // silent; shown on 'effects' command

    case 'effect_tick':
      return [c(`(${msg.effectName}) ${msg.message}`, A.MAGENTA, uc)];

    case 'stealth_update': {
      const text = msg.message || (msg.hidden ? 'You slip into the shadows.' : 'You step out of the shadows.');
      return [c(text, A.GRAY, uc)];
    }

    case 'meditate_update': {
      const text = msg.message || (msg.meditating ? 'You enter a meditative trance.' : 'You end your meditation.');
      return [c(text, A.BLUE, uc)];
    }

    case 'rest_update': {
      const text = msg.message || (msg.resting ? 'You rest for a moment.' : 'You stop resting.');
      return [c(text, A.BLUE, uc)];
    }

    case 'track_result':
      return [c(msg.message, msg.success ? A.GREEN : A.YELLOW, uc)];

    // --- Inventory ---
    case 'inventory_update': {
      const inv = msg.inventory || [];
      const lines = [c('--- Inventory ---', A.CYAN, uc)];
      if (inv.length === 0) {
        lines.push('  (empty)');
      } else {
        const equipped = inv.filter(i => i.equipped);
        const carried = inv.filter(i => !i.equipped);
        if (equipped.length > 0) {
          lines.push(c('  Equipped:', A.GRAY, uc));
          for (const i of equipped) {
            lines.push(`    [${i.slot}] ${i.itemId}${i.quantity > 1 ? ' x' + i.quantity : ''}`);
          }
        }
        if (carried.length > 0) {
          lines.push(c('  Carried:', A.GRAY, uc));
          for (const i of carried) {
            lines.push(`    ${i.itemId}${i.quantity > 1 ? ' x' + i.quantity : ''}`);
          }
        }
      }
      const coinAmt = coinStr(msg.coins);
      if (coinAmt) lines.push(`  Coins: ${coinAmt}`);
      return lines;
    }

    case 'room_items_update': {
      const items = state.groundItems || [];
      const coins = state.groundCoins || {};
      const lines = [];
      if (items.length > 0) {
        const itemList = items.map(i => i.quantity > 1 ? `${i.name} x${i.quantity}` : i.name).join(', ');
        lines.push(c(`  On the ground: ${itemList}`, A.WHITE, uc));
      }
      const coinAmt = coinStr(coins);
      if (coinAmt) lines.push(c(`  Coins on ground: ${coinAmt}`, A.YELLOW, uc));
      return lines;
    }

    case 'loot_received': {
      const items = (msg.items || []).map(i => `${i.itemName}${i.quantity > 1 ? ' x' + i.quantity : ''}`).join(', ');
      return [c(`You receive from ${msg.npcName}: ${items}`, A.GREEN, uc)];
    }

    case 'loot_dropped': {
      const items = (msg.items || []).map(i => `${i.itemName}${i.quantity > 1 ? ' x' + i.quantity : ''}`).join(', ');
      const coins = coinStr(msg.coins);
      const parts = [items, coins].filter(Boolean).join(', ');
      return [c(`${msg.npcName} drops: ${parts || 'nothing'}`, A.YELLOW, uc)];
    }

    case 'pickup_result': {
      if (msg.isCoin) return [c(`You pick up the ${msg.itemName}.`, A.GREEN, uc)];
      const qty = msg.quantity > 1 ? ` x${msg.quantity}` : '';
      return [c(`You pick up the ${msg.itemName}${qty}.`, A.GREEN, uc)];
    }

    case 'item_used':
      return [c(`${msg.itemName}: ${msg.message}`, A.GREEN, uc)];

    case 'equip_update':
      return [msg.itemId
        ? c(`Equipped ${msg.itemName} in ${msg.slot}.`, A.WHITE, uc)
        : c(`Unequipped ${msg.slot}.`, A.WHITE, uc)];

    // --- Progression ---
    case 'xp_gained': {
      const amt = Number(msg.amount);
      const cur = Number(msg.currentXp);
      const next = Number(msg.xpToNextLevel);
      if (amt >= 0) return [c(`You gain ${amt} XP. (${cur}/${next})`, A.YELLOW, uc)];
      return [c(`You lose ${-amt} XP. (${cur}/${next})`, A.RED, uc)];
    }

    case 'level_up':
      return [
        c(`*** LEVEL UP! You are now level ${msg.newLevel}! ***`, A.BOLD_YELLOW, uc),
        c(`  HP: +${msg.hpRoll} (now ${msg.newMaxHp} max)  MP: +${msg.mpRoll} (now ${msg.newMaxMp} max)  CP: +${msg.cpGained} (${msg.totalUnspentCp} unspent)`, A.YELLOW, uc),
      ];

    case 'trainer_info': {
      const lines = [c('--- Trainer ---', A.CYAN, uc)];
      if (msg.canLevelUp) lines.push(c('  You are ready to level up! (type: levelup)', A.BOLD_GREEN, uc));
      lines.push(`  Unspent CP: ${msg.unspentCp}`);
      const st = msg.currentStats;
      if (st) {
        lines.push(`  STR:${st.strength} AGI:${st.agility} INT:${st.intellect} WIL:${st.willpower} HEA:${st.health} CHA:${st.charm}`);
      }
      lines.push(c('  Type: train stat <STR|AGI|INT|WIL|HEA|CHA>', A.GRAY, uc));
      return lines;
    }

    case 'stat_trained':
      return [c(`${msg.stat} increased to ${msg.newValue}. (${msg.remainingCp} CP remaining)`, A.CYAN, uc)];

    // --- Vendor ---
    case 'vendor_info': {
      const lines = [c(`--- ${msg.vendorName} ---`, A.CYAN, uc)];
      const coinAmt = coinStr(msg.playerCoins);
      if (coinAmt) lines.push(`  Your coins: ${coinAmt}`);
      for (let i = 0; i < (msg.items || []).length; i++) {
        const vi = msg.items[i];
        const name = vi.item?.name || vi.itemId || '?';
        const price = coinStr(vi.price) || 'free';
        lines.push(`  ${i + 1}. ${name} — ${price}`);
      }
      lines.push(c("  Type 'buy <item>' to purchase, 'sell <item>' to sell.", A.GRAY, uc));
      return lines;
    }

    case 'buy_result':
      return [c(msg.message, msg.success ? A.GREEN : A.RED, uc)];

    case 'sell_result':
      return [c(msg.message, msg.success ? A.GREEN : A.RED, uc)];

    case 'interact_result':
      return [c(`${msg.featureName}: ${msg.message}`, msg.success ? A.GREEN : A.YELLOW, uc)];

    // --- Crafting ---
    case 'crafting_menu': {
      const lines = [c(`--- ${msg.crafterName} ---`, A.CYAN, uc)];
      const coinAmt = coinStr(msg.playerCoins);
      if (coinAmt) lines.push(`  Your coins: ${coinAmt}`);
      for (let i = 0; i < (msg.recipes || []).length; i++) {
        const r = msg.recipes[i];
        lines.push(`  ${i + 1}. ${r.name || r.recipeId}`);
      }
      lines.push(c("  Type 'craft <recipe>' to craft.", A.GRAY, uc));
      return lines;
    }

    case 'craft_result':
      return [c(msg.message, msg.success ? A.GREEN : A.RED, uc)];

    // --- Interactable prompts ---
    case 'place_item_prompt': {
      const accepted = (msg.acceptedItems || []).join(', ');
      return [
        c(`[${msg.label}] ${msg.prompt}`, A.CYAN, uc),
        c(`  Accepted: ${accepted || 'any item'}`, A.GRAY, uc),
        c(`  Type 'place <item>' or 'cancel'.`, A.GRAY, uc),
      ];
    }

    case 'riddle_prompt': {
      const lines = [c(`[${msg.label}] ${msg.question}`, A.CYAN, uc)];
      if (msg.hint) lines.push(c(`  Hint: ${msg.hint}`, A.GRAY, uc));
      lines.push(c(`  Type 'answer <text>' or 'cancel'.`, A.GRAY, uc));
      return lines;
    }

    case 'choice_prompt': {
      const lines = [c(`[${msg.label}] ${msg.question}`, A.CYAN, uc)];
      for (let i = 0; i < (msg.options || []).length; i++) {
        lines.push(`  ${i + 1}. ${msg.options[i].label}`);
      }
      lines.push(c(`  Type 'choose <n>' or 'cancel'.`, A.GRAY, uc));
      return lines;
    }

    // --- Party ---
    case 'party_invite_received':
      return [c(`** ${msg.inviterName} invites you to join their party. Type 'party accept ${msg.inviterName}' or 'party decline ${msg.inviterName}'.`, A.BOLD_CYAN, uc)];

    case 'party_formed': {
      const names = (msg.members || []).map(m => m.name).join(', ');
      return [c(`** Party formed: ${names}`, A.CYAN, uc)];
    }

    case 'party_member_joined':
      return [c(`** ${msg.member?.name || '?'} joins the party.`, A.CYAN, uc)];

    case 'party_member_left': {
      const action = msg.reason === 'kicked' ? 'was kicked from' : 'leaves';
      return [c(`** ${msg.memberName} ${action} the party.`, A.CYAN, uc)];
    }

    case 'party_disbanded':
      return [c(`** The party has disbanded (${msg.reason || 'disbanded'}).`, A.CYAN, uc)];

    case 'party_member_update':
      return []; // silent; reflected in 'party' command

    case 'party_chat':
      return [c(`[Party] ${msg.senderName}: ${msg.message}`, A.CYAN, uc)];

    case 'party_info': {
      const lines = [c('--- Party ---', A.CYAN, uc)];
      for (const m of (msg.members || [])) {
        const leader = m.name === msg.leaderId ? c(' (leader)', A.BOLD_CYAN, uc) : '';
        const bar = hpBar(m.currentHp, m.maxHp, 6, uc);
        lines.push(`  ${m.name}${leader} — HP: ${m.currentHp}/${m.maxHp} ${bar}`);
      }
      return lines;
    }

    case 'party_leader_changed':
      return [c(`** ${msg.newLeaderName} is now party leader.`, A.CYAN, uc)];

    // --- Follow ---
    case 'follow_update': {
      if (msg.state === 'OFF') {
        return [c(`** ${msg.followerName} stops following ${msg.targetName}.`, A.GRAY, uc)];
      }
      return [c(`** ${msg.followerName} is now following ${msg.targetName}.`, A.GRAY, uc)];
    }

    case 'rally_ping':
      return [c(`** ${msg.leaderName} calls a rally to ${msg.roomName} (${msg.zoneName})!`, A.BOLD_CYAN, uc)];

    case 'follow_failed':
      return [c(`** Follow failed: ${msg.reason}`, A.YELLOW, uc)];

    // --- NPC interaction ---
    case 'npc_dialogue':
      return [c(`${msg.npc_name} says: "${msg.content}"`, A.YELLOW, uc)];

    // --- System ---
    case 'system_message':
      return [c(msg.message, A.YELLOW, uc)];

    case 'server_shutdown':
      return [c(`*** SERVER SHUTDOWN: ${msg.message} (${msg.secondsRemaining}s) ***`, A.BOLD_RED, uc)];

    case 'error':
      return [c(`ERROR: ${msg.message}`, A.RED, uc)];

    case 'tutorial':
      return []; // suppressed for terminal mode

    default:
      return [];
  }
}

export function renderPrompt(state, useColor = false) {
  const uc = useColor;
  const p = state.player;
  if (!p) return '> ';

  const hp = `HP:${p.hp}/${p.maxHp}`;
  const mp = p.maxMp > 0 ? ` MP:${p.mp}/${p.maxMp}` : '';

  const flags = [];
  if (state.attackMode) flags.push(uc ? `${A.RED}[Attack]${A.RESET}` : '[Attack]');
  if (state.isHidden) flags.push(uc ? `${A.GRAY}[Hidden]${A.RESET}` : '[Hidden]');
  if (state.isMeditating) flags.push(uc ? `${A.BLUE}[Med]${A.RESET}` : '[Med]');
  if (state.isResting) flags.push(uc ? `${A.BLUE}[Rest]${A.RESET}` : '[Rest]');
  const flagStr = flags.length > 0 ? ' ' + flags.join(' ') : '';

  if (uc) {
    const ratio = p.maxHp > 0 ? p.hp / p.maxHp : 0;
    const hpColor = ratio > 0.5 ? A.GREEN : ratio > 0.25 ? A.YELLOW : A.RED;
    return `${hpColor}<${hp}${mp}>${A.RESET}${flagStr} `;
  }
  return `<${hp}${mp}>${flagStr} `;
}

export function renderPartyState(party, useColor = false) {
  const uc = useColor;
  if (!party) return ['You are not in a party.'];
  const lines = [c('--- Party ---', A.CYAN, uc)];
  for (const m of (party.members || [])) {
    const leader = m.isLeader ? c(' (leader)', A.BOLD_CYAN, uc) : '';
    const bar = hpBar(m.hp, m.maxHp, 6, uc);
    lines.push(`  ${m.name}${leader} — HP: ${m.hp}/${m.maxHp} ${bar}`);
  }
  return lines;
}

export function renderHelp(useColor = false) {
  const uc = useColor;
  const h = (s) => c(s, A.BOLD_CYAN, uc);
  const g = (s) => c(s, A.GRAY, uc);
  return [
    '',
    h('--- NeoMud Commands ---'),
    '',
    `${h('Movement')}   n s e w u d  (or full names: north south east west up down)`,
    `${h('Look')}       look | l`,
    `${h('Say')}        say <msg>`,
    `${h('Tells')}      /tell <player> <msg>  or  tell <player> <msg>`,
    `${h('Who')}        who`,
    '',
    `${h('Attack')}     attack <npc>    stop (end combat)    flee`,
    `${h('Skills')}     bash [target]   kick [target]   sneak   meditate   track [target]`,
    `${h('Spells')}     cast <spellId> [target]   ready <spellId>`,
    '',
    `${h('Inventory')}  inv | i`,
    `${h('Get')}        get <item>    get all`,
    `${h('Drop')}       drop <item> [qty]`,
    `${h('Use')}        use <item>`,
    `${h('Equip')}      equip <item>    unequip <slot>`,
    '',
    `${h('Vendors')}    shop   buy <item> [qty]   sell <item> [qty]`,
    `${h('Trainer')}    train   levelup   train stat <STAT>`,
    `${h('Crafting')}   crafting   craft <recipe>`,
    `${h('NPC')}        talk <npc>`,
    `${h('Interact')}   interact <feature>   activate <feature>`,
    '',
    `${h('Prompts')}    answer <text>   choose <n>   place <item>   cancel`,
    '',
    `${h('Party')}      party invite/accept/decline/leave/kick/say <msg>`,
    `${h('Follow')}     follow <name>   unfollow   rally`,
    `${h('Atlas')}      atlas | world`,
    '',
    `${h('Info')}       hp   effects   skills   spells   party`,
    `${h('Quit')}       quit | exit`,
    '',
    g('  Partial names are accepted for NPCs, items, and features.'),
    g("  Example: 'attack rat' matches 'Tunnel Rat', 'get sw' matches 'Iron Sword'"),
    '',
  ];
}
