import GenericCrudEditor from '../components/GenericCrudEditor';
import type { FieldConfig } from '../components/GenericCrudEditor';
import EditorPageHeader from '../components/EditorPageHeader';

const fields: FieldConfig[] = [
  { key: 'id', label: 'ID', type: 'text', placeholder: 'e.g. fireball', help: 'Unique identifier referenced by class spell lists' },
  { key: 'name', label: 'Name', type: 'text', placeholder: 'Fireball', help: 'Display name shown in spell book and combat log' },
  { key: 'description', label: 'Description', type: 'textarea', rows: 3, help: 'Flavor text shown in the spell book' },
  {
    key: 'school', label: 'School', type: 'select',
    help: 'Magic school — determines which classes can learn this spell',
    options: [
      { value: 'mage', label: 'Mage' },
      { value: 'priest', label: 'Priest' },
      { value: 'druid', label: 'Druid' },
      { value: 'kai', label: 'Kai' },
      { value: 'bard', label: 'Bard' },
    ],
  },
  {
    key: 'spellType', label: 'Spell Type', type: 'select',
    help: 'How the spell affects its target',
    options: [
      { value: 'DAMAGE', label: 'Damage' },
      { value: 'HEAL', label: 'Heal' },
      { value: 'BUFF', label: 'Buff' },
      { value: 'DOT', label: 'DoT' },
      { value: 'HOT', label: 'HoT' },
    ],
  },
  { key: 'manaCost', label: 'Mana Cost', type: 'number', help: 'MP consumed per cast' },
  { key: 'cooldownTicks', label: 'Cooldown (ticks)', type: 'number', help: 'Turns before the spell can be cast again (1 tick = 1.5s)' },
  { key: 'levelRequired', label: 'Level Required', type: 'number', help: 'Minimum player level to learn this spell' },
  {
    key: 'primaryStat', label: 'Primary Stat', type: 'select',
    help: 'Stat that scales spell power',
    options: [
      { value: 'strength', label: 'Strength' },
      { value: 'agility', label: 'Agility' },
      { value: 'intellect', label: 'Intellect' },
      { value: 'willpower', label: 'Willpower' },
      { value: 'health', label: 'Health' },
      { value: 'charm', label: 'Charm' },
    ],
  },
  { key: 'basePower', label: 'Base Power', type: 'number', help: 'Base damage or healing before stat scaling' },
  { key: 'tickPower', label: 'Tick Power (DoT/HoT per tick)', type: 'number', help: 'Damage or healing applied each tick for DoT/HoT spells' },
  {
    key: 'targetType', label: 'Target Type', type: 'select',
    help: 'Who the spell can target',
    options: [
      { value: 'SELF', label: 'Self' },
      { value: 'ALLY', label: 'Ally' },
      { value: 'ENEMY', label: 'Enemy' },
      { value: 'AOE', label: 'AoE' },
    ],
  },
  {
    key: 'effectType', label: 'Effect Type', type: 'select',
    help: 'Secondary effect applied alongside the main spell',
    options: [
      { value: '', label: 'None' },
      { value: 'BUFF_STRENGTH', label: 'Buff Strength' },
      { value: 'BUFF_AGILITY', label: 'Buff Agility' },
      { value: 'BUFF_WILLPOWER', label: 'Buff Willpower' },
      { value: 'POISON', label: 'Poison' },
      { value: 'HEAL_OVER_TIME', label: 'Heal Over Time' },
    ],
  },
  { key: 'effectDuration', label: 'Effect Duration (ticks)', type: 'number', help: 'How many ticks the secondary effect lasts' },
  { key: 'castMessage', label: 'Cast Message', type: 'text', help: 'Flavor text shown in combat log when cast' },
  { key: 'castSound', label: 'Cast Sound', type: 'sfx', audioCategory: 'spells' },
  { key: 'impactSound', label: 'Impact Sound', type: 'sfx', audioCategory: 'spells' },
  { key: 'missSound', label: 'Miss Sound', type: 'sfx', audioCategory: 'spells' },
  { key: 'imagePrompt', label: 'Image Prompt', type: 'textarea', rows: 3 },
  { key: 'imageStyle', label: 'Image Style', type: 'text' },
  { key: 'imageNegativePrompt', label: 'Negative Prompt', type: 'text' },
  { key: 'imageWidth', label: 'Image Width', type: 'number' },
  { key: 'imageHeight', label: 'Image Height', type: 'number' },
];

const pageHeader = (
  <EditorPageHeader storageKey="spells">
    Spells are organized by magic school (Mage, Priest, Druid, Kai, Bard). A class can only learn spells
    from schools it has access to — set school access levels in the Classes editor.
    Each spell type works differently: Damage/Heal are instant, DoT/HoT tick over time, Buffs apply stat bonuses.
  </EditorPageHeader>
);

function SpellEditor() {
  return <GenericCrudEditor entityName="Spell" apiPath="/spells" fields={fields} imagePreview={{ entityType: 'spell', maxWidth: 256, maxHeight: 256 }} pageHeader={pageHeader} />;
}

export default SpellEditor;
