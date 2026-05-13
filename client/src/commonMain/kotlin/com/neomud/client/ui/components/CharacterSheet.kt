package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.neomud.client.platform.LocalIsLandscape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.shared.model.*
import org.jetbrains.compose.resources.painterResource

// ─────────────────────────────────────────────
// Palette — shared medieval aesthetic
// ─────────────────────────────────────────────
private val DeepVoid = Color(0xFF080604)
private val WornLeather = Color(0xFF1A1510)
private val BurnishedGold = Color(0xFFCCA855)
private val TorchAmber = Color(0xFFBBA060)
private val BoneWhite = Color(0xFFD8CCAA)
private val AshGray = Color(0xFF5A5040)
private val DimText = Color(0xFFAAAAAA)
private val BrightText = Color(0xFFCCCCCC)

private val CopperColor = Color(0xFFCD7F32)
private val SilverColor = Color(0xFFC0C0C0)
private val GoldColor = Color(0xFFFFD700)
private val PlatinumColor = Color(0xFFE5E4E2)

// ─────────────────────────────────────────────
// Stone frame drawing — beveled edges, rivets
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

// ─────────────────────────────────────────────
// Ornamental divider
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
            "\u2500\u2500 \u2726 \u2500\u2500",
            color = AshGray.copy(alpha = 0.5f),
            fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Box(modifier = Modifier.weight(1f).height(1.dp).background(
            Brush.horizontalGradient(listOf(AshGray.copy(alpha = 0.4f), Color.Transparent))
        ))
    }
}

@Composable
fun CharacterSheet(
    player: Player,
    classCatalog: Map<String, CharacterClassDef>,
    equipment: Map<String, String>,
    itemCatalog: Map<String, Item>,
    activeEffects: List<ActiveEffect>,
    playerCoins: Coins,
    skillCatalog: Map<String, SkillDef> = emptyMap(),
    spellCatalog: Map<String, SpellDef> = emptyMap(),
    isHidden: Boolean = false,
    onClose: () -> Unit
) {
    val isLandscape = LocalIsLandscape.current

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
                            painter = painterResource(MudIcons.CharacterSheet),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "Character Sheet",
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

                if (isLandscape) {
                    CharacterSheetLandscape(
                        player = player,
                        classCatalog = classCatalog,
                        equipment = equipment,
                        itemCatalog = itemCatalog,
                        activeEffects = activeEffects,
                        playerCoins = playerCoins,
                        skillCatalog = skillCatalog,
                        spellCatalog = spellCatalog,
                        isHidden = isHidden
                    )
                } else {
                    CharacterSheetPortrait(
                        player = player,
                        classCatalog = classCatalog,
                        equipment = equipment,
                        itemCatalog = itemCatalog,
                        activeEffects = activeEffects,
                        playerCoins = playerCoins,
                        skillCatalog = skillCatalog,
                        spellCatalog = spellCatalog,
                        isHidden = isHidden
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CharacterSheetPortrait(
    player: Player,
    classCatalog: Map<String, CharacterClassDef>,
    equipment: Map<String, String>,
    itemCatalog: Map<String, Item>,
    activeEffects: List<ActiveEffect>,
    playerCoins: Coins,
    skillCatalog: Map<String, SkillDef> = emptyMap(),
    spellCatalog: Map<String, SpellDef> = emptyMap(),
    isHidden: Boolean = false
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState())
    ) {
        NameAndVitals(player, classCatalog)
        Spacer(modifier = Modifier.height(10.dp))
        RunicDivider()
        Spacer(modifier = Modifier.height(10.dp))
        StatsSection(player)
        Spacer(modifier = Modifier.height(10.dp))
        RunicDivider()
        Spacer(modifier = Modifier.height(10.dp))
        EquipmentSection(equipment, itemCatalog)
        Spacer(modifier = Modifier.height(10.dp))
        RunicDivider()
        Spacer(modifier = Modifier.height(10.dp))
        SkillsSection(player, classCatalog, skillCatalog)
        Spacer(modifier = Modifier.height(10.dp))
        SpellsSection(player, classCatalog, spellCatalog)
        Spacer(modifier = Modifier.height(10.dp))
        RunicDivider()
        Spacer(modifier = Modifier.height(10.dp))
        ActiveEffectsSection(activeEffects, isHidden)
        Spacer(modifier = Modifier.height(10.dp))
        CoinsSection(playerCoins)
    }
}

@Composable
private fun ColumnScope.CharacterSheetLandscape(
    player: Player,
    classCatalog: Map<String, CharacterClassDef>,
    equipment: Map<String, String>,
    itemCatalog: Map<String, Item>,
    activeEffects: List<ActiveEffect>,
    playerCoins: Coins,
    skillCatalog: Map<String, SkillDef> = emptyMap(),
    spellCatalog: Map<String, SpellDef> = emptyMap(),
    isHidden: Boolean = false
) {
    Row(
        modifier = Modifier.weight(1f),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left column
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            NameAndVitals(player, classCatalog)
            Spacer(modifier = Modifier.height(10.dp))
            RunicDivider()
            Spacer(modifier = Modifier.height(10.dp))
            StatsSection(player)
            Spacer(modifier = Modifier.height(10.dp))
            RunicDivider()
            Spacer(modifier = Modifier.height(10.dp))
            SkillsSection(player, classCatalog, skillCatalog)
            Spacer(modifier = Modifier.height(10.dp))
            SpellsSection(player, classCatalog, spellCatalog)
        }

        // Vertical divider — stone-styled
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(2.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, AshGray.copy(alpha = 0.4f), AshGray.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )

        // Right column
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            EquipmentSection(equipment, itemCatalog)
            Spacer(modifier = Modifier.height(10.dp))
            RunicDivider()
            Spacer(modifier = Modifier.height(10.dp))
            ActiveEffectsSection(activeEffects, isHidden)
            Spacer(modifier = Modifier.height(10.dp))
            RunicDivider()
            Spacer(modifier = Modifier.height(10.dp))
            CoinsSection(playerCoins)
        }
    }
}

