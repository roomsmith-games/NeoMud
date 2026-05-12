package com.neomud.client.ui.components

import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.neomud.client.ui.theme.StoneTheme
import org.jetbrains.compose.resources.painterResource
import com.neomud.shared.model.Coins
import com.neomud.shared.model.InventoryItem
import com.neomud.shared.model.Item

// ─────────────────────────────────────────────
// Palette — shared medieval aesthetic
// ─────────────────────────────────────────────
private val DeepVoid = Color(0xFF080604)
private val WornLeather = Color(0xFF1A1510)
private val BurnishedGold = Color(0xFFCCA855)
private val TorchAmber = Color(0xFFBBA060)
private val BoneWhite = Color(0xFFD8CCAA)
private val AshGray = Color(0xFF5A5040)
private val ConsumableBorder = Color(0xFF55CCAA)
private val DefaultBorder = Color(0xFF3A3228)
private val CellBgColor = Color(0xFF0E0B08)

private val CopperColor = Color(0xFFCD7F32)
private val SilverColor = Color(0xFFC0C0C0)
private val GoldColor = Color(0xFFFFD700)
private val PlatinumColor = Color(0xFFE5E4E2)

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

/** Format raw item ID as display name: "item:leather_cap" -> "Leather Cap" */
internal fun displayName(item: Item?, itemId: String): String {
    if (item != null) return item.name
    val raw = itemId.substringAfter(":")
    return raw.split('_').joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}

@Composable
fun InventoryPanel(
    inventory: List<InventoryItem>,
    itemCatalog: Map<String, Item>,
    playerCoins: Coins,
    onUseItem: (String) -> Unit,
    onClose: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.92f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { /* consume all backdrop touches */ }
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
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(MudIcons.Inventory),
                            contentDescription = "Inventory",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Inventory",
                            color = BurnishedGold,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
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

                CoinsDisplay(playerCoins)

                BagGrid(
                    inventory = inventory,
                    itemCatalog = itemCatalog,
                    onUseItem = onUseItem,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun CoinsDisplay(playerCoins: Coins) {
    if (!playerCoins.isEmpty()) {
        Text(
            "Coins",
            color = TorchAmber,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (playerCoins.platinum > 0) CoinBadge("${playerCoins.platinum} PP", PlatinumColor)
            if (playerCoins.gold > 0) CoinBadge("${playerCoins.gold} GP", GoldColor)
            if (playerCoins.silver > 0) CoinBadge("${playerCoins.silver} SP", SilverColor)
            if (playerCoins.copper > 0) CoinBadge("${playerCoins.copper} CP", CopperColor)
        }
        Spacer(modifier = Modifier.height(8.dp))
        // Runic divider
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
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun BagGrid(
    inventory: List<InventoryItem>,
    itemCatalog: Map<String, Item>,
    onUseItem: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Text(
        "Consumables & Items",
        color = TorchAmber,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
    Spacer(modifier = Modifier.height(4.dp))

    val bagItems = inventory
        .filter { invItem ->
            if (invItem.equipped) return@filter false
            val catalogItem = itemCatalog[invItem.itemId]
            val hasSlot = catalogItem?.slot?.isNotEmpty() ?: invItem.slot.isNotEmpty()
            !hasSlot
        }
        .sortedWith(
            compareBy<InventoryItem> {
                if (itemCatalog[it.itemId]?.type == "consumable") 0 else 1
            }.thenBy {
                itemCatalog[it.itemId]?.name ?: it.itemId
            }
        )
    if (bagItems.isEmpty()) {
        Text("Your bag is empty.", color = AshGray, fontSize = 12.sp)
    } else {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = modifier,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(bagItems) { invItem ->
                val item = itemCatalog[invItem.itemId]
                BagItemCell(
                    invItem = invItem,
                    item = item,
                    onUseItem = onUseItem
                )
            }
        }
    }
}

@Composable
private fun BagItemCell(
    invItem: InventoryItem,
    item: Item?,
    onUseItem: (String) -> Unit
) {
    val serverBaseUrl = LocalServerBaseUrl.current
    val isConsumable = item?.type == "consumable"
    val borderColor = if (isConsumable) ConsumableBorder else DefaultBorder
    val context = LocalPlatformContext.current

    var lastTapMark by remember { mutableStateOf<kotlin.time.TimeMark?>(null) }
    var tapped by remember { mutableStateOf(false) }
    val flashAlpha by animateFloatAsState(
        targetValue = if (tapped) 0.4f else 0f,
        animationSpec = tween(durationMillis = 100),
        finishedListener = { tapped = false },
        label = "consumableFlash"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.then(
            if (isConsumable) Modifier.clickable {
                val mark = lastTapMark
                if (mark == null || mark.elapsedNow() >= 500.milliseconds) {
                    lastTapMark = kotlin.time.TimeSource.Monotonic.markNow()
                    tapped = true
                    onUseItem(invItem.itemId)
                }
            }
            else Modifier
        )
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(CellBgColor, RoundedCornerShape(6.dp))
                .border(1.dp, borderColor, RoundedCornerShape(6.dp))
                .drawBehind {
                    // Inner bevel for depth
                    val w = size.width; val h = size.height
                    drawLine(StoneTheme.innerShadow, Offset(1f, h - 1f), Offset(w - 1f, h - 1f), 1f)
                    drawLine(StoneTheme.innerShadow, Offset(w - 1f, 1f), Offset(w - 1f, h - 1f), 1f)
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(spriteUrl(serverBaseUrl, invItem.itemId))
                    .crossfade(200)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = displayName(item, invItem.itemId),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(48.dp)
                    .padding(2.dp)
            )
            if (invItem.quantity > 1) {
                Text(
                    text = "${invItem.quantity}",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .background(Color(0xCC000000), RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
            // Flash overlay on tap
            if (isConsumable && flashAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .alpha(flashAlpha)
                        .background(Color.White, RoundedCornerShape(6.dp))
                )
            }
            // "Use" badge on consumables
            if (isConsumable) {
                Text(
                    text = "Use",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .background(ConsumableBorder.copy(alpha = 0.8f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                )
            }
        }
        Text(
            text = displayName(item, invItem.itemId),
            fontSize = 10.sp,
            color = BoneWhite,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CoinBadge(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 12.sp,
        color = color,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .background(DeepVoid, RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}
