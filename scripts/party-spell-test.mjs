#!/usr/bin/env node
/**
 * Multi-player party spell test script.
 * Connects 3 characters simultaneously to test PARTY_ROOM spells.
 */
import WebSocket from 'ws';

const SERVER = 'ws://localhost:8080/game';
const DELAY = 800; // ms between commands

// Character configs
const CHARACTERS = [
  { username: 'priest1', password: 'testpass123', name: 'Priest', class: 'PRIEST', race: 'HUMAN', register: false },
  { username: 'bard1', password: 'testpass123', name: 'Bard', class: 'BARD', race: 'ELF', register: true },
  { username: 'warrior1', password: 'testpass123', name: 'Warrior', class: 'WARRIOR', race: 'HUMAN', register: true },
];

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

class GameClient {
  constructor(config) {
    this.config = config;
    this.ws = null;
    this.loggedIn = false;
    this.player = null;
    this.room = null;
    this.events = [];
    this.spellEffects = [];
    this.activeEffects = [];
    this.partyState = null;
    this.errors = [];
    this.ready = false;
    this.loginResolve = null;
  }

  connect() {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(SERVER);
      this.loginResolve = resolve;

      this.ws.on('open', () => {
        console.log(`[${this.config.name}] Connected`);
      });

      this.ws.on('message', (data) => {
        try {
          const msg = JSON.parse(data.toString());
          this.handleMessage(msg);
        } catch (e) {
          console.error(`[${this.config.name}] Parse error:`, e.message);
        }
      });

      this.ws.on('error', (err) => {
        console.error(`[${this.config.name}] WS error:`, err.message);
        reject(err);
      });

      this.ws.on('close', () => {
        console.log(`[${this.config.name}] Disconnected`);
      });

      // Timeout for login
      setTimeout(() => {
        if (!this.loggedIn) {
          reject(new Error(`${this.config.name} login timeout`));
        }
      }, 10000);
    });
  }

  handleMessage(msg) {
    const type = msg.type;

    // Debug: log ALL message types during early connection
    if (!this.ready) {
      console.log(`  [${this.config.name}] <-- ${type}`);
    }

    switch (type) {
      case 'server_hello':
      case 'server_info':
        // Send login or register after receiving all catalog syncs
        // Wait for spell_catalog_sync before sending login
        break;

      case 'spell_catalog_sync':
      case 'spell_catalog':
        // All catalogs received, now login
        if (!this.loggedIn && !this._loginSent) {
          this._loginSent = true;
          if (this.config.register) {
            console.log(`  [${this.config.name}] --> register (${this.config.username})`);
            this.send({
              type: 'register',
              username: this.config.username,
              password: this.config.password,
              characterName: this.config.name,
              characterClass: this.config.class,
              race: this.config.race,
              gender: 'male'
            });
          } else {
            console.log(`  [${this.config.name}] --> login (${this.config.username})`);
            this.send({ type: 'login', username: this.config.username, password: this.config.password });
          }
        }
        break;

      case 'login_ok':
        this.loggedIn = true;
        this.player = msg;
        console.log(`[${this.config.name}] Logged in as Lv${msg.level} ${msg.race} ${msg.characterClass}`);
        break;

      case 'room_info':
        this.room = msg;
        if (!this.ready) {
          this.ready = true;
          if (this.loginResolve) this.loginResolve();
        }
        break;

      case 'auth_error':
        const errMsg = msg.message || msg.error || msg.reason || JSON.stringify(msg);
        console.error(`[${this.config.name}] Auth error: ${errMsg}`);
        // If registration fails, try login instead
        if (this.config.register) {
          this.config.register = false;
          console.log(`  [${this.config.name}] --> login (fallback after register fail)`);
          this.send({ type: 'login', username: this.config.username, password: this.config.password });
        }
        break;

      case 'spell_effect':
        this.spellEffects.push(msg);
        console.log(`[${this.config.name}] SpellEffect: ${msg.spellName} from ${msg.casterName} -> ${msg.targetName || 'self'} | power=${msg.power} type=${msg.effectType}`);
        break;

      case 'active_effects_update':
        this.activeEffects = msg.effects || [];
        console.log(`[${this.config.name}] ActiveEffects: ${JSON.stringify(this.activeEffects.map(e => e.name || e.effectType))}`);
        break;

      case 'player_stats_update':
        if (msg.hp !== undefined) {
          const oldHp = this.player?.hp;
          if (this.player) {
            this.player.hp = msg.hp;
            this.player.maxHp = msg.maxHp || this.player.maxHp;
            this.player.mp = msg.mp;
            this.player.maxMp = msg.maxMp || this.player.maxMp;
          }
          if (oldHp !== msg.hp) {
            console.log(`[${this.config.name}] HP: ${oldHp} -> ${msg.hp}/${msg.maxHp || '?'} | MP: ${msg.mp}/${msg.maxMp || '?'}`);
          }
        }
        break;

      case 'combat_hit':
        console.log(`[${this.config.name}] CombatHit: ${msg.attackerName} -> ${msg.targetName} for ${msg.damage} (${msg.result})`);
        break;

      case 'party_update':
        this.partyState = msg;
        console.log(`[${this.config.name}] PartyUpdate: ${JSON.stringify(msg.members?.map(m => m.name) || [])}`);
        break;

      case 'party_invite':
        console.log(`[${this.config.name}] Party invite from ${msg.inviterName}`);
        break;

      case 'system_message':
        console.log(`[${this.config.name}] System: ${msg.message}`);
        this.events.push({ type: 'system', message: msg.message });
        break;

      case 'error':
        console.log(`[${this.config.name}] ERROR: ${msg.message}`);
        this.errors.push(msg.message);
        break;

      case 'chat_message':
        if (msg.channel === 'party') {
          console.log(`[${this.config.name}] [PARTY] ${msg.senderName}: ${msg.message}`);
        }
        break;

      case 'npc_entered':
      case 'npc_left':
      case 'player_entered':
      case 'player_left':
        console.log(`[${this.config.name}] ${type}: ${msg.name || msg.npcName || msg.playerName}`);
        break;

      // Ignore catalog syncs, map data, etc.
      case 'class_catalog':
      case 'item_catalog':
      case 'skill_catalog':
      case 'race_catalog':
      case 'spell_catalog':
      case 'map_data':
      case 'inventory_update':
      case 'room_items_update':
      case 'tutorial':
      case 'discovery_update':
        break;

      default:
        // Log unknown message types
        if (!['player_stats_update'].includes(type)) {
          // console.log(`[${this.config.name}] Unhandled: ${type}`);
        }
    }
  }

  send(msg) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  disconnect() {
    this.ws?.close();
  }
}

