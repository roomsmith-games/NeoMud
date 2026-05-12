package com.neomud.client.ui.components

import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neomud.client.ui.theme.StoneTheme
import org.jetbrains.compose.resources.painterResource
import com.neomud.shared.model.EquipmentSlots
import com.neomud.shared.model.InventoryItem
import com.neomud.shared.model.Item

// ─────────────────────────────────────────────
// Palette: forged iron, worn leather, torchlight
// ─────────────────────────────────────────────
private val DeepVoid = Color(0xFF080604)
private val IronDark = Color(0xFF0D0B09)
private val WornLeather = Color(0xFF1A1510)
private val BurnishedGold = Color(0xFFCCA855)
private val TorchAmber = Color(0xFFBBA060)
private val EmberOrange = Color(0xFFAA6B3A)
private val FrostSteel = Color(0xFF7090AA)
private val VerdantUpgrade = Color(0xFF44CC55)
private val CrimsonDowngrade = Color(0xFFCC4444)
private val BoneWhite = Color(0xFFD8CCAA)
private val AshGray = Color(0xFF5A5040)
private val SlotVoid = Color(0xFF0A0806)
private val FilledSlotEdge = Color(0xFF7A6545)
private val EmptySlotEdge = Color(0xFF2A2218)
private val HighlightGold = Color(0xFFFFD700)

// Slot placeholder icons — shown when slot is empty
private val SLOT_ICONS = mapOf(
    EquipmentSlots.HEAD to "H",
    EquipmentSlots.NECK to "N",
    EquipmentSlots.WEAPON to "W",
    EquipmentSlots.CHEST to "C",
    EquipmentSlots.SHIELD to "S",
    EquipmentSlots.HANDS to "G",
    EquipmentSlots.RING to "R",
    EquipmentSlots.BACK to "B",
    EquipmentSlots.LEGS to "L",
    EquipmentSlots.FEET to "F",
)

private fun itemStatSummary(item: Item?): String? {
    if (item == null) return null
    val parts = mutableListOf<String>()
    if (item.slot == EquipmentSlots.WEAPON) {
        parts.add("DMG +${item.damageBonus}")
        if (item.damageRange > 0) parts.add("(1-${item.damageRange})")
    } else {
        if (item.armorValue > 0) parts.add("ARM ${item.armorValue}")
        if (item.damageBonus > 0) parts.add("DMG +${item.damageBonus}")
    }
    return parts.joinToString(" ").ifEmpty { null }
}

private fun primaryStatValue(item: Item?, slot: String): Int {
    if (item == null) return 0
    return if (slot == EquipmentSlots.WEAPON) item.damageBonus else item.armorValue
}

private fun primaryStatLabel(slot: String): String {
    return if (slot == EquipmentSlots.WEAPON) "DMG" else "ARM"
}

