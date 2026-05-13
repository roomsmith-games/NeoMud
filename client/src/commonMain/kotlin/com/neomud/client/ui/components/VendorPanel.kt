package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import coil3.compose.LocalPlatformContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.shared.model.Coins
import com.neomud.shared.model.EquipmentSlots
import com.neomud.shared.model.InventoryItem
import com.neomud.shared.model.Item
import com.neomud.shared.model.VendorItem
import com.neomud.shared.protocol.ServerMessage

// ─────────────────────────────────────────────
// Palette — shared medieval aesthetic
// ─────────────────────────────────────────────
private val DeepVoid = Color(0xFF080604)
private val WornLeather = Color(0xFF1A1510)
private val BurnishedGold = Color(0xFFCCA855)
private val TorchAmber = Color(0xFFBBA060)
private val EmberOrange = Color(0xFFAA6B3A)
private val BoneWhite = Color(0xFFD8CCAA)
private val AshGray = Color(0xFF5A5040)
private val VerdantUpgrade = Color(0xFF44CC55)
private val CrimsonDowngrade = Color(0xFFCC4444)

private fun primaryStatValue(item: Item?, slot: String): Int {
    if (item == null) return 0
    return if (slot == EquipmentSlots.WEAPON) item.damageBonus else item.armorValue
}

private fun primaryStatLabel(slot: String): String {
    return if (slot == EquipmentSlots.WEAPON) "DMG" else "ARM"
}

// ─────────────────────────────────────────────
// Stone frame drawing
// ─────────────────────────────────────────────
private fun DrawScope.drawStoneFrame(borderPx: Float) {
    val w = size.width
    val h = size.height

    drawRect(StoneTheme.frameMid, Offset.Zero, Size(w, borderPx))
    drawRect(StoneTheme.frameMid, Offset(0f, h - borderPx), Size(w, borderPx))
    drawRect(StoneTheme.frameMid, Offset(0f, borderPx), Size(borderPx, h - borderPx * 2))
    drawRect(StoneTheme.frameMid, Offset(w - borderPx, borderPx), Size(borderPx, h - borderPx * 2))

    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(0f, h), strokeWidth = 1f)
    drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), strokeWidth = 1f)
    drawLine(StoneTheme.innerShadow, Offset(w - 1f, 0f), Offset(w - 1f, h), strokeWidth = 1f)

    drawLine(StoneTheme.innerShadow, Offset(borderPx, h - borderPx), Offset(w - borderPx, h - borderPx), strokeWidth = 1f)
    drawLine(StoneTheme.innerShadow, Offset(w - borderPx, borderPx), Offset(w - borderPx, h - borderPx), strokeWidth = 1f)

    drawLine(StoneTheme.runeGlow, Offset(borderPx, borderPx), Offset(w - borderPx, borderPx), strokeWidth = 1f)
    drawLine(StoneTheme.runeGlow, Offset(borderPx, borderPx), Offset(borderPx, h - borderPx), strokeWidth = 1f)

    val rivetRadius = 3f
    val rivetOffset = borderPx / 2f
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(rivetOffset, rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(w - rivetOffset, rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(rivetOffset, h - rivetOffset))
    drawCircle(StoneTheme.metalGold, rivetRadius, Offset(w - rivetOffset, h - rivetOffset))
}

