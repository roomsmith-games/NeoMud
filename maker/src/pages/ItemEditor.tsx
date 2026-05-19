import GenericCrudEditor from '../components/GenericCrudEditor';
import type { FieldConfig } from '../components/GenericCrudEditor';
import EditorPageHeader from '../components/EditorPageHeader';

const isEquipment = (f: Record<string, any>) => f.type === 'weapon' || f.type === 'armor';
const isConsumable = (f: Record<string, any>) => f.type === 'consumable';
const isStackable = (f: Record<string, any>) => !isEquipment(f);

const fields: FieldConfig[] = [
  { key: 'id', label: 'ID', type: 'text', placeholder: 'e.g. iron_sword', help: 'Unique identifier used in loot tables, vendor inventories, and recipes' },
  { key: 'name', label: 'Name', type: 'text', placeholder: 'e.g. My New Item', help: 'Display name shown to players' },
  { key: 'description', label: 'Description', type: 'textarea', rows: 3, help: 'Flavor text shown when a player examines the item' },
  {
    key: 'type', label: 'Category', type: 'select',
    help: 'Determines which fields appear below and how the item behaves in-game',
    options: [
      { value: 'weapon', label: 'Equipment — Weapon' },
      { value: 'armor', label: 'Equipment — Armor' },
      { value: 'consumable', label: 'Consumable' },
      { value: 'crafting', label: 'Crafting Material' },
      { value: 'misc', label: 'Misc' },
    ],
  },
  { key: 'value', label: 'Value (gold)', type: 'number', help: 'Base price in gold — vendors buy at a fraction of this' },
  { key: 'weight', label: 'Weight', type: 'number', help: 'Encumbrance value (not currently enforced)' },
  {
    key: 'slot', label: 'Slot', type: 'select',
    visibleWhen: isEquipment,
    help: 'Equipment slot this item occupies when worn',
    options: [
      { value: '', label: 'None' },
      { value: 'weapon', label: 'Main Hand' },
      { value: 'shield', label: 'Off Hand' },
      { value: 'head', label: 'Head' },
      { value: 'chest', label: 'Chest' },
      { value: 'legs', label: 'Legs' },
      { value: 'feet', label: 'Feet' },
      { value: 'hands', label: 'Hands' },
      { value: 'neck', label: 'Neck' },
      { value: 'ring', label: 'Ring' },
      { value: 'back', label: 'Back' },
    ],
  },
  { key: 'damageBonus', label: 'Damage Bonus', type: 'number', visibleWhen: isEquipment, help: 'Flat bonus added to melee attack rolls' },
  { key: 'damageRange', label: 'Damage Range', type: 'number', visibleWhen: isEquipment, help: 'Random damage spread — attack rolls 0 to this value' },
  { key: 'armorValue', label: 'Armor Value', type: 'number', visibleWhen: isEquipment, help: 'Damage reduction when equipped' },
  { key: 'levelRequirement', label: 'Level Requirement', type: 'number', visibleWhen: isEquipment, help: 'Minimum player level to equip this item' },
  { key: 'attackSound', label: 'Attack Sound', type: 'sfx', audioCategory: 'items', visibleWhen: isEquipment },
  { key: 'missSound', label: 'Miss Sound', type: 'sfx', audioCategory: 'items', visibleWhen: isEquipment },
  { key: 'useEffect', label: 'Use Effect', type: 'text', help: 'Effect string applied on use (e.g. heal:25)', visibleWhen: isConsumable },
  { key: 'useSound', label: 'Use Sound', type: 'sfx', audioCategory: 'items', visibleWhen: isConsumable },
  { key: 'stackable', label: 'Stackable', type: 'checkbox', visibleWhen: isStackable, help: 'Whether multiple units stack in one inventory slot' },
  { key: 'maxStack', label: 'Max Stack', type: 'number', visibleWhen: isStackable, help: 'Maximum units per inventory slot (0 = unlimited)' },
  { key: 'imagePrompt', label: 'Image Prompt', type: 'textarea', rows: 3 },
  { key: 'imageStyle', label: 'Image Style', type: 'text' },
  { key: 'imageNegativePrompt', label: 'Image Negative Prompt', type: 'text' },
  { key: 'imageWidth', label: 'Image Width (max 256)', type: 'number', max: 256 },
  { key: 'imageHeight', label: 'Image Height (max 256)', type: 'number', max: 256 },
];

const pageHeader = (
  <EditorPageHeader storageKey="items">
    Items are anything players can carry: weapons, armor, consumables, and crafting materials.
    They appear in vendor inventories, NPC loot tables, and crafting recipes.
    Choose a category to see the relevant fields — weapons and armor get combat stats, consumables get use effects.
  </EditorPageHeader>
);

function ItemEditor() {
  return <GenericCrudEditor entityName="Item" apiPath="/items" fields={fields} imagePreview={{ entityType: 'item', maxWidth: 256, maxHeight: 256 }} pageHeader={pageHeader} />;
}

export default ItemEditor;