// ─────────────────────────────────────────────
// Stone frame drawing — matches ThemedFrame style
// with beveled edges, corner rivets, inner glow
// ─────────────────────────────────────────────
private fun DrawScope.drawStoneFrame(borderPx: Float) {
    val w = size.width
    val h = size.height

    // Frame body fill
    drawRect(StoneTheme.frameMid, Offset.Zero, Size(w, borderPx))
    drawRect(StoneTheme.frameMid, Offset(0f, h - borderPx), Size(w, borderPx))
    drawRect(StoneTheme.frameMid, Offset(0f, borderPx), Size(borderPx, h - borderPx * 2))
    drawRect(StoneTheme.frameMid, Offset(w - borderPx, borderPx), Size(borderPx, h - borderPx * 2))

    // Outer bevel highlight (top + left = light)
    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(0f, h), strokeWidth = 1f)

    // Outer bevel shadow (bottom + right = dark)
    drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), strokeWidth = 1f)
    drawLine(StoneTheme.innerShadow, Offset(w - 1f, 0f), Offset(w - 1f, h), strokeWidth = 1f)

    // Inner edge shadow
    drawLine(StoneTheme.innerShadow, Offset(borderPx, h - borderPx), Offset(w - borderPx, h - borderPx), strokeWidth = 1f)
    drawLine(StoneTheme.innerShadow, Offset(w - borderPx, borderPx), Offset(w - borderPx, h - borderPx), strokeWidth = 1f)

    // Inner glow — runic green
    drawLine(StoneTheme.runeGlow, Offset(borderPx, borderPx), Offset(w - borderPx, borderPx), strokeWidth = 1f)
    drawLine(StoneTheme.runeGlow, Offset(borderPx, borderPx), Offset(borderPx, h - borderPx), strokeWidth = 1f)

    // Corner rivets
    val rivetRadius = 3f
    val rivetOffset = borderPx / 2f
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(rivetOffset, rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(w - rivetOffset, rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(rivetOffset, h - rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(w - rivetOffset, h - rivetOffset))
}

// Body silhouette drawing — faint humanoid outline behind paperdoll
private fun DrawScope.drawBodySilhouette() {
    val cx = size.width / 2f
    val color = AshGray.copy(alpha = 0.08f)
    val lineWidth = 2f

    // Head circle
    drawCircle(color, radius = 18f, center = Offset(cx, 26f))

    // Neck
    drawLine(color, Offset(cx, 44f), Offset(cx, 55f), strokeWidth = lineWidth)

    // Shoulders
    drawLine(color, Offset(cx - 60f, 70f), Offset(cx + 60f, 70f), strokeWidth = lineWidth)

    // Torso
    drawLine(color, Offset(cx - 30f, 55f), Offset(cx - 60f, 70f), strokeWidth = lineWidth)
    drawLine(color, Offset(cx + 30f, 55f), Offset(cx + 60f, 70f), strokeWidth = lineWidth)
    drawLine(color, Offset(cx - 40f, 70f), Offset(cx - 30f, 170f), strokeWidth = lineWidth)
    drawLine(color, Offset(cx + 40f, 70f), Offset(cx + 30f, 170f), strokeWidth = lineWidth)

    // Arms
    drawLine(color, Offset(cx - 60f, 70f), Offset(cx - 80f, 140f), strokeWidth = lineWidth)
    drawLine(color, Offset(cx + 60f, 70f), Offset(cx + 80f, 140f), strokeWidth = lineWidth)

    // Waist
    drawLine(color, Offset(cx - 30f, 170f), Offset(cx + 30f, 170f), strokeWidth = lineWidth)

    // Legs
    drawLine(color, Offset(cx - 20f, 170f), Offset(cx - 25f, 250f), strokeWidth = lineWidth)
    drawLine(color, Offset(cx + 20f, 170f), Offset(cx + 25f, 250f), strokeWidth = lineWidth)

    // Feet
    drawLine(color, Offset(cx - 25f, 250f), Offset(cx - 35f, 260f), strokeWidth = lineWidth)
    drawLine(color, Offset(cx + 25f, 250f), Offset(cx + 35f, 260f), strokeWidth = lineWidth)
}

// ═════════════════════════════════════════════
// MAIN PANEL
// ═════════════════════════════════════════════
@Composable
fun EquipmentPanel(
    inventory: List<InventoryItem>,
    equipment: Map<String, String>,
    itemCatalog: Map<String, Item>,
    onEquipItem: (String, String) -> Unit,
    onUnequipItem: (String) -> Unit,
    onClose: () -> Unit
) {
    var selectedBagItemId by remember { mutableStateOf<String?>(null) }
    var selectedEquippedSlot by remember { mutableStateOf<String?>(null) }
    val pulseTransition = rememberInfiniteTransition(label = "slotPulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { selectedBagItemId = null; selectedEquippedSlot = null }
            .padding(8.dp)
    ) {
        // Stone frame with beveled edges and corner rivets
        val borderPx = 4.dp
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind { drawStoneFrame(borderPx.toPx()) }
                .padding(borderPx)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            WornLeather,
                            Color(0xFF100E0B),
                            DeepVoid,
                            Color(0xFF100E0B),
                            WornLeather
                        )
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
                // ─── Header ───
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(MudIcons.Equipment),
                            contentDescription = "Equipment",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Equipment",
                            color = BurnishedGold,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val equippedItems = equipment.values.mapNotNull { itemCatalog[it] }
                    val totalArmor = equippedItems.sumOf { it.armorValue }
                    val totalDmgBonus = equippedItems.sumOf { it.damageBonus }
                    val weaponRange = equipment[EquipmentSlots.WEAPON]
                        ?.let { itemCatalog[it] }?.damageRange ?: 0

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (totalArmor > 0) {
                            StatBadge("ARM $totalArmor", FrostSteel)
                        }
                        if (totalDmgBonus > 0 || weaponRange > 0) {
                            val dmgText = if (weaponRange > 0) "DMG +$totalDmgBonus (1-$weaponRange)"
                            else "DMG +$totalDmgBonus"
                            StatBadge(dmgText, EmberOrange)
                        }
                        // Close button — iron rivet
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(StoneTheme.frameLight, StoneTheme.frameDark)
                                    ),
                                    RoundedCornerShape(4.dp)
                                )
                                .drawBehind {
                                    // Bevel highlight
                                    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(size.width, 0f), 1f)
                                    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(0f, size.height), 1f)
                                    drawLine(StoneTheme.innerShadow, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
                                    drawLine(StoneTheme.innerShadow, Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 1f)
                                }
                                .clickable(onClick = onClose),
                            contentAlignment = Alignment.Center
                        ) {
                            CloseIcon(color = BoneWhite)
                        }
                    }
                }

                // Gold ornamental line
                Spacer(modifier = Modifier.height(6.dp))
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

                // ─── Scrollable content ───
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val selectedItem = selectedBagItemId?.let { itemCatalog[it] }
                    val selectedBagSlot = selectedItem?.slot?.ifEmpty { null }
                        ?: selectedBagItemId?.let { id ->
                            inventory.find { it.itemId == id }?.slot?.ifEmpty { null }
                        }

                    // ═══════════════════════════════
                    // PAPERDOLL — body silhouette
                    // ═══════════════════════════════
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawBehind {
                                drawBodySilhouette()
                                // Vertical spine
                                val cx = size.width / 2f
                                drawLine(
                                    AshGray.copy(alpha = 0.15f),
                                    Offset(cx, 0f),
                                    Offset(cx, size.height),
                                    strokeWidth = 1f
                                )
                            },
                        contentAlignment = Alignment.TopCenter
                    ) {
                        // Callback: tapping a paperdoll slot selects it for inspection
                        val onSlotTapped: (String) -> Unit = { slot ->
                            selectedEquippedSlot = if (selectedEquippedSlot == slot) null else slot
                            selectedBagItemId = null // clear bag selection when inspecting equipped
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // Head
                            PaperdollSlot(
                                EquipmentSlots.HEAD, equipment[EquipmentSlots.HEAD], itemCatalog,
                                comparisonHighlight = selectedBagSlot == EquipmentSlots.HEAD,
                                inspectionHighlight = selectedEquippedSlot == EquipmentSlots.HEAD,
                                pulseAlpha = pulseAlpha,
                                onSlotTapped = onSlotTapped
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            // Neck (smaller)
                            PaperdollSlot(
                                EquipmentSlots.NECK, equipment[EquipmentSlots.NECK], itemCatalog,
                                comparisonHighlight = selectedBagSlot == EquipmentSlots.NECK,
                                inspectionHighlight = selectedEquippedSlot == EquipmentSlots.NECK,
                                pulseAlpha = pulseAlpha,
                                onSlotTapped = onSlotTapped,
                                slotSize = 48
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            // Weapon / Chest / Shield — torso row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                                    PaperdollSlot(
                                        EquipmentSlots.WEAPON, equipment[EquipmentSlots.WEAPON], itemCatalog,
                                        comparisonHighlight = selectedBagSlot == EquipmentSlots.WEAPON,
                                        inspectionHighlight = selectedEquippedSlot == EquipmentSlots.WEAPON,
                                        pulseAlpha = pulseAlpha,
                                        onSlotTapped = onSlotTapped
                                    )
                                }
                                Box(contentAlignment = Alignment.Center) {
                                    PaperdollSlot(
                                        EquipmentSlots.CHEST, equipment[EquipmentSlots.CHEST], itemCatalog,
                                        comparisonHighlight = selectedBagSlot == EquipmentSlots.CHEST,
                                        inspectionHighlight = selectedEquippedSlot == EquipmentSlots.CHEST,
                                        pulseAlpha = pulseAlpha,
                                        onSlotTapped = onSlotTapped,
                                        slotSize = 68
                                    )
                                }
                                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                    PaperdollSlot(
                                        EquipmentSlots.SHIELD, equipment[EquipmentSlots.SHIELD], itemCatalog,
                                        comparisonHighlight = selectedBagSlot == EquipmentSlots.SHIELD,
                                        inspectionHighlight = selectedEquippedSlot == EquipmentSlots.SHIELD,
                                        pulseAlpha = pulseAlpha,
                                        onSlotTapped = onSlotTapped
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))

                            // Back (cloak) — between torso and arms
                            PaperdollSlot(
                                EquipmentSlots.BACK, equipment[EquipmentSlots.BACK], itemCatalog,
                                comparisonHighlight = selectedBagSlot == EquipmentSlots.BACK,
                                inspectionHighlight = selectedEquippedSlot == EquipmentSlots.BACK,
                                pulseAlpha = pulseAlpha,
                                onSlotTapped = onSlotTapped,
                                slotSize = 48
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            // Hands / Ring — arms row
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(40.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PaperdollSlot(
                                    EquipmentSlots.HANDS, equipment[EquipmentSlots.HANDS], itemCatalog,
                                    comparisonHighlight = selectedBagSlot == EquipmentSlots.HANDS,
                                    inspectionHighlight = selectedEquippedSlot == EquipmentSlots.HANDS,
                                    pulseAlpha = pulseAlpha,
                                    onSlotTapped = onSlotTapped,
                                    slotSize = 50
                                )
                                PaperdollSlot(
                                    EquipmentSlots.RING, equipment[EquipmentSlots.RING], itemCatalog,
                                    comparisonHighlight = selectedBagSlot == EquipmentSlots.RING,
                                    inspectionHighlight = selectedEquippedSlot == EquipmentSlots.RING,
                                    pulseAlpha = pulseAlpha,
                                    onSlotTapped = onSlotTapped,
                                    slotSize = 44
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))

                            // Legs
                            PaperdollSlot(
                                EquipmentSlots.LEGS, equipment[EquipmentSlots.LEGS], itemCatalog,
                                comparisonHighlight = selectedBagSlot == EquipmentSlots.LEGS,
                                inspectionHighlight = selectedEquippedSlot == EquipmentSlots.LEGS,
                                pulseAlpha = pulseAlpha,
                                onSlotTapped = onSlotTapped
                            )
                            Spacer(modifier = Modifier.height(2.dp))

                            // Feet
                            PaperdollSlot(
                                EquipmentSlots.FEET, equipment[EquipmentSlots.FEET], itemCatalog,
                                comparisonHighlight = selectedBagSlot == EquipmentSlots.FEET,
                                inspectionHighlight = selectedEquippedSlot == EquipmentSlots.FEET,
                                pulseAlpha = pulseAlpha,
                                onSlotTapped = onSlotTapped,
                                slotSize = 50
                            )
                        }
                    }

                    // ═══════════════════════════════
                    // EQUIPPED ITEM INSPECTION
                    // ═══════════════════════════════
                    if (selectedEquippedSlot != null) {
                        val equippedItemId = equipment[selectedEquippedSlot!!]
                        val equippedItem = equippedItemId?.let { itemCatalog[it] }
                        if (equippedItem != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            EquippedItemInspectionPanel(
                                item = equippedItem,
                                slot = selectedEquippedSlot!!,
                                onUnequip = {
                                    onUnequipItem(selectedEquippedSlot!!)
                                    selectedEquippedSlot = null
                                },
                                onDismiss = { selectedEquippedSlot = null }
                            )
                        }
                    }

                    // ═══════════════════════════════
                    // COMPARISON PANEL (bag item vs equipped)
                    // ═══════════════════════════════
                    if (selectedBagItemId != null && selectedItem != null && selectedBagSlot != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        ComparisonPanel(
                            bagItem = selectedItem,
                            equippedItem = equipment[selectedBagSlot]?.let { itemCatalog[it] },
                            slot = selectedBagSlot,
                            onEquip = {
                                onEquipItem(selectedBagItemId!!, selectedBagSlot)
                                selectedBagItemId = null
                            },
                            onCancel = { selectedBagItemId = null }
                        )
                    }

                    // ═══════════════════════════════
                    // RUNIC DIVIDER
                    // ═══════════════════════════════
                    Spacer(modifier = Modifier.height(8.dp))
                    RunicDivider()
                    Spacer(modifier = Modifier.height(8.dp))

                    // ═══════════════════════════════
                    // EQUIPPABLE BAG ITEMS
                    // ═══════════════════════════════
                    Text(
                        "Equippable Items",
                        color = TorchAmber,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Text(
                        "Tap to compare - tap again to deselect",
                        color = AshGray,
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(5.dp))

                    val equippableItems = inventory.filter { invItem ->
                        if (invItem.equipped) return@filter false
                        val catalogItem = itemCatalog[invItem.itemId]
                        if (catalogItem != null) {
                            catalogItem.slot.isNotEmpty()
                        } else {
                            invItem.slot.isNotEmpty()
                        }
                    }

                    if (equippableItems.isEmpty()) {
                        Text(
                            "No equippable items in bag.",
                            color = AshGray,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        equippableItems.forEach { invItem ->
                            val item = itemCatalog[invItem.itemId]
                            val slot = item?.slot?.ifEmpty { null } ?: invItem.slot
                            val equippedItemId = equipment[slot]
                            val equippedItem = equippedItemId?.let { itemCatalog[it] }
                            val isSelected = invItem.itemId == selectedBagItemId

                            EquippableBagRow(
                                invItem = invItem,
                                item = item,
                                slot = slot,
                                equippedItem = equippedItem,
                                selected = isSelected,
                                onSelect = {
                                    selectedBagItemId = if (isSelected) null else invItem.itemId
                                }
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Stat badge — beveled inset
// ─────────────────────────────────────────────
@Composable
private fun StatBadge(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .background(DeepVoid, RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp)
    )
}

// ─────────────────────────────────────────────
// Runic divider — ornamental separator
// ─────────────────────────────────────────────
@Composable
private fun RunicDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, AshGray.copy(alpha = 0.4f)))
        ))
        Text(
            "-- * --",
            color = AshGray.copy(alpha = 0.5f),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(
            Brush.horizontalGradient(listOf(AshGray.copy(alpha = 0.4f), Color.Transparent))
        ))
    }
}