async function main() {
  console.log('=== PARTY_ROOM Spell Test ===\n');

  // Connect all characters
  const clients = CHARACTERS.map(c => new GameClient(c));

  console.log('--- Phase 1: Connect all characters ---');
  try {
    await Promise.all(clients.map(c => c.connect()));
  } catch (e) {
    console.error('Connection failed:', e.message);
    clients.forEach(c => c.disconnect());
    process.exit(1);
  }

  const [priest, bard, warrior] = clients;
  await sleep(1000);

  // First, use admin (bob) to set levels so spells are available
  console.log('\n--- Phase 2: Level up characters via admin ---');
  // Connect admin
  const admin = new GameClient({ username: 'bob', password: 'testpass123', name: 'Bob', register: false });
  try {
    await admin.connect();
    await sleep(500);

    // Set levels for all characters
    admin.send({ type: 'admin_command', command: 'setlevel', args: ['Priest', '5'] });
    await sleep(500);
    admin.send({ type: 'admin_command', command: 'setlevel', args: ['Bard', '5'] });
    await sleep(500);
    admin.send({ type: 'admin_command', command: 'setlevel', args: ['Warrior', '5'] });
    await sleep(500);

    // Also boost stats for testing
    admin.send({ type: 'admin_command', command: 'setstat', args: ['Priest', 'willpower', '50'] });
    await sleep(300);
    admin.send({ type: 'admin_command', command: 'setstat', args: ['Bard', 'willpower', '50'] });
    await sleep(300);
    admin.send({ type: 'admin_command', command: 'setstat', args: ['Bard', 'charm', '50'] });
    await sleep(300);

    admin.disconnect();
  } catch (e) {
    console.error('Admin connection failed:', e.message);
    console.log('Continuing without level-up...');
  }

  await sleep(1000);

  // Move everyone to the same room (Town Square)
  console.log('\n--- Phase 3: Move all to Town Square ---');
  priest.send({ type: 'move', direction: 'NORTH' }); // Temple -> Town Square
  await sleep(DELAY);
  bard.send({ type: 'move', direction: 'NORTH' }); // Temple -> Town Square (if starting at temple)
  await sleep(DELAY);
  warrior.send({ type: 'move', direction: 'NORTH' }); // Temple -> Town Square
  await sleep(2000);

  console.log(`\nPriest room: ${priest.room?.name} (${priest.room?.roomId})`);
  console.log(`Bard room: ${bard.room?.name} (${bard.room?.roomId})`);
  console.log(`Warrior room: ${warrior.room?.name} (${warrior.room?.roomId})`);

  // Form party: Priest invites others
  console.log('\n--- Phase 4: Form party ---');
  priest.send({ type: 'party_invite', targetName: 'Warrior' });
  await sleep(DELAY);
  priest.send({ type: 'party_invite', targetName: 'Bard' });
  await sleep(DELAY);

  // Accept invites
  warrior.send({ type: 'party_accept' });
  await sleep(DELAY);
  bard.send({ type: 'party_accept' });
  await sleep(2000);

  console.log(`\nPriest party: ${JSON.stringify(priest.partyState?.members?.map(m => m.name))}`);
  console.log(`Warrior party: ${JSON.stringify(warrior.partyState?.members?.map(m => m.name))}`);
  console.log(`Bard party: ${JSON.stringify(bard.partyState?.members?.map(m => m.name))}`);

  // Deal some damage to test healing
  console.log('\n--- Phase 5: Take some damage (move to forest for combat) ---');
  // Move party to forest edge for combat
  priest.send({ type: 'move', direction: 'EAST' }); // Square -> Market
  await sleep(DELAY);
  bard.send({ type: 'move', direction: 'EAST' });
  await sleep(DELAY);
  warrior.send({ type: 'move', direction: 'EAST' });
  await sleep(DELAY);
  // Market -> East (forest edge?)
  priest.send({ type: 'move', direction: 'EAST' });
  await sleep(DELAY);
  bard.send({ type: 'move', direction: 'EAST' });
  await sleep(DELAY);
  warrior.send({ type: 'move', direction: 'EAST' });
  await sleep(3000);

  console.log(`\nAfter moving east twice:`);
  console.log(`Priest room: ${priest.room?.name} (${priest.room?.roomId}), HP: ${priest.player?.hp}/${priest.player?.maxHp}`);
  console.log(`Bard room: ${bard.room?.name} (${bard.room?.roomId}), HP: ${bard.player?.hp}/${bard.player?.maxHp}`);
  console.log(`Warrior room: ${warrior.room?.name} (${warrior.room?.roomId}), HP: ${warrior.player?.hp}/${warrior.player?.maxHp}`);

  // TEST 1: PRAYER_OF_MENDING (Priest group heal)
  console.log('\n\n========================================');
  console.log('=== TEST 1: PRAYER_OF_MENDING ===');
  console.log('========================================');

  // Record HP before
  const priestHpBefore = priest.player?.hp;
  const bardHpBefore = bard.player?.hp;
  const warriorHpBefore = warrior.player?.hp;
  const priestMpBefore = priest.player?.mp;

  console.log(`Before cast - Priest HP: ${priestHpBefore}, Bard HP: ${bardHpBefore}, Warrior HP: ${warriorHpBefore}, Priest MP: ${priestMpBefore}`);

  // Clear spell effects
  priest.spellEffects = [];
  bard.spellEffects = [];
  warrior.spellEffects = [];

  priest.send({ type: 'cast_spell', spellId: 'PRAYER_OF_MENDING' });
  await sleep(3000);

  console.log(`\nAfter PRAYER_OF_MENDING:`);
  console.log(`Priest HP: ${priest.player?.hp} (was ${priestHpBefore}), MP: ${priest.player?.mp} (was ${priestMpBefore})`);
  console.log(`Bard HP: ${bard.player?.hp} (was ${bardHpBefore})`);
  console.log(`Warrior HP: ${warrior.player?.hp} (was ${warriorHpBefore})`);
  console.log(`Priest spell effects received: ${priest.spellEffects.length}`);
  console.log(`Bard spell effects received: ${bard.spellEffects.length}`);
  console.log(`Warrior spell effects received: ${warrior.spellEffects.length}`);
  console.log(`Priest errors: ${JSON.stringify(priest.errors)}`);

  // TEST 2: WAR_HYMN (Bard group buff)
  console.log('\n\n========================================');
  console.log('=== TEST 2: WAR_HYMN ===');
  console.log('========================================');

  priest.spellEffects = [];
  bard.spellEffects = [];
  warrior.spellEffects = [];
  priest.activeEffects = [];
  bard.activeEffects = [];
  warrior.activeEffects = [];

  bard.send({ type: 'cast_spell', spellId: 'WAR_HYMN' });
  await sleep(3000);

  console.log(`\nAfter WAR_HYMN:`);
  console.log(`Bard MP: ${bard.player?.mp}`);
  console.log(`Priest activeEffects: ${JSON.stringify(priest.activeEffects)}`);
  console.log(`Bard activeEffects: ${JSON.stringify(bard.activeEffects)}`);
  console.log(`Warrior activeEffects: ${JSON.stringify(warrior.activeEffects)}`);
  console.log(`Priest spell effects: ${priest.spellEffects.length}`);
  console.log(`Bard spell effects: ${bard.spellEffects.length}`);
  console.log(`Warrior spell effects: ${warrior.spellEffects.length}`);
  console.log(`Bard errors: ${JSON.stringify(bard.errors)}`);

  // TEST 3: SOOTHING_AURA (Bard group HoT)
  console.log('\n\n========================================');
  console.log('=== TEST 3: SOOTHING_AURA ===');
  console.log('========================================');

  priest.spellEffects = [];
  bard.spellEffects = [];
  warrior.spellEffects = [];

  bard.send({ type: 'cast_spell', spellId: 'SOOTHING_AURA' });
  await sleep(3000);

  console.log(`\nAfter SOOTHING_AURA:`);
  console.log(`Bard MP: ${bard.player?.mp}`);
  console.log(`Priest activeEffects: ${JSON.stringify(priest.activeEffects)}`);
  console.log(`Bard activeEffects: ${JSON.stringify(bard.activeEffects)}`);
  console.log(`Warrior activeEffects: ${JSON.stringify(warrior.activeEffects)}`);
  console.log(`Bard errors: ${JSON.stringify(bard.errors)}`);

  // TEST 4: REJUVENATION (group HoT)
  console.log('\n\n========================================');
  console.log('=== TEST 4: REJUVENATION ===');
  console.log('========================================');

  priest.spellEffects = [];
  bard.spellEffects = [];
  warrior.spellEffects = [];

  priest.send({ type: 'cast_spell', spellId: 'REJUVENATION' });
  await sleep(3000);

  console.log(`\nAfter REJUVENATION:`);
  console.log(`Priest MP: ${priest.player?.mp}`);
  console.log(`Priest activeEffects: ${JSON.stringify(priest.activeEffects)}`);
  console.log(`Bard activeEffects: ${JSON.stringify(bard.activeEffects)}`);
  console.log(`Warrior activeEffects: ${JSON.stringify(warrior.activeEffects)}`);
  console.log(`Priest errors: ${JSON.stringify(priest.errors)}`);

  // TEST 5: Different room test - move warrior away, cast group heal
  console.log('\n\n========================================');
  console.log('=== TEST 5: Different Room (Warrior leaves) ===');
  console.log('========================================');

  warrior.send({ type: 'move', direction: 'WEST' });
  await sleep(2000);
  console.log(`Warrior moved to: ${warrior.room?.name} (${warrior.room?.roomId})`);
  console.log(`Priest still at: ${priest.room?.name} (${priest.room?.roomId})`);

  priest.spellEffects = [];
  bard.spellEffects = [];
  warrior.spellEffects = [];
  const warriorHpBeforeRemote = warrior.player?.hp;

  priest.send({ type: 'cast_spell', spellId: 'PRAYER_OF_MENDING' });
  await sleep(3000);

  console.log(`\nAfter PRAYER_OF_MENDING with Warrior in different room:`);
  console.log(`Priest HP: ${priest.player?.hp}, spell effects: ${priest.spellEffects.length}`);
  console.log(`Bard HP: ${bard.player?.hp}, spell effects: ${bard.spellEffects.length}`);
  console.log(`Warrior HP: ${warrior.player?.hp} (was ${warriorHpBeforeRemote}), spell effects: ${warrior.spellEffects.length}`);
  console.log(`Warrior should NOT have received the heal (different room)`);

  // TEST 6: Solo cast (no party members in room)
  console.log('\n\n========================================');
  console.log('=== TEST 6: Solo cast (only caster in room) ===');
  console.log('========================================');

  // Move bard away too
  bard.send({ type: 'move', direction: 'WEST' });
  await sleep(2000);
  console.log(`Bard moved to: ${bard.room?.name}`);
  console.log(`Priest alone at: ${priest.room?.name}`);

  priest.spellEffects = [];
  const priestHpBeforeSolo = priest.player?.hp;
  const priestMpBeforeSolo = priest.player?.mp;

  priest.send({ type: 'cast_spell', spellId: 'PRAYER_OF_MENDING' });
  await sleep(3000);

  console.log(`\nAfter solo PRAYER_OF_MENDING:`);
  console.log(`Priest HP: ${priest.player?.hp} (was ${priestHpBeforeSolo}), MP: ${priest.player?.mp} (was ${priestMpBeforeSolo})`);
  console.log(`Priest spell effects: ${priest.spellEffects.length}`);
  console.log(`Priest errors: ${JSON.stringify(priest.errors)}`);
  console.log(`Should still heal caster (self is party member in room)`);

  // TEST 7: MEND_WOUNDS (ALLY target - single target party heal for comparison)
  console.log('\n\n========================================');
  console.log('=== TEST 7: MEND_WOUNDS (ALLY single target) ===');
  console.log('========================================');

  // Move bard back
  bard.send({ type: 'move', direction: 'EAST' });
  await sleep(2000);

  priest.spellEffects = [];
  bard.spellEffects = [];
  const bardHpBeforeMend = bard.player?.hp;

  priest.send({ type: 'cast_spell', spellId: 'MEND_WOUNDS', targetName: 'Bard' });
  await sleep(3000);

  console.log(`\nAfter MEND_WOUNDS on Bard:`);
  console.log(`Bard HP: ${bard.player?.hp} (was ${bardHpBeforeMend})`);
  console.log(`Priest MP: ${priest.player?.mp}`);
  console.log(`Priest spell effects: ${priest.spellEffects.length}`);
  console.log(`Bard spell effects: ${bard.spellEffects.length}`);
  console.log(`Priest errors: ${JSON.stringify(priest.errors)}`);

  // Summary
  console.log('\n\n========================================');
  console.log('=== TEST SUMMARY ===');
  console.log('========================================');
  console.log(`\nAll errors encountered:`);
  console.log(`  Priest: ${JSON.stringify(priest.errors)}`);
  console.log(`  Bard: ${JSON.stringify(bard.errors)}`);
  console.log(`  Warrior: ${JSON.stringify(warrior.errors)}`);

  // Clean up
  clients.forEach(c => c.disconnect());
  admin.disconnect();

  console.log('\n--- Test complete ---');
  process.exit(0);
}

main().catch(e => {
  console.error('Fatal:', e);
  process.exit(1);
});
