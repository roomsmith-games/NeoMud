package com.neomud.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.client.ui.theme.MudColors
import com.neomud.shared.model.FollowState
import com.neomud.shared.model.PartyMember
import com.neomud.shared.model.SpellDef
import com.neomud.shared.model.TargetType

@Composable
fun PartyHudOverlay(
    members: List<PartyMember>,
    playerName: String?,
    followTarget: String?,
    followState: FollowState,
    readiedSpellId: String? = null,
    spellCatalog: Map<String, SpellDef> = emptyMap(),
    onCastAllySpell: ((String) -> Unit)? = null,
    onOpenPartyPanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val otherMembers = members.filter { it.name != playerName }
    if (otherMembers.isEmpty()) return

    val readiedSpell = readiedSpellId?.let { spellCatalog[it] }
    val isAllySpellReadied = readiedSpell?.targetType == TargetType.ALLY

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xBB111111))
            .clickable { onOpenPartyPanel() }
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (member in otherMembers) {
            PartyHudMemberRow(
                member = member,
                isFollowTarget = followTarget == member.name && followState != FollowState.OFF,
                isAllyTargetable = isAllySpellReadied,
                onTap = if (isAllySpellReadied) {
                    { onCastAllySpell?.invoke(member.name) }
                } else null
            )
        }
    }
}

@Composable
private fun PartyHudMemberRow(
    member: PartyMember,
    isFollowTarget: Boolean,
    isAllyTargetable: Boolean = false,
    onTap: (() -> Unit)? = null
) {
    val hpPct = if (member.maxHp > 0) member.currentHp.toFloat() / member.maxHp else 0f
    val hpColor = when {
        hpPct > 0.6f -> Color(0xFF55FF55)
        hpPct > 0.3f -> Color(0xFFFFFF55)
        else -> Color(0xFFFF5555)
    }
    val classInitial = member.characterClass.firstOrNull()?.uppercase() ?: "?"
    val crown = if (member.isLeader) " ♕" else ""
    val followArrow = if (isFollowTarget) " →" else ""

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .then(
                if (isAllyTargetable) Modifier
                    .border(1.dp, MudColors.healTarget.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                    .clickable { onTap?.invoke() }
                    .padding(2.dp)
                else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .background(Color(0xFF333333), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(classInitial, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFFCCCCCC))
        }
        Text(
            "${member.name.take(8)}$crown$followArrow",
            fontSize = 10.sp,
            color = Color(0xFFCCCCCC),
            maxLines = 1
        )
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(5.dp)
                .background(Color(0xFF1A1A1A), RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(hpPct)
                    .background(hpColor, RoundedCornerShape(2.dp))
            )
        }
    }
}
