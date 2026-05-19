import GenericCrudEditor from '../components/GenericCrudEditor';
import type { FieldConfig } from '../components/GenericCrudEditor';
import EditorPageHeader from '../components/EditorPageHeader';

const fields: FieldConfig[] = [
  { key: 'id', label: 'ID', type: 'text', placeholder: 'e.g. slash', help: 'Unique identifier referenced by class skill lists' },
  { key: 'name', label: 'Name', type: 'text', placeholder: 'Slash', help: 'Display name shown to players' },
  { key: 'description', label: 'Description', type: 'textarea', rows: 3, help: 'Flavor text shown in the skill list' },
  {
    key: 'category', label: 'Category', type: 'select',
    help: 'Determines when and how the skill is used',
    options: [
      { value: 'combat', label: 'Combat' },
      { value: 'defense', label: 'Defense' },
      { value: 'stealth', label: 'Stealth' },
      { value: 'utility', label: 'Utility' },
    ],
  },
  {
    key: 'primaryStat', label: 'Primary Stat', type: 'select',
    help: 'Main stat for the skill check roll',
    options: [
      { value: 'strength', label: 'Strength' },
      { value: 'agility', label: 'Agility' },
      { value: 'intellect', label: 'Intellect' },
      { value: 'willpower', label: 'Willpower' },
      { value: 'health', label: 'Health' },
      { value: 'charm', label: 'Charm' },
    ],
  },
  {
    key: 'secondaryStat', label: 'Secondary Stat', type: 'select',
    help: 'Optional secondary stat that adds a minor bonus to the check',
    options: [
      { value: '', label: 'None' },
      { value: 'strength', label: 'Strength' },
      { value: 'agility', label: 'Agility' },
      { value: 'intellect', label: 'Intellect' },
      { value: 'willpower', label: 'Willpower' },
      { value: 'health', label: 'Health' },
      { value: 'charm', label: 'Charm' },
    ],
  },
  { key: 'cooldownTicks', label: 'Cooldown (ticks)', type: 'number', help: 'Turns before the skill can be used again (1 tick = 1.5s)' },
  { key: 'manaCost', label: 'Mana Cost', type: 'number', help: 'MP consumed per use (0 for physical skills)' },
  { key: 'difficulty', label: 'Difficulty', type: 'number', help: 'Default check DC (15)' },
  { key: 'isPassive', label: 'Passive', type: 'checkbox', placeholder: 'Is a passive skill', help: 'Passive skills are always active and need no cooldown' },
  {
    key: 'classRestrictions', label: 'Class Restrictions', type: 'checklist' as const,
    checklistOptions: [
      { value: 'BARD', label: 'Bard' },
      { value: 'CLERIC', label: 'Cleric' },
      { value: 'DRUID', label: 'Druid' },
      { value: 'GYPSY', label: 'Gypsy' },
      { value: 'MAGE', label: 'Mage' },
      { value: 'MISSIONARY', label: 'Missionary' },
      { value: 'MYSTIC', label: 'Mystic' },
      { value: 'NINJA', label: 'Ninja' },
      { value: 'PALADIN', label: 'Paladin' },
      { value: 'PRIEST', label: 'Priest' },
      { value: 'RANGER', label: 'Ranger' },
      { value: 'THIEF', label: 'Thief' },
      { value: 'WARLOCK', label: 'Warlock' },
      { value: 'WARRIOR', label: 'Warrior' },
      { value: 'WITCHHUNTER', label: 'Witch Hunter' },
    ],
    help: 'Classes that can use this skill (empty = all classes)',
  },
  { key: 'properties', label: 'Properties (JSON)', type: 'json', rows: 4, help: 'Arbitrary skill properties object' },
  { key: 'imagePrompt', label: 'Image Prompt', type: 'textarea', rows: 3 },
  { key: 'imageStyle', label: 'Image Style', type: 'text' },
  { key: 'imageNegativePrompt', label: 'Negative Prompt', type: 'text' },
  { key: 'imageWidth', label: 'Image Width', type: 'number' },
  { key: 'imageHeight', label: 'Image Height', type: 'number' },
];

const pageHeader = (
  <EditorPageHeader storageKey="skills">
    Skills are active and passive abilities available to player classes. Active skills (Bash, Kick, Sneak)
    have cooldowns and stat checks. Passive skills (Dodge, Parry, Perception) are always on.
    Assign skills to classes in the Classes editor.
  </EditorPageHeader>
);

function SkillEditor() {
  return <GenericCrudEditor entityName="Skill" apiPath="/skills" fields={fields} imagePreview={{ entityType: 'skill', maxWidth: 256, maxHeight: 256 }} pageHeader={pageHeader} />;
}

export default SkillEditor;
