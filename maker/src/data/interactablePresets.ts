export interface InteractablePreset {
  id: string;
  label: string;
  category: string;
  description: string;
  actionType: string;
  triggerType: string;
  defaults: Record<string, any>;
}

export const ACTION_TYPE_DESCRIPTIONS: Record<string, string> = {
  EXIT_OPEN: 'Unlocks or opens a blocked exit in the specified direction.',
  TREASURE_DROP: 'Drops loot from a loot table when activated.',
  MONSTER_SPAWN: 'Spawns one or more hostile NPCs in the room.',
  ROOM_EFFECT: 'Applies an effect (heal, damage, buff) to the player.',
  TELEPORT: 'Moves the player to a target room.',
  DAMAGE_TRAP: 'Deals damage on trigger, with optional save check.',
  PLACE_ITEM: 'Two-phase: prompts player to place an item, then validates it.',
  PUZZLE_STEP: 'One step in a multi-step sequence puzzle. Wrong order resets progress.',
  RIDDLE_PROMPT: 'Two-phase: presents a riddle, then checks the player\'s answer.',
  CONDITIONAL_TRIGGER: 'Checks a condition (item, flag, or level) before opening a gated exit.',
  CHOICE_PROMPT: 'Presents a branching dialog that sets a permanent player flag.',
};