@Composable
private fun NameAndVitals(player: Player, classCatalog: Map<String, CharacterClassDef>) {
    val className = classCatalog[player.characterClass]?.name ?: player.characterClass
    val serverBaseUrl = LocalServerBaseUrl.current

    // Sprite + name
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (serverBaseUrl.isNotBlank() && player.race.isNotEmpty()) {
            val raceId = player.race.lowercase()
            val classId = player.characterClass.lowercase()
            val gender = player.gender
            val spriteUrl = "$serverBaseUrl/assets/images/players/${raceId}_${gender}_${classId}.webp"
            val context = LocalPlatformContext.current
            Box(
                modifier = Modifier
                    .height(72.dp)
                    .widthIn(max = 54.dp)
                    .border(1.dp, AshGray, RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(DeepVoid)
            ) {
                AsyncImage(
                    model = coil3.request.ImageRequest.Builder(context)
                        .data(spriteUrl)
                        .crossfade(200)
                        .memoryCachePolicy(CachePolicy.ENABLED)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .build(),
                    contentDescription = "${player.name} portrait",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.matchParentSize()
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
        }
        Column {
            Text(
                text = player.name,
                color = BurnishedGold,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            val raceLabel = if (player.race.isNotEmpty()) {
                val formattedRace = player.race.lowercase().replaceFirstChar { it.uppercase() }.replace("_", " ")
                "$formattedRace  \u2022  "
            } else ""
            Text(
                text = "$raceLabel$className  \u2022  Level ${player.level}",
                color = DimText,
                fontSize = 13.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    SectionHeader("Vitals")
    Spacer(modifier = Modifier.height(4.dp))
    VitalBar(
        label = "HP",
        current = player.currentHp,
        max = player.maxHp,
        color = when {
            player.maxHp > 0 && player.currentHp.toFloat() / player.maxHp > 0.5f -> Color(0xFF4CAF50)
            player.maxHp > 0 && player.currentHp.toFloat() / player.maxHp > 0.25f -> Color(0xFFFF9800)
            else -> Color(0xFFF44336)
        }
    )
    Spacer(modifier = Modifier.height(4.dp))
    VitalBar(
        label = "MP",
        current = player.currentMp,
        max = player.maxMp,
        color = Color(0xFF448AFF)
    )

    if (player.xpToNextLevel > 0) {
        Spacer(modifier = Modifier.height(4.dp))
        VitalBar(
            label = "XP",
            current = player.currentXp.toInt(),
            max = player.xpToNextLevel.toInt(),
            color = BurnishedGold
        )
    }
    if (player.unspentCp > 0) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "\u2726 Unspent CP: ${player.unspentCp}",
            fontSize = 12.sp,
            color = TorchAmber,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StatsSection(player: Player) {
    SectionHeader("Stats")
    Spacer(modifier = Modifier.height(4.dp))
    StatsGrid(player.stats)
}

@Composable
private fun EquipmentSection(equipment: Map<String, String>, itemCatalog: Map<String, Item>) {
    SectionHeader("Equipment")
    Spacer(modifier = Modifier.height(4.dp))
    for (slot in EquipmentSlots.DEFAULT_SLOTS) {
        val equippedItemId = equipment[slot]
        val item = equippedItemId?.let { itemCatalog[it] }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .background(
                    if (item != null) Color(0xFF0E0B08) else Color.Transparent,
                    RoundedCornerShape(3.dp)
                )
                .padding(horizontal = 4.dp, vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = slot.replaceFirstChar { it.uppercase() },
                color = AshGray,
                fontSize = 12.sp,
                modifier = Modifier.width(64.dp)
            )
            Text(
                text = item?.name ?: "-- empty --",
                color = if (item != null) BoneWhite else Color(0xFF3A3228),
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            if (item != null) {
                val statText = when {
                    item.slot == EquipmentSlots.WEAPON && item.damageBonus > 0 ->
                        "DMG +${item.damageBonus}"
                    item.armorValue > 0 -> "ARM ${item.armorValue}"
                    else -> null
                }
                if (statText != null) {
                    Text(
                        text = statText,
                        fontSize = 10.sp,
                        color = TorchAmber,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SkillsSection(
    player: Player,
    classCatalog: Map<String, CharacterClassDef>,
    skillCatalog: Map<String, SkillDef>
) {
    SectionHeader("Skills")
    Spacer(modifier = Modifier.height(4.dp))
    val classDef = classCatalog[player.characterClass]
    val classSkillIds = classDef?.skills ?: emptyList()
    if (classSkillIds.isEmpty() || skillCatalog.isEmpty()) {
        Text("No skills", color = AshGray, fontSize = 12.sp)
    } else {
        for (skillId in classSkillIds) {
            val skill = skillCatalog[skillId] ?: continue
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = skill.name,
                    color = BoneWhite,
                    fontSize = 12.sp,
                    modifier = Modifier.width(80.dp)
                )
                Text(
                    text = skill.description,
                    color = DimText,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun SpellsSection(
    player: Player,
    classCatalog: Map<String, CharacterClassDef>,
    spellCatalog: Map<String, SpellDef>
) {
    val classDef = classCatalog[player.characterClass]
    val schools = classDef?.magicSchools ?: emptyMap()
    if (schools.isEmpty()) return

    val knownSpells = spellCatalog.values
        .filter { spell -> spell.school in schools && spell.levelRequired <= player.level }
        .sortedWith(compareBy({ it.school }, { it.levelRequired }, { it.name }))

    Spacer(modifier = Modifier.height(10.dp))
    RunicDivider()
    Spacer(modifier = Modifier.height(10.dp))

    SectionHeader("Spells")
    Spacer(modifier = Modifier.height(4.dp))
    if (knownSpells.isEmpty()) {
        Text("No spells learned yet", color = AshGray, fontSize = 12.sp)
    } else {
        for (spell in knownSpells) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = spell.name,
                    color = Color(0xFF88AAFF),
                    fontSize = 12.sp,
                    modifier = Modifier.width(90.dp)
                )
                Text(
                    text = "${spell.manaCost} MP",
                    color = Color(0xFF448AFF),
                    fontSize = 11.sp,
                    modifier = Modifier.width(40.dp)
                )
                Text(
                    text = spell.description,
                    color = DimText,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 2
                )
            }
        }
    }
}

@Composable
private fun ActiveEffectsSection(activeEffects: List<ActiveEffect>, isHidden: Boolean = false) {
    SectionHeader("Active Effects")
    Spacer(modifier = Modifier.height(4.dp))
    if (activeEffects.isEmpty() && !isHidden) {
        Text("No active effects", color = AshGray, fontSize = 12.sp)
    } else {
        if (isHidden) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF555555).copy(alpha = 0.8f))
                        .border(1.dp, AshGray, CircleShape)
                ) {
                    Image(
                        painter = painterResource(MudIcons.Hidden),
                        contentDescription = "Hidden",
                        modifier = Modifier.size(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Hidden",
                    color = BoneWhite,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "stealth",
                    color = AshGray,
                    fontSize = 11.sp
                )
            }
        }
        for (effect in activeEffects) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = effect.name,
                    color = BoneWhite,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${effect.remainingTicks} ticks",
                    color = DimText,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun CoinsSection(playerCoins: Coins) {
    SectionHeader("Coins")
    Spacer(modifier = Modifier.height(4.dp))
    if (playerCoins.isEmpty()) {
        Text("No coins", color = AshGray, fontSize = 12.sp)
    } else {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (playerCoins.platinum > 0) CoinBadge("${playerCoins.platinum} PP", PlatinumColor)
            if (playerCoins.gold > 0) CoinBadge("${playerCoins.gold} GP", GoldColor)
            if (playerCoins.silver > 0) CoinBadge("${playerCoins.silver} SP", SilverColor)
            if (playerCoins.copper > 0) CoinBadge("${playerCoins.copper} CP", CopperColor)
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = TorchAmber,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun VitalBar(label: String, current: Int, max: Int, color: Color) {
    val fraction = if (max > 0) (current.toFloat() / max).coerceIn(0f, 1f) else 0f
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label: $current/$max",
            fontSize = 12.sp,
            color = BrightText,
            modifier = Modifier.width(100.dp)
        )
        // Stone-styled progress bar
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
                .background(DeepVoid, RoundedCornerShape(2.dp))
                .border(1.dp, AshGray.copy(alpha = 0.3f), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(
                        Brush.horizontalGradient(
                            listOf(color.copy(alpha = 0.7f), color)
                        ),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}

@Composable
private fun StatsGrid(stats: Stats) {
    val statEntries = listOf(
        "STR" to stats.strength,
        "AGI" to stats.agility,
        "INT" to stats.intellect,
        "WIL" to stats.willpower,
        "HLT" to stats.health,
        "CHM" to stats.charm
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        for ((name, value) in statEntries) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(DeepVoid, RoundedCornerShape(4.dp))
                    .border(1.dp, AshGray.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(text = name, color = AshGray, fontSize = 10.sp)
                Text(text = value.toString(), color = BurnishedGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
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
