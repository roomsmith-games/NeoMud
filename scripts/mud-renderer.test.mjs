/**
 * mud-renderer.test.mjs — Tests for mud-renderer.mjs
 *
 * Run with: node --test scripts/mud-renderer.test.mjs
 *       or: npm test (in scripts/)
 */
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { renderMessage, renderPrompt, renderHelp, renderPartyState } from './mud-renderer.mjs';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------
function hasAnsi(line) {
  return /\x1b\[/.test(line);
}

function hasReset(line) {
  return /\x1b\[0m/.test(line);
}

function allColoredLinesReset(lines) {
  for (const line of lines) {
    if (hasAnsi(line) && !hasReset(line)) return false;
  }
  return true;
}

const EMPTY_STATE = {
  npcsInRoom: [],
  playersInRoom: [],
  groundItems: [],
  groundCoins: { copper: 0, silver: 0, gold: 0 },
  party: null,
  player: null,
  activeEffects: [],
  pendingPrompt: null,
};

const ROOM = {
  name: 'Town Square',
  description: 'A busy crossroads.',
  exits: { NORTH: 'mill:tavern', SOUTH: 'mill:gate', EAST: 'mill:market' },
  lockedExits: { EAST: 1 },
  interactables: [
    { id: 'f1', label: 'Wishing Well', triggerType: 'ON_ACTION', description: '' },
    { id: 'f2', label: 'Trap Door', triggerType: 'ON_ENTER', description: '' },
  ],
};

const PLAYER = { name: 'Aldric', characterClass: 'Warrior', race: 'Human', level: 5, currentHp: 45, maxHp: 60, currentMp: 10, maxMp: 30, currentXp: 1200, xpToNextLevel: 2000, unspentCp: 3 };

// ---------------------------------------------------------------------------
// No-throw coverage — all 80 message types
// ---------------------------------------------------------------------------
describe('no-throw coverage', () => {
  const ALL_TYPES = [
    'active_effects_update','atlas_data','attack_mode_update','auth_error','buy_result',
    'choice_prompt','class_catalog_sync','combat_hit','connection_rejected','craft_result',
    'crafting_menu','effect_tick','equip_update','error','follow_failed','follow_update',
    'interact_result','inventory_update','item_catalog_sync','item_used','level_up',
    'login_ok','loot_dropped','loot_received','map_data','meditate_update','move_error',
    'move_ok','name_check_result','npc_ability_effect','npc_dialogue','npc_died',
    'npc_entered','npc_left','npc_phase_shift','party_chat','party_disbanded','party_formed',
    'party_info','party_invite_received','party_leader_changed','party_member_joined',
    'party_member_left','party_member_update','pickup_result','place_item_prompt',
    'platform_auth_ok','player_died','player_entered','player_left','player_says','pong',
    'race_catalog_sync','rally_ping','register_ok','rest_update','riddle_prompt','room_info',
    'room_items_update','sell_result','server_hello','server_shutdown','session_conflict',
    'session_displaced','skill_catalog_sync','skill_effect','spell_cast_result',
    'spell_catalog_sync','spell_effect','stat_trained','stealth_update','system_message',
    'tell_received','tell_sent','track_result','trainer_info','tutorial','vendor_info',
    'who_list','xp_gained',
  ];

  const FULL_STUB = {
    player: PLAYER, member: { name: 'X', characterClass: 'Mage', level: 3, currentHp: 20, maxHp: 40, isLeader: false },
    members: [{ name: 'X', characterClass: 'Mage', level: 3, currentHp: 20, maxHp: 40, leaderId: 'X' }],
    effects: [{ name: 'Poison', type: 'DOT', remainingTicks: 3 }],
    items: [{ item: { id: 'item:sword', name: 'Iron Sword' }, price: { gold: 0, silver: 5, copper: 0 }, itemId: 'item:sword' }],
    recipes: [{ recipeId: 'recipe:potion', name: 'Health Potion' }],
    options: [{ id: 'opt1', label: 'Option 1' }],
    results: [{ targetName: 'Aldric', damage: 10, saved: false, newHp: 35, maxHp: 60 }],
    npcs: [{ id: 'n1', name: 'Rat', hostile: true, currentHp: 20, maxHp: 30 }],
    players: [{ name: 'Bob', characterClass: 'Mage', level: 2 }],
    room: ROOM,
    coins: { copper: 5, silver: 2, gold: 0 },
    playerCoins: { copper: 5, silver: 2, gold: 0 },
    updatedCoins: { copper: 0, silver: 1, gold: 0 },
    updatedInventory: [],
    equipment: {},
    inventory: [{ itemId: 'item:sword', quantity: 1, equipped: false }],
    acceptedItems: ['item:key'],
    skillName: 'Bash', spellName: 'Fireball', abilityName: 'Tail Swipe',
    attackerName: 'Rat', defenderName: 'Aldric', damage: 8, defenderHp: 37, defenderMaxHp: 60,
    isPlayerDefender: true, isMiss: false, isDodge: false, isParry: false, isBackstab: false,
    casterName: 'Wizard', targetName: 'Aldric', effectAmount: 15, targetNewHp: 50, targetMaxHp: 60,
    isPlayerTarget: true, targetId: 'n1', targetHp: 20, targetMaxHp: 30,
    userName: 'Aldric', skillId: 'skill:bash', message: 'Test message',
    success: true, spellId: 'spell:fireball', newMp: 10, newHp: 50,
    npcId: 'n1', npcName: 'Tunnel Rat', killerName: 'Aldric', roomId: 'mill:town_square',
    phaseName: 'Enraged', currentHp: 40, maxHp: 80, spriteId: 'npc:rat2',
    playerName: 'Bob', direction: 'NORTH', spawned: false, hostile: true,
    senderName: 'Alice', targetName: 'Aldric', reason: 'left',
    leaderName: 'Alice', roomName: 'Town Square', zoneName: 'Millhaven',
    followerName: 'Bob', state: 'FOLLOWING',
    inviterName: 'Alice', partySize: 2, inviterLevel: 5, inviterClass: 'Warrior',
    partyId: 'p1', leaderId: 'Alice', newLeaderId: 'Alice', newLeaderName: 'Alice',
    memberName: 'Bob',
    crafterName: 'Blacksmith', vendorName: 'Merchant',
    featureId: 'f1', featureName: 'Wishing Well', label: 'Wishing Well',
    question: 'What has keys but no locks?', hint: 'Think musical.', prompt: 'Place an offering.',
    npc_id: 'n1', npc_name: 'Sphinx', content: 'A riddle for you...',
    amount: 100, currentXp: 1300, xpToNextLevel: 2000,
    newLevel: 6, hpRoll: 8, newMaxHp: 68, mpRoll: 4, newMaxMp: 34, cpGained: 2, totalUnspentCp: 5, xpToNextLevel: 2500,
    canLevelUp: false, unspentCp: 3, totalCpEarned: 63, baseStats: { strength: 12, agility: 10, intellect: 8, willpower: 9, health: 11, charm: 7 }, currentStats: { strength: 14, agility: 10, intellect: 8, willpower: 9, health: 11, charm: 7 },
    stat: 'STRENGTH', newValue: 15, cpSpent: 1, remainingCp: 2,
    itemName: 'Iron Sword', quantity: 2, isCoin: false, slot: 'MAIN_HAND', itemId: 'item:sword',
    hidden: true, meditating: false, resting: false,
    secondsRemaining: 30, characterName: 'Aldric',
    engineVersion: '1.0', protocolVersion: 1, worldName: 'TestWorld', worldVersion: '1',
    minClientVersion: '1.0', updateUrl: 'https://example.com',
    needsCharacterCreation: false, platformUserId: 'u1',
    usernameAvailable: true, characterNameAvailable: true,
    enabled: true, effectName: 'Poison', newHp: 40, sound: '', newMp: -1,
    key: 'welcome', title: 'Welcome!', blocking: false,
    classes: [], races: [], skills: [], spells: [], itemsList: [],
    hasPlayers: false, playerRoomId: 'mill:town_square', visitedRooms: [],
    zoneNames: {}, rooms: [],
  };

  for (const type of ALL_TYPES) {
    test(`${type} does not throw`, () => {
      const result = renderMessage({ ...FULL_STUB, type }, EMPTY_STATE, false);
      assert.ok(Array.isArray(result), `renderMessage should return an array for ${type}`);
    });
  }
});

// ---------------------------------------------------------------------------
// Silent messages return []
// ---------------------------------------------------------------------------
describe('silent messages', () => {
  const SILENT = [
    'server_hello', 'platform_auth_ok', 'name_check_result', 'pong',
    'class_catalog_sync', 'item_catalog_sync', 'skill_catalog_sync', 'race_catalog_sync', 'spell_catalog_sync',
    'map_data', 'atlas_data', 'active_effects_update', 'party_member_update', 'tutorial',
  ];
  for (const type of SILENT) {
    test(`${type} returns []`, () => {
      const lines = renderMessage({ type, effects: [], classes: [], items: [], skills: [], spells: [], races: [], rooms: [], playerRoomId: '', visitedRooms: [], zoneNames: {}, memberName: 'X', currentHp: 50, maxHp: 60, currentMp: 10, maxMp: 30, roomId: '' }, EMPTY_STATE, false);
      assert.deepEqual(lines, []);
    });
  }
});

// ---------------------------------------------------------------------------
// Room display
// ---------------------------------------------------------------------------
describe('room display', () => {
  const state = {
    ...EMPTY_STATE,
    npcsInRoom: [
      { id: 'n1', name: 'Tunnel Rat', hostile: true, hp: 20, maxHp: 40 },
      { id: 'n2', name: 'Town Crier', hostile: false, hp: 30, maxHp: 30 },
    ],
    playersInRoom: [{ name: 'Bob', class: 'Mage', level: 3 }],
    groundItems: [{ itemId: 'item:key', name: 'Rusty Key', quantity: 1 }, { itemId: 'item:potion', name: 'Health Potion', quantity: 3 }],
    groundCoins: { copper: 5, silver: 2, gold: 0 },
  };

  test('room_info contains room name', () => {
    const lines = renderMessage({ type: 'room_info', room: ROOM, npcs: [], players: [] }, state, false);
    assert.ok(lines.some(l => l.includes('Town Square')), 'Should include room name');
  });

  test('room_info contains description', () => {
    const lines = renderMessage({ type: 'room_info', room: ROOM, npcs: [], players: [] }, state, false);
    assert.ok(lines.some(l => l.includes('busy crossroads')), 'Should include description');
  });

  test('room_info lists exits', () => {
    const lines = renderMessage({ type: 'room_info', room: ROOM, npcs: [], players: [] }, state, false);
    const exitsLine = lines.find(l => l.includes('[Exits:'));
    assert.ok(exitsLine, 'Should have exits line');
    assert.ok(exitsLine.includes('north'), 'Should show north');
    assert.ok(exitsLine.includes('south'), 'Should show south');
  });

  test('locked exits marked', () => {
    const lines = renderMessage({ type: 'room_info', room: ROOM, npcs: [], players: [] }, state, false);
    const exitsLine = lines.find(l => l.includes('[Exits:'));
    assert.ok(exitsLine.includes('east(locked)'), 'Should mark locked exit');
  });

  test('room_info lists NPCs from state', () => {
    const lines = renderMessage({ type: 'room_info', room: ROOM, npcs: [], players: [] }, state, false);
    const npcLine = lines.find(l => l.includes('NPCs:'));
    assert.ok(npcLine, 'Should have NPCs line');
    assert.ok(npcLine.includes('Tunnel Rat'), 'Should show Tunnel Rat');
    assert.ok(npcLine.includes('Town Crier'), 'Should show Town Crier');
  });

  test('hostile NPCs prefixed with *', () => {
    const lines = renderMessage({ type: 'room_info', room: ROOM, npcs: [], players: [] }, state, false);
    const npcLine = lines.find(l => l.includes('NPCs:'));
    assert.ok(npcLine.includes('*Tunnel Rat'), 'Hostile NPC should be prefixed with *');
    assert.ok(!npcLine.includes('*Town Crier'), 'Friendly NPC should not be prefixed');
  });

  test('room_info shows ground items', () => {
    const lines = renderMessage({ type: 'room_info', room: ROOM, npcs: [], players: [] }, state, false);
    const itemLine = lines.find(l => l.includes('Items:'));
    assert.ok(itemLine, 'Should have items line');
    assert.ok(itemLine.includes('Rusty Key'), 'Should show Rusty Key');
    assert.ok(itemLine.includes('Health Potion x3'), 'Should show stacked potions');
  });

  test('room_info shows coins', () => {
    const lines = renderMessage({ type: 'room_info', room: ROOM, npcs: [], players: [] }, state, false);
    assert.ok(lines.some(l => l.includes('2s') && l.includes('5c')), 'Should show coins on ground');
  });

  test('ON_ACTION interactables shown, ON_ENTER hidden', () => {
    const lines = renderMessage({ type: 'room_info', room: ROOM, npcs: [], players: [] }, state, false);
    const featLine = lines.find(l => l.includes('Features:'));
    assert.ok(featLine, 'Should have features line');
    assert.ok(featLine.includes('Wishing Well'), 'ON_ACTION feature should be shown');
    assert.ok(!featLine.includes('Trap Door'), 'ON_ENTER trap should be hidden');
  });

  test('move_ok shows direction and room', () => {
    const lines = renderMessage({ type: 'move_ok', direction: 'NORTH', room: ROOM, npcs: [], players: [] }, state, false);
    assert.ok(lines.some(l => l.includes('head north')), 'Should show direction');
    assert.ok(lines.some(l => l.includes('Town Square')), 'Should show room name');
  });

  test('room with no exits', () => {
    const emptyRoom = { ...ROOM, exits: {} };
    const lines = renderMessage({ type: 'room_info', room: emptyRoom, npcs: [], players: [] }, EMPTY_STATE, false);
    assert.ok(lines.some(l => l.includes('[Exits: none]')), 'Should show no exits');
  });
});

// ---------------------------------------------------------------------------
// Combat
// ---------------------------------------------------------------------------
describe('combat', () => {
  test('normal hit shows attacker, defender, damage', () => {
    const lines = renderMessage({ type: 'combat_hit', attackerName: 'Rat', defenderName: 'Aldric', damage: 12, defenderHp: 33, defenderMaxHp: 60, isPlayerDefender: true, isMiss: false, isDodge: false, isParry: false, isBackstab: false, defenderId: 'n1' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('Rat hits Aldric for 12'), 'Hit line should show damage');
    assert.ok(lines[1].includes('33/60'), 'Should show HP');
  });

  test('miss line', () => {
    const lines = renderMessage({ type: 'combat_hit', attackerName: 'Rat', defenderName: 'Aldric', damage: 0, defenderHp: 45, defenderMaxHp: 60, isMiss: true, isDodge: false, isParry: false, isBackstab: false, isPlayerDefender: true, defenderId: '' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('misses'), 'Should say misses');
  });

  test('dodge line', () => {
    const lines = renderMessage({ type: 'combat_hit', attackerName: 'Rat', defenderName: 'Aldric', damage: 0, defenderHp: 45, defenderMaxHp: 60, isMiss: false, isDodge: true, isParry: false, isBackstab: false, isPlayerDefender: true, defenderId: '' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('dodges'), 'Should say dodges');
  });

  test('parry line', () => {
    const lines = renderMessage({ type: 'combat_hit', attackerName: 'Rat', defenderName: 'Aldric', damage: 0, defenderHp: 45, defenderMaxHp: 60, isMiss: false, isDodge: false, isParry: true, isBackstab: false, isPlayerDefender: true, defenderId: '' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('parries'), 'Should say parries');
  });

  test('backstab line', () => {
    const lines = renderMessage({ type: 'combat_hit', attackerName: 'Aldric', defenderName: 'Rat', damage: 35, defenderHp: 5, defenderMaxHp: 40, isMiss: false, isDodge: false, isParry: false, isBackstab: true, isPlayerDefender: false, defenderId: 'n1' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('BACKSTABS'), 'Should say BACKSTABS');
  });

  test('npc_died shows killer and victim', () => {
    const lines = renderMessage({ type: 'npc_died', npcId: 'n1', npcName: 'Tunnel Rat', killerName: 'Aldric', roomId: 'mill:sq' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('Aldric') && lines[0].includes('Tunnel Rat'), 'Should name killer and victim');
  });

  test('player_died shows killer', () => {
    const lines = renderMessage({ type: 'player_died', killerName: 'Tunnel Rat', respawnRoomId: 'mill:sq', respawnHp: 30, respawnMp: 10 }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('Tunnel Rat'), 'Should show killer');
    assert.ok(lines[0].includes('slain'), 'Should say slain');
  });

  test('attack_mode_update ON', () => {
    const lines = renderMessage({ type: 'attack_mode_update', enabled: true }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('ATTACK MODE ON'));
  });

  test('attack_mode_update OFF', () => {
    const lines = renderMessage({ type: 'attack_mode_update', enabled: false }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('ATTACK MODE OFF'));
  });

  test('npc_phase_shift shows phase name', () => {
    const lines = renderMessage({ type: 'npc_phase_shift', npcId: 'n1', npcName: 'Boss', phaseName: 'Enraged', message: 'The boss glows red!', currentHp: 50, maxHp: 100, spriteId: '', sound: '' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('ENRAGED'), 'Should show phase name');
    assert.ok(lines.some(l => l.includes('50/100')), 'Should show HP');
  });
});

// ---------------------------------------------------------------------------
// HP bar thresholds
// ---------------------------------------------------------------------------
describe('hp bar thresholds', () => {
  function getHpBarColor(current, max) {
    const lines = renderMessage({ type: 'combat_hit', attackerName: 'A', defenderName: 'B', damage: 1, defenderHp: current, defenderMaxHp: max, isMiss: false, isDodge: false, isParry: false, isBackstab: false, isPlayerDefender: false, defenderId: '' }, EMPTY_STATE, true);
    const hpLine = lines[1]; // second line has HP bar
    if (hpLine.includes('\x1b[32m')) return 'green';
    if (hpLine.includes('\x1b[33m')) return 'yellow';
    if (hpLine.includes('\x1b[31m')) return 'red';
    return 'none';
  }

  test('>50% HP is green', () => assert.equal(getHpBarColor(51, 100), 'green'));
  test('50% HP is yellow', () => assert.equal(getHpBarColor(50, 100), 'yellow'));
  test('25-50% HP is yellow', () => assert.equal(getHpBarColor(30, 100), 'yellow'));
  test('25% HP is red', () => assert.equal(getHpBarColor(25, 100), 'red'));
  test('<25% HP is red', () => assert.equal(getHpBarColor(10, 100), 'red'));
  test('0 HP is red', () => assert.equal(getHpBarColor(0, 100), 'red'));
});

// ---------------------------------------------------------------------------
// ANSI color compliance — every colored line must end with RESET
// ---------------------------------------------------------------------------
describe('ANSI color compliance', () => {
  const COLORED_TYPES = [
    { type: 'login_ok', msg: { player: PLAYER } },
    { type: 'auth_error', msg: { reason: 'Bad password' } },
    { type: 'system_message', msg: { message: 'Hello' } },
    { type: 'error', msg: { message: 'Oops' } },
    { type: 'server_shutdown', msg: { message: 'Restarting', secondsRemaining: 30 } },
    { type: 'player_says', msg: { playerName: 'Bob', message: 'Hi' } },
    { type: 'tell_received', msg: { senderName: 'Bob', message: 'Hello' } },
    { type: 'npc_died', msg: { npcId: 'n1', npcName: 'Rat', killerName: 'Aldric', roomId: 'r1' } },
    { type: 'player_died', msg: { killerName: 'Rat', respawnRoomId: 'r1', respawnHp: 30, respawnMp: 0 } },
    { type: 'xp_gained', msg: { amount: 100, currentXp: 200, xpToNextLevel: 1000 } },
    { type: 'level_up', msg: { newLevel: 6, hpRoll: 8, newMaxHp: 68, mpRoll: 4, newMaxMp: 34, cpGained: 2, totalUnspentCp: 5, xpToNextLevel: 2500 } },
    { type: 'loot_received', msg: { npcName: 'Rat', items: [{ itemName: 'Pelt', quantity: 1 }] } },
    { type: 'party_chat', msg: { senderName: 'Alice', message: 'Ready?' } },
    { type: 'follow_update', msg: { followerName: 'Bob', targetName: 'Alice', state: 'FOLLOWING' } },
    { type: 'session_displaced', msg: { reason: 'Kicked' } },
    { type: 'connection_rejected', msg: { reason: 'Version mismatch' } },
  ];

  for (const { type, msg } of COLORED_TYPES) {
    test(`${type} — all colored lines end with RESET`, () => {
      const lines = renderMessage({ type, ...msg }, EMPTY_STATE, true);
      assert.ok(lines.length > 0, `${type} should produce output`);
      assert.ok(allColoredLinesReset(lines), `All colored lines in ${type} should end with RESET (no color bleed)`);
    });
  }

  test('no color codes when useColor=false', () => {
    const lines = renderMessage({ type: 'login_ok', player: PLAYER }, EMPTY_STATE, false);
    assert.ok(lines.every(l => !hasAnsi(l)), 'No ANSI codes in plain mode');
  });
});

// ---------------------------------------------------------------------------
// Inventory
// ---------------------------------------------------------------------------
describe('inventory', () => {
  test('empty inventory', () => {
    const lines = renderMessage({ type: 'inventory_update', inventory: [], equipment: {}, coins: {} }, EMPTY_STATE, false);
    assert.ok(lines.some(l => l.includes('empty')), 'Should say empty');
  });

  test('shows item names', () => {
    const lines = renderMessage({ type: 'inventory_update', inventory: [{ itemId: 'item:sword', quantity: 1, equipped: false, slot: '' }], equipment: {}, coins: { gold: 1, silver: 2, copper: 0 } }, EMPTY_STATE, false);
    assert.ok(lines.some(l => l.includes('item:sword')), 'Should show item');
    assert.ok(lines.some(l => l.includes('1g') && l.includes('2s')), 'Should show coins');
  });

  test('stacked items show quantity', () => {
    const lines = renderMessage({ type: 'inventory_update', inventory: [{ itemId: 'item:arrow', quantity: 20, equipped: false, slot: '' }], equipment: {}, coins: {} }, EMPTY_STATE, false);
    assert.ok(lines.some(l => l.includes('x20')), 'Should show quantity');
  });

  test('equipped items in separate section', () => {
    const lines = renderMessage({ type: 'inventory_update', inventory: [{ itemId: 'item:sword', quantity: 1, equipped: true, slot: 'MAIN_HAND' }], equipment: { MAIN_HAND: 'item:sword' }, coins: {} }, EMPTY_STATE, false);
    assert.ok(lines.some(l => l.includes('Equipped')), 'Should have Equipped section');
    assert.ok(lines.some(l => l.includes('MAIN_HAND')), 'Should show slot');
  });

  test('pickup_result singular', () => {
    const lines = renderMessage({ type: 'pickup_result', itemName: 'Iron Key', quantity: 1, isCoin: false }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('Iron Key'), 'Should name the item');
    assert.ok(!lines[0].includes('x1'), 'Should not show x1 for singular');
  });

  test('pickup_result plural shows quantity', () => {
    const lines = renderMessage({ type: 'pickup_result', itemName: 'Arrow', quantity: 10, isCoin: false }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('x10'), 'Should show quantity for stacks');
  });

  test('loot_dropped stacks', () => {
    const lines = renderMessage({ type: 'loot_dropped', npcName: 'Rat', items: [{ itemName: 'Pelt', quantity: 2 }], coins: { gold: 0, silver: 1, copper: 0 } }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('x2'), 'Should show stack qty');
    assert.ok(lines[0].includes('1s'), 'Should show coins');
  });
});

// ---------------------------------------------------------------------------
// Progression
// ---------------------------------------------------------------------------
describe('progression', () => {
  test('xp_gained positive', () => {
    const lines = renderMessage({ type: 'xp_gained', amount: 150, currentXp: 1350, xpToNextLevel: 2000 }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('150 XP'), 'Should show XP amount');
    assert.ok(lines[0].includes('1350/2000'), 'Should show progress');
  });

  test('xp_gained negative (penalty)', () => {
    const lines = renderMessage({ type: 'xp_gained', amount: -50, currentXp: 1300, xpToNextLevel: 2000 }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('lose'), 'Should say lose for negative XP');
    assert.ok(lines[0].includes('50'), 'Should show penalty amount');
  });

  test('level_up shows new level and bonuses', () => {
    const lines = renderMessage({ type: 'level_up', newLevel: 6, hpRoll: 8, newMaxHp: 68, mpRoll: 4, newMaxMp: 34, cpGained: 2, totalUnspentCp: 5, xpToNextLevel: 2500 }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('LEVEL UP'), 'Should say LEVEL UP');
    assert.ok(lines[0].includes('level 6'), 'Should show new level');
    assert.ok(lines[1].includes('+8'), 'Should show HP roll');
    assert.ok(lines[1].includes('+4'), 'Should show MP roll');
  });
});

// ---------------------------------------------------------------------------
// Chat / communication
// ---------------------------------------------------------------------------
describe('communication', () => {
  test('player_says', () => {
    const lines = renderMessage({ type: 'player_says', playerName: 'Alice', message: 'Hello there!' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('Alice') && lines[0].includes('Hello there!'));
  });

  test('tell_received', () => {
    const lines = renderMessage({ type: 'tell_received', senderName: 'Alice', message: 'Secret!' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('Alice') && lines[0].includes('Secret!'));
  });

  test('tell_sent', () => {
    const lines = renderMessage({ type: 'tell_sent', targetName: 'Bob', message: 'Hey!' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('Bob') && lines[0].includes('Hey!'));
  });

  test('who_list empty', () => {
    const lines = renderMessage({ type: 'who_list', players: [] }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('No players'), 'Empty who list');
  });

  test('who_list shows players', () => {
    const lines = renderMessage({ type: 'who_list', players: [{ name: 'Aldric', characterClass: 'Warrior', level: 5, zone: 'Millhaven' }] }, EMPTY_STATE, false);
    assert.ok(lines.some(l => l.includes('Aldric') && l.includes('Warrior') && l.includes('Millhaven')));
  });
});

// ---------------------------------------------------------------------------
// Interactive prompts
// ---------------------------------------------------------------------------
describe('interactive prompts', () => {
  test('riddle_prompt shows question and instructions', () => {
    const lines = renderMessage({ type: 'riddle_prompt', featureId: 'f1', label: 'Sphinx', question: 'What walks on four legs?', hint: 'Think alive.' }, EMPTY_STATE, false);
    assert.ok(lines.some(l => l.includes('What walks on four legs?')));
    assert.ok(lines.some(l => l.includes('Think alive.')));
    assert.ok(lines.some(l => l.includes("'answer")));
  });

  test('riddle_prompt without hint omits hint line', () => {
    const lines = renderMessage({ type: 'riddle_prompt', featureId: 'f1', label: 'Sphinx', question: 'Guess?', hint: null }, EMPTY_STATE, false);
    assert.ok(!lines.some(l => l.includes('Hint:')), 'Should not show hint line when null');
  });

  test('choice_prompt numbered options', () => {
    const lines = renderMessage({ type: 'choice_prompt', featureId: 'f1', label: 'Guard', question: 'What do you want?', options: [{ id: 'opt1', label: 'Fight' }, { id: 'opt2', label: 'Flee' }] }, EMPTY_STATE, false);
    assert.ok(lines.some(l => l.includes('1.') && l.includes('Fight')));
    assert.ok(lines.some(l => l.includes('2.') && l.includes('Flee')));
    assert.ok(lines.some(l => l.includes("'choose")));
  });

  test('place_item_prompt shows accepted items', () => {
    const lines = renderMessage({ type: 'place_item_prompt', featureId: 'f1', label: 'Altar', prompt: 'Place an offering.', acceptedItems: ['item:gem', 'item:key'] }, EMPTY_STATE, false);
    assert.ok(lines.some(l => l.includes('gem') && l.includes('key')));
    assert.ok(lines.some(l => l.includes("'place")));
  });
});

// ---------------------------------------------------------------------------
// Party / Follow
// ---------------------------------------------------------------------------
describe('party and follow', () => {
  test('party_invite_received includes instructions', () => {
    const lines = renderMessage({ type: 'party_invite_received', inviterName: 'Alice', partySize: 2, inviterLevel: 5, inviterClass: 'Warrior' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('Alice'));
    assert.ok(lines[0].includes('party accept'));
  });

  test('party_chat shows [Party] prefix', () => {
    const lines = renderMessage({ type: 'party_chat', senderName: 'Alice', message: 'Ready?' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('[Party]'));
  });

  test('follow_update following', () => {
    const lines = renderMessage({ type: 'follow_update', followerName: 'Bob', targetName: 'Alice', state: 'FOLLOWING' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('following'));
  });

  test('follow_update OFF', () => {
    const lines = renderMessage({ type: 'follow_update', followerName: 'Bob', targetName: 'Alice', state: 'OFF' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('stops following'));
  });

  test('rally_ping shows leader and destination', () => {
    const lines = renderMessage({ type: 'rally_ping', leaderName: 'Alice', roomId: 'r1', roomName: 'Town Square', zoneName: 'Millhaven' }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('Alice') && lines[0].includes('Town Square'));
  });
});

// ---------------------------------------------------------------------------
// renderPrompt
// ---------------------------------------------------------------------------
describe('renderPrompt', () => {
  test('no player returns > ', () => {
    assert.equal(renderPrompt(EMPTY_STATE, false), '> ');
  });

  test('shows HP and MP', () => {
    const state = { ...EMPTY_STATE, player: { hp: 45, maxHp: 60, mp: 10, maxMp: 30 } };
    const p = renderPrompt(state, false);
    assert.ok(p.includes('HP:45/60'));
    assert.ok(p.includes('MP:10/30'));
  });

  test('omits MP section when maxMp=0', () => {
    const state = { ...EMPTY_STATE, player: { hp: 30, maxHp: 60, mp: 0, maxMp: 0 } };
    const p = renderPrompt(state, false);
    assert.ok(!p.includes('MP:'), 'Should not show MP for non-caster');
  });

  test('[Attack] flag shown', () => {
    const state = { ...EMPTY_STATE, player: { hp: 30, maxHp: 60, mp: 0, maxMp: 0 }, attackMode: true };
    assert.ok(renderPrompt(state, false).includes('[Attack]'));
  });

  test('[Hidden] flag shown', () => {
    const state = { ...EMPTY_STATE, player: { hp: 30, maxHp: 60, mp: 0, maxMp: 0 }, isHidden: true };
    assert.ok(renderPrompt(state, false).includes('[Hidden]'));
  });

  test('[Med] flag shown', () => {
    const state = { ...EMPTY_STATE, player: { hp: 30, maxHp: 60, mp: 20, maxMp: 30 }, isMeditating: true };
    assert.ok(renderPrompt(state, false).includes('[Med]'));
  });

  test('[Rest] flag shown', () => {
    const state = { ...EMPTY_STATE, player: { hp: 30, maxHp: 60, mp: 20, maxMp: 30 }, isResting: true };
    assert.ok(renderPrompt(state, false).includes('[Rest]'));
  });

  test('color mode — prompt contains ANSI', () => {
    const state = { ...EMPTY_STATE, player: { hp: 45, maxHp: 60, mp: 10, maxMp: 30 } };
    assert.ok(hasAnsi(renderPrompt(state, true)));
  });

  test('prompt HP color green when >50%', () => {
    const state = { ...EMPTY_STATE, player: { hp: 55, maxHp: 60, mp: 0, maxMp: 0 } };
    assert.ok(renderPrompt(state, true).includes('\x1b[32m'), '>50% HP should be green');
  });

  test('prompt HP color yellow when 25-50%', () => {
    const state = { ...EMPTY_STATE, player: { hp: 20, maxHp: 60, mp: 0, maxMp: 0 } };
    assert.ok(renderPrompt(state, true).includes('\x1b[33m'), '25-50% HP should be yellow');
  });

  test('prompt HP color red when <25%', () => {
    const state = { ...EMPTY_STATE, player: { hp: 5, maxHp: 60, mp: 0, maxMp: 0 } };
    assert.ok(renderPrompt(state, true).includes('\x1b[31m'), '<25% HP should be red');
  });
});

// ---------------------------------------------------------------------------
// renderHelp
// ---------------------------------------------------------------------------
describe('renderHelp', () => {
  test('returns non-empty array', () => {
    assert.ok(renderHelp(false).length > 5);
  });

  test('covers key commands', () => {
    const text = renderHelp(false).join('\n');
    for (const cmd of ['Movement', 'attack', 'inv', 'party', 'quit']) {
      assert.ok(text.includes(cmd), `Help should mention ${cmd}`);
    }
  });

  test('color mode — contains ANSI', () => {
    assert.ok(renderHelp(true).some(l => hasAnsi(l)));
  });
});

// ---------------------------------------------------------------------------
// renderPartyState
// ---------------------------------------------------------------------------
describe('renderPartyState', () => {
  test('null party returns not-in-party message', () => {
    const lines = renderPartyState(null, false);
    assert.ok(lines[0].includes('not in a party'));
  });

  test('shows member names', () => {
    const party = { members: [{ name: 'Alice', isLeader: true, hp: 50, maxHp: 60 }, { name: 'Bob', isLeader: false, hp: 30, maxHp: 40 }] };
    const lines = renderPartyState(party, false);
    assert.ok(lines.some(l => l.includes('Alice')));
    assert.ok(lines.some(l => l.includes('Bob')));
  });

  test('marks leader', () => {
    const party = { members: [{ name: 'Alice', isLeader: true, hp: 50, maxHp: 60 }] };
    const lines = renderPartyState(party, false);
    assert.ok(lines.some(l => l.includes('leader')));
  });
});

// ---------------------------------------------------------------------------
// Vendor / crafting
// ---------------------------------------------------------------------------
describe('vendor and crafting', () => {
  test('vendor_info shows item list with prices', () => {
    const lines = renderMessage({ type: 'vendor_info', vendorName: 'Smith', items: [{ item: { name: 'Iron Sword', id: 'item:sword' }, price: { gold: 0, silver: 5, copper: 0 } }, { item: { name: 'Shield', id: 'item:shield' }, price: { gold: 1, silver: 0, copper: 0 } }], playerCoins: { gold: 2, silver: 0, copper: 0 }, playerInventory: [], playerCharm: 0 }, EMPTY_STATE, false);
    assert.ok(lines.some(l => l.includes('Iron Sword') && l.includes('5s')));
    assert.ok(lines.some(l => l.includes('Shield') && l.includes('1g')));
    assert.ok(lines.some(l => l.includes('2g')), 'Should show player coins');
  });

  test('buy_result success vs failure color', () => {
    const success = renderMessage({ type: 'buy_result', success: true, message: 'Bought!', updatedCoins: {}, updatedInventory: [], equipment: {} }, EMPTY_STATE, true);
    const failure = renderMessage({ type: 'buy_result', success: false, message: 'Not enough gold.', updatedCoins: {}, updatedInventory: [], equipment: {} }, EMPTY_STATE, true);
    assert.ok(success[0].includes('\x1b[32m'), 'Success should be green');
    assert.ok(failure[0].includes('\x1b[31m'), 'Failure should be red');
  });

  test('crafting_menu shows recipe list', () => {
    const lines = renderMessage({ type: 'crafting_menu', crafterName: 'Forge', recipes: [{ recipeId: 'r1', name: 'Health Potion' }, { recipeId: 'r2', name: 'Steel Sword' }], playerCoins: { gold: 0, silver: 3, copper: 0 }, interactSound: '', exitSound: '' }, EMPTY_STATE, false);
    assert.ok(lines.some(l => l.includes('Health Potion')));
    assert.ok(lines.some(l => l.includes('Steel Sword')));
  });
});

// ---------------------------------------------------------------------------
// Edge cases
// ---------------------------------------------------------------------------
describe('edge cases', () => {
  test('room with no NPCs, players, items, or coins', () => {
    const emptyRoom = { name: 'Empty Cell', description: 'Nothing here.', exits: {}, lockedExits: {}, interactables: [] };
    const lines = renderMessage({ type: 'room_info', room: emptyRoom, npcs: [], players: [] }, EMPTY_STATE, false);
    assert.ok(!lines.some(l => l.includes('NPCs:')), 'Should not show NPCs line when empty');
    assert.ok(!lines.some(l => l.includes('Players:')), 'Should not show Players line when empty');
    assert.ok(!lines.some(l => l.includes('Items:')), 'Should not show Items line when empty');
  });

  test('0 HP combat bar fully empty', () => {
    const lines = renderMessage({ type: 'combat_hit', attackerName: 'A', defenderName: 'B', damage: 60, defenderHp: 0, defenderMaxHp: 60, isMiss: false, isDodge: false, isParry: false, isBackstab: false, isPlayerDefender: false, defenderId: '' }, EMPTY_STATE, false);
    assert.ok(lines[1].includes('0/60'));
    assert.ok(lines[1].includes('░░░░░░░░'), 'Empty HP bar should be all empties');
  });

  test('loot_dropped with no items or coins', () => {
    const lines = renderMessage({ type: 'loot_dropped', npcName: 'Ghost', items: [], coins: { copper: 0, silver: 0, gold: 0 } }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('nothing'), 'Should say nothing when no loot');
  });

  test('xp_gained with 0 amount', () => {
    const lines = renderMessage({ type: 'xp_gained', amount: 0, currentXp: 500, xpToNextLevel: 1000 }, EMPTY_STATE, false);
    assert.ok(lines[0].includes('0 XP'));
  });

  test('room_items_update empty — returns []', () => {
    const lines = renderMessage({ type: 'room_items_update', items: [], coins: { copper: 0, silver: 0, gold: 0 } }, { ...EMPTY_STATE, groundItems: [], groundCoins: { copper: 0, silver: 0, gold: 0 } }, false);
    assert.deepEqual(lines, []);
  });

  test('room_items_update with items prints ground line', () => {
    const state = { ...EMPTY_STATE, groundItems: [{ itemId: 'item:key', name: 'Rusty Key', quantity: 1 }], groundCoins: { copper: 0, silver: 0, gold: 0 } };
    const lines = renderMessage({ type: 'room_items_update', items: [{ itemId: 'item:key', quantity: 1 }], coins: {} }, state, false);
    assert.ok(lines.some(l => l.includes('Rusty Key')), 'Should show item name from state');
  });
});
