import GenericCrudEditor from '../components/GenericCrudEditor';
import type { FieldConfig } from '../components/GenericCrudEditor';
import EditorPageHeader from '../components/EditorPageHeader';

const fields: FieldConfig[] = [
  { key: 'id', label: 'ID', type: 'text', placeholder: 'e.g. elf', help: 'Unique identifier, e.g. human, elf, dwarf' },
  { key: 'name', label: 'Name', type: 'text', placeholder: 'Elf', help: 'Display name shown during character creation' },
  { key: 'description', label: 'Description', type: 'textarea', rows: 3, help: 'Lore text shown when selecting this race' },
  { key: 'xpModifier', label: 'XP Modifier', type: 'number', help: '1.0 = normal rate' },
  { key: 'statModifiers', label: 'Stat Modifiers', type: 'stat-grid' as const, allowNegative: true, help: 'Stat bonuses/penalties for this race (can be negative)' },
  { key: 'imagePrompt', label: 'Image Prompt', type: 'textarea', rows: 3 },
  { key: 'imageStyle', label: 'Image Style', type: 'text' },
  { key: 'imageNegativePrompt', label: 'Negative Prompt', type: 'text' },
  { key: 'imageWidth', label: 'Image Width', type: 'number' },
  { key: 'imageHeight', label: 'Image Height', type: 'number' },
];

const pageHeader = (
  <EditorPageHeader storageKey="races">
    Races define the species players can choose during character creation. Each race has stat modifiers
    that shift the base stats up or down, and an XP modifier that affects leveling speed.
  </EditorPageHeader>
);

function RaceEditor() {
  return <GenericCrudEditor entityName="Race" apiPath="/races" fields={fields} imagePreview={{ entityType: 'race', maxWidth: 256, maxHeight: 256 }} pageHeader={pageHeader} />;
}

export default RaceEditor;
