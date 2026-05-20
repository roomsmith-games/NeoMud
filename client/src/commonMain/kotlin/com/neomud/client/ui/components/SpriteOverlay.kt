package com.neomud.client.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.neomud.client.ui.theme.MudColors
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.shared.model.CharacterClassDef
import com.neomud.shared.model.Coins
import com.neomud.shared.model.GroundItem
import com.neomud.shared.model.Item
import com.neomud.shared.model.Npc
import com.neomud.shared.model.PartyMember
import com.neomud.shared.model.PlayerInfo
import com.neomud.shared.model.SpellDef
import com.neomud.shared.model.TargetType
import org.jetbrains.compose.resources.painterResource

private val SelectedGlow = Color(0xFFFF3333)

fun spriteUrl(serverBaseUrl: String, entityId: String): String {
    val baseId = entityId.substringBefore('#')
    val prefix = baseId.substringBefore(':')
    val filename = baseId.replace(':', '_') + ".webp"
    val folder = "${prefix}s" // npc -> npcs, item -> items
    return "$serverBaseUrl/assets/images/$folder/$filename"
}

fun pcSpriteUrl(serverBaseUrl: String, info: PlayerInfo): String {
    return "$serverBaseUrl/assets/${info.spriteUrl}"
}

private fun coinSpriteUrl(serverBaseUrl: String, coinType: String): String {
    return "$serverBaseUrl/assets/images/coins/coin_${coinType}.webp"
}

private sealed class RoomEntity {
    abstract val stableKey: String
    data class NpcEntity(val npc: Npc) : RoomEntity() {
        override val stableKey: String get() = "npc:${npc.id}"
    }
    data class PcEntity(val info: PlayerInfo) : RoomEntity() {
        override val stableKey: String get() = "pc:${info.name}"
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpriteOverlay(
    npcs: List<Npc>,
    players: List<PlayerInfo> = emptyList(),
    groundItems: List<GroundItem>,
    groundCoins: Coins,
    itemCatalog: Map<String, Item>,
    selectedTargetId: String?,
    onSelectTarget: (String?) -> Unit,
    onPickupItem: (String, Int) -> Unit,
    onPickupCoins: (String) -> Unit,
    onPlayerTap: ((PlayerInfo) -> Unit)? = null,
    onPlayerLongPress: ((PlayerInfo) -> Unit)? = null,
    readiedSpellId: String? = null,
    onCastSpell: ((String, String) -> Unit)? = null,
    spellSlots: List<String?> = emptyList(),
    spellCatalog: Map<String, SpellDef> = emptyMap(),
    classCatalog: Map<String, CharacterClassDef> = emptyMap(),
    playerCharacterClass: String? = null,
    onAttackTarget: ((String) -> Unit)? = null,
    onTrackTarget: ((String) -> Unit)? = null,
    onKickTarget: ((String) -> Unit)? = null,
    partyMembersInfo: Map<String, PartyMember> = emptyMap(),
    partyLeaderName: String? = null,
    modifier: Modifier = Modifier
) {
    val serverBaseUrl = LocalServerBaseUrl.current
    val contextMenuNpcId = remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        // Dismiss scrim when context menu is showing
        if (contextMenuNpcId.value != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { contextMenuNpcId.value = null }
            )
        }

        val allEntities = (npcs.map { RoomEntity.NpcEntity(it) } +
                players.map { RoomEntity.PcEntity(it) }).take(7)