export const INTERACTABLE_PRESETS: InteractablePreset[] = [
  {
    id: 'preset_locked_door',
    label: 'Locked Door (Lever)',
    category: 'Doors & Locks',
    description: 'A lever that unlocks a door in the specified direction.',
    actionType: 'EXIT_OPEN',
    triggerType: 'ON_ACTION',
    defaults: {
      label: 'Rusty Lever',
      description: 'You pull the lever and hear a grinding sound as the passage opens.',
      icon: '',
      difficultyCheck: 'STRENGTH',
      difficulty: 15,
      actionData: { direction: '' },
    },
  },
  {
    id: 'preset_treasure_chest',
    label: 'Hidden Treasure Chest',
    category: 'Doors & Locks',
    description: 'A hidden chest that drops loot when found and opened.',
    actionType: 'TREASURE_DROP',
    triggerType: 'ON_ACTION',
    defaults: {
      label: 'Dusty Chest',
      description: 'You pry open the chest and find treasure inside!',
      icon: '',
      perceptionDC: 12,
      actionData: { lootTableId: '' },
    },
  },
  {
    id: 'preset_ambush',
    label: 'Monster Ambush',
    category: 'Traps',
    description: 'Hostile NPCs spawn when a player enters the room.',
    actionType: 'MONSTER_SPAWN',
    triggerType: 'ON_ENTER',
    defaults: {
      label: 'Ambush',
      description: 'Creatures burst from the shadows!',
      icon: '',
      actionData: { npcId: '', count: '2' },
    },
  },
  {
    id: 'preset_healing_fountain',
    label: 'Healing Fountain',
    category: 'Effects',
    description: 'Restores HP when a player interacts with it.',
    actionType: 'ROOM_EFFECT',
    triggerType: 'ON_ACTION',
    defaults: {
      label: 'Glowing Fountain',
      description: 'Warm light washes over you as your wounds knit.',
      icon: '',
      cooldownTicks: 40,
      actionData: { effectType: 'HEAL', value: '50', durationTicks: '0', message: 'The fountain restores your vitality.' },
    },
  },
  {
    id: 'preset_mana_well',
    label: 'Mana Well',
    category: 'Effects',
    description: 'Restores MP when a player interacts with it.',
    actionType: 'ROOM_EFFECT',
    triggerType: 'ON_ACTION',
    defaults: {
      label: 'Arcane Well',
      description: 'Arcane energy surges through you.',
      icon: '',
      cooldownTicks: 40,
      actionData: { effectType: 'MANA_REGEN', value: '30', durationTicks: '0', message: 'The well replenishes your mana.' },
    },
  },
  {
    id: 'preset_teleport_portal',
    label: 'Teleport Portal',
    category: 'Effects',
    description: 'Moves the player to a target room instantly.',
    actionType: 'TELEPORT',
    triggerType: 'ON_ACTION',
    defaults: {
      label: 'Shimmering Portal',
      description: 'You step through the portal and the world shifts around you.',
      icon: '',
      actionData: { targetRoomId: '', message: 'The portal pulls you through.' },
    },
  },
  {
    id: 'preset_dart_trap',
    label: 'Poison Dart Trap',
    category: 'Traps',
    description: 'Deals damage when a player enters, with an agility save to dodge.',
    actionType: 'DAMAGE_TRAP',
    triggerType: 'ON_ENTER',
    defaults: {
      label: 'Pressure Plate',
      description: 'You hear a click as darts fly from the walls!',
      icon: '',
      perceptionDC: 14,
      actionData: {
        damage: '20', damageType: 'poison',
        saveStat: 'AGILITY', saveType: 'DODGE', saveDC: '14',
        damageMessage: 'A dart pierces your flesh!',
        dodgeMessage: 'You leap aside just in time!',
      },
    },
  },
  {
    id: 'preset_key_pedestal',
    label: 'Key Pedestal (Place Item)',
    category: 'Puzzles',
    description: 'Prompts the player to place a specific item to open a passage.',
    actionType: 'PLACE_ITEM',
    triggerType: 'ON_ACTION',
    defaults: {
      label: 'Stone Pedestal',
      description: 'The pedestal glows as the key slots into place.',
      icon: '',
      actionData: {
        acceptedItems: '', consumeItem: 'true',
        successDirection: '', promptText: 'Place an item on the pedestal.',
        successMessage: 'The seal opens with a grinding sigh.',
        failureMessage: 'Nothing happens — that\'s not what fits.',
      },
    },
  },
  {
    id: 'preset_puzzle_step',
    label: 'Sequence Puzzle Step',
    category: 'Puzzles',
    description: 'One step in a multi-step sequence. Wrong order resets progress.',
    actionType: 'PUZZLE_STEP',
    triggerType: 'ON_ACTION',
    defaults: {
      label: 'Ancient Pillar',
      description: 'The pillar hums with energy.',
      icon: '',
      actionData: {
        puzzleGroupId: '', puzzleStepIndex: '0', puzzleTotalSteps: '3',
        advanceMessage: 'The pillar resonates.',
        successMessage: 'All pillars sing in harmony — the way opens!',
        resetMessage: 'The pillars dim. The sequence is lost.',
        successDirection: '',
      },
    },
  },
  {
    id: 'preset_riddle_gate',
    label: 'Riddle Gate',
    category: 'Puzzles',
    description: 'Presents a riddle. Correct answer opens a passage.',
    actionType: 'RIDDLE_PROMPT',
    triggerType: 'ON_ACTION',
    defaults: {
      label: 'Inscribed Arch',
      description: 'The inscription flashes — the way is open.',
      icon: '',
      actionData: {
        question: '', hint: '', acceptedAnswers: '', synonyms: '',
        successDirection: '', successMessage: 'The door groans open.',
        failureMessage: 'The inscription pulses red — wrong.',
      },
    },
  },
  {
    id: 'preset_level_gate',
    label: 'Level Gate',
    category: 'Doors & Locks',
    description: 'Only players above a minimum level can pass.',
    actionType: 'CONDITIONAL_TRIGGER',
    triggerType: 'ON_ACTION',
    defaults: {
      label: 'Warded Threshold',
      description: 'The ward recognizes your strength.',
      icon: '',
      actionData: {
        conditionType: 'LEVEL', requiredLevel: '10',
        successDirection: '', successMessage: 'The ward fades and the way opens.',
        failureMessage: 'The ward pushes you back — you are not ready.',
      },
    },
  },
  {
    id: 'preset_item_gate',
    label: 'Item Gate (Key Required)',
    category: 'Doors & Locks',
    description: 'Checks if the player has a specific item before opening.',
    actionType: 'CONDITIONAL_TRIGGER',
    triggerType: 'ON_ACTION',
    defaults: {
      label: 'Sealed Door',
      description: 'The door recognizes the key.',
      icon: '',
      actionData: {
        conditionType: 'ITEM', requiredItemId: '',
        successDirection: '', successMessage: 'The seal breaks and the door swings open.',
        failureMessage: 'The door is sealed. You lack the key.',
      },
    },
  },
  {
    id: 'preset_branching_choice',
    label: 'Branching Choice',
    category: 'Story',
    description: 'Presents a permanent branching dialog that sets a player flag.',
    actionType: 'CHOICE_PROMPT',
    triggerType: 'ON_ACTION',
    defaults: {
      label: 'Crossroads Obelisk',
      description: 'Your choice echoes across fate.',
      icon: '',
      actionData: {
        question: '', flagKey: '',
        options: '[{"id":"a","label":"Option A"},{"id":"b","label":"Option B"}]',
        alreadyChosenMessage: 'Your choice has already been made.',
      },
    },
  },
];

export const PRESET_CATEGORIES = [...new Set(INTERACTABLE_PRESETS.map((p) => p.category))];