// ═════════════════════════════════════════════
// PAPERDOLL SLOT — beveled stone recess with
// placeholder icon, item name, pulsing highlight
// ═════════════════════════════════════════════
@Composable
private fun PaperdollSlot(
    slot: String,
    equippedItemId: String?,
    itemCatalog: Map<String, Item>,
    comparisonHighlight: Boolean,
    inspectionHighlight: Boolean,
    pulseAlpha: Float,
    onSlotTapped: (String) -> Unit,
    slotSize: Int = 58
) {
    val serverBaseUrl = LocalServerBaseUrl.current
    val item = equippedItemId?.let { itemCatalog[it] }
    val context = LocalPlatformContext.current

    val outerBorder = when {
        inspectionHighlight -> TorchAmber.copy(alpha = pulseAlpha)
        comparisonHighlight -> HighlightGold.copy(alpha = pulseAlpha)
        equippedItemId != null -> FilledSlotEdge
        else -> EmptySlotEdge
    }
    val outerWidth = if (inspectionHighlight || comparisonHighlight) 2.dp else 1.dp

    val innerBg = if (equippedItemId != null)
        Brush.linearGradient(
            listOf(Color(0xFF1E1810), Color(0xFF120E0A)),
            start = Offset(0f, 0f),
            end = Offset(slotSize.toFloat(), slotSize.toFloat())
        )
    else
        Brush.linearGradient(
            listOf(Color(0xFF0C0A08), Color(0xFF060504)),
            start = Offset(0f, 0f),
            end = Offset(slotSize.toFloat(), slotSize.toFloat())
        )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Double-border: outer edge + inner inset shadow
        Box(
            modifier = Modifier
                .size(slotSize.dp)
                .border(outerWidth, outerBorder, RoundedCornerShape(6.dp))
                .padding(2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(innerBg)
                .then(
                    if (equippedItemId != null) Modifier.clickable { onSlotTapped(slot) }
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (equippedItemId != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(spriteUrl(serverBaseUrl, equippedItemId))
                        .crossfade(200)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = displayName(item, equippedItemId),
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size((slotSize - 12).dp)
                        .padding(1.dp)
                )
            } else {
                // Empty slot — show placeholder icon
                Text(
                    text = SLOT_ICONS[slot] ?: "?",
                    fontSize = (slotSize / 3).sp,
                    color = EmptySlotEdge.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        }
        // Slot label or item name
        if (item != null) {
            Text(
                text = item.name,
                fontSize = 9.sp,
                color = BoneWhite,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = (slotSize + 10).dp)
            )
        } else {
            Text(
                text = slot.replaceFirstChar { it.uppercase() },
                fontSize = 9.sp,
                color = when {
                    inspectionHighlight -> TorchAmber.copy(alpha = pulseAlpha * 0.8f)
                    comparisonHighlight -> HighlightGold.copy(alpha = pulseAlpha * 0.8f)
                    else -> AshGray
                },
                textAlign = TextAlign.Center
            )
        }
        // Stat inscription
        val stats = itemStatSummary(item)
        if (stats != null) {
            Text(
                text = stats,
                fontSize = 8.sp,
                color = TorchAmber,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}

// ═════════════════════════════════════════════
// EQUIPPED ITEM INSPECTION — anvil examination
// ═════════════════════════════════════════════
@Composable
private fun EquippedItemInspectionPanel(
    item: Item,
    slot: String,
    onUnequip: () -> Unit,
    onDismiss: () -> Unit
) {
    val serverBaseUrl = LocalServerBaseUrl.current
    val context = LocalPlatformContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Stone frame with amber glow
                val bPx = 3f
                val w = size.width; val h = size.height
                drawRect(StoneTheme.frameMid, Offset.Zero, Size(w, bPx))
                drawRect(StoneTheme.frameMid, Offset(0f, h - bPx), Size(w, bPx))
                drawRect(StoneTheme.frameMid, Offset(0f, bPx), Size(bPx, h - bPx * 2))
                drawRect(StoneTheme.frameMid, Offset(w - bPx, bPx), Size(bPx, h - bPx * 2))
                // Amber inner glow instead of green — inspection warmth
                val amber = TorchAmber.copy(alpha = 0.4f)
                drawLine(amber, Offset(bPx, bPx), Offset(w - bPx, bPx), 1f)
                drawLine(amber, Offset(bPx, bPx), Offset(bPx, h - bPx), 1f)
                drawLine(StoneTheme.innerShadow, Offset(bPx, h - bPx), Offset(w - bPx, h - bPx), 1f)
                drawLine(StoneTheme.innerShadow, Offset(w - bPx, bPx), Offset(w - bPx, h - bPx), 1f)
            }
            .padding(3.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1C1610), Color(0xFF0E0B08), Color(0xFF1C1610))
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* block dismiss propagation */ }
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            "${slot.replaceFirstChar { it.uppercase() }} - Inspect",
            color = TorchAmber,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Item sprite + details
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            // Large item icon
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .border(1.dp, FilledSlotEdge, RoundedCornerShape(6.dp))
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF1E1810), Color(0xFF0A0806)),
                            start = Offset(0f, 0f),
                            end = Offset(64f, 64f)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(spriteUrl(serverBaseUrl, item.id))
                        .crossfade(200)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = item.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Stats column
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = BurnishedGold
                )
                Text(
                    slot.replaceFirstChar { it.uppercase() },
                    fontSize = 11.sp,
                    color = AshGray
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Stat rows
                if (item.slot == EquipmentSlots.WEAPON) {
                    if (item.damageBonus > 0) {
                        StatRow("Damage Bonus", "+${item.damageBonus}", EmberOrange)
                    }
                    if (item.damageRange > 0) {
                        StatRow("Damage Range", "1-${item.damageRange}", EmberOrange)
                    }
                }
                if (item.armorValue > 0) {
                    StatRow("Armor", "${item.armorValue}", FrostSteel)
                }
                if (item.levelRequirement > 0) {
                    StatRow("Required Level", "${item.levelRequirement}", AshGray)
                }
                if (item.weight > 0) {
                    StatRow("Weight", "${item.weight}", AshGray)
                }
                if (item.description.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        item.description,
                        fontSize = 10.sp,
                        color = BoneWhite.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            // Dismiss — stone button
            Box(
                modifier = Modifier
                    .drawBehind {
                        val w = size.width; val h = size.height
                        drawRect(Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark)))
                        drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(w, 0f), 1f)
                        drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(0f, h), 1f)
                        drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                        drawLine(StoneTheme.innerShadow, Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
                    }
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", fontSize = 12.sp, color = BoneWhite)
            }
            // Unequip — crimson/amber button
            Box(
                modifier = Modifier
                    .drawBehind {
                        val w = size.width; val h = size.height
                        drawRect(Brush.verticalGradient(listOf(EmberOrange, Color(0xFF884422))))
                        drawLine(EmberOrange.copy(alpha = 0.7f), Offset(0f, 0f), Offset(w, 0f), 1f)
                        drawLine(EmberOrange.copy(alpha = 0.7f), Offset(0f, 0f), Offset(0f, h), 1f)
                        drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                        drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
                    }
                    .clickable(onClick = onUnequip)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Unequip", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun StatRow(label: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = AshGray)
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// ═════════════════════════════════════════════
// COMPARISON PANEL — stone-framed inspection
// ═════════════════════════════════════════════
@Composable
private fun ComparisonPanel(
    bagItem: Item,
    equippedItem: Item?,
    slot: String,
    onEquip: () -> Unit,
    onCancel: () -> Unit
) {
    val label = primaryStatLabel(slot)
    val equippedStat = primaryStatValue(equippedItem, slot)
    val bagStat = primaryStatValue(bagItem, slot)
    val delta = bagStat - equippedStat

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Mini stone frame around comparison
                val bPx = 3f
                val w = size.width; val h = size.height
                drawRect(StoneTheme.frameMid, Offset.Zero, Size(w, bPx))
                drawRect(StoneTheme.frameMid, Offset(0f, h - bPx), Size(w, bPx))
                drawRect(StoneTheme.frameMid, Offset(0f, bPx), Size(bPx, h - bPx * 2))
                drawRect(StoneTheme.frameMid, Offset(w - bPx, bPx), Size(bPx, h - bPx * 2))
                // Inner glow
                drawLine(StoneTheme.runeGlow, Offset(bPx, bPx), Offset(w - bPx, bPx), 1f)
                drawLine(StoneTheme.runeGlow, Offset(bPx, bPx), Offset(bPx, h - bPx), 1f)
            }
            .padding(3.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1C1610), Color(0xFF0E0B08), Color(0xFF1C1610))
                )
            )
            .padding(10.dp)
    ) {
        Text(
            "Compare - ${slot.replaceFirstChar { it.uppercase() }}",
            color = HighlightGold,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Top
        ) {
            // Equipped column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("Equipped", fontSize = 9.sp, color = AshGray)
                Spacer(modifier = Modifier.height(4.dp))
                if (equippedItem != null) {
                    ComparisonItemIcon(equippedItem.id, FilledSlotEdge)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(equippedItem.name, fontSize = 11.sp, color = BoneWhite,
                        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    itemStatSummary(equippedItem)?.let {
                        Text(it, fontSize = 10.sp, color = TorchAmber)
                    }
                    if (equippedItem.levelRequirement > 0)
                        Text("Lv ${equippedItem.levelRequirement}", fontSize = 9.sp, color = AshGray)
                } else {
                    Box(modifier = Modifier.size(44.dp)
                        .background(SlotVoid, RoundedCornerShape(6.dp))
                        .border(1.dp, EmptySlotEdge, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            SLOT_ICONS[slot] ?: "?",
                            fontSize = 16.sp,
                            color = EmptySlotEdge.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("-- empty --", fontSize = 11.sp, color = AshGray)
                }
            }

            // Delta column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 30.dp)
            ) {
                if (bagStat > 0 || equippedStat > 0) {
                    val deltaColor = when {
                        delta > 0 -> VerdantUpgrade
                        delta < 0 -> CrimsonDowngrade
                        else -> AshGray
                    }
                    Text(">", fontSize = 16.sp, color = deltaColor)
                    val deltaText = when {
                        delta > 0 -> "$label +$delta"
                        delta < 0 -> "$label $delta"
                        else -> "same"
                    }
                    Text(deltaText, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = deltaColor)
                    if (slot == EquipmentSlots.WEAPON) {
                        val rangeDelta = bagItem.damageRange - (equippedItem?.damageRange ?: 0)
                        if (rangeDelta != 0)
                            Text(
                                if (rangeDelta > 0) "Range +$rangeDelta" else "Range $rangeDelta",
                                fontSize = 9.sp,
                                color = if (rangeDelta > 0) VerdantUpgrade else CrimsonDowngrade
                            )
                    }
                }
            }

            // New item column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text("New", fontSize = 9.sp, color = AshGray)
                Spacer(modifier = Modifier.height(4.dp))
                ComparisonItemIcon(bagItem.id, HighlightGold)
                Spacer(modifier = Modifier.height(3.dp))
                Text(bagItem.name, fontSize = 11.sp, color = HighlightGold,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                itemStatSummary(bagItem)?.let {
                    Text(it, fontSize = 10.sp, color = TorchAmber)
                }
                if (bagItem.levelRequirement > 0)
                    Text("Lv ${bagItem.levelRequirement}", fontSize = 9.sp, color = AshGray)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons — stone-styled
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            // Cancel — stone button
            Box(
                modifier = Modifier
                    .drawBehind {
                        val w = size.width; val h = size.height
                        drawRect(Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark)))
                        drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(w, 0f), 1f)
                        drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(0f, h), 1f)
                        drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                        drawLine(StoneTheme.innerShadow, Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
                    }
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", fontSize = 12.sp, color = BoneWhite)
            }
            // Equip — verdant or ember depending on upgrade
            val equipColor = if (delta >= 0) VerdantUpgrade else EmberOrange
            Box(
                modifier = Modifier
                    .drawBehind {
                        val w = size.width; val h = size.height
                        val bg = if (delta >= 0)
                            Brush.verticalGradient(listOf(VerdantUpgrade, Color(0xFF228833)))
                        else
                            Brush.verticalGradient(listOf(EmberOrange, Color(0xFF884422)))
                        drawRect(bg)
                        drawLine(equipColor.copy(alpha = 0.7f), Offset(0f, 0f), Offset(w, 0f), 1f)
                        drawLine(equipColor.copy(alpha = 0.7f), Offset(0f, 0f), Offset(0f, h), 1f)
                        drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                        drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
                    }
                    .clickable(onClick = onEquip)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Equip", fontSize = 12.sp, color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ComparisonItemIcon(itemId: String, borderColor: Color) {
    val serverBaseUrl = LocalServerBaseUrl.current
    val context = LocalPlatformContext.current
    Box(
        modifier = Modifier
            .size(44.dp)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF1A1510), Color(0xFF0A0806)),
                    start = Offset(0f, 0f),
                    end = Offset(44f, 44f)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(spriteUrl(serverBaseUrl, itemId))
                .crossfade(200)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(34.dp)
        )
    }
}

