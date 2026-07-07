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
 *        node scripts/game-relay.mjs --interactive [--color] <username> <password>   (classic scrolling MUD text)
 *        node scripts/game-relay.mjs --id <suffix> <username> <password>             (namespaced instance)
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
import readline from 'readline';
import { renderMessage, renderPrompt, renderHelp, renderPartyState } from './mud-renderer.mjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// --id <suffix> enables multiple relay instances (separate state/command/lock files)
// --interactive enables stdin readline mode with text rendering
// --color enables ANSI color output for --interactive mode
const idIdx = process.argv.indexOf('--id');
const INSTANCE_ID = idIdx !== -1 && idIdx + 1 < process.argv.length ? process.argv[idIdx + 1] : null;
let INTERACTIVE_MODE = false;
let COLOR_MODE = false;
const fileSuffix = INSTANCE_ID ? `-${INSTANCE_ID}` : '';

const STATE_FILE = path.join(__dirname, `relay-state${fileSuffix}.json`);
const COMMAND_FILE = path.join(__dirname, `relay-command${fileSuffix}.json`);
const TEMP_STATE_FILE = path.join(__dirname, `.relay-state${fileSuffix}.tmp`);
const PROCESSING_FILE = path.join(__dirname, `.relay-command${fileSuffix}.processing`);
const LOCK_FILE = path.join(__dirname, `relay${fileSuffix}.lock`);

const COMMAND_POLL_MS = 250;
const PING_INTERVAL_MS = 30_000;
const COMMAND_SPACING_MS = 150;
const MAX_RECENT_EVENTS = 50;
const STATE_WRITE_DEBOUNCE_MS = 100;
const MAX_RECONNECT_ATTEMPTS = 5;
const RECONNECT_BASE_MS = 2000;
const COMMAND_DEDUP_MS = 500;

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
let characterOverride = null;
let username, password;

