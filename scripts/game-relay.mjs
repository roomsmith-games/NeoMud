#!/usr/bin/env node
/**
 * game-relay.mjs — WebSocket relay for Claude playtesting
 *
 * Maintains a persistent WebSocket connection to the NeoMud server and
 * exposes game state via files that Claude can read/write each turn.
 *
 * Usage: node scripts/game-relay.mjs <username> <password>
 *        node scripts/game-relay.mjs --register <username> <password> <charName> <class> <race> <gender>
 *        node scripts/game-relay.mjs --guest <charName> <class> [race] [gender]
 *        node scripts/game-relay.mjs --url wss://stage.neomud.app/worlds/{worldId}/game <username> <password>
 *        node scripts/game-relay.mjs --staging <worldSlug>              (platform JWT auth, existing character)
 *        node scripts/game-relay.mjs --staging <worldSlug> --register <charName> <class> [race] [gender]
 *
 * Staging mode logs into the platform API, resolves the world's WS endpoint,
 * and connects with JWT auth. Credentials come from NEOMUD_PLATFORM_EMAIL /
 * NEOMUD_PLATFORM_PASSWORD env vars (or defaults to the staging admin account).
 *
 * Environment: NEOMUD_URL overrides the default ws://localhost:8080/game
 *              NEOMUD_PLATFORM_API overrides https://stage-api.neomud.app/api/v1
 *              NEOMUD_PLATFORM_EMAIL / NEOMUD_PLATFORM_PASSWORD for staging auth
 */

import { WebSocket } from 'ws';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const STATE_FILE = path.join(__dirname, 'relay-state.json');
const COMMAND_FILE = path.join(__dirname, 'relay-command.json');
const TEMP_STATE_FILE = path.join(__dirname, '.relay-state.tmp');
const LOCK_FILE = path.join(__dirname, 'relay.lock');

const COMMAND_POLL_MS = 250;
const PING_INTERVAL_MS = 30_000;
const COMMAND_SPACING_MS = 150;
const MAX_RECENT_EVENTS = 50;
const STATE_WRITE_DEBOUNCE_MS = 100;

// ---------------------------------------------------------------------------
// CLI args — parse --url and --register flags, then positional user/pass
// ---------------------------------------------------------------------------
const rawArgs = process.argv.slice(2);
let cliUrl = null;
let registerMode = false;
let guestMode = false;
let stagingMode = false;
let stagingSlug = null;
let registerOpts = {};
let guestOpts = {};
let platformRegisterOpts = {};
let platformAccessToken = null;
let username, password;

// Extract --url and --staging flags first
const args = [];
for (let i = 0; i < rawArgs.length; i++) {
  if (rawArgs[i] === '--url' && i + 1 < rawArgs.length) {
    cliUrl = rawArgs[++i];
  } else if (rawArgs[i] === '--staging' && i + 1 < rawArgs.length) {
    stagingMode = true;
    stagingSlug = rawArgs[++i];
  } else {
    args.push(rawArgs[i]);
  }
}

let SERVER_URL = cliUrl || process.env.NEOMUD_URL || 'ws://localhost:8080/game';

