#!/usr/bin/env node
/**
 * two-player-test.mjs — Dual WebSocket test for ALLY healing spells
 *
 * Registers/logs in two players and runs a scripted test sequence:
 * 1. Register Lumina (PRIEST) and Varn (WARRIOR)
 * 2. Form a party
 * 3. Move to forest, Fighter takes damage
 * 4. Test MEND_WOUNDS targeting ally
 * 5. Test ready_spell auto-cast on lowest HP party member
 * 6. Test BARK_SKIN buff on ally
 * 7. Verify threat generation
 */

import { WebSocket } from 'ws';

const SERVER_URL = process.env.NEOMUD_URL || 'ws://localhost:8080/game';
const TICK_MS = 2000; // wait time between actions

// ─── Helpers ───────────────────────────────────────────────────────────────
function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }

function createPlayer(name) {
  return {
    name,
    ws: null,
    state: { loggedIn: false, player: null, room: null, npcsInRoom: [], playersInRoom: [], events: [], inventory: [], activeEffects: [], party: null },
    messageLog: [],
  };
}

function connect(player) {
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(SERVER_URL);
    player.ws = ws;
    ws.on('open', () => {
      console.log(`[${player.name}] Connected`);
      resolve();
    });
    ws.on('error', (err) => {
      console.error(`[${player.name}] WS Error:`, err.message);
      reject(err);
    });
    ws.on('message', (data) => {
      try {
        const msg = JSON.parse(data.toString());
        handleMessage(player, msg);
      } catch (e) {
        console.error(`[${player.name}] Parse error:`, e.message);
      }
    });
    ws.on('close', () => {
      console.log(`[${player.name}] Disconnected`);
    });
  });
}

function send(player, msg) {
  const json = JSON.stringify(msg);
  console.log(`[${player.name}] >>> ${json}`);
  player.ws.send(json);
}

function handleMessage(player, msg) {
  player.messageLog.push(msg);
  const type = msg.type;

  switch (type) {
    case 'login_ok':
      player.state.loggedIn = true;
      player.state.player = msg.player || msg;
      console.log(`[${player.name}] Logged in: L${msg.player?.level || '?'} ${msg.player?.className || ''}`);
      break;
    case 'room_info':
      player.state.room = msg;
      console.log(`[${player.name}] Room: ${msg.roomName} [${msg.roomId}] exits: ${Object.keys(msg.exits || {}).join(',')}`);
      if (msg.npcs) player.state.npcsInRoom = msg.npcs;
      if (msg.players) player.state.playersInRoom = msg.players;
      break;
    case 'combat_hit':
      console.log(`[${player.name}] COMBAT: ${msg.attackerName} -> ${msg.targetName} for ${msg.damage} dmg ${msg.targetHp !== undefined ? `(${msg.targetHp}/${msg.targetMaxHp} HP)` : ''}`);
      break;
    case 'npc_killed':
      console.log(`[${player.name}] KILL: ${msg.npcName} killed by ${msg.killerName}`);
      break;
    case 'spell_effect':
      console.log(`[${player.name}] SPELL: ${msg.casterName} cast ${msg.spellName} on ${msg.targetName} - ${msg.effectType} ${msg.amount || ''} ${msg.message || ''}`);
      break;
    case 'skill_effect':
      console.log(`[${player.name}] SKILL: ${msg.actorName} used ${msg.skillName} on ${msg.targetName} - ${msg.damage || 0} dmg`);
      break;
    case 'hp_update':
      if (player.state.player) {
        player.state.player.hp = msg.hp;
        player.state.player.maxHp = msg.maxHp;
      }
      console.log(`[${player.name}] HP: ${msg.hp}/${msg.maxHp}`);
      break;
    case 'mp_update':
      if (player.state.player) {
        player.state.player.mp = msg.mp;
        player.state.player.maxMp = msg.maxMp;
      }
      console.log(`[${player.name}] MP: ${msg.mp}/${msg.maxMp}`);
      break;
    case 'player_entered':
      console.log(`[${player.name}] ENTERED: ${msg.playerName}`);
      break;
    case 'player_left':
      console.log(`[${player.name}] LEFT: ${msg.playerName} went ${msg.direction}`);
      break;
    case 'npc_entered':
      console.log(`[${player.name}] NPC ENTERED: ${msg.npcName}`);
      break;
    case 'system_message':
    case 'error':
      console.log(`[${player.name}] ${type.toUpperCase()}: ${msg.message || msg.text || JSON.stringify(msg)}`);
      break;
    case 'party_invite':
      console.log(`[${player.name}] PARTY INVITE from ${msg.inviterName}`);
      break;
    case 'party_update':
      player.state.party = msg;
      console.log(`[${player.name}] PARTY UPDATE: ${JSON.stringify(msg.members?.map(m => m.name) || msg)}`);
      break;
    case 'effect_applied':
      console.log(`[${player.name}] EFFECT APPLIED: ${msg.effectName || msg.name} on ${msg.targetName || 'self'} (${msg.type || ''})`);
      break;
    case 'effect_expired':
      console.log(`[${player.name}] EFFECT EXPIRED: ${msg.effectName || msg.name}`);
      break;
    case 'inventory_update':
      player.state.inventory = msg.items || [];
      break;
    case 'class_catalog_sync':
    case 'item_catalog_sync':
    case 'map_data':
    case 'room_items_update':
    case 'tutorial':
    case 'discovery_update':
      // Suppress noisy messages
      break;
    default:
      if (!['npc_left', 'room_effect'].includes(type)) {
        console.log(`[${player.name}] MSG(${type}): ${JSON.stringify(msg).slice(0, 200)}`);
      }
  }
}