// Extract --url, --staging, --character, and --id flags first
const args = [];
for (let i = 0; i < rawArgs.length; i++) {
  if (rawArgs[i] === '--url' && i + 1 < rawArgs.length) {
    cliUrl = rawArgs[++i];
  } else if (rawArgs[i] === '--staging' && i + 1 < rawArgs.length) {
    stagingMode = true;
    stagingSlug = rawArgs[++i];
  } else if (rawArgs[i] === '--character' && i + 1 < rawArgs.length) {
    characterOverride = rawArgs[++i];
  } else if (rawArgs[i] === '--id' && i + 1 < rawArgs.length) {
    i++; // already parsed above
  } else if (rawArgs[i] === '--interactive') {
    INTERACTIVE_MODE = true;
  } else if (rawArgs[i] === '--color') {
    COLOR_MODE = true;
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
  party: null,
  following: null,
  pendingPartyInvites: [],
  pendingPrompt: null,
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
    if (msg.characterNames && msg.characterNames.length > 1) {
      pushEvent('system', `Available characters: ${msg.characterNames.join(', ')}`);
    }
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
      const charName = characterOverride || (msg.characterNames && msg.characterNames[0]) || msg.characterName;
      pushEvent('system', `Logging in as ${charName}...`);
      send({ type: 'platform_login', characterName: charName });
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
    reconnectAttempts = 0;
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

    if (msg.reason && msg.reason.toLowerCase().includes('already logged in')) {
      console.log('[relay] Account already logged in. Waiting 5s then retrying...');
      setTimeout(() => {
        if (!state.loggedIn) {
          console.log('[relay] Retrying login...');
          retryAuth();
        }
      }, 5000);
    } else if (msg.reason && msg.reason.toLowerCase().includes('too many')) {
      console.error('[relay] Rate limited. Exiting.');
      shutdownRelay(1);
    } else {
      console.error('[relay] Fatal auth error. Exiting.');
      shutdownRelay(1);
    }
  },

  session_conflict(msg) {
    pushEvent('system', `Session conflict for ${msg.characterName} — forcing takeover`);
    console.log('[relay] Session conflict detected — re-sending with force=true');
    if (stagingMode) {
      const charName = characterOverride || msg.characterName;
      send({ type: 'platform_login', characterName: charName, force: true });
    } else if (guestMode) {
      // Guest sessions use unique UUID usernames and cannot conflict.
      // If we get here, something is wrong — exit rather than silently re-register.
      console.error('[relay] Unexpected session_conflict in guest mode');
      shutdownRelay(1);
    } else {
      send({ type: 'login', username, password, force: true });
    }
  },

  session_displaced(msg) {
    pushEvent('system', `Session displaced: ${msg.reason || 'Another session logged in'}`);
    console.log('[relay] Session displaced by another login. Exiting.');
    serverShuttingDown = true;
    shutdownRelay(0);
  },
  connection_rejected(msg) {
    pushEvent('error', `Connection rejected: ${msg.reason}`);
    console.error(`[relay] Connection rejected: ${msg.reason}`);
    shutdownRelay(1);
  },
  name_check_result(msg) {
    const charStatus = msg.characterNameAvailable ? 'available' : 'taken';
    pushEvent('system', `Name check: character name is ${charStatus}`);
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
  tell_received(msg) {
    pushEvent('tell', `${msg.senderName} tells you: "${msg.message}"`);
  },
  tell_sent(msg) {
    pushEvent('tell', `You tell ${msg.targetName}: "${msg.message}"`);
  },
  who_list(msg) {
    const players = (msg.players || []);
    const names = players.map(p => `${p.name} (Lv${p.level} ${p.characterClass})`).join(', ');
    pushEvent('who', `${players.length} online: ${names}`);
  },

  // Party
  party_invite_received(msg) {
    state.pendingPartyInvites.push({ from: msg.inviterName, partySize: msg.partySize || 1 });
    pushEvent('party', `${msg.inviterName} invited you to a party (${msg.partySize || 1} members)`);
  },
  party_formed(msg) {
    state.party = {
      id: msg.partyId,
      leader: msg.leaderId,
      members: (msg.members || []).map(formatPartyMember),
    };
    state.pendingPartyInvites = [];
    const names = (msg.members || []).map(m => m.name).join(', ');
    pushEvent('party', `Party formed: ${names} (leader: ${msg.leaderId})`);
  },
  party_member_joined(msg) {
    if (state.party) {
      state.party.members.push(formatPartyMember(msg.member));
    }
    pushEvent('party', `${msg.member.name} joined the party`);
  },
  party_member_left(msg) {
    if (state.party) {
      state.party.members = state.party.members.filter(m => m.name !== msg.memberName);
    }
    const action = msg.reason === 'kicked' ? 'was kicked from' : 'left';
    pushEvent('party', `${msg.memberName} ${action} the party`);
  },
  party_disbanded(msg) {
    state.party = null;
    state.following = null;
    pushEvent('party', `Party disbanded: ${msg.reason || 'disbanded'}`);
  },
  party_member_update(msg) {
    if (!state.party) return;
    const member = state.party.members.find(m => m.name === msg.memberName);
    if (member) {
      if (msg.currentHp != null) member.hp = msg.currentHp;
      if (msg.maxHp != null) member.maxHp = msg.maxHp;
      if (msg.currentMp != null) member.mp = msg.currentMp;
      if (msg.maxMp != null) member.maxMp = msg.maxMp;
      if (msg.roomId != null) member.roomId = msg.roomId;
    }
    scheduleStateWrite();
  },
  party_chat(msg) {
    pushEvent('party_chat', `[Party] ${msg.senderName}: ${msg.message}`);
  },
  party_leader_changed(msg) {
    if (state.party) {
      state.party.leader = msg.newLeaderId;
      for (const m of state.party.members) {
        m.isLeader = m.name === msg.newLeaderId;
      }
    }
    pushEvent('party', `${msg.newLeaderId} is now the party leader`);
  },
  party_info(msg) {
    state.party = {
      id: msg.partyId,
      leader: msg.leaderId,
      members: (msg.members || []).map(formatPartyMember),
    };
    pushEvent('party', 'Party info restored');
  },

  // Follow
  follow_update(msg) {
    if (state.player && msg.followerName === state.player.name) {
      if (msg.state === 'OFF') {
        state.following = null;
      } else {
        state.following = { target: msg.targetName, state: msg.state };
      }
    }
    pushEvent('follow', `${msg.followerName} ${msg.state === 'OFF' ? 'stopped following' : 'is following'} ${msg.targetName}`);
  },
  follow_failed(msg) {
    pushEvent('follow', `Follow failed: ${msg.reason}`);
  },

  // Rally
  rally_ping(msg) {
    pushEvent('rally', `${msg.leaderName} rallies the party! (${msg.roomName} in ${msg.zoneName})`);
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
    state.pendingPrompt = null;
    scheduleStateWrite();
  },

  npc_phase_shift(msg) {
    pushEvent('phase_shift', `⚡ ${msg.npcName} — ${msg.phaseName}: ${msg.message} (${msg.currentHp}/${msg.maxHp} HP)`);
  },
  npc_ability_effect(msg) {
    for (const r of (msg.results || [])) {
      if (state.player && r.targetName === state.player.name && r.newHp != null) {
        state.player.hp = r.newHp;
      }
    }
    const hits = (msg.results || []).map(r => `${r.targetName}: ${r.damage} dmg${r.saved ? ' (saved)' : ''}`).join(', ');
    pushEvent('ability', `${msg.npcName} uses ${msg.abilityName}: ${hits}`);
  },
  npc_dialogue(msg) {
    pushEvent('dialogue', `${msg.npc_name}: ${msg.content}`);
    state.pendingPrompt = { type: 'dialogue', npcId: msg.npc_id, npcName: msg.npc_name, content: msg.content };
    scheduleStateWrite();
  },
  choice_prompt(msg) {
    const opts = (msg.options || []).map(o => `[${o.id}] ${o.label}`).join(', ');
    pushEvent('choice_prompt', `${msg.label}: ${msg.question} — Options: ${opts}`);
    state.pendingPrompt = { type: 'choice', featureId: msg.featureId, label: msg.label, question: msg.question, options: msg.options };
    scheduleStateWrite();
  },
  place_item_prompt(msg) {
    const accepted = (msg.acceptedItems || []).join(', ');
    pushEvent('place_item_prompt', `${msg.label}: ${msg.prompt} — Accepted items: ${accepted}`);
    state.pendingPrompt = { type: 'place_item', featureId: msg.featureId, label: msg.label, prompt: msg.prompt, acceptedItems: msg.acceptedItems };
    scheduleStateWrite();
  },
  riddle_prompt(msg) {
    const hint = msg.hint ? ` (Hint: ${msg.hint})` : '';
    pushEvent('riddle_prompt', `${msg.label}: ${msg.question}${hint}`);
    state.pendingPrompt = { type: 'riddle', featureId: msg.featureId, label: msg.label, question: msg.question, hint: msg.hint };
    scheduleStateWrite();
  },

  // Crafting
  crafting_menu(msg) {
    const recipes = (msg.recipes || []).map(r => `${r.name || r.recipeId} [${r.recipeId}]`).join(', ');
    pushEvent('crafting', `${msg.crafterName} offers: ${recipes}`);
    state.pendingPrompt = { type: 'crafting', crafterName: msg.crafterName, recipes: msg.recipes };
    scheduleStateWrite();
  },
  craft_result(msg) {
    if (msg.success) {
      state.inventory = (msg.updatedInventory || []).map(formatInventoryItem);
      state.equipment = msg.equipment || state.equipment;
      if (msg.updatedCoins) state.coins = formatCoins(msg.updatedCoins);
    }
    pushEvent('crafting', `Craft: ${msg.message}`);
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
    interactables: (room.interactables || []).map(i => ({
      id: i.id,
      label: i.label || i.id,
      description: i.description || '',
      actionType: i.actionType || '',
      triggerType: i.triggerType || 'ON_ACTION',
    })),
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
  // Reset ground items and pending prompts — server will send room_items_update separately
  state.groundItems = [];
  state.groundCoins = { copper: 0, silver: 0, gold: 0 };
  state.pendingPrompt = null;
  scheduleStateWrite();
}

function formatPlayerInfo(p) {
  return {
    name: p.name || p.playerName || '',
    class: p.characterClass || '',
    level: p.level || 0,
  };
}

function formatPartyMember(m) {
  return {
    name: m.name || '',
    class: m.characterClass || '',
    level: m.level || 0,
    hp: m.currentHp ?? m.hp ?? 0,
    maxHp: m.maxHp || 0,
    mp: m.currentMp ?? m.mp ?? 0,
    maxMp: m.maxMp || 0,
    roomId: m.roomId || '',
    isLeader: m.isLeader || false,
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
// Interactive mode — output, local commands, command parser
// ---------------------------------------------------------------------------
function printOutput(lines) {
  if (!lines || lines.length === 0) return;
  if (rl) {
    readline.clearLine(process.stdout, 0);
    readline.cursorTo(process.stdout, 0);
  }
  for (const line of lines) {
    process.stdout.write(line + '\n');
  }
  if (rl && state.loggedIn) {
    rl.setPrompt(renderPrompt(state, COLOR_MODE));
    rl.prompt(true);
  }
}

function handleLocalCommand(cmd) {
  switch (cmd) {
    case 'help':
      printOutput(renderHelp(COLOR_MODE));
      break;
    case 'party_info':
      printOutput(renderPartyState(state.party, COLOR_MODE));
      break;
    case 'hp': {
      const p = state.player;
      if (p) {
        const bar = p.maxHp > 0 ? ` [${'█'.repeat(Math.round(p.hp/p.maxHp*10))}${'░'.repeat(10-Math.round(p.hp/p.maxHp*10))}]` : '';
        process.stdout.write(`HP: ${p.hp}/${p.maxHp}${bar}  MP: ${p.mp}/${p.maxMp}\n`);
      }
      break;
    }
    case 'effects':
      if (state.activeEffects.length > 0) {
        for (const e of state.activeEffects) {
          process.stdout.write(`  ${e.name} (${e.remainingTicks} ticks)\n`);
        }
      } else {
        process.stdout.write('No active effects.\n');
      }
      break;
    case 'skills':
      if (state.availableSkills.length > 0) {
        process.stdout.write('Available skills:\n');
        for (const s of state.availableSkills) {
          if (!s.isPassive) process.stdout.write(`  ${s.id}: ${s.name} — ${s.description}\n`);
        }
      } else {
        process.stdout.write('No active skills available.\n');
      }
      break;
    case 'spells':
      if (state.availableSpells.length > 0) {
        process.stdout.write('Available spells:\n');
        for (const s of state.availableSpells) {
          process.stdout.write(`  ${s.id}: ${s.name} (${s.school}) — MP:${s.manaCost}\n`);
        }
      } else {
        process.stdout.write('No spells available.\n');
      }
      break;
    case 'cancel':
      state.pendingPrompt = null;
      process.stdout.write('Cancelled.\n');
      scheduleStateWrite();
      break;
    case 'quit':
      shutdownRelay(0);
      break;
  }
}

function resolveNpc(name, s) {
  if (!name) return null;
  const npcs = s.npcsInRoom || [];
  const n = name.toLowerCase();
  return npcs.find(x => x.name.toLowerCase() === n)
    || npcs.find(x => x.name.toLowerCase().startsWith(n))
    || npcs.find(x => x.name.toLowerCase().includes(n))
    || null;
}

function resolveCurrentTarget(s) {
  if (s.selectedTarget) {
    const npc = (s.npcsInRoom || []).find(n => n.id === s.selectedTarget);
    if (npc) return npc;
  }
  return (s.npcsInRoom || []).find(n => n.hostile) || null;
}

function resolveGroundItem(name, s) {
  if (!name) return null;
  const items = s.groundItems || [];
  const n = name.toLowerCase();
  return items.find(i => (i.name || i.itemId).toLowerCase() === n)
    || items.find(i => (i.name || i.itemId).toLowerCase().startsWith(n))
    || items.find(i => (i.name || i.itemId).toLowerCase().includes(n))
    || null;
}

function resolveInventoryItem(name, s) {
  if (!name) return null;
  const items = s.inventory || [];
  const n = name.toLowerCase();
  return items.find(i => (i.name || i.itemId).toLowerCase() === n)
    || items.find(i => (i.name || i.itemId).toLowerCase().startsWith(n))
    || items.find(i => (i.name || i.itemId).toLowerCase().includes(n))
    || null;
}

function resolveInteractable(name, s) {
  if (!name) return null;
  const features = (s.room?.interactables || []).filter(i => i.triggerType !== 'ON_ENTER');
  const n = name.toLowerCase();
  return features.find(f => f.label.toLowerCase() === n)
    || features.find(f => f.label.toLowerCase().startsWith(n))
    || features.find(f => f.label.toLowerCase().includes(n))
    || null;
}

const DIR_MAP = {
  n: 'NORTH', s: 'SOUTH', e: 'EAST', w: 'WEST', u: 'UP', d: 'DOWN',
  north: 'NORTH', south: 'SOUTH', east: 'EAST', west: 'WEST', up: 'UP', down: 'DOWN',
  ne: 'NORTHEAST', nw: 'NORTHWEST', se: 'SOUTHEAST', sw: 'SOUTHWEST',
  northeast: 'NORTHEAST', northwest: 'NORTHWEST', southeast: 'SOUTHEAST', southwest: 'SOUTHWEST',
};

function parseInteractiveCommand(line) {
  const parts = line.trim().split(/\s+/);
  if (!parts[0]) return null;
  const cmd = parts[0].toLowerCase();
  const arg1 = parts[1] || '';
  const rest = parts.slice(1).join(' ');

  if (DIR_MAP[cmd]) return [{ type: 'move', direction: DIR_MAP[cmd] }];

  switch (cmd) {
    case 'look': case 'l':
      return [{ type: 'look' }];

    case 'say': {
      if (!rest) return { error: 'Say what?' };
      return [{ type: 'say', message: rest }];
    }

    // Slash commands routed through say (tell, who, reply, etc.)
    case '/tell': case 'tell': case 't':
      if (!arg1) return { error: 'Usage: tell <player> <message>' };
      return [{ type: 'say', message: `/tell ${rest}` }];

    case 'who':
      return [{ type: 'say', message: '/who' }];

    case 'reply': case 'r':
      if (!rest) return { error: 'Usage: reply <message>' };
      return [{ type: 'say', message: `/reply ${rest}` }];

    case 'attack': case 'kill': case 'k': {
      if (!arg1) {
        if (state.attackMode) return [{ type: 'attack_toggle', enabled: false }];
        return { error: 'Attack what? (attack <npc>)' };
      }
      const npc = resolveNpc(arg1, state);
      if (!npc) return { error: `No NPC named '${arg1}' here.` };
      state.selectedTarget = npc.id;
      return [
        { type: 'select_target', npcId: npc.id },
        { type: 'attack_toggle', enabled: true },
      ];
    }

    case 'stop': case 'flee':
      return [
        { type: 'attack_toggle', enabled: false },
        { type: 'select_target', npcId: null },
      ];

    case 'select': case 'target': {
      if (!arg1) {
        state.selectedTarget = null;
        return [{ type: 'select_target', npcId: null }];
      }
      const npc = resolveNpc(arg1, state);
      if (!npc) return { error: `No NPC named '${arg1}' here.` };
      state.selectedTarget = npc.id;
      return [{ type: 'select_target', npcId: npc.id }];
    }

    case 'inv': case 'inventory': case 'i':
      return [{ type: 'view_inventory' }];

    case 'get': case 'take': case 'pickup': {
      if (!arg1) return { error: 'Get what?' };
      if (arg1.toLowerCase() === 'all') {
        const msgs = [];
        for (const item of (state.groundItems || [])) {
          msgs.push({ type: 'pickup_item', itemId: item.itemId, quantity: item.quantity || 1 });
        }
        const coins = state.groundCoins || {};
        if (coins.gold > 0) msgs.push({ type: 'pickup_coins', coinType: 'gold' });
        if (coins.silver > 0) msgs.push({ type: 'pickup_coins', coinType: 'silver' });
        if (coins.copper > 0) msgs.push({ type: 'pickup_coins', coinType: 'copper' });
        return msgs.length > 0 ? msgs : { error: 'Nothing to pick up.' };
      }
      if (['gold', 'silver', 'copper'].includes(arg1.toLowerCase())) {
        return [{ type: 'pickup_coins', coinType: arg1.toLowerCase() }];
      }
      const item = resolveGroundItem(arg1, state);
      if (!item) return { error: `No item '${arg1}' on the ground.` };
      const qty = parseInt(parts[2]) || item.quantity || 1;
      return [{ type: 'pickup_item', itemId: item.itemId, quantity: qty }];
    }

    case 'drop': {
      if (!arg1) return { error: 'Drop what?' };
      const item = resolveInventoryItem(arg1, state);
      if (!item) return { error: `No item '${arg1}' in inventory.` };
      const qty = parseInt(parts[2]) || 1;
      return [{ type: 'drop_item', itemId: item.itemId, quantity: qty }];
    }

    case 'use': {
      if (!arg1) return { error: 'Use what?' };
      const item = resolveInventoryItem(arg1, state);
      if (!item) return { error: `No item '${arg1}' in inventory. (Did you mean 'interact <feature>'?)` };
      return [{ type: 'use_item', itemId: item.itemId }];
    }

    case 'equip': case 'wear': case 'wield': {
      if (!arg1) return { error: 'Equip what?' };
      const item = resolveInventoryItem(arg1, state);
      if (!item) return { error: `No item '${arg1}' in inventory.` };
      return [{ type: 'equip_item', itemId: item.itemId, slot: '' }];
    }

    case 'unequip': case 'remove':
      if (!arg1) return { error: 'Unequip what slot?' };
      return [{ type: 'unequip_item', slot: arg1 }];

    case 'sneak':
      return [{ type: 'sneak_toggle', enabled: !state.isHidden }];
    case 'hide':
      return [{ type: 'sneak_toggle', enabled: true }];
    case 'unhide': case 'reveal':
      return [{ type: 'sneak_toggle', enabled: false }];

    case 'bash': {
      const npc = arg1 ? resolveNpc(arg1, state) : resolveCurrentTarget(state);
      if (!npc) return { error: 'Bash what? (attack <npc> first to select a target)' };
      return [{ type: 'use_skill', skillId: 'skill:bash', targetId: npc.id }];
    }

    case 'kick': {
      const npc = arg1 ? resolveNpc(arg1, state) : resolveCurrentTarget(state);
      if (!npc) return { error: 'Kick what? (attack <npc> first to select a target)' };
      return [{ type: 'use_skill', skillId: 'skill:kick', targetId: npc.id }];
    }

    case 'meditate':
      return [{ type: 'use_skill', skillId: 'skill:meditate' }];

    case 'track': {
      const npc = arg1 ? resolveNpc(arg1, state) : null;
      return [{ type: 'use_skill', skillId: 'skill:track', targetId: npc?.id || null }];
    }

    case 'skill': {
      if (!arg1) return { error: 'Which skill? (skill <skillId> [target])' };
      const npc = parts[2] ? resolveNpc(parts[2], state) : null;
      return [{ type: 'use_skill', skillId: arg1, targetId: npc?.id || null }];
    }

    case 'cast': {
      if (!arg1) return { error: 'Cast which spell? (cast <spellId> [target])' };
      const npc = parts[2] ? resolveNpc(parts[2], state) : null;
      return [{ type: 'cast_spell', spellId: arg1, targetId: npc?.id || null }];
    }

    case 'ready':
      return [{ type: 'ready_spell', spellId: arg1 || null }];

    case 'train': case 'trainer':
      if (arg1 === 'stat' && parts[2]) {
        return [{ type: 'train_stat', stat: parts[2].toUpperCase(), points: parseInt(parts[3]) || 1 }];
      }
      return [{ type: 'interact_trainer' }];

    case 'levelup':
      return [{ type: 'train_level_up' }];

    case 'shop': case 'vendor':
      return [{ type: 'interact_vendor' }];

    case 'buy': {
      if (!arg1) return { error: 'Buy what?' };
      const qty = parseInt(parts[2]) || 1;
      return [{ type: 'buy_item', itemId: arg1, quantity: qty }];
    }

    case 'sell': {
      if (!arg1) return { error: 'Sell what?' };
      const item = resolveInventoryItem(arg1, state);
      if (!item) return { error: `No item '${arg1}' in inventory.` };
      const qty = parseInt(parts[2]) || 1;
      return [{ type: 'sell_item', itemId: item.itemId, quantity: qty }];
    }

    case 'interact': case 'activate': {
      const features = (state.room?.interactables || []).filter(i => i.triggerType !== 'ON_ENTER');
      if (!arg1 && features.length === 1) {
        return [{ type: 'interact_feature', featureId: features[0].id }];
      }
      if (!arg1) return { error: 'Interact with what?' };
      const feature = resolveInteractable(arg1, state);
      if (!feature) return { error: `No feature '${arg1}' here.` };
      return [{ type: 'interact_feature', featureId: feature.id }];
    }

    case 'talk': case 'npc': {
      if (!arg1) return { error: 'Talk to whom?' };
      const npc = resolveNpc(arg1, state);
      if (!npc) return { error: `No NPC named '${arg1}' here.` };
      return [{ type: 'interact_npc', npc_id: npc.id }];
    }

    case 'answer': {
      const prompt = state.pendingPrompt;
      if (!prompt || prompt.type !== 'riddle') return { error: 'No riddle to answer.' };
      if (!rest) return { error: 'Answer what?' };
      return [{ type: 'answer_riddle', feature_id: prompt.featureId, answer: rest }];
    }

    case 'choose': {
      const prompt = state.pendingPrompt;
      if (!prompt || prompt.type !== 'choice') return { error: 'No choice active.' };
      const n = parseInt(arg1);
      if (isNaN(n) || n < 1 || n > (prompt.options || []).length) {
        return { error: `Choose a number 1-${(prompt.options || []).length}.` };
      }
      return [{ type: 'make_choice', feature_id: prompt.featureId, choice_id: prompt.options[n - 1].id }];
    }

    case 'place': {
      const prompt = state.pendingPrompt;
      if (!prompt || prompt.type !== 'place_item') return { error: 'No item placement active.' };
      if (!arg1) return { error: 'Place what item?' };
      const item = resolveInventoryItem(arg1, state);
      if (!item) return { error: `No item '${arg1}' in inventory.` };
      return [{ type: 'place_item', feature_id: prompt.featureId, item_id: item.itemId }];
    }

    case 'cancel':
      return { local: 'cancel' };

    case 'crafting': case 'recipes':
      return [{ type: 'interact_crafter' }];

    case 'craft': {
      if (!arg1) return [{ type: 'interact_crafter' }];
      return [{ type: 'craft_item', recipeId: arg1 }];
    }

    case 'party': {
      const sub = arg1.toLowerCase();
      switch (sub) {
        case 'invite': return parts[2] ? [{ type: 'party_invite', targetName: parts[2] }] : { error: 'Invite whom?' };
        case 'accept': return parts[2] ? [{ type: 'party_accept', inviterName: parts[2] }] : { error: 'Accept whose invite?' };
        case 'decline': return parts[2] ? [{ type: 'party_decline', inviterName: parts[2] }] : { error: 'Decline whose invite?' };
        case 'leave': return [{ type: 'party_leave' }];
        case 'kick': return parts[2] ? [{ type: 'party_kick', targetName: parts[2] }] : { error: 'Kick whom?' };
        case 'say': return [{ type: 'party_say', message: parts.slice(2).join(' ') }];
        case 'info': case '': case undefined: return { local: 'party_info' };
        default: return { error: `Unknown party command '${sub}'. Try: invite, accept, decline, leave, kick, say, info` };
      }
    }

    case 'ps': case ';':
      if (!rest) return { error: 'Party say what?' };
      return [{ type: 'party_say', message: rest }];

    case 'follow': {
      if (!arg1) return { error: 'Follow whom?' };
      return [{ type: 'follow', targetName: arg1 }];
    }

    case 'unfollow': case 'stopfollow':
      return [{ type: 'follow_stop' }];

    case 'rally':
      return [{ type: 'rally' }];

    case 'atlas': case 'world':
      return [{ type: 'request_atlas' }];

    case 'help': case '?':
      return { local: 'help' };

    case 'hp': case 'health':
      return { local: 'hp' };

    case 'effects': case 'buffs':
      return { local: 'effects' };

    case 'skills':
      return { local: 'skills' };

    case 'spells':
      return { local: 'spells' };

    case 'quit': case 'exit': case 'q':
      return { local: 'quit' };

    default:
      return { error: `Unknown command: '${cmd}'. Type 'help' for a list of commands.` };
  }
}

// ---------------------------------------------------------------------------
// WebSocket connection
// ---------------------------------------------------------------------------
let ws = null;
let rl = null;
let pingTimer = null;
let commandPollTimer = null;
let serverShuttingDown = false;
let reconnectAttempts = 0;
let isProcessingCommand = false;
let lastCommandHash = '';
let lastCommandTime = 0;

function send(msg) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(msg));
  }
}

function retryAuth() {
  if (stagingMode) {
    send({ type: 'platform_login', characterName: characterOverride || null });
  } else if (guestMode) {
    send({ type: 'guest_login', characterName: guestOpts.charName, characterClass: guestOpts.charClass, race: guestOpts.race, gender: guestOpts.gender });
  } else {
    send({ type: 'login', username, password });
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
      } else if (!INTERACTIVE_MODE) {
        console.log(`[relay] Unhandled message type: ${msg.type}`);
      }
      if (INTERACTIVE_MODE) {
        const lines = renderMessage(msg, state, COLOR_MODE);
        if (lines.length > 0) printOutput(lines);
      }
    } catch (err) {
      console.error('[relay] Failed to parse message:', err.message);
    }
  });

  ws.on('close', (code, reason) => {
    cleanup();
    state.connected = false;
    state.loggedIn = false;
    state.party = null;
    state.following = null;
    state.pendingPartyInvites = [];
    scheduleStateWrite();

    if (serverShuttingDown) {
      console.log(`[relay] Server shut down gracefully (code=${code}). Exiting.`);
      shutdownRelay(0);
      return;
    }

    if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
      console.error(`[relay] Max reconnection attempts (${MAX_RECONNECT_ATTEMPTS}) reached. Exiting.`);
      shutdownRelay(1);
      return;
    }

    const delay = RECONNECT_BASE_MS * Math.pow(2, reconnectAttempts);
    reconnectAttempts++;
    console.log(`[relay] Connection lost (code=${code}). Reconnecting in ${delay}ms (attempt ${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS})...`);
    pushEvent('system', `Connection lost. Reconnecting in ${delay / 1000}s...`);
    setTimeout(() => connect(), delay);
  });

  ws.on('error', (err) => {
    console.error(`[relay] WebSocket error: ${err.message}`);
    // close event will fire after this and trigger shutdownRelay
  });
}