// ---------------------------------------------------------------------------
// Staging mode: login to platform API, resolve world WS endpoint, get JWT
// ---------------------------------------------------------------------------
if (stagingMode) {
  const platformApi = process.env.NEOMUD_PLATFORM_API || 'https://stage-api.neomud.app/api/v1';
  const platformEmail = process.env.NEOMUD_PLATFORM_EMAIL || 'admin@neomud.app';
  const platformPassword = process.env.NEOMUD_PLATFORM_PASSWORD || '2JQSAng3x7nZkbX';

  console.log(`[relay] Staging mode: logging into ${platformApi}...`);
  const loginRes = await fetch(`${platformApi}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: platformEmail, password: platformPassword }),
  });
  if (!loginRes.ok) {
    console.error(`[relay] Platform login failed: ${loginRes.status} ${await loginRes.text()}`);
    process.exit(1);
  }
  const { accessToken, user: platformUser } = await loginRes.json();
  platformAccessToken = accessToken;
  console.log(`[relay] Logged in as ${platformUser.displayName} (${platformUser.id})`);

  console.log(`[relay] Resolving world '${stagingSlug}'...`);
  const worldRes = await fetch(`${platformApi}/worlds/${stagingSlug}`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!worldRes.ok) {
    console.error(`[relay] World lookup failed: ${worldRes.status} ${await worldRes.text()}`);
    process.exit(1);
  }
  const worldData = await worldRes.json();
  if (worldData.serverStatus !== 'ONLINE') {
    console.error(`[relay] World '${stagingSlug}' is ${worldData.serverStatus}, not ONLINE`);
    process.exit(1);
  }

  SERVER_URL = `${worldData.serverEndpoint}?token=${accessToken}`;
  console.log(`[relay] World '${worldData.name}' → ${worldData.serverEndpoint}`);

  // In staging mode, check if --register follows for platform character creation
  if (args[0] === '--register') {
    registerMode = true;
    platformRegisterOpts = {
      charName: args[1],
      charClass: args[2],
      race: args[3] || 'HUMAN',
      gender: args[4] || 'male',
      stats: null,
    };
    if (!platformRegisterOpts.charName || !platformRegisterOpts.charClass) {
      console.error('Usage: node scripts/game-relay.mjs --staging <slug> --register <charName> <class> [race] [gender]');
      process.exit(1);
    }
  }

  // Staging uses platform_login/platform_register, not username/password
  username = `platform_${platformUser.id}`;
  password = '';
} else if (args[0] === '--register') {
  registerMode = true;
  username = args[1];
  password = args[2];
  registerOpts = {
    charName: args[3],
    charClass: args[4],
    race: args[5] || 'HUMAN',
    gender: args[6] || 'male',
    stats: null,
  };
  if (!username || !password || !registerOpts.charName || !registerOpts.charClass) {
    console.error('Usage: node scripts/game-relay.mjs [--url <ws-url>] --register <user> <pass> <charName> <class> [race] [gender]');
    process.exit(1);
  }
} else if (args[0] === '--guest') {
  guestMode = true;
  guestOpts = {
    charName: args[1],
    charClass: args[2],
    race: args[3] || 'HUMAN',
    gender: args[4] || 'male',
    stats: null,
  };
  if (!guestOpts.charName || !guestOpts.charClass) {
    console.error('Usage: node scripts/game-relay.mjs [--url <ws-url>] --guest <charName> <class> [race] [gender]');
    process.exit(1);
  }
  username = 'guest';
  password = 'guest';
} else if (!stagingMode) {
  username = args[0];
  password = args[1];
  if (!username || !password) {
    console.error('Usage: node scripts/game-relay.mjs [--url <ws-url>] <username> <password>');
    process.exit(1);
  }
}

// ---------------------------------------------------------------------------
// Catalog data (received on connect, used for registration stat allocation)
// ---------------------------------------------------------------------------
let classCatalog = [];
let raceCatalog = [];
let skillCatalog = [];
let spellCatalog = [];
let itemCatalogMap = {}; // itemId -> item name, built from ItemCatalogSync
let catalogsReceived = { classes: false, races: false };
let registrationSent = false;

/**
 * Cross-reference the player's class with skill/spell catalogs to determine
 * what abilities are available, then write them into state.
 */
function computePlayerAbilities() {
  if (!state.player) return;
  const classDef = classCatalog.find(c => c.id === state.player.class);
  if (!classDef) return;

  // Skills: mirror server logic — classRestrictions empty = all classes,
  // otherwise the player's class must be in the list
  const playerClass = state.player.class;
  state.availableSkills = skillCatalog
    .filter(s => {
      const restrictions = s.classRestrictions || [];
      return restrictions.length === 0 || restrictions.includes(playerClass);
    })
    .map(s => ({
      id: s.id,
      name: s.name,
      description: s.description,
      category: s.category,
      manaCost: s.manaCost || 0,
      cooldownTicks: s.cooldownTicks || 0,
      isPassive: s.isPassive || false,
    }));

  // Spells: filter by class magic schools and player level
  const magicSchools = classDef.magicSchools || {};
  state.availableSpells = spellCatalog
    .filter(sp => {
      const schoolMax = magicSchools[sp.school];
      if (schoolMax == null) return false; // class doesn't have this school
      if (sp.schoolLevel > schoolMax) return false; // school level too high
      return true;
    })
    .map(sp => ({
      id: sp.id,
      name: sp.name,
      description: sp.description,
      school: sp.school,
      spellType: sp.spellType,
      manaCost: sp.manaCost,
      cooldownTicks: sp.cooldownTicks || 0,
      levelRequired: sp.levelRequired || 1,
      targetType: sp.targetType,
      basePower: sp.basePower || 0,
    }));

  scheduleStateWrite();
}

function tryRegister() {
  if (registrationSent || !registerMode) return;
  if (!catalogsReceived.classes || !catalogsReceived.races) return;

  const opts = stagingMode ? platformRegisterOpts : registerOpts;
  const classDef = classCatalog.find(c => c.id === opts.charClass);
  if (!classDef) {
    console.error(`[relay] Unknown class: ${opts.charClass}`);
    console.error('[relay] Available classes:', classCatalog.map(c => c.id).join(', '));
    process.exit(1);
  }
  const raceDef = raceCatalog.find(r => r.id === opts.race);
  const raceMods = raceDef?.statModifiers || { strength: 0, agility: 0, intellect: 0, willpower: 0, health: 0, charm: 0 };
  const mins = classDef.minimumStats;

  const base = {
    strength: Math.max(1, mins.strength + raceMods.strength),
    agility: Math.max(1, mins.agility + raceMods.agility),
    intellect: Math.max(1, mins.intellect + raceMods.intellect),
    willpower: Math.max(1, mins.willpower + raceMods.willpower),
    health: Math.max(1, mins.health + raceMods.health),
    charm: Math.max(1, mins.charm + raceMods.charm),
  };

  const stats = {
    strength: base.strength + 10,
    agility: base.agility + 10,
    intellect: base.intellect + 10,
    willpower: base.willpower + 10,
    health: base.health + 10,
    charm: base.charm + 10,
  };

  registrationSent = true;

  if (stagingMode) {
    console.log('[relay] Platform registering with stats:', JSON.stringify(stats));
    send({
      type: 'platform_register',
      characterName: opts.charName,
      characterClass: opts.charClass,
      race: opts.race,
      gender: opts.gender,
      allocatedStats: stats,
    });
  } else {
    console.log('[relay] Registering with stats:', JSON.stringify(stats));
    send({
      type: 'register',
      username,
      password,
      characterName: opts.charName,
      characterClass: opts.charClass,
      race: opts.race,
      gender: opts.gender,
      allocatedStats: stats,
    });
  }
}

function tryGuestLogin() {
  if (registrationSent || !guestMode) return;
  if (!catalogsReceived.classes || !catalogsReceived.races) return;

  const classDef = classCatalog.find(c => c.id === guestOpts.charClass);
  if (!classDef) {
    console.error(`[relay] Unknown class: ${guestOpts.charClass}`);
    console.error('[relay] Available classes:', classCatalog.map(c => c.id).join(', '));
    process.exit(1);
  }
  const raceDef = raceCatalog.find(r => r.id === guestOpts.race);
  const raceMods = raceDef?.statModifiers || { strength: 0, agility: 0, intellect: 0, willpower: 0, health: 0, charm: 0 };
  const mins = classDef.minimumStats;

  const base = {
    strength: Math.max(1, mins.strength + raceMods.strength),
    agility: Math.max(1, mins.agility + raceMods.agility),
    intellect: Math.max(1, mins.intellect + raceMods.intellect),
    willpower: Math.max(1, mins.willpower + raceMods.willpower),
    health: Math.max(1, mins.health + raceMods.health),
    charm: Math.max(1, mins.charm + raceMods.charm),
  };

  guestOpts.stats = {
    strength: base.strength + 10,
    agility: base.agility + 10,
    intellect: base.intellect + 10,
    willpower: base.willpower + 10,
    health: base.health + 10,
    charm: base.charm + 10,
  };

  console.log('[relay] Guest login with stats:', JSON.stringify(guestOpts.stats));
  registrationSent = true;
  send({
    type: 'guest_login',
    characterName: guestOpts.charName,
    characterClass: guestOpts.charClass,
    race: guestOpts.race,
    gender: guestOpts.gender,
    allocatedStats: guestOpts.stats,
  });
}

// ---------------------------------------------------------------------------
// Game state model
// ---------------------------------------------------------------------------
const state = {
  connected: false,
  loggedIn: false,
  player: null,
  room: null,
  npcsInRoom: [],
  playersInRoom: [],
  groundItems: [],
  groundCoins: { copper: 0, silver: 0, gold: 0 },
  inventory: [],
  equipment: {},
  coins: { copper: 0, silver: 0, gold: 0 },
  attackMode: false,
  selectedTarget: null,
  isHidden: false,
  isMeditating: false,
  isResting: false,
  activeEffects: [],
  availableSkills: [],
  availableSpells: [],
  recentEvents: [],
};

function pushEvent(type, summary) {
  const time = new Date().toLocaleTimeString('en-US', { hour12: false });
  state.recentEvents.push({ time, type, summary });
  if (state.recentEvents.length > MAX_RECENT_EVENTS) {
    state.recentEvents = state.recentEvents.slice(-MAX_RECENT_EVENTS);
  }
  scheduleStateWrite();
}

// ---------------------------------------------------------------------------
// State file writing (debounced, atomic)
// ---------------------------------------------------------------------------
let writeTimer = null;

function scheduleStateWrite() {
  if (writeTimer) return;
  writeTimer = setTimeout(() => {
    writeTimer = null;
    writeStateFile();
  }, STATE_WRITE_DEBOUNCE_MS);
}

function writeStateFile() {
  try {
    const json = JSON.stringify(state, null, 2);
    fs.writeFileSync(TEMP_STATE_FILE, json, 'utf8');
    fs.renameSync(TEMP_STATE_FILE, STATE_FILE);
  } catch (err) {
    console.error('[relay] Failed to write state file:', err.message);
  }
}

// ---------------------------------------------------------------------------
// ServerMessage handlers
// ---------------------------------------------------------------------------
const handlers = {
  // Handshake
  server_hello(msg) {
    pushEvent('system', `Server: ${msg.worldName || 'NeoMud'} v${msg.engineVersion} (protocol ${msg.protocolVersion})`);
    const hello = { type: 'client_hello', clientVersion: msg.engineVersion, protocolVersion: msg.protocolVersion || 1 };
    if (platformAccessToken) hello.platformToken = platformAccessToken;
    send(hello);
  },

  // Platform auth response — fires after client_hello with valid JWT
  platform_auth_ok(msg) {
    pushEvent('system', `Platform auth OK: ${msg.characterName || '(new character needed)'}`);
    if (msg.needsCharacterCreation) {
      if (registerMode && stagingMode) {
        pushEvent('system', 'Creating platform character...');
        // tryRegister will send platform_register once catalogs arrive
      } else {
        pushEvent('system', 'No character on this world. Use --register to create one.');
        console.error('[relay] No character found. Re-run with: --staging <slug> --register <name> <class> [race] [gender]');
        process.exit(1);
      }
    } else {
      pushEvent('system', `Logging in as ${msg.characterName}...`);
      send({ type: 'platform_login' });
    }
  },

  // Auth
  register_ok() {
    if (guestMode || stagingMode) {
      pushEvent('system', 'Registration successful. Waiting for auto-login...');
    } else {
      pushEvent('system', 'Registration successful. Logging in...');
      send({ type: 'login', username, password });
    }
  },
  login_ok(msg) {
    state.loggedIn = true;
    const p = msg.player;
    state.player = {
      name: p.name,
      class: p.characterClass,
      race: p.race || '',
      gender: p.gender || '',
      level: p.level,
      hp: p.currentHp,
      maxHp: p.maxHp,
      mp: p.currentMp || 0,
      maxMp: p.maxMp || 0,
      xp: p.currentXp || 0,
      xpToNextLevel: p.xpToNextLevel || 0,
      stats: p.stats || null,
      unspentCp: p.unspentCp || 0,
    };
    pushEvent('system', `Logged in as ${p.name} (Lv${p.level} ${p.race || ''} ${p.characterClass})`);
    computePlayerAbilities();
  },
  auth_error(msg) {
    pushEvent('error', `Auth error: ${msg.reason}`);
    console.error('[relay] Auth error:', msg.reason);
  },

  // Room / Movement
  room_info(msg) {
    updateRoom(msg.room, msg.players, msg.npcs);
    pushEvent('room', `Entered ${msg.room.name}`);
  },
  move_ok(msg) {
    updateRoom(msg.room, msg.players, msg.npcs);
    pushEvent('move', `Moved ${msg.direction} to ${msg.room.name}`);
  },
  move_error(msg) {
    pushEvent('error', `Move failed: ${msg.reason}`);
  },

  // Presence
  player_entered(msg) {
    if (msg.playerInfo) {
      state.playersInRoom = state.playersInRoom.filter(p => p.name !== msg.playerName);
      state.playersInRoom.push(formatPlayerInfo(msg.playerInfo));
    }
    pushEvent('presence', `${msg.playerName} entered the room`);
  },
  player_left(msg) {
    state.playersInRoom = state.playersInRoom.filter(p => p.name !== msg.playerName);
    pushEvent('presence', `${msg.playerName} left ${msg.direction}`);
  },
  npc_entered(msg) {
    if (msg.spawned) {
      pushEvent('spawn', `${msg.npcName} appeared`);
    } else {
      pushEvent('presence', `${msg.npcName} entered the room`);
    }
    state.npcsInRoom.push({
      id: msg.npcId,
      name: msg.npcName,
      templateId: msg.templateId || '',
      hostile: msg.hostile,
      hp: msg.currentHp,
      maxHp: msg.maxHp,
    });
    scheduleStateWrite();
  },
  npc_left(msg) {
    state.npcsInRoom = state.npcsInRoom.filter(n => n.id !== msg.npcId);
    pushEvent('presence', `${msg.npcName} left ${msg.direction}`);
  },

  // Chat
  player_says(msg) {
    pushEvent('chat', `${msg.playerName} says: "${msg.message}"`);
  },

  // Combat
  combat_hit(msg) {
    // Update NPC HP in our local list
    if (!msg.isPlayerDefender && msg.defenderId) {
      const npc = state.npcsInRoom.find(n => n.id === msg.defenderId);
      if (npc) {
        npc.hp = msg.defenderHp;
        npc.maxHp = msg.defenderMaxHp;
      }
    }
    // Update player HP if we're the defender
    if (msg.isPlayerDefender && state.player && msg.defenderName === state.player.name) {
      state.player.hp = msg.defenderHp;
      state.player.maxHp = msg.defenderMaxHp;
    }

    let summary;
    if (msg.isMiss) {
      summary = `${msg.attackerName} missed ${msg.defenderName}`;
    } else if (msg.isDodge) {
      summary = `${msg.defenderName} dodged ${msg.attackerName}'s attack`;
    } else if (msg.isParry) {
      summary = `${msg.defenderName} parried ${msg.attackerName}'s attack`;
    } else if (msg.isBackstab) {
      summary = `${msg.attackerName} backstabbed ${msg.defenderName} for ${msg.damage} damage (${msg.defenderHp}/${msg.defenderMaxHp} HP)`;
    } else {
      summary = `${msg.attackerName} hit ${msg.defenderName} for ${msg.damage} damage (${msg.defenderHp}/${msg.defenderMaxHp} HP)`;
    }
    pushEvent('combat_hit', summary);
  },
  skill_effect(msg) {
    if (msg.targetId) {
      const npc = state.npcsInRoom.find(n => n.id === msg.targetId);
      if (npc) {
        npc.hp = msg.targetHp;
        npc.maxHp = msg.targetMaxHp;
      }
    }
    pushEvent('skill_effect', `${msg.userName} used ${msg.skillName} on ${msg.targetName}: ${msg.damage} damage (${msg.targetHp}/${msg.targetMaxHp} HP)`);
  },
  spell_effect(msg) {
    if (msg.targetId && !msg.isPlayerTarget) {
      const npc = state.npcsInRoom.find(n => n.id === msg.targetId);
      if (npc) {
        npc.hp = msg.targetNewHp;
        npc.maxHp = msg.targetMaxHp;
      }
    }
    if (msg.isPlayerTarget && state.player && msg.targetName === state.player.name) {
      state.player.hp = msg.targetNewHp;
      state.player.maxHp = msg.targetMaxHp;
    }
    pushEvent('spell_effect', `${msg.casterName} cast ${msg.spellName} on ${msg.targetName}: ${msg.effectAmount} (${msg.targetNewHp}/${msg.targetMaxHp} HP)`);
  },
  spell_cast_result(msg) {
    if (state.player) state.player.mp = msg.newMp;
    if (msg.newHp != null && state.player) state.player.hp = msg.newHp;
    pushEvent('spell', `${msg.spellName}: ${msg.message}`);
  },
  npc_died(msg) {
    state.npcsInRoom = state.npcsInRoom.filter(n => n.id !== msg.npcId);
    pushEvent('npc_killed', `${msg.npcName} was killed by ${msg.killerName}`);
  },
  player_died(msg) {
    if (state.player) {
      state.player.hp = msg.respawnHp;
      state.player.mp = msg.respawnMp || 0;
    }
    state.attackMode = false;
    state.selectedTarget = null;
    pushEvent('player_died', `Killed by ${msg.killerName}! Respawned.`);
  },
  attack_mode_update(msg) {
    state.attackMode = msg.enabled;
    pushEvent('combat', `Attack mode ${msg.enabled ? 'ON' : 'OFF'}`);
  },

  // Effects
  active_effects_update(msg) {
    state.activeEffects = (msg.effects || []).map(e => ({
      name: e.name,
      remainingTicks: e.remainingTicks,
      type: e.type || '',
    }));
    scheduleStateWrite();
  },
  effect_tick(msg) {
    if (state.player) state.player.hp = msg.newHp;
    if (msg.newMp >= 0 && state.player) state.player.mp = msg.newMp;
    pushEvent('effect_tick', msg.message);
  },

  // Stealth / Meditation
  stealth_update(msg) {
    state.isHidden = msg.hidden;
    if (msg.message) pushEvent('stealth', msg.message);
    else pushEvent('stealth', msg.hidden ? 'You are hidden' : 'You are visible');
  },
  meditate_update(msg) {
    state.isMeditating = msg.meditating;
    if (msg.message) pushEvent('meditate', msg.message);
    else pushEvent('meditate', msg.meditating ? 'Meditating...' : 'Stopped meditating');
  },
  rest_update(msg) {
    state.isResting = msg.resting;
    if (msg.message) pushEvent('rest', msg.message);
    else pushEvent('rest', msg.resting ? 'Resting...' : 'Stopped resting');
  },
  track_result(msg) {
    pushEvent('track', msg.message);
  },

  // Inventory / Items
  inventory_update(msg) {
    state.inventory = (msg.inventory || []).map(formatInventoryItem);
    state.equipment = msg.equipment || {};
    if (msg.coins) state.coins = formatCoins(msg.coins);
    scheduleStateWrite();
  },
  room_items_update(msg) {
    state.groundItems = (msg.items || []).map(i => ({
      itemId: i.itemId,
      name: itemCatalogMap[i.itemId] || i.itemId,
      quantity: i.quantity || 1,
    }));
    state.groundCoins = formatCoins(msg.coins);
    scheduleStateWrite();
  },
  loot_received(msg) {
    const names = (msg.items || []).map(i => `${i.itemName}${i.quantity > 1 ? ' x' + i.quantity : ''}`).join(', ');
    pushEvent('loot', `Received from ${msg.npcName}: ${names}`);
  },
  loot_dropped(msg) {
    const names = (msg.items || []).map(i => `${i.itemName}${i.quantity > 1 ? ' x' + i.quantity : ''}`).join(', ');
    const coinStr = formatCoinString(msg.coins);
    const parts = [names, coinStr].filter(Boolean);
    pushEvent('loot', `${msg.npcName} dropped: ${parts.join(', ') || 'nothing'}`);
  },
  pickup_result(msg) {
    pushEvent('pickup', `Picked up ${msg.quantity}x ${msg.itemName}${msg.isCoin ? ' (coins)' : ''}`);
  },
  item_used(msg) {
    if (state.player) {
      state.player.hp = msg.newHp;
      state.player.mp = msg.newMp;
    }
    pushEvent('item', `Used ${msg.itemName}: ${msg.message}`);
  },
  equip_update(msg) {
    if (msg.itemId) {
      state.equipment[msg.slot] = msg.itemId;
    } else {
      delete state.equipment[msg.slot];
    }
    pushEvent('equip', msg.itemId ? `Equipped ${msg.itemName} in ${msg.slot}` : `Unequipped ${msg.slot}`);
  },

  // Progression
  xp_gained(msg) {
    if (state.player) {
      state.player.xp = msg.currentXp;
      state.player.xpToNextLevel = msg.xpToNextLevel;
    }
    const xpText = msg.amount >= 0
      ? `Gained ${msg.amount} XP (${msg.currentXp}/${msg.xpToNextLevel})`
      : `Lost ${-msg.amount} XP (${msg.currentXp}/${msg.xpToNextLevel})`;
    pushEvent('xp', xpText);
  },
  level_up(msg) {
    if (state.player) {
      state.player.level = msg.newLevel;
      state.player.maxHp = msg.newMaxHp;
      state.player.hp = msg.newMaxHp;      // Full heal on level up
      state.player.maxMp = msg.newMaxMp;
      state.player.mp = msg.newMaxMp;      // Full mana on level up
      state.player.unspentCp = msg.totalUnspentCp;
      state.player.xpToNextLevel = msg.xpToNextLevel;
    }
    pushEvent('level_up', `LEVEL UP! Now level ${msg.newLevel} (+${msg.hpRoll} HP, +${msg.mpRoll} MP, +${msg.cpGained} CP)`);
    computePlayerAbilities();
  },
  trainer_info(msg) {
    const lines = ['The trainer can help you level up and allocate Character Points (CP) to improve your stats. You earn CP each time you level up.'];
    if (msg.canLevelUp) {
      lines.push('You are ready to level up!');
    } else {
      lines.push('You are not ready to level up yet. Gain more XP by defeating enemies.');
    }
    const creationPool = 60; // StatAllocator.CP_POOL
    const levelingCp = msg.totalCpEarned - creationPool;
    if (levelingCp > 0) {
      lines.push(`CP: ${msg.unspentCp} unspent / ${levelingCp} earned from leveling (${msg.totalCpEarned} total budget).`);
    } else if (msg.unspentCp > 0) {
      lines.push(`CP: ${msg.unspentCp} unspent.`);
    } else {
      lines.push('You have no CP yet. Level up to earn CP for stat training!');
    }
    pushEvent('trainer', lines.join(' '));
  },
  stat_trained(msg) {
    if (state.player) {
      // Update the trained stat
      if (state.player.stats) {
        state.player.stats[msg.stat.toLowerCase()] = msg.newValue;
      }
      state.player.unspentCp = msg.remainingCp;
      // Update HP/MP if threshold bonuses changed them
      if (msg.maxHp > 0) {
        state.player.hp = msg.currentHp;
        state.player.maxHp = msg.maxHp;
      }
      if (msg.maxMp > 0) {
        state.player.mp = msg.currentMp;
        state.player.maxMp = msg.maxMp;
      }
    }
    pushEvent('trainer', `Trained ${msg.stat} to ${msg.newValue} (${msg.remainingCp} CP remaining)`);
  },

  // Vendor
  vendor_info(msg) {
    const itemList = (msg.items || []).map(i => {
      const name = i.item?.name || '?';
      const id = i.item?.id || i.itemId || '?';
      const price = formatCoinString(i.price) || 'free';
      return `${name} [${id}] (${price})`;
    }).join(', ');
    pushEvent('vendor', `${msg.vendorName} sells: ${itemList}`);
  },
  buy_result(msg) {
    if (msg.success) {
      state.inventory = (msg.updatedInventory || []).map(formatInventoryItem);
      state.equipment = msg.equipment || state.equipment;
      state.coins = formatCoins(msg.updatedCoins);
    }
    pushEvent('vendor', `Buy: ${msg.message}`);
  },
  sell_result(msg) {
    if (msg.success) {
      state.inventory = (msg.updatedInventory || []).map(formatInventoryItem);
      state.equipment = msg.equipment || state.equipment;
      state.coins = formatCoins(msg.updatedCoins);
    }
    pushEvent('vendor', `Sell: ${msg.message}`);
  },

  // World features
  interact_result(msg) {
    pushEvent('interact', `${msg.featureName}: ${msg.message}`);
  },

  npc_phase_shift(msg) {
    pushEvent('phase_shift', `⚡ ${msg.npcName} — ${msg.phaseName}: ${msg.message} (${msg.currentHp}/${msg.maxHp} HP)`);
  },
  choice_prompt(msg) {
    const opts = (msg.options || []).map(o => `[${o.id}] ${o.label}`).join(', ');
    pushEvent('choice_prompt', `${msg.label}: ${msg.question} — Options: ${opts}`);
  },

  // System
  system_message(msg) {
    pushEvent('system_message', msg.message);
  },
  tutorial(msg) {
    pushEvent('tutorial', `[${msg.title}] ${msg.content}`);
  },
  error(msg) {
    pushEvent('error', msg.message);
  },
  pong() { /* no-op */ },

  // Catalog syncs — store class/race for registration, log all
  class_catalog_sync(msg) {
    classCatalog = msg.classes || [];
    catalogsReceived.classes = true;
    pushEvent('system', `Received class catalog (${classCatalog.length} classes)`);
    tryRegister();
    tryGuestLogin();
  },
  item_catalog_sync(msg) {
    itemCatalogMap = {};
    for (const item of (msg.items || [])) {
      itemCatalogMap[item.id] = item.name;
    }
    pushEvent('system', `Received item catalog (${Object.keys(itemCatalogMap).length} items)`);
  },
  skill_catalog_sync(msg) {
    skillCatalog = msg.skills || [];
    pushEvent('system', `Received skill catalog (${skillCatalog.length} skills)`);
    computePlayerAbilities();
  },
  race_catalog_sync(msg) {
    raceCatalog = msg.races || [];
    catalogsReceived.races = true;
    pushEvent('system', `Received race catalog (${raceCatalog.length} races)`);
    tryRegister();
    tryGuestLogin();
  },
  spell_catalog_sync(msg) {
    spellCatalog = msg.spells || [];
    pushEvent('system', `Received spell catalog (${spellCatalog.length} spells)`);
    computePlayerAbilities();
  },

  // Server shutdown
  server_shutdown(msg) {
    pushEvent('server_shutdown', msg.message);
    console.log(`[relay] SERVER SHUTDOWN: ${msg.message} (${msg.secondsRemaining}s remaining)`);
    if (msg.secondsRemaining <= 0) {
      serverShuttingDown = true;
    }
  },

  // Map data — just log
  map_data() { pushEvent('system', 'Received map data'); },
};

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function updateRoom(room, players, npcs) {
  state.room = {
    id: room.id,
    name: room.name,
    description: room.description || '',
    exits: room.exits || {},
  };
  state.playersInRoom = (players || []).map(formatPlayerInfo);
  state.npcsInRoom = (npcs || []).map(n => ({
    id: n.id || '',
    name: n.name,
    templateId: n.templateId || '',
    hostile: n.hostile ?? false,
    hp: n.currentHp ?? n.hp ?? 0,
    maxHp: n.maxHp ?? 0,
  }));
  // Reset ground items — server will send room_items_update separately
  state.groundItems = [];
  state.groundCoins = { copper: 0, silver: 0, gold: 0 };
  scheduleStateWrite();
}

