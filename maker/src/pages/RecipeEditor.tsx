import GenericCrudEditor from '../components/GenericCrudEditor';
import type { FieldConfig } from '../components/GenericCrudEditor';
import EditorPageHeader from '../components/EditorPageHeader';

const fields: FieldConfig[] = [
  { key: 'id', label: 'ID', type: 'text', placeholder: 'e.g. recipe:antivenom_vial', help: 'Unique identifier, typically recipe:item_name' },
  { key: 'name', label: 'Name', type: 'text', placeholder: 'Antivenom Vial', help: 'Display name shown at crafting stations' },
  { key: 'description', label: 'Description', type: 'textarea', rows: 3, help: 'Flavor text shown in the recipe list' },
  {
    key: 'category', label: 'Category', type: 'select',
    help: 'What type of item this recipe produces',
    options: [
      { value: 'consumable', label: 'Consumable' },
      { value: 'weapon', label: 'Weapon' },
      { value: 'armor', label: 'Armor' },
      { value: 'accessory', label: 'Accessory' },
      { value: 'scroll', label: 'Scroll' },
    ],
  },
  { key: 'materials', label: 'Materials', type: 'textarea', rows: 4, help: 'JSON array: [{"itemId": "item:xxx", "quantity": 2}]' },
  { key: 'cost', label: 'Cost', type: 'textarea', rows: 2, help: 'JSON object: {"silver": 1, "gold": 0}' },
  { key: 'outputItemId', label: 'Output Item ID', type: 'text', placeholder: 'e.g. item:antivenom_vial', help: 'The item ID produced — must exist in the Items editor' },
  { key: 'outputQuantity', label: 'Output Quantity', type: 'number', help: 'How many items crafted per recipe use' },
  { key: 'levelRequirement', label: 'Level Requirement', type: 'number', help: 'Minimum player level to craft this recipe' },
  { key: 'classRestriction', label: 'Class Restriction', type: 'text', help: 'Leave empty for no restriction' },
];

const pageHeader = (
  <EditorPageHeader storageKey="recipes">
    Recipes let players combine materials at a crafter NPC. Create items first (especially crafting materials),
    then define recipes that consume them. Players discover recipes through crafter NPCs placed in zones.
  </EditorPageHeader>
);

function RecipeEditor() {
  return <GenericCrudEditor entityName="Recipe" apiPath="/recipes" fields={fields} pageHeader={pageHeader} />;
}

export default RecipeEditor;
