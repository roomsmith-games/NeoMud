package com.neomud.client.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.client.ui.theme.MudColors
import com.neomud.shared.model.SpellDef
import com.neomud.shared.model.TargetType
import org.jetbrains.compose.resources.painterResource

fun schoolColor(school: String): Color = when (school) {
    "mage" -> Color(0xFF5599FF)
    "priest" -> Color(0xFFFFDD44)
    "druid" -> Color(0xFF55CC55)
    "kai" -> Color(0xFFFF7744)
    "bard" -> Color(0xFFCC77FF)
    else -> MudColors.spell
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpellBar(
    spellSlots: List<String?>,
    spellCatalog: Map<String, SpellDef>,
    readiedSpellId: String?,
    currentMp: Int,
    onReadySpell: (Int) -> Unit,
    onOpenSpellPicker: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        spellSlots.forEachIndexed { index, spellId ->
            key(index) {
            val spell = spellId?.let { spellCatalog[it] }
            val isReadied = spell != null && spellId == readiedSpellId
            val hasEnoughMp = spell == null || currentMp >= spell.manaCost

            val isAllySpell = spell != null && (spell.targetType == TargetType.ALLY || spell.targetType == TargetType.PARTY_ROOM)
            val borderColor = when {
                isReadied && isAllySpell -> MudColors.healTarget
                isReadied -> MudColors.spell
                spell != null && hasEnoughMp -> schoolColor(spell.school)
                spell != null -> Color.Gray
                else -> Color(0xFF555555)
            }
            val bgColor = when {
                isReadied && isAllySpell -> Color(0x4444CC44)
                isReadied -> Color(0x449B59FF)
                else -> Color.Transparent
            }
            val slotTooltip = spell?.name ?: "Empty Spell Slot"

            StoneTooltip(slotTooltip) {
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .combinedClickable(
                        onClick = {
                            if (spell != null) {
                                onReadySpell(index)
                            } else {
                                onOpenSpellPicker(index)
                            }
                        },
                        onLongClick = { onOpenSpellPicker(index) }
                    ),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(
                    width = if (isReadied) 2.dp else 1.dp,
                    color = borderColor
                ),
                color = bgColor
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (spell != null) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(MudIcons.spellIcon(spell.id)),
                                contentDescription = spell.name,
                                modifier = Modifier
                                    .size(22.dp)
                                    .alpha(if (hasEnoughMp) 1f else 0.4f)
                            )
                            Text(
                                text = "${spell.manaCost}",
                                fontSize = 8.sp,
                                color = if (hasEnoughMp) Color(0xFF88BBFF) else Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Empty slot — show school icon
                        Image(
                            painter = painterResource(MudIcons.SchoolDefault),
                            contentDescription = "Empty spell slot",
                            modifier = Modifier
                                .size(24.dp)
                                .alpha(0.35f)
                        )
                    }
                }
            }
            } // StoneTooltip
            }
        }
    }
}