function cleanup() {
  if (pingTimer) { clearInterval(pingTimer); pingTimer = null; }
  if (commandPollTimer) { clearInterval(commandPollTimer); commandPollTimer = null; }
  if (idleTimer) { clearInterval(idleTimer); idleTimer = null; }
}

// ---------------------------------------------------------------------------
// Command file polling
// ---------------------------------------------------------------------------
async function pollCommandFile() {
  if (isProcessingCommand) return;
  if (!state.loggedIn) {
    if (fs.existsSync(COMMAND_FILE)) {
      console.warn('[relay] Ignoring command — not logged in yet');
    }
    return;
  }

  // Atomic claim via rename (POSIX-atomic, prevents multi-fire)
  try {
    fs.renameSync(COMMAND_FILE, PROCESSING_FILE);
  } catch (err) {
    if (err.code === 'ENOENT') return;
    console.error('[relay] Failed to claim command file:', err.message);
    return;
  }

  isProcessingCommand = true;
  let commands;
  try {
    const raw = fs.readFileSync(PROCESSING_FILE, 'utf8');
    fs.unlinkSync(PROCESSING_FILE);

    // Content-hash dedup: skip identical commands within dedup window
    const hash = Buffer.from(raw).toString('base64').slice(0, 64);
    const now = Date.now();
    if (hash === lastCommandHash && now - lastCommandTime < COMMAND_DEDUP_MS) {
      console.log('[relay] Skipping duplicate command');
      return;
    }
    lastCommandHash = hash;
    lastCommandTime = now;

    commands = JSON.parse(raw);
  } catch (err) {
    console.error('[relay] Failed to read command file:', err.message);
    try { fs.unlinkSync(PROCESSING_FILE); } catch {}
    return;
  } finally {
    isProcessingCommand = false;
  }

  if (!Array.isArray(commands)) {
    commands = [commands];
  }

  console.log(`[relay] Processing ${commands.length} command(s)`);
  lastCommandAt = Date.now();
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
      console.log(`[relay] Killing previous relay process (PID ${pid})...`);
      try { process.kill(pid, 'SIGTERM'); } catch {}
      // Brief wait for it to clean up
      const deadline = Date.now() + 3000;
      while (isProcessRunning(pid) && Date.now() < deadline) {
        Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 100);
      }
      if (isProcessRunning(pid)) {
        try { process.kill(pid, 'SIGKILL'); } catch {}
      }
    } else if (!isNaN(pid)) {
      console.log(`[relay] Removing stale lock (PID ${pid})`);
    }
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
if (INTERACTIVE_MODE) console.log('[relay] Interactive mode ON. Type \'help\' for commands.');

