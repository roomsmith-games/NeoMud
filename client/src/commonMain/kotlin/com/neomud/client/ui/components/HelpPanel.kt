package com.neomud.client.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.client.ui.theme.StoneTheme
import org.jetbrains.compose.resources.painterResource

// ─────────────────────────────────────────────
// Palette
// ─────────────────────────────────────────────
private val DeepVoid = Color(0xFF080604)
private val WornLeather = Color(0xFF1A1510)
private val BurnishedGold = Color(0xFFCCA855)
private val TorchAmber = Color(0xFFBBA060)
private val BoneWhite = Color(0xFFD8CCAA)
private val AshGray = Color(0xFF5A5040)
private val BrightText = Color(0xFFCCCCCC)

// ─────────────────────────────────────────────
// Stone frame
// ─────────────────────────────────────────────
private fun DrawScope.drawStoneFrame(borderPx: Float) {
    val w = size.width; val h = size.height
    drawRect(StoneTheme.frameMid, Offset.Zero, Size(w, borderPx))
    drawRect(StoneTheme.frameMid, Offset(0f, h - borderPx), Size(w, borderPx))
    drawRect(StoneTheme.frameMid, Offset(0f, borderPx), Size(borderPx, h - borderPx * 2))
    drawRect(StoneTheme.frameMid, Offset(w - borderPx, borderPx), Size(borderPx, h - borderPx * 2))
    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(w, 0f), 1f)
    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(0f, h), 1f)
    drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
    drawLine(StoneTheme.innerShadow, Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
    drawLine(StoneTheme.innerShadow, Offset(borderPx, h - borderPx), Offset(w - borderPx, h - borderPx), 1f)
    drawLine(StoneTheme.innerShadow, Offset(w - borderPx, borderPx), Offset(w - borderPx, h - borderPx), 1f)
    drawLine(StoneTheme.runeGlow, Offset(borderPx, borderPx), Offset(w - borderPx, borderPx), 1f)
    drawLine(StoneTheme.runeGlow, Offset(borderPx, borderPx), Offset(borderPx, h - borderPx), 1f)
    val rivetRadius = 3f; val rivetOffset = borderPx / 2f
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(rivetOffset, rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(w - rivetOffset, rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(rivetOffset, h - rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(w - rivetOffset, h - rivetOffset))
}

// ─────────────────────────────────────────────
// Help section data
// ─────────────────────────────────────────────
private data class HelpSection(
    val icon: org.jetbrains.compose.resources.DrawableResource,
    val title: String,
    val entries: List<HelpEntry>
)

private data class HelpEntry(
    val heading: String,
    val body: String,
    val icon: org.jetbrains.compose.resources.DrawableResource? = null
)

private val helpSections = listOf(
    HelpSection(MudIcons.Attack, "Getting Started", listOf(
        HelpEntry("Rooms & Movement", "You stand in a room. Tap the directional arrows to move through exits. Grayed-out directions have no passage. The room name and description appear at the top of the screen."),
        HelpEntry("The Minimap", "A floating map shows rooms you have visited. Fog-of-war hides unexplored areas. Tap the map toolbar button for a full-screen view.", icon = MudIcons.Map),
        HelpEntry("NPCs", "Non-player characters inhabit rooms. Hostile NPCs (monsters) can be fought. Friendly NPCs like vendors and trainers offer services \u2014 tap their sprite to interact."),
        HelpEntry("Ground Items", "Loot and coins dropped by slain foes appear at the bottom of the room. Tap items or coin piles to pick them up.")
    )),
    HelpSection(MudIcons.Attack, "Combat", listOf(
        HelpEntry("Selecting a Target", "Tap a hostile NPC\u2019s sprite to select it as your target. A red border highlights the selected NPC."),
        HelpEntry("Attack Mode", "Tap the crossed-swords button to toggle attack mode. While active, your character automatically attacks the selected target each combat tick (1.5 seconds).", icon = MudIcons.Attack),
        HelpEntry("Melee Damage", "Melee damage is based on your Strength, equipped weapon, and a random roll. Armor reduces incoming damage."),
        HelpEntry("Death & Respawn", "If your HP reaches zero, you die and respawn at the town entrance with full health. You keep your inventory and equipment."),
        HelpEntry("NPC Context Menu", "Long-press a hostile NPC for quick actions: attack, track, kick, or cast a spell directly on it."),
        HelpEntry("Monster Abilities", "Bosses and tougher monsters have special abilities — area attacks that hit everyone in the room, and dramatic phase changes at low health. High stats let you partially resist some abilities. Watch the combat log and adapt.")
    )),
    HelpSection(MudIcons.SchoolMage, "Spells & Magic", listOf(
        HelpEntry("Spell Schools", "There are five schools of magic: Mage, Priest, Druid, Kai, and Bard. Your class determines which schools you can learn."),
        HelpEntry("Spell Slots", "You have four spell slots in the spell bar. Tap a slot to ready that spell, or long-press to assign a different spell from the spell picker."),
        HelpEntry("Casting", "A readied spell auto-casts on your target during the next combat tick. Some spells target enemies, others heal you or buff allies."),
        HelpEntry("Ally & Group Spells", "Healers can cast on party members — tap an ally’s sprite or their HUD bar to target them. Group spells like Prayer of Mending and War Hymn heal or buff every party member in the room at once, at reduced power per target."),
        HelpEntry("Mana", "Spells cost mana (MP). When you run low, use the Meditate skill to regenerate MP, or drink a mana potion.", icon = MudIcons.Meditate)
    )),
    HelpSection(MudIcons.Bash, "Skills", listOf(
        HelpEntry("Bash", "A powerful strike that deals bonus damage and has a 50% chance to stun the target for 2 ticks, preventing it from attacking. Has a cooldown between uses.", icon = MudIcons.Bash),
        HelpEntry("Kick", "Knock an NPC into an adjacent room. Choose a direction \u2014 the NPC flies through the exit and is stunned for 2 ticks upon landing.", icon = MudIcons.Kick),
        HelpEntry("Sneak", "Enter stealth. While hidden, your next attack becomes a backstab dealing massive damage. Moving or being hit breaks stealth.", icon = MudIcons.Sneak),
        HelpEntry("Meditate", "Sit and regenerate mana over time. Breaks if you enter combat or move.", icon = MudIcons.Meditate),
        HelpEntry("Track", "Locate an NPC that has fled or wandered away. Shows which direction they went.", icon = MudIcons.Track),
        HelpEntry("Pick Lock", "Attempt to unlock a locked exit or interactable. Requires the Pick Lock skill.", icon = MudIcons.PickLock),
        HelpEntry("Rest", "Sit and regenerate HP over time. Breaks if you enter combat or move.", icon = MudIcons.Rest),
        HelpEntry("Cooldowns", "Most skills have a cooldown (shown as a dimmed button). Wait for the cooldown to expire before using the skill again.")
    )),
    HelpSection(MudIcons.Inventory, "Inventory & Equipment", listOf(
        HelpEntry("Inventory", "Open the backpack icon to view your carried items. Tap an item for options: equip, use, or drop.", icon = MudIcons.Inventory),
        HelpEntry("Equipment", "Open the shield icon to view your paperdoll. Tap a slot to see the equipped item\u2019s stats. Equip gear from your inventory to boost your combat power.", icon = MudIcons.Equipment),
        HelpEntry("Equipment Slots", "Head, body, arms, legs, feet, weapon, offhand, ring, neck, and back. Each slot accepts specific item types."),
        HelpEntry("Consumables", "Potions and food can be used from your inventory. Health potions restore HP, mana potions restore MP. Some items have cooldowns.")
    )),
    HelpSection(MudIcons.Vendor, "Vendors & Trainers", listOf(
        HelpEntry("Vendors", "Approach a vendor NPC and tap their sprite to open the shop. Buy tab shows their wares; sell tab lets you sell your items.", icon = MudIcons.Vendor),
        HelpEntry("Coins", "There are four coin types: copper, silver, gold, and platinum. 100 copper = 1 silver, 100 silver = 1 gold, 100 gold = 1 platinum."),
        HelpEntry("Trainers", "Visit a trainer NPC to level up when you have enough XP. Leveling grants stat points and may unlock new spells or skills.", icon = MudIcons.Trainer),
        HelpEntry("Spending CP", "Class Points (CP) are earned on level-up. Spend them at trainers to improve your abilities.")
    )),
    HelpSection(MudIcons.Crafter, "Crafting", listOf(
        HelpEntry("Crafter NPCs", "Look for crafter NPCs in town. Tap their sprite to open the crafting panel.", icon = MudIcons.Crafter),
        HelpEntry("Recipes", "The crafter shows available recipes. Each recipe requires specific materials and produces a specific item."),
        HelpEntry("Materials", "Gather crafting materials by defeating monsters or buying from vendors. Check a recipe\u2019s requirements to see what you need."),
        HelpEntry("Crafting an Item", "Select a recipe, ensure you have the required materials in your inventory, and tap Craft. The crafter produces the item instantly.")
    )),
    HelpSection(MudIcons.InteractDefault, "Party Play", listOf(
        HelpEntry("Forming a Party", "Type /invite <name> (or use the invite field in the party panel) to invite another player \u2014 invites work across rooms and show your level and class. Up to 4 players per party."),
        HelpEntry("The Party Panel", "Tap the P button in the toolbar to open the party panel: see every member\u2019s HP/MP, follow a member, promote a new leader, or leave the party."),
        HelpEntry("Party HUD", "While in a party, member health bars float in the top-left corner. Members in a different room appear dimmed with an \u201celsewhere\u201d label."),
        HelpEntry("XP & Loot", "Party members in the same room earn bonus XP from kills. All loot and coins drop to the ground \u2014 first come, first served, no assignments."),
        HelpEntry("Party Chat", "Start a message with /p to talk only to your party. Party chat appears in cyan-green, and the say bar shows a PARTY badge while you\u2019re typing one."),
        HelpEntry("Following & the Map", "Use /follow <name> to automatically walk with a party member. Party members appear as colored dots on your minimap.")
    )),
    HelpSection(MudIcons.InteractDefault, "Chat & Social", listOf(
        HelpEntry("Say", "Type in the text bar at the bottom and press Enter to speak to all players in your current room."),
        HelpEntry("Slash Commands", "Start a message with / to use commands \u2014 an autocomplete menu suggests matches as you type. /help lists every command."),
        HelpEntry("Tells", "/tell <name> <message> whispers privately to a player anywhere in the world \u2014 no need to share a room."),
        HelpEntry("Other Players", "You can see other players\u2019 sprites in the room. Their names appear below their character.")
    )),
    HelpSection(MudIcons.Help, "Stats & Character", listOf(
        HelpEntry("Attributes", "Six stats govern your abilities: Strength (melee damage), Agility (dodge/speed), Intellect (spell power), Willpower (mana pool), Health (HP pool), Charm (vendor prices)."),
        HelpEntry("Classes", "Your class determines your skills, spells, and stat requirements. Warriors excel at melee, Mages at spells, Rogues at stealth, and hybrid classes blend multiple roles."),
        HelpEntry("Races", "Six races are available, each with stat modifiers. Choose a race that complements your class."),
        HelpEntry("Leveling", "Gain XP by defeating monsters. When you have enough XP, visit a trainer to level up. Each level grants stats and may unlock new abilities."),
        HelpEntry("Character Sheet", "Tap the HP/MP bars to open your full character sheet. View stats, equipment, active effects, skills, spells, and coins.")
    )),
    HelpSection(MudIcons.Map, "Map & Exploration", listOf(
        HelpEntry("Minimap", "The floating minimap shows nearby rooms. Rooms you\u2019ve visited are revealed; unexplored rooms remain hidden.", icon = MudIcons.Map),
        HelpEntry("Full Map", "Tap the map toolbar button to open a full-screen map of all discovered rooms."),
        HelpEntry("Locked Exits", "Some exits are locked. Use the Pick Lock skill to attempt to open them. A lock icon appears on locked directions.", icon = MudIcons.PickLock),
        HelpEntry("Room Features", "Some rooms have special features: treasure drops, monster spawns, magical effects, or teleporters. Icons on the room overlay indicate what\u2019s available.")
    ))
)

// ─────────────────────────────────────────────
// HelpPanel composable
// ─────────────────────────────────────────────
@Composable
fun HelpPanel(
    onClose: () -> Unit
) {
    var expandedIndex by remember { mutableIntStateOf(-1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* consume backdrop touches */ }
            .padding(8.dp)
    ) {
        val borderPx = 4.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawStoneFrame(borderPx.toPx()) }
                .padding(borderPx)
                .background(
                    Brush.verticalGradient(
                        listOf(WornLeather, Color(0xFF100E0B), DeepVoid, Color(0xFF100E0B), WornLeather)
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* block backdrop dismiss */ }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            painter = painterResource(MudIcons.Help),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Text(
                            "Adventurer\u2019s Tome",
                            color = BurnishedGold,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    // Close button
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .background(
                                Brush.verticalGradient(
                                    listOf(StoneTheme.frameLight, StoneTheme.frameDark)
                                ),
                                RoundedCornerShape(3.dp)
                            )
                            .drawBehind {
                                val w = size.width; val h = size.height
                                drawLine(StoneTheme.frameLight.copy(alpha = 0.5f), Offset(0f, 0f), Offset(w, 0f), 1f)
                                drawLine(StoneTheme.frameLight.copy(alpha = 0.5f), Offset(0f, 0f), Offset(0f, h), 1f)
                                drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                                drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
                            }
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        CloseIcon(color = BoneWhite)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Gold ornamental line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, BurnishedGold.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable accordion sections
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    helpSections.forEachIndexed { index, section ->
                        val isExpanded = expandedIndex == index
                        AccordionSection(
                            icon = section.icon,
                            title = section.title,
                            isExpanded = isExpanded,
                            entries = section.entries,
                            onClick = {
                                expandedIndex = if (isExpanded) -1 else index
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccordionSection(
    icon: org.jetbrains.compose.resources.DrawableResource,
    title: String,
    isExpanded: Boolean,
    entries: List<HelpEntry>,
    onClick: () -> Unit
) {
    Column {
        // Header row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF14110E), DeepVoid)
                    ),
                    RoundedCornerShape(6.dp)
                )
                .drawBehind {
                    val w = size.width; val h = size.height
                    drawLine(AshGray.copy(alpha = 0.15f), Offset(0f, 0f), Offset(w, 0f), 1f)
                    drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                }
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                color = BoneWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (isExpanded) CollapseArrow(color = AshGray) else ExpandArrow(color = AshGray)
        }

        // Expandable body
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepVoid)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                entries.forEach { entry ->
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (entry.icon != null) {
                                Image(
                                    painter = painterResource(entry.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = entry.heading,
                                color = TorchAmber,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = entry.body,
                            color = BrightText,
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