// ═════════════════════════════════════════════
// EQUIPPABLE BAG ROW — stone-bordered item row
// ═════════════════════════════════════════════
@Composable
private fun EquippableBagRow(
    invItem: InventoryItem,
    item: Item?,
    slot: String,
    equippedItem: Item?,
    selected: Boolean,
    onSelect: () -> Unit
) {
    val serverBaseUrl = LocalServerBaseUrl.current
    val context = LocalPlatformContext.current

    val bagStat = primaryStatValue(item, slot)
    val equippedStat = primaryStatValue(equippedItem, slot)
    val delta = bagStat - equippedStat

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val w = size.width; val h = size.height
                if (selected) {
                    // Selected: gold beveled edge
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF1E1808), IronDark)))
                    drawLine(HighlightGold.copy(alpha = 0.6f), Offset(0f, 0f), Offset(w, 0f), 1f)
                    drawLine(HighlightGold.copy(alpha = 0.6f), Offset(0f, 0f), Offset(0f, h), 1f)
                    drawLine(HighlightGold.copy(alpha = 0.3f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                    drawLine(HighlightGold.copy(alpha = 0.3f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
                } else {
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF14110E), IronDark)))
                    drawLine(AshGray.copy(alpha = 0.2f), Offset(0f, 0f), Offset(w, 0f), 1f)
                    drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                }
            }
            .clip(RoundedCornerShape(6.dp))
            .clickable { onSelect() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon
        Box(modifier = Modifier.size(38.dp), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(spriteUrl(serverBaseUrl, invItem.itemId))
                    .crossfade(200)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = displayName(item, invItem.itemId),
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(34.dp)
            )
            if (invItem.quantity > 1) {
                Text(
                    "${invItem.quantity}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd)
                        .background(Color(0xCC000000), RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName(item, invItem.itemId), fontSize = 13.sp,
                color = if (selected) HighlightGold else BoneWhite,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(slot.replaceFirstChar { it.uppercase() }, fontSize = 10.sp, color = AshGray)
        }

        val stats = itemStatSummary(item)
        if (stats != null) {
            Text(stats, fontSize = 10.sp, color = TorchAmber, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.width(6.dp))
        }

        if (bagStat > 0 || equippedStat > 0) {
            val (arrow, color) = when {
                delta > 0 -> "^+$delta" to VerdantUpgrade
                delta < 0 -> "v$delta" to CrimsonDowngrade
                else -> "=" to AshGray
            }
            Text(arrow, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