function formatPlayerInfo(p) {
  return {
    name: p.name || p.playerName || '',
    class: p.characterClass || '',
    level: p.level || 0,
  };
}

function formatInventoryItem(i) {
  return {
    itemId: i.itemId,
    name: itemCatalogMap[i.itemId] || i.itemId,
    quantity: i.quantity || 1,
    equipped: i.equipped || false,
    slot: i.slot || '',
  };
}

function formatCoins(c) {
  if (!c) return { copper: 0, silver: 0, gold: 0 };
  return { copper: c.copper || 0, silver: c.silver || 0, gold: c.gold || 0 };
}

function formatCoinString(c) {
  if (!c) return '';
  const parts = [];
  if (c.gold) parts.push(`${c.gold}g`);
  if (c.silver) parts.push(`${c.silver}s`);
  if (c.copper) parts.push(`${c.copper}c`);
  return parts.join(' ');
}

// ---------------------------------------------------------------------------
// WebSocket connection
// ---------------------------------------------------------------------------
let ws = null;
let pingTimer = null;
let commandPollTimer = null;
let serverShuttingDown = false;

function send(msg) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(msg));
  }
}

function connect() {
  console.log(`[relay] Connecting to ${SERVER_URL}...`);
  ws = new WebSocket(SERVER_URL);

  ws.on('open', () => {
    console.log('[relay] Connected');
    state.connected = true;
    scheduleStateWrite();

    // Authenticate — staging waits for platform_auth_ok after client_hello; local sends login immediately
    registrationSent = false;
    if (!stagingMode && !registerMode && !guestMode) {
      send({ type: 'login', username, password });
    }
    // Staging: platform_login/platform_register sent after platform_auth_ok handler fires
    // Register/Guest: tryRegister()/tryGuestLogin() fires once catalogs arrive

    // Keepalive ping
    pingTimer = setInterval(() => send({ type: 'ping' }), PING_INTERVAL_MS);

    // Start polling for command file
    commandPollTimer = setInterval(pollCommandFile, COMMAND_POLL_MS);
  });

  ws.on('message', (data) => {
    try {
      const msg = JSON.parse(data.toString());
      const handler = handlers[msg.type];
      if (handler) {
        handler(msg);
      } else {
        console.log(`[relay] Unhandled message type: ${msg.type}`);
      }
    } catch (err) {
      console.error('[relay] Failed to parse message:', err.message);
    }
  });

  ws.on('close', (code, reason) => {
    if (serverShuttingDown) {
      console.log(`[relay] Server shut down gracefully (code=${code}). Exiting.`);
      shutdownRelay(0);
    } else {
      console.error(`[relay] Connection lost (code=${code}). Exiting.`);
      shutdownRelay(1);
    }
  });

  ws.on('error', (err) => {
    console.error(`[relay] WebSocket error: ${err.message}`);
    // close event will fire after this and trigger shutdownRelay
  });
}

