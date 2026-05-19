import GenericCrudEditor from '../components/GenericCrudEditor';
import type { FieldConfig } from '../components/GenericCrudEditor';
import EditorPageHeader from '../components/EditorPageHeader';

const fields: FieldConfig[] = [
  { key: 'id', label: 'ID', type: 'text', placeholder: 'e.g. warrior', help: 'Unique identifier, e.g. warrior, mage, thief' },
  { key: 'name', label: 'Name', type: 'text', placeholder: 'Warrior', help: 'Display name shown during character creation' },
  { key: 'description', label: 'Description', type: 'textarea', rows: 3, help: 'Lore text shown when selecting this class' },
  { key: 'hpPerLevelMin', label: 'HP Per Level (Min)', type: 'number', help: 'Minimum HP gained on level-up (rolled between min and max)' },
  { key: 'hpPerLevelMax', label: 'HP Per Level (Max)', type: 'number', help: 'Maximum HP gained on level-up' },
  { key: 'mpPerLevelMin', label: 'MP Per Level (Min)', type: 'number', help: 'Minimum MP gained on level-up (set both to 0 for non-casters)' },
  { key: 'mpPerLevelMax', label: 'MP Per Level (Max)', type: 'number', help: 'Maximum MP gained on level-up' },
  { key: 'xpModifier', label: 'XP Modifier', type: 'number', help: '1.0 = normal rate' },
  { key: 'minimumStats', label: 'Minimum Stats', type: 'stat-grid' as const, help: 'Minimum stat requirements to choose this class' },
  {
    key: 'skills', label: 'Skills', type: 'checklist' as const,
    checklistOptions: [
      { value: 'BASH', label: 'Bash', group: 'combat' },
      { value: 'KICK', label: 'Kick', group: 'combat' },
      { value: 'BACKSTAB', label: 'Backstab', group: 'combat' },
      { value: 'PARRY', label: 'Parry', group: 'defense' },
      { value: 'DODGE', label: 'Dodge', group: 'defense' },
      { value: 'SNEAK', label: 'Sneak', group: 'stealth' },
      { value: 'TRACK', label: 'Track', group: 'utility' },
      { value: 'MEDITATE', label: 'Meditate', group: 'utility' },
      { value: 'PERCEPTION', label: 'Perception', group: 'utility' },
      { value: 'PICK_LOCK', label: 'Pick Lock', group: 'utility' },
      { value: 'HAGGLE', label: 'Haggle', group: 'utility' },
      { value: 'REST', label: 'Rest', group: 'utility' },
    ],
    help: 'Skills available to this class',
  },
  { key: 'properties', label: 'Properties (JSON)', type: 'json', rows: 4, help: 'Arbitrary class properties object' },
  { key: 'magicSchools', label: 'Magic Schools', type: 'school-levels' as const, help: 'Access level per magic school (0 = none, 3 = master)' },
  { key: 'imagePrompt', label: 'Image Prompt', type: 'textarea', rows: 3 },
  { key: 'imageStyle', label: 'Image Style', type: 'text' },
  { key: 'imageNegativePrompt', label: 'Negative Prompt', type: 'text' },
  { key: 'imageWidth', label: 'Image Width', type: 'number' },
  { key: 'imageHeight', label: 'Image Height', type: 'number' },
];

const pageHeader = (
  <EditorPageHeader storageKey="classes">
    Classes define what a player can do: their HP/MP growth, available skills, and magic school access.
    Minimum stats control which race+stat-roll combos can pick this class. Skills and magic schools
    are the primary way classes feel different in play.
  </EditorPageHeader>
);

function ClassEditor() {
  return <GenericCrudEditor entityName="Class" apiPath="/character-classes" fields={fields} imagePreview={{ entityType: 'character-class', maxWidth: 256, maxHeight: 256 }} pageHeader={pageHeader} />;
}

export default ClassEditor;