acquireLock();

// Clean up stale files
try { fs.unlinkSync(STATE_FILE); } catch {}
try { fs.unlinkSync(COMMAND_FILE); } catch {}
try { fs.unlinkSync(TEMP_STATE_FILE); } catch {}
try { fs.unlinkSync(PROCESSING_FILE); } catch {}

// Write initial state
writeStateFile();
connect();

// Set up readline for interactive mode
if (INTERACTIVE_MODE) {
  rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
    terminal: true,
  });

  rl.setPrompt('(connecting...) ');
  rl.prompt();

  rl.on('line', (input) => {
    lastCommandAt = Date.now();
    const line = input.trim();

    if (!line) {
      if (state.loggedIn) {
        rl.setPrompt(renderPrompt(state, COLOR_MODE));
      }
      rl.prompt();
      return;
    }

    if (!state.loggedIn) {
      process.stdout.write('Not connected yet. Please wait...\n');
      rl.prompt();
      return;
    }

    const result = parseInteractiveCommand(line);

    if (!result) {
      rl.prompt();
      return;
    }

    if (result.error) {
      process.stdout.write(result.error + '\n');
      rl.setPrompt(renderPrompt(state, COLOR_MODE));
      rl.prompt();
      return;
    }

    if (result.local) {
      handleLocalCommand(result.local);
      rl.setPrompt(renderPrompt(state, COLOR_MODE));
      rl.prompt();
      return;
    }

    if (Array.isArray(result)) {
      for (const msg of result) {
        send(msg);
      }
    }

    rl.setPrompt(renderPrompt(state, COLOR_MODE));
    rl.prompt();
  });

  rl.on('close', () => {
    console.log('\n[relay] Input closed. Shutting down...');
    shutdownRelay(0);
  });
}

// Graceful shutdown
function shutdownRelay(exitCode = 0) {
  cleanup();
  if (ws) { try { ws.close(); } catch {} }
  if (rl) { try { rl.close(); } catch {} rl = null; }
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
process.on('uncaughtException', (err) => {
  console.error('[relay] Uncaught exception:', err.message);
  shutdownRelay(1);
});
process.on('unhandledRejection', (err) => {
  console.error('[relay] Unhandled rejection:', err);
  shutdownRelay(1);
});

// Idle timeout — auto-exit if no commands processed for 5 minutes.
// Prevents orphaned relay processes when agents finish without stopping the relay.
const IDLE_TIMEOUT_MS = 5 * 60 * 1000;
let lastCommandAt = Date.now();
let idleTimer = setInterval(() => {
  if (Date.now() - lastCommandAt > IDLE_TIMEOUT_MS) {
    console.log('[relay] No commands for 5 minutes. Shutting down.');
    shutdownRelay(0);
  }
}, 60_000);