function cleanup() {
  if (pingTimer) { clearInterval(pingTimer); pingTimer = null; }
  if (commandPollTimer) { clearInterval(commandPollTimer); commandPollTimer = null; }
}

// ---------------------------------------------------------------------------
// Command file polling
// ---------------------------------------------------------------------------
async function pollCommandFile() {
  if (!fs.existsSync(COMMAND_FILE)) return;

  let commands;
  try {
    const raw = fs.readFileSync(COMMAND_FILE, 'utf8');
    commands = JSON.parse(raw);
    // Delete immediately so we don't re-process
    fs.unlinkSync(COMMAND_FILE);
  } catch (err) {
    console.error('[relay] Failed to read command file:', err.message);
    // Try to delete corrupt file
    try { fs.unlinkSync(COMMAND_FILE); } catch {}
    return;
  }

  if (!Array.isArray(commands)) {
    commands = [commands];
  }

  console.log(`[relay] Processing ${commands.length} command(s)`);
  for (const cmd of commands) {
    send(cmd);
    console.log(`[relay]   -> ${cmd.type}`);
    if (commands.length > 1) {
      await sleep(COMMAND_SPACING_MS);
    }
  }
}

function sleep(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

// ---------------------------------------------------------------------------
// PID lockfile — prevent multiple relay instances
// ---------------------------------------------------------------------------
function isProcessRunning(pid) {
  try {
    process.kill(pid, 0); // signal 0 = existence check, doesn't kill
    return true;
  } catch {
    return false; // ESRCH = no such process
  }
}

function acquireLock() {
  try {
    const existing = fs.readFileSync(LOCK_FILE, 'utf8').trim();
    const pid = parseInt(existing, 10);
    if (!isNaN(pid) && pid !== process.pid && isProcessRunning(pid)) {
      console.error(`[relay] Another relay is already running (PID ${pid}).`);
      console.error('[relay] Kill it first or delete scripts/relay.lock to override.');
      process.exit(1);
    }
    // Stale lock from a dead process — reclaim it
    console.log(`[relay] Removing stale lock (PID ${pid})`);
  } catch {
    // No lock file exists — fine
  }
  fs.writeFileSync(LOCK_FILE, String(process.pid), 'utf8');
}

function releaseLock() {
  try {
    const contents = fs.readFileSync(LOCK_FILE, 'utf8').trim();
    if (contents === String(process.pid)) {
      fs.unlinkSync(LOCK_FILE);
    }
  } catch {}
}

// ---------------------------------------------------------------------------
// Startup
// ---------------------------------------------------------------------------
console.log('[relay] NeoMud Game Relay');
const mode = guestMode ? 'guest' : registerMode ? 'register' : 'login';
console.log(`[relay] User: ${guestMode ? guestOpts.charName : username}, Mode: ${mode}`);

acquireLock();

// Clean up stale files
try { fs.unlinkSync(STATE_FILE); } catch {}
try { fs.unlinkSync(COMMAND_FILE); } catch {}
try { fs.unlinkSync(TEMP_STATE_FILE); } catch {}

// Write initial state
writeStateFile();
connect();

// Graceful shutdown
function shutdownRelay(exitCode = 0) {
  cleanup();
  if (ws) { try { ws.close(); } catch {} }
  try { fs.unlinkSync(STATE_FILE); } catch {}
  try { fs.unlinkSync(TEMP_STATE_FILE); } catch {}
  releaseLock();
  process.exit(exitCode);
}

process.on('SIGINT', () => {
  console.log('\n[relay] Shutting down...');
  shutdownRelay(0);
});
process.on('SIGTERM', () => {
  console.log('[relay] Received SIGTERM. Shutting down...');
  shutdownRelay(0);
});