        if (allEntities.isNotEmpty()) {
            val totalEntities = npcs.size + players.size
            // Front row: first 4 entities (fill bottom row first)
            val frontRow = allEntities.take(4)
            // Back row: overflow entities 5-7
            val backRow = allEntities.drop(4)

            val context = coil3.compose.LocalPlatformContext.current

            // Back row — sits higher, smaller scale
            if (backRow.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.55f)
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    backRow.forEach { entity ->
                        key(entity.stableKey) {
                            EntitySprite(
                                entity = entity,
                                serverBaseUrl = serverBaseUrl,
                                context = context,
                                selectedTargetId = selectedTargetId,
                                onSelectTarget = onSelectTarget,
                                onPlayerTap = onPlayerTap,
                                onPlayerLongPress = onPlayerLongPress,
                                readiedSpellId = readiedSpellId,
                                onCastSpell = onCastSpell,
                                contextMenuNpcId = contextMenuNpcId,
                                spellSlots = spellSlots,
                                spellCatalog = spellCatalog,
                                classCatalog = classCatalog,
                                playerCharacterClass = playerCharacterClass,
                                onAttackTarget = onAttackTarget,
                                onTrackTarget = onTrackTarget,
                                onKickTarget = onKickTarget,
                                partyMembersInfo = partyMembersInfo,
                                partyLeaderName = partyLeaderName,
                                scale = 0.75f,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }

            // Front row — at bottom, full scale
            if (frontRow.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.38f)
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    frontRow.forEach { entity ->
                        key(entity.stableKey) {
                            EntitySprite(
                                entity = entity,
                                serverBaseUrl = serverBaseUrl,
                                context = context,
                                selectedTargetId = selectedTargetId,
                                onSelectTarget = onSelectTarget,
                                onPlayerTap = onPlayerTap,
                                onPlayerLongPress = onPlayerLongPress,
                                readiedSpellId = readiedSpellId,
                                onCastSpell = onCastSpell,
                                contextMenuNpcId = contextMenuNpcId,
                                spellSlots = spellSlots,
                                spellCatalog = spellCatalog,
                                classCatalog = classCatalog,
                                playerCharacterClass = playerCharacterClass,
                                onAttackTarget = onAttackTarget,
                                onTrackTarget = onTrackTarget,
                                onKickTarget = onKickTarget,
                                partyMembersInfo = partyMembersInfo,
                                partyLeaderName = partyLeaderName,
                                scale = 1.0f,
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }
            }

            // Overflow indicator
            if (totalEntities > 7) {
                Text(
                    text = "+${totalEntities - 7} more",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = 8.dp, top = 4.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        // Ground loot — items and coins along the bottom
        val hasLoot = groundItems.isNotEmpty() || !groundCoins.isEmpty()
        if (hasLoot) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Bottom
            ) {
                val context = coil3.compose.LocalPlatformContext.current

                // Coin piles
                val coinTypes = listOf(
                    Triple("platinum", groundCoins.platinum, "Platinum coins"),
                    Triple("gold", groundCoins.gold, "Gold coins"),
                    Triple("silver", groundCoins.silver, "Silver coins"),
                    Triple("copper", groundCoins.copper, "Copper coins")
                )
                coinTypes.filter { it.second > 0 }.forEach { (type, qty, desc) ->
                    key("coin:$type") {
                        LootSprite(
                            imageUrl = coinSpriteUrl(serverBaseUrl, type),
                            contentDescription = desc,
                            quantity = qty,
                            onClick = { onPickupCoins(type) },
                            context = context
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }

                // Item sprites
                groundItems.take(6).forEach { groundItem ->
                    key("loot:${groundItem.itemId}") {
                        val item = itemCatalog[groundItem.itemId]
                        LootSprite(
                            imageUrl = spriteUrl(serverBaseUrl, groundItem.itemId),
                            contentDescription = item?.name ?: groundItem.itemId,
                            quantity = groundItem.quantity,
                            onClick = { onPickupItem(groundItem.itemId, groundItem.quantity) },
                            context = context
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntitySprite(
    entity: RoomEntity,
    serverBaseUrl: String,
    context: coil3.PlatformContext,
    selectedTargetId: String?,
    onSelectTarget: (String?) -> Unit,
    onPlayerTap: ((PlayerInfo) -> Unit)?,
    onPlayerLongPress: ((PlayerInfo) -> Unit)?,
    readiedSpellId: String?,
    onCastSpell: ((String, String) -> Unit)?,
    contextMenuNpcId: MutableState<String?>,
    spellSlots: List<String?>,
    spellCatalog: Map<String, SpellDef>,
    classCatalog: Map<String, CharacterClassDef>,
    playerCharacterClass: String?,
    onAttackTarget: ((String) -> Unit)?,
    onTrackTarget: ((String) -> Unit)?,
    onKickTarget: ((String) -> Unit)?,
    partyMembersInfo: Map<String, PartyMember>,
    partyLeaderName: String?,
    scale: Float,
    modifier: Modifier = Modifier
) {
    when (entity) {
        is RoomEntity.NpcEntity -> {
            val npc = entity.npc
            val isSelected = npc.id == selectedTargetId
            val showContextMenu = contextMenuNpcId.value == npc.id
            Box(
                modifier = modifier
                    .then(
                        if (isSelected) Modifier.border(
                            2.dp, SelectedGlow, RoundedCornerShape(4.dp)
                        ) else Modifier
                    )
                    .combinedClickable(
                        onClick = {
                            if (npc.hostile) {
                                if (readiedSpellId != null && onCastSpell != null) {
                                    onCastSpell(readiedSpellId, npc.id)
                                } else {
                                    onSelectTarget(if (isSelected) null else npc.id)
                                }
                            } else {
                                onSelectTarget(npc.id)
                            }
                        },
                        onLongClick = {
                            if (npc.hostile) {
                                contextMenuNpcId.value = npc.id
                            }
                        }
                    ),
                contentAlignment = Alignment.BottomCenter
            ) {
                // Context menu above sprite
                if (showContextMenu) {
                    NpcContextMenu(
                        npcId = npc.id,
                        spellSlots = spellSlots,
                        spellCatalog = spellCatalog,
                        classCatalog = classCatalog,
                        playerCharacterClass = playerCharacterClass,
                        onAttackTarget = {
                            onAttackTarget?.invoke(npc.id)
                            contextMenuNpcId.value = null
                        },
                        onCastSpell = { spellId ->
                            onCastSpell?.invoke(spellId, npc.id)
                            contextMenuNpcId.value = null
                        },
                        onTrackTarget = if (onTrackTarget != null) {
                            {
                                onTrackTarget.invoke(npc.id)
                                contextMenuNpcId.value = null
                            }
                        } else null,
                        onKickTarget = if (onKickTarget != null) {
                            {
                                onKickTarget.invoke(npc.id)
                                contextMenuNpcId.value = null
                            }
                        } else null,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                val spriteId = npc.spriteOverride.ifEmpty { npc.id }
                val npcModel = remember(spriteId) {
                    coil3.request.ImageRequest.Builder(context)
                        .data(spriteUrl(serverBaseUrl, spriteId))
                        .crossfade(200)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = npcModel,
                    contentDescription = npc.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxHeight(0.85f * scale)
                )
            }
        }
        is RoomEntity.PcEntity -> {
            val info = entity.info
            val partyMember = partyMembersInfo[info.name]
            val isPartyMember = partyMember != null
            val isLeader = info.name == partyLeaderName
            Column(
                modifier = modifier
                    .then(
                        if (isPartyMember) Modifier.border(
                            1.5.dp,
                            MudColors.partyChat.copy(alpha = 0.6f),
                            RoundedCornerShape(4.dp)
                        ) else Modifier
                    )
                    .combinedClickable(
                        onClick = { onPlayerTap?.invoke(info) },
                        onLongClick = { onPlayerLongPress?.invoke(info) }
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                val pcModel = remember(info.spriteUrl) {
                    coil3.request.ImageRequest.Builder(context)
                        .data(pcSpriteUrl(serverBaseUrl, info))
                        .crossfade(200)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build()
                }
                AsyncImage(
                    model = pcModel,
                    contentDescription = info.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .weight(1f, fill = false)
                )
                val displayName = if (isLeader) "♕ ${info.name}" else info.name
                val nameBgColor = if (isPartyMember) Color(0xAA2255AA) else Color(0xAA000000)
                Text(
                    text = displayName,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .background(nameBgColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
                if (partyMember != null) {
                    val hpPct = if (partyMember.maxHp > 0) partyMember.currentHp.toFloat() / partyMember.maxHp else 0f
                    val hpColor = when {
                        hpPct > 0.6f -> Color(0xFF55FF55)
                        hpPct > 0.3f -> Color(0xFFFFFF55)
                        else -> Color(0xFFFF5555)
                    }
                    Box(
                        modifier = Modifier
                            .width(48.dp)
                            .height(4.dp)
                            .background(Color(0xFF1A1A1A), RoundedCornerShape(2.dp))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(hpPct.coerceIn(0f, 1f))
                                .background(hpColor, RoundedCornerShape(2.dp))
                        )
                    }
                    if (partyMember.maxMp > 0) {
                        val mpPct = partyMember.currentMp.toFloat() / partyMember.maxMp
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(3.dp)
                                .background(Color(0xFF1A1A1A), RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(mpPct.coerceIn(0f, 1f))
                                    .background(Color(0xFF5555FF), RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Returns the list of targetable spells from the player's spell slots
 * that belong to the player's class magic schools. Self-only spells are
 * excluded since they don't accept a target; everything else (heals,
 * damage, buffs that target another entity) is included.
 */
internal fun targetableSlottedSpells(
    spellSlots: List<String?>,
    spellCatalog: Map<String, SpellDef>,
    classCatalog: Map<String, CharacterClassDef>,
    playerCharacterClass: String?
): List<SpellDef> {
    val classDef = playerCharacterClass?.let { classCatalog[it] }
    val classSchools = classDef?.magicSchools?.keys ?: emptySet()
    return spellSlots
        .filterNotNull()
        .distinct()
        .mapNotNull { spellCatalog[it] }
        .filter { it.targetType != TargetType.SELF && it.school in classSchools }
}

// schoolColor() is defined in SpellBar.kt (same package)

@Composable
private fun NpcContextMenu(
    npcId: String,
    spellSlots: List<String?>,
    spellCatalog: Map<String, SpellDef>,
    classCatalog: Map<String, CharacterClassDef>,
    playerCharacterClass: String?,
    onAttackTarget: () -> Unit,
    onCastSpell: (String) -> Unit,
    onTrackTarget: (() -> Unit)? = null,
    onKickTarget: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val offensiveSpells = targetableSlottedSpells(
        spellSlots, spellCatalog, classCatalog, playerCharacterClass
    )

    val stoneBg = Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark))

    Row(
        modifier = modifier
            .background(Color(0xCC1a1a2e.toInt()), RoundedCornerShape(6.dp))
            .padding(horizontal = 4.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Attack button (always present)
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(stoneBg, CircleShape)
                .border(1.dp, StoneTheme.frameMid, CircleShape)
                .clickable { onAttackTarget() },
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(MudIcons.Attack),
                contentDescription = "Attack",
                modifier = Modifier.size(20.dp)
            )
        }

        // Track button
        if (onTrackTarget != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(stoneBg, CircleShape)
                    .border(1.dp, Color(0xFF55AA55).copy(alpha = 0.7f), CircleShape)
                    .clickable { onTrackTarget() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(MudIcons.TrackNpc),
                    contentDescription = "Track",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Kick button
        if (onKickTarget != null) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(stoneBg, CircleShape)
                    .border(1.dp, Color(0xFFFF5533).copy(alpha = 0.7f), CircleShape)
                    .clickable { onKickTarget() },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(MudIcons.KickNpc),
                    contentDescription = "Kick",
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Offensive spell buttons
        offensiveSpells.forEach { spell ->
            key(spell.id) {
                val color = schoolColor(spell.school)
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(stoneBg, CircleShape)
                        .border(1.dp, color.copy(alpha = 0.7f), CircleShape)
                        .clickable { onCastSpell(spell.id) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = spell.name.take(2),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = color
                    )
                }
            }
        }
    }
}

@Composable
private fun LootSprite(
    imageUrl: String,
    contentDescription: String,
    quantity: Int,
    onClick: () -> Unit,
    context: coil3.PlatformContext
) {
    Box(
        modifier = Modifier.clickable { onClick() }
    ) {
        AsyncImage(
            model = coil3.request.ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(200)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(40.dp)
        )
        if (quantity > 1) {
            Text(
                text = "$quantity",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(Color(0xCC000000), RoundedCornerShape(3.dp))
                    .padding(horizontal = 3.dp, vertical = 1.dp)
            )
        }
    }
}
