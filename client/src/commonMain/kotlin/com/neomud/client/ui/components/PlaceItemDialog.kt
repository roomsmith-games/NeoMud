package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.shared.model.InventoryItem
import com.neomud.shared.model.Item
import com.neomud.shared.protocol.ServerMessage

/**
 * Modal dialog for the PLACE_ITEM puzzle interaction. Mirrors the server contract
 * in [com.neomud.server.game.commands.InteractCommand]:
 *   - server sends [ServerMessage.PlaceItemPrompt] with featureId/label/prompt/acceptedItems
 *   - player picks an item the server will accept
 *   - dialog dispatches [com.neomud.shared.protocol.ClientMessage.PlaceItem]
 *
 * The dialog filters the inventory client-side (acceptedItems ∩ owned ∩ !equipped) so
 * the player never has the option to send an itemId the server would reject. Equipped
 * items are hidden from the picker even though the server also enforces the same rule.
 */
@Composable
fun PlaceItemDialog(
    prompt: ServerMessage.PlaceItemPrompt,
    inventory: List<InventoryItem>,
    itemCatalog: Map<String, Item>,
    onPlace: (itemId: String) -> Unit,
    onDismiss: () -> Unit
) {
    val candidates = inventory.filter { it.itemId in prompt.acceptedItems && !it.equipped }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .testTag("placeItemDialog"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // swallow inner clicks so taps on the panel itself don't dismiss
                )
                .drawBehind {
                    val w = size.width; val h = size.height
                    drawRect(StoneTheme.panelBg)
                    drawRect(StoneTheme.frameMid, size = size)
                    drawRect(
                        StoneTheme.panelBg,
                        topLeft = Offset(3f, 3f),
                        size = Size(w - 6f, h - 6f)
                    )
                    drawLine(StoneTheme.frameLight, Offset(1f, 1f), Offset(w - 1f, 1f), 2f)
                    drawLine(StoneTheme.frameLight, Offset(1f, 1f), Offset(1f, h - 1f), 2f)
                    drawLine(StoneTheme.innerShadow, Offset(1f, h - 2f), Offset(w - 1f, h - 2f), 2f)
                    drawLine(StoneTheme.innerShadow, Offset(w - 2f, 1f), Offset(w - 2f, h - 1f), 2f)
                    drawLine(StoneTheme.metalGold, Offset(12f, 64f), Offset(w - 12f, 64f), 1f)
                }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = prompt.label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCCA855)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = prompt.prompt,
                fontSize = 14.sp,
                color = Color(0xFFCCCCCC)
            )
            Spacer(modifier = Modifier.height(16.dp))

            if (candidates.isEmpty()) {
                Box(modifier = Modifier.testTag("placeItemEmptyState")) {
                    Text(
                        text = "Nothing in your pack fits here.",
                        fontSize = 13.sp,
                        color = Color(0xFFAAAAAA)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (inv in candidates) {
                        val name = itemCatalog[inv.itemId]?.name ?: inv.itemId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPlace(inv.itemId) }
                                .testTag("placeItemRow:${inv.itemId}")
                                .padding(vertical = 6.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = name,
                                fontSize = 14.sp,
                                color = Color(0xFFD8CCAA),
                                modifier = Modifier.weight(1f)
                            )
                            if (inv.quantity > 1) {
                                Text(
                                    text = "×${inv.quantity}",
                                    fontSize = 13.sp,
                                    color = Color(0xFFAAAAAA)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Box(
                modifier = Modifier
                    .drawBehind {
                        val w = size.width; val h = size.height
                        drawRect(Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark)))
                        drawLine(StoneTheme.frameLight, Offset(0f, 0.5f), Offset(w, 0.5f), 1f)
                        drawLine(StoneTheme.frameLight, Offset(0.5f, 0f), Offset(0.5f, h), 1f)
                        drawLine(StoneTheme.innerShadow, Offset(0f, h - 0.5f), Offset(w, h - 0.5f), 1f)
                        drawLine(StoneTheme.innerShadow, Offset(w - 0.5f, 0f), Offset(w - 0.5f, h), 1f)
                    }
                    .clickable { onDismiss() }
                    .testTag("placeItemCancel")
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Cancel",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCCA855)
                )
            }
        }
    }
}