@Composable
fun VendorPanel(
    vendorInfo: ServerMessage.VendorInfo,
    playerLevel: Int,
    itemCatalog: Map<String, Item>,
    onBuy: (String) -> Unit,
    onSell: (String) -> Unit,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        val borderPx = 4.dp
        Box(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { /* prevent overlay dismiss when clicking panel content */ }
                .drawBehind { drawStoneFrame(borderPx.toPx()) }
                .padding(borderPx)
                .background(
                    Brush.verticalGradient(
                        listOf(WornLeather, Color(0xFF100E0B), DeepVoid, Color(0xFF100E0B), WornLeather)
                    )
                )
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = vendorInfo.vendorName,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = BurnishedGold
                        )
                        if (vendorInfo.hasHaggle) {
                            Text(
                                text = "Haggle Active",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DeepVoid,
                                modifier = Modifier
                                    .background(VerdantUpgrade, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    // Close button — stone beveled
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
                                drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(size.width, 0f), 1f)
                                drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(0f, size.height), 1f)
                                drawLine(StoneTheme.innerShadow, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
                                drawLine(StoneTheme.innerShadow, Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 1f)
                            }
                            .testTag("close_button")
                            .clickable(onClick = onClose),
                        contentAlignment = Alignment.Center
                    ) {
                        CloseIcon(color = BoneWhite)
                    }
                }

                // Player coins
                Text(
                    text = "Your coins: ${vendorInfo.playerCoins.displayString()}",
                    fontSize = 13.sp,
                    color = BurnishedGold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                // Tabs — stone-styled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VendorTab("Buy", selected = selectedTab == 0, onClick = { selectedTab = 0 },
                        modifier = Modifier.weight(1f))
                    VendorTab("Sell", selected = selectedTab == 1, onClick = { selectedTab = 1 },
                        modifier = Modifier.weight(1f))
                }

                // Ornamental divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, BurnishedGold.copy(alpha = 0.4f), Color.Transparent)
                            )
                        )
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedTab == 0) {
                        val equippedBySlot = vendorInfo.playerInventory
                            .filter { it.equipped && it.slot.isNotEmpty() }
                            .mapNotNull { inv -> itemCatalog[inv.itemId]?.let { inv.slot to it } }
                            .toMap()
                        for (vendorItem in vendorInfo.items) {
                            val ownedQty = vendorInfo.playerInventory
                                .filter { it.itemId == vendorItem.item.id }
                                .sumOf { it.quantity }
                            BuyItemRow(
                                vendorItem = vendorItem,
                                playerCoins = vendorInfo.playerCoins,
                                playerLevel = playerLevel,
                                ownedCount = ownedQty,
                                equippedItem = equippedBySlot[vendorItem.item.slot],
                                onBuy = { onBuy(vendorItem.item.id) }
                            )
                        }
                    } else {
                        val sellableItems = vendorInfo.playerInventory.filter { !it.equipped }
                        if (sellableItems.isEmpty()) {
                            Text(
                                text = "You have nothing to sell.",
                                fontSize = 13.sp,
                                color = AshGray,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                        for (invItem in sellableItems) {
                            val item = itemCatalog[invItem.itemId]
                            SellItemRow(
                                inventoryItem = invItem,
                                item = item,
                                playerCharm = vendorInfo.playerCharm,
                                hasHaggle = vendorInfo.hasHaggle,
                                onSell = { onSell(invItem.itemId) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VendorTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (selected)
        Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameMid))
    else
        Brush.verticalGradient(listOf(StoneTheme.frameDark, Color(0xFF0D0A08)))

    Box(
        modifier = modifier
            .height(32.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .drawBehind {
                val w = size.width; val h = size.height
                if (selected) {
                    drawLine(BurnishedGold.copy(alpha = 0.6f), Offset(0f, 0f), Offset(w, 0f), 1f)
                    drawLine(BurnishedGold.copy(alpha = 0.6f), Offset(0f, 0f), Offset(0f, h), 1f)
                } else {
                    drawLine(StoneTheme.frameLight.copy(alpha = 0.3f), Offset(0f, 0f), Offset(w, 0f), 1f)
                }
                drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                drawLine(StoneTheme.innerShadow, Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) BurnishedGold else AshGray
        )
    }
}

@Composable
private fun BuyItemRow(
    vendorItem: VendorItem,
    playerCoins: Coins,
    playerLevel: Int,
    ownedCount: Int,
    equippedItem: Item? = null,
    onBuy: () -> Unit
) {
    val item = vendorItem.item
    val canAfford = playerCoins.totalCopper() >= vendorItem.price.totalCopper()
    val meetsLevel = playerLevel >= item.levelRequirement
    val canBuy = canAfford && meetsLevel
    val context = LocalPlatformContext.current
    val serverBaseUrl = LocalServerBaseUrl.current

    val nameColor = when {
        !meetsLevel -> Color(0xFF666666)
        !canAfford -> AshGray
        else -> BoneWhite
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF14110E), DeepVoid)),
                RoundedCornerShape(6.dp)
            )
            .drawBehind {
                val w = size.width; val h = size.height
                drawLine(AshGray.copy(alpha = 0.15f), Offset(0f, 0f), Offset(w, 0f), 1f)
                drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(DeepVoid, RoundedCornerShape(6.dp))
                .border(1.dp, AshGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
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
                modifier = Modifier.size(32.dp).padding(2.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Line 1: Item name
            Text(
                text = item.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = nameColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Line 2: Owned count (if any)
            if (ownedCount > 0) {
                Text(
                    text = "Owned: $ownedCount",
                    fontSize = 11.sp,
                    color = VerdantUpgrade
                )
            }
            // Line 3: Level badge + slot/stats
            val statsParts = mutableListOf<String>()
            if (item.slot.isNotEmpty()) statsParts.add(item.slot.replaceFirstChar { it.uppercase() })
            if (item.armorValue > 0) statsParts.add("ARM ${item.armorValue}")
            if (item.damageBonus > 0) {
                val dmgText = if (item.damageRange > 0) "DMG +${item.damageBonus} (1-${item.damageRange})" else "DMG +${item.damageBonus}"
                statsParts.add(dmgText)
            }
            if (item.levelRequirement > 1 || statsParts.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.levelRequirement > 1) {
                        Text(
                            text = "Lv${item.levelRequirement}",
                            fontSize = 11.sp,
                            color = if (meetsLevel) AshGray else Color(0xFFCC4444)
                        )
                        if (statsParts.isNotEmpty()) {
                            Text(
                                text = " | ",
                                fontSize = 11.sp,
                                color = TorchAmber
                            )
                        }
                    }
                    if (statsParts.isNotEmpty()) {
                        Text(
                            text = statsParts.joinToString(" | "),
                            fontSize = 11.sp,
                            color = TorchAmber
                        )
                    }
                }
            }
            // Comparison indicator vs equipped gear
            if (item.slot.isNotEmpty()) {
                val label = primaryStatLabel(item.slot)
                val vendorStat = primaryStatValue(item, item.slot)
                val equippedStat = primaryStatValue(equippedItem, item.slot)
                val delta = vendorStat - equippedStat
                val (compText, compColor) = when {
                    equippedItem == null -> "No gear equipped" to AshGray
                    delta > 0 -> "$label +$delta upgrade" to VerdantUpgrade
                    delta < 0 -> "$label $delta downgrade" to CrimsonDowngrade
                    else -> "$label same as equipped" to AshGray
                }
                Text(
                    text = compText,
                    fontSize = 10.sp,
                    color = compColor,
                    modifier = Modifier.testTag("comparison_indicator")
                )
            }
        }

        Text(
            text = vendorItem.price.displayString(),
            fontSize = 12.sp,
            color = if (canAfford) BurnishedGold else Color(0xFF884444),
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Buy button — stone-styled
        Box(
            modifier = Modifier
                .height(32.dp)
                .background(
                    if (canBuy)
                        Brush.verticalGradient(listOf(Color(0xFF1565C0), Color(0xFF0D47A1)))
                    else
                        Brush.verticalGradient(listOf(StoneTheme.frameDark, Color(0xFF0D0A08))),
                    RoundedCornerShape(4.dp)
                )
                .drawBehind {
                    val w = size.width; val h = size.height
                    if (canBuy) {
                        drawLine(Color(0xFF42A5F5).copy(alpha = 0.5f), Offset(0f, 0f), Offset(w, 0f), 1f)
                        drawLine(Color(0xFF42A5F5).copy(alpha = 0.5f), Offset(0f, 0f), Offset(0f, h), 1f)
                    }
                    drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                    drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
                }
                .then(if (canBuy) Modifier.clickable(onClick = onBuy) else Modifier)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Buy", fontSize = 12.sp,
                color = if (canBuy) Color.White else AshGray,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SellItemRow(
    inventoryItem: InventoryItem,
    item: Item?,
    playerCharm: Int,
    hasHaggle: Boolean = false,
    onSell: () -> Unit
) {
    val itemName = item?.name ?: inventoryItem.itemId
    val itemValue = item?.value ?: 0
    val sellPriceCopper = if (itemValue > 0) Coins.sellPriceCopper(itemValue, inventoryItem.quantity, playerCharm, hasHaggle) else 0L
    val sellPrice = Coins.fromCopper(sellPriceCopper)
    val canSell = itemValue > 0
    val context = LocalPlatformContext.current
    val serverBaseUrl = LocalServerBaseUrl.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(
                Brush.horizontalGradient(listOf(Color(0xFF14110E), DeepVoid)),
                RoundedCornerShape(6.dp)
            )
            .drawBehind {
                val w = size.width; val h = size.height
                drawLine(AshGray.copy(alpha = 0.15f), Offset(0f, 0f), Offset(w, 0f), 1f)
                drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(DeepVoid, RoundedCornerShape(6.dp))
                .border(1.dp, AshGray.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(spriteUrl(serverBaseUrl, inventoryItem.itemId))
                    .crossfade(200)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = itemName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(32.dp).padding(2.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = itemName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (canSell) BoneWhite else Color(0xFF666666),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (inventoryItem.quantity > 1) {
                    Text(
                        text = " x${inventoryItem.quantity}",
                        fontSize = 11.sp,
                        color = AshGray
                    )
                }
            }
        }

        Text(
            text = if (canSell) sellPrice.displayString() else "No value",
            fontSize = 12.sp,
            color = if (canSell) BurnishedGold else AshGray,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        // Sell button — stone-styled
        Box(
            modifier = Modifier
                .height(32.dp)
                .background(
                    if (canSell)
                        Brush.verticalGradient(listOf(Color(0xFF2E7D32), Color(0xFF1B5E20)))
                    else
                        Brush.verticalGradient(listOf(StoneTheme.frameDark, Color(0xFF0D0A08))),
                    RoundedCornerShape(4.dp)
                )
                .drawBehind {
                    val w = size.width; val h = size.height
                    if (canSell) {
                        drawLine(VerdantUpgrade.copy(alpha = 0.5f), Offset(0f, 0f), Offset(w, 0f), 1f)
                        drawLine(VerdantUpgrade.copy(alpha = 0.5f), Offset(0f, 0f), Offset(0f, h), 1f)
                    }
                    drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                    drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
                }
                .then(if (canSell) Modifier.clickable(onClick = onSell) else Modifier)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Sell", fontSize = 12.sp,
                color = if (canSell) Color.White else AshGray,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