function getRecentMessages(player, type, count = 5) {
  return player.messageLog.filter(m => m.type === type).slice(-count);
}

// ─── Main Test Sequence ────────────────────────────────────────────────────
async function main() {
  const lumina = createPlayer('Lumina');
  const varn = createPlayer('Varn');

  console.log('\n═══════════════════════════════════════════════════');
  console.log('  ALLY HEALING SPELL SYSTEM — TWO-PLAYER TEST');
  console.log('═══════════════════════════════════════════════════\n');

  // --- Step 1: Connect both players ---
  console.log('\n▶ STEP 1: Connecting players...\n');
  await connect(lumina);
  await connect(varn);
  await sleep(500);

  // Try to register, fall back to login
  console.log('\n▶ STEP 1a: Registering/Logging in Lumina (PRIEST)...\n');
  send(lumina, { type: 'register', username: 'lumina_ally1', password: 'testpass123', characterName: 'Lumina', className: 'PRIEST', raceName: 'HUMAN', gender: 'female' });
  await sleep(3000);

  if (!lumina.state.loggedIn) {
    console.log('[Lumina] Registration may have failed (name taken?), trying login...');
    send(lumina, { type: 'login', username: 'lumina_ally1', password: 'testpass123' });
    await sleep(3000);
  }

  console.log('\n▶ STEP 1b: Registering/Logging in Varn (WARRIOR)...\n');
  send(varn, { type: 'register', username: 'varn_ally1', password: 'testpass123', characterName: 'Varn', className: 'WARRIOR', raceName: 'HUMAN', gender: 'male' });
  await sleep(3000);

  if (!varn.state.loggedIn) {
    console.log('[Varn] Registration may have failed (name taken?), trying login...');
    send(varn, { type: 'login', username: 'varn_ally1', password: 'testpass123' });
    await sleep(3000);
  }

  if (!lumina.state.loggedIn || !varn.state.loggedIn) {
    console.error('FATAL: Could not log in both players');
    lumina.ws.close(); varn.ws.close();
    process.exit(1);
  }

  console.log(`\n✓ Lumina logged in: L${lumina.state.player?.level} ${lumina.state.player?.className}`);
  console.log(`✓ Varn logged in: L${varn.state.player?.level} ${varn.state.player?.className}`);

  // --- Step 2: Form a party ---
  console.log('\n▶ STEP 2: Forming a party (Lumina invites Varn)...\n');

  // Both need to be in the same room first - both start at temple
  await sleep(1000);

  send(lumina, { type: 'party_invite', targetName: 'Varn' });
  await sleep(2000);

  send(varn, { type: 'party_accept', inviterName: 'Lumina' });
  await sleep(2000);

  // Check party state
  const luminaParty = getRecentMessages(lumina, 'party_update');
  const varnParty = getRecentMessages(varn, 'party_update');
  console.log(`\nParty messages for Lumina: ${luminaParty.length}`);
  console.log(`Party messages for Varn: ${varnParty.length}`);
  if (luminaParty.length > 0) console.log('Lumina party:', JSON.stringify(luminaParty[luminaParty.length - 1]).slice(0, 300));
  if (varnParty.length > 0) console.log('Varn party:', JSON.stringify(varnParty[varnParty.length - 1]).slice(0, 300));

  // --- Step 3: Move to forest, Fighter takes damage ---
  console.log('\n▶ STEP 3: Moving to Forest Edge for combat...\n');

  // Move both to town square then forest
  send(lumina, { type: 'move', direction: 'NORTH' });
  send(varn, { type: 'move', direction: 'NORTH' });
  await sleep(2000);

  send(lumina, { type: 'move', direction: 'NORTH' });
  send(varn, { type: 'move', direction: 'NORTH' });
  await sleep(2000);

  // Check room for NPCs
  console.log(`\nLumina room: ${lumina.state.room?.roomName} [${lumina.state.room?.roomId}]`);
  console.log(`Varn room: ${varn.state.room?.roomName} [${varn.state.room?.roomId}]`);
  console.log(`NPCs in Lumina's room: ${JSON.stringify(lumina.state.npcsInRoom?.map(n => ({ name: n.name, id: n.id, hp: n.hp })))}`);

  // Wait for hostile NPCs to spawn or attack
  await sleep(3000);

  // Find a hostile NPC for Varn to fight
  let targetNpc = lumina.state.npcsInRoom?.find(n => n.hostile);
  if (!targetNpc) {
    console.log('No hostile NPC in room yet. Waiting...');
    await sleep(5000);
    targetNpc = lumina.state.npcsInRoom?.find(n => n.hostile);
  }

  if (targetNpc) {
    console.log(`\nFound hostile NPC: ${targetNpc.name} (${targetNpc.id})`);

    // Varn engages the NPC
    send(varn, { type: 'select_target', npcId: targetNpc.id });
    await sleep(500);
    send(varn, { type: 'attack_toggle', enabled: true });
    await sleep(3000); // Let a few combat ticks happen

    console.log(`\nVarn HP after combat: ${varn.state.player?.hp}/${varn.state.player?.maxHp}`);
  } else {
    console.log('\nNo hostile NPCs found. Simulating damage scenario...');
    // Even without damage, we can still test the spell targeting
  }

  // --- Step 4: Test MEND_WOUNDS targeting ally ---
  console.log('\n▶ STEP 4: Testing MEND_WOUNDS (ALLY heal) — Lumina targets Varn...\n');

  send(lumina, { type: 'cast_spell', spellId: 'MEND_WOUNDS', targetId: 'Varn' });
  await sleep(3000);

  // Check for spell_effect messages
  const spellEffects1 = getRecentMessages(lumina, 'spell_effect');
  const errors1 = getRecentMessages(lumina, 'error');
  const sysMsgs1 = getRecentMessages(lumina, 'system_message');

  console.log(`\nSpell effects after MEND_WOUNDS: ${spellEffects1.length}`);
  spellEffects1.forEach(m => console.log(`  -> ${m.casterName} cast ${m.spellName} on ${m.targetName}: ${m.effectType} ${m.amount || ''}`));
  if (errors1.length > 0) console.log(`Errors: ${errors1.map(e => e.message || e.text).join(', ')}`);
  if (sysMsgs1.length > 0) console.log(`System msgs: ${sysMsgs1.map(e => e.message || e.text).join(', ')}`);

  console.log(`\nVarn HP after heal: ${varn.state.player?.hp}/${varn.state.player?.maxHp}`);
  console.log(`Lumina MP after heal: ${lumina.state.player?.mp}/${lumina.state.player?.maxMp}`);

  // --- Step 5: Test ready_spell auto-cast ---
  console.log('\n▶ STEP 5: Testing MEND_WOUNDS auto-cast via ready_spell...\n');

  // Ready the heal spell
  send(lumina, { type: 'ready_spell', spellId: 'MEND_WOUNDS' });
  await sleep(1000);

  // Enable attack mode - should auto-cast on lowest HP party member
  send(lumina, { type: 'attack_toggle', enabled: true });
  await sleep(5000); // Wait for several ticks

  const spellEffects2 = player => player.messageLog.filter(m => m.type === 'spell_effect' && m.spellName?.toLowerCase().includes('mend'));
  console.log(`\nMend Wounds casts seen by Lumina: ${spellEffects2(lumina).length}`);
  console.log(`Mend Wounds casts seen by Varn: ${spellEffects2(varn).length}`);
  spellEffects2(lumina).slice(-3).forEach(m => console.log(`  -> ${m.casterName} -> ${m.targetName}: ${m.effectType} ${m.amount || ''}`));

  // Stop attacking
  send(lumina, { type: 'attack_toggle', enabled: false });
  await sleep(1000);

  // --- Step 6: Test BARK_SKIN buff on ally ---
  console.log('\n▶ STEP 6: Testing BARK_SKIN (ALLY buff) on Varn...\n');

  send(lumina, { type: 'cast_spell', spellId: 'BARK_SKIN', targetId: 'Varn' });
  await sleep(3000);

  const spellEffects3 = getRecentMessages(lumina, 'spell_effect', 10);
  const effectApplied = lumina.messageLog.filter(m => m.type === 'effect_applied');
  const errors3 = lumina.messageLog.filter(m => m.type === 'error').slice(-3);

  console.log(`\nSpell effects after BARK_SKIN: ${spellEffects3.length}`);
  spellEffects3.slice(-3).forEach(m => console.log(`  -> ${m.casterName} cast ${m.spellName} on ${m.targetName}: ${m.effectType} ${m.amount || ''}`));
  console.log(`Effects applied: ${effectApplied.length}`);
  effectApplied.slice(-3).forEach(m => console.log(`  -> ${m.effectName || m.name} on ${m.targetName || 'self'}`));
  if (errors3.length > 0) console.log(`Recent errors: ${errors3.map(e => e.message || e.text).join(', ')}`);

  // Check Varn's active effects
  const varnEffects = varn.messageLog.filter(m => m.type === 'effect_applied');
  console.log(`\nVarn's effects applied: ${varnEffects.length}`);
  varnEffects.forEach(m => console.log(`  -> ${m.effectName || m.name} (${m.type || ''})`));

  // --- Step 7: Test threat generation from healing ---
  console.log('\n▶ STEP 7: Verifying threat generation (healer draws NPC aggro)...\n');

  // If Varn is still fighting, healing should generate threat
  if (targetNpc) {
    // Cast heal while Varn is in combat
    send(lumina, { type: 'cast_spell', spellId: 'MEND_WOUNDS', targetId: 'Varn' });
    await sleep(4000);

    // Check if NPC attacks Lumina
    const luminaCombatHits = lumina.messageLog.filter(m => m.type === 'combat_hit' && m.targetName === 'Lumina');
    console.log(`\nHits on Lumina (threat from healing): ${luminaCombatHits.length}`);
    luminaCombatHits.slice(-3).forEach(m => console.log(`  -> ${m.attackerName} hit Lumina for ${m.damage}`));
  } else {
    console.log('No NPC combat to test threat generation with.');
  }

  // --- Summary ---
  console.log('\n═══════════════════════════════════════════════════');
  console.log('  TEST SUMMARY');
  console.log('═══════════════════════════════════════════════════\n');

  // Gather all errors
  const allLuminaErrors = lumina.messageLog.filter(m => m.type === 'error');
  const allVarnErrors = varn.messageLog.filter(m => m.type === 'error');

  console.log(`Lumina errors (${allLuminaErrors.length}):`);
  allLuminaErrors.forEach(m => console.log(`  ERROR: ${m.message || m.text || JSON.stringify(m)}`));

  console.log(`\nVarn errors (${allVarnErrors.length}):`);
  allVarnErrors.forEach(m => console.log(`  ERROR: ${m.message || m.text || JSON.stringify(m)}`));

  // All spell effects
  const allSpellEffects = lumina.messageLog.filter(m => m.type === 'spell_effect');
  console.log(`\nAll spell effects seen (${allSpellEffects.length}):`);
  allSpellEffects.forEach(m => console.log(`  ${m.casterName} cast ${m.spellName} -> ${m.targetName}: ${m.effectType} ${m.amount || ''} ${m.message || ''}`));

  // Final state
  console.log(`\nFinal Lumina: HP ${lumina.state.player?.hp}/${lumina.state.player?.maxHp}, MP ${lumina.state.player?.mp}/${lumina.state.player?.maxMp}`);
  console.log(`Final Varn: HP ${varn.state.player?.hp}/${varn.state.player?.maxHp}`);

  // Cleanup
  send(varn, { type: 'attack_toggle', enabled: false });
  await sleep(1000);
  lumina.ws.close();
  varn.ws.close();

  console.log('\n═══════════════════════════════════════════════════');
  console.log('  TEST COMPLETE');
  console.log('═══════════════════════════════════════════════════\n');
}

main().catch(err => {
  console.error('Test failed:', err);
  process.exit(1);
});
