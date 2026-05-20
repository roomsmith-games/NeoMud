package com.neomud.client.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neomud.client.ui.theme.MudColors
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.client.ui.components.ReconnectOverlay
import com.neomud.client.ui.components.StoneButton
import com.neomud.client.ui.components.StoneTooltip
import com.neomud.client.ui.components.StoneDivider
import com.neomud.client.ui.components.ThemedFrame
import com.neomud.client.ui.components.CharacterSheet
import com.neomud.client.ui.components.DirectionPad
import com.neomud.client.ui.components.EntitySidebar
import com.neomud.client.ui.components.FloatingMiniMap
import com.neomud.client.ui.components.GameLog
import com.neomud.client.ui.components.EquipmentPanel
import com.neomud.client.ui.components.InventoryPanel
import com.neomud.client.ui.components.LocalServerBaseUrl
import com.neomud.client.ui.components.MiniMap
import com.neomud.client.ui.components.PlayerStatusPanel
import com.neomud.client.ui.components.RoomBackground
import com.neomud.client.ui.components.RoomItemsSidebar
import com.neomud.client.platform.LocalIsLandscape
import com.neomud.client.platform.LocalSetLayoutPreference
import com.neomud.client.platform.PlatformAudioManager
import com.neomud.client.ui.components.HelpPanel
import com.neomud.client.ui.components.SettingsPanel
import com.neomud.client.ui.components.SpellBar
import com.neomud.client.ui.components.SpellPicker
import com.neomud.client.ui.components.SpriteOverlay
import com.neomud.client.ui.components.CraftingPanel
import com.neomud.client.ui.components.TrainerPanel
import com.neomud.client.ui.components.VendorPanel
import com.neomud.client.ui.components.PlaceItemDialog
import com.neomud.client.ui.components.ChoiceDialog
import com.neomud.client.ui.components.RiddleDialog
import com.neomud.client.ui.components.KickDirectionPicker
import com.neomud.client.ui.components.LockTargetPicker
import com.neomud.client.ui.components.MapOverlay
import com.neomud.client.ui.components.WorldAtlas
import com.neomud.client.ui.components.PartyPanel
import com.neomud.client.ui.components.PartyInviteDialog
import com.neomud.client.ui.components.PartyHudOverlay
import com.neomud.client.ui.components.PlayerTooltip
import com.neomud.client.ui.components.MudIcons
import com.neomud.client.viewmodel.GameViewModel
import com.neomud.shared.model.Direction
import com.neomud.shared.model.PlayerInfo
import com.neomud.shared.model.RoomInteractable

@Composable
fun GameScreen(
    gameViewModel: GameViewModel,
    onLogout: () -> Unit = {},
    audioManager: PlatformAudioManager? = null
) {
    val roomInfo by gameViewModel.roomInfo.collectAsState()
    val mapData by gameViewModel.mapData.collectAsState()
    val gameLog by gameViewModel.gameLog.collectAsState()
    val player by gameViewModel.player.collectAsState()
    val roomEntities by gameViewModel.roomEntities.collectAsState()
    val attackMode by gameViewModel.attackMode.collectAsState()
    val selectedTargetId by gameViewModel.selectedTargetId.collectAsState()
    val showInventory by gameViewModel.showInventory.collectAsState()
    val showEquipment by gameViewModel.showEquipment.collectAsState()
    val inventory by gameViewModel.inventory.collectAsState()
    val equipment by gameViewModel.equipment.collectAsState()
    val itemCatalog by gameViewModel.itemCatalog.collectAsState()
    val playerCoins by gameViewModel.playerCoins.collectAsState()
    val roomGroundItems by gameViewModel.roomGroundItems.collectAsState()
    val roomGroundCoins by gameViewModel.roomGroundCoins.collectAsState()
    val activeEffects by gameViewModel.activeEffects.collectAsState()
    val showCharacterSheet by gameViewModel.showCharacterSheet.collectAsState()
    val showSettings by gameViewModel.showSettings.collectAsState()
    val showHelp by gameViewModel.showHelp.collectAsState()
    val classCatalog by gameViewModel.classCatalog.collectAsState()
    val deathMessage by gameViewModel.deathMessage.collectAsState()
    val tutorialMessage by gameViewModel.tutorialMessage.collectAsState()
    val showTrainer by gameViewModel.showTrainer.collectAsState()
    val trainerInfo by gameViewModel.trainerInfo.collectAsState()
    val showVendor by gameViewModel.showVendor.collectAsState()
    val vendorInfo by gameViewModel.vendorInfo.collectAsState()
    val showCrafting by gameViewModel.showCrafting.collectAsState()
    val crafterInfo by gameViewModel.crafterInfo.collectAsState()
    val showPlaceItem by gameViewModel.showPlaceItem.collectAsState()
    val placeItemPrompt by gameViewModel.placeItemPrompt.collectAsState()
    val showRiddle by gameViewModel.showRiddle.collectAsState()
    val riddlePrompt by gameViewModel.riddlePrompt.collectAsState()
    val showChoice by gameViewModel.showChoice.collectAsState()
    val choicePrompt by gameViewModel.choicePrompt.collectAsState()
    val spellCatalogState by gameViewModel.spellCatalog.collectAsState()
    val spellSlots by gameViewModel.spellSlots.collectAsState()
    val readiedSpellId by gameViewModel.readiedSpellId.collectAsState()
    val showSpellPicker by gameViewModel.showSpellPicker.collectAsState()
    val editingSlotIndex by gameViewModel.editingSlotIndex.collectAsState()

    val showMap by gameViewModel.showMap.collectAsState()
    val visitedRooms by gameViewModel.visitedRooms.collectAsState()
    val showAtlas by gameViewModel.showAtlas.collectAsState()
    val atlasData by gameViewModel.atlasData.collectAsState()

    val showLockTargetPicker by gameViewModel.showLockTargetPicker.collectAsState()
    val showKickDirectionPicker by gameViewModel.showKickDirectionPicker.collectAsState()
    val kickPendingNpcId by gameViewModel.kickPendingNpcId.collectAsState()

    val roomPlayers by gameViewModel.roomPlayers.collectAsState()
    var tooltipPlayer by remember { mutableStateOf<PlayerInfo?>(null) }

    val partyMembers by gameViewModel.partyMembers.collectAsState()
    val partyLeaderId by gameViewModel.partyLeaderId.collectAsState()
    val showPartyPanel by gameViewModel.showPartyPanel.collectAsState()
    val partyInvite by gameViewModel.partyInvite.collectAsState()
    val isInParty by gameViewModel.isInParty.collectAsState()
    val followTarget by gameViewModel.followTarget.collectAsState()
    val followState by gameViewModel.followState.collectAsState()

    var sayText by remember { mutableStateOf("") }

    val isHidden by gameViewModel.isHidden.collectAsState()
    val isMeditating by gameViewModel.isMeditating.collectAsState()
    val isResting by gameViewModel.isResting.collectAsState()
    val skillCatalog by gameViewModel.skillCatalog.collectAsState()

    val availableExits = roomInfo?.room?.exits?.keys ?: emptySet()
    val hasHostiles = roomEntities.any { it.hostile }
    val hasTrainer = roomEntities.any { it.behaviorType == "trainer" }
    val canLevelUp = player?.let { it.currentXp >= it.xpToNextLevel && it.level < 30 } == true
    val showTrainerButton = hasTrainer && (canLevelUp || (player?.unspentCp ?: 0) > 0)
    val hasVendor = roomEntities.any { it.behaviorType == "vendor" }
    val hasCrafter = roomEntities.any { it.behaviorType == "crafter" }

    val isLandscape = LocalIsLandscape.current
    val onSetLayoutPreference = LocalSetLayoutPreference.current

    CompositionLocalProvider(LocalServerBaseUrl provides gameViewModel.serverBaseUrl) {
    Box(modifier = Modifier.fillMaxSize().background(StoneTheme.panelBg).windowInsetsPadding(WindowInsets.safeDrawing)) {
        if (isLandscape) {
            GameScreenLandscape(
                gameViewModel = gameViewModel,
                availableExits = availableExits,
                hasHostiles = hasHostiles,
                showTrainerButton = showTrainerButton,
                showVendorButton = hasVendor,
                showCrafterButton = hasCrafter,
                sayText = sayText,
                onSayTextChange = { sayText = it },
                roomPlayers = roomPlayers,
                onPlayerTap = { gameViewModel.selectTarget(it.name) },
                onPlayerLongPress = { tooltipPlayer = it }
            )
        } else {
            GameScreenPortrait(
                gameViewModel = gameViewModel,
                availableExits = availableExits,
                hasHostiles = hasHostiles,
                showTrainerButton = showTrainerButton,
                showVendorButton = hasVendor,
                showCrafterButton = hasCrafter,
                sayText = sayText,
                onSayTextChange = { sayText = it },
                roomPlayers = roomPlayers,
                onPlayerTap = { gameViewModel.selectTarget(it.name) },
                onPlayerLongPress = { tooltipPlayer = it }
            )
        }

        // Overlays (shared between layouts)

        // Character sheet overlay
        if (showCharacterSheet) {
            val p = player
            if (p != null) {
                CharacterSheet(
                    player = p,
                    classCatalog = classCatalog,
                    equipment = equipment,
                    itemCatalog = itemCatalog,
                    activeEffects = activeEffects,
                    playerCoins = playerCoins,
                    skillCatalog = skillCatalog,
                    spellCatalog = spellCatalogState,
                    isHidden = isHidden,
                    onClose = { gameViewModel.toggleCharacterSheet() }
                )
            }
        }

        // Inventory overlay
        if (showInventory) {
            InventoryPanel(
                inventory = inventory,
                itemCatalog = itemCatalog,
                playerCoins = playerCoins,

                onUseItem = { itemId -> gameViewModel.useItem(itemId) },
                onClose = { gameViewModel.toggleInventory() }
            )
        }

        // Equipment overlay
        if (showEquipment) {
            EquipmentPanel(
                inventory = inventory,
                equipment = equipment,
                itemCatalog = itemCatalog,

                onEquipItem = { itemId, slot -> gameViewModel.equipItem(itemId, slot) },
                onUnequipItem = { slot -> gameViewModel.unequipItem(slot) },
                onClose = { gameViewModel.toggleEquipment() }
            )
        }

        // Help overlay
        if (showHelp) {
            HelpPanel(
                onClose = { gameViewModel.toggleHelp() }
            )
        }

        // Settings overlay
        if (showSettings) {
            SettingsPanel(
                isLandscape = isLandscape,
                onSetLayoutPreference = onSetLayoutPreference,
                onLogout = {
                    gameViewModel.toggleSettings()
                    onLogout()
                },
                onClose = { gameViewModel.toggleSettings() },
                audioManager = audioManager,
                isGuest = player?.isGuest == true
            )
        }

        // Trainer overlay
        if (showTrainer) {
            val info = trainerInfo
            if (info != null) {
                TrainerPanel(
                    trainerInfo = info,
                    onLevelUp = { gameViewModel.trainLevelUp() },
                    onAllocateStats = { stats -> gameViewModel.allocateTrainedStats(stats) },
                    onClose = { gameViewModel.dismissTrainer() }
                )
            }
        }

        // Vendor overlay
        if (showVendor) {
            val info = vendorInfo
            if (info != null) {
                VendorPanel(
                    vendorInfo = info,
                    playerLevel = player?.level ?: 1,
                    itemCatalog = itemCatalog,
                    onBuy = { itemId, qty -> gameViewModel.buyItem(itemId, qty) },
                    onSell = { itemId, qty -> gameViewModel.sellItem(itemId, qty) },
                    onClose = { gameViewModel.dismissVendor() }
                )
            }
        }

        // Crafting overlay
        if (showCrafting) {
            val info = crafterInfo
            if (info != null) {
                CraftingPanel(
                    crafterInfo = info,
                    playerLevel = player?.level ?: 1,
                    itemCatalog = itemCatalog,
                    onCraft = { recipeId -> gameViewModel.craftItem(recipeId) },
                    onClose = { gameViewModel.dismissCrafter() }
                )
            }
        }

        // PlaceItem altar dialog (PUZZLE two-phase)
        if (showPlaceItem) {
            val pip = placeItemPrompt
            if (pip != null) {
                PlaceItemDialog(
                    prompt = pip,
                    inventory = inventory,
                    itemCatalog = itemCatalog,
                    onPlace = { itemId -> gameViewModel.placeItem(pip.featureId, itemId) },
                    onDismiss = { gameViewModel.dismissPlaceItem() }
                )
            }
        }

        // Riddle dialog (RIDDLE_PROMPT two-phase)
        if (showRiddle) {
            val rp = riddlePrompt
            if (rp != null) {
                RiddleDialog(
                    prompt = rp,
                    onSubmit = { answer -> gameViewModel.submitRiddleAnswer(rp.featureId, answer) },
                    onDismiss = { gameViewModel.dismissRiddle() }
                )
            }
        }

        // Choice dialog (CHOICE_PROMPT two-phase)
        if (showChoice) {
            val cp = choicePrompt
            if (cp != null) {
                ChoiceDialog(
                    prompt = cp,
                    onChoice = { choiceId -> gameViewModel.submitChoice(cp.featureId, choiceId) },
                    onDismiss = { gameViewModel.dismissChoice() }
                )
            }
        }

        // Spell picker overlay
        if (showSpellPicker) {
            val slotIdx = editingSlotIndex
            if (slotIdx != null) {
                val p = player
                val cd = p?.let { classCatalog[it.characterClass] }
                SpellPicker(
                    slotIndex = slotIdx,
                    spells = spellCatalogState.values.toList(),
                    classDef = cd,
                    playerLevel = p?.level ?: 1,
                    onAssignSpell = { spellId -> gameViewModel.assignSpellToSlot(slotIdx, spellId) },
                    onClose = { gameViewModel.dismissSpellPicker() }
                )
            }
        }

        // Map overlay
        if (showMap) {
            val data = mapData
            if (data != null) {
                val currentRoom = roomInfo?.room
                key(data.playerRoomId) {
                    MapOverlay(
                        rooms = data.rooms,
                        playerRoomId = data.playerRoomId,
                        visitedRoomIds = visitedRooms,
                        currentZoneName = currentRoom?.zoneId ?: "",
                        onClose = { gameViewModel.toggleMap() }
                    )
                }
            }
        }

        // World Atlas overlay
        if (showAtlas) {
            val data = atlasData
            if (data != null) {
                WorldAtlas(
                    rooms = data.rooms,
                    playerRoomId = data.playerRoomId,
                    visitedRoomIds = visitedRooms,
                    zoneNames = data.zoneNames,
                    onClose = { gameViewModel.toggleAtlas() }
                )
            }
        }

        // Lock target picker overlay
        if (showLockTargetPicker) {
            val currentRoom = roomInfo?.room
            val lockedExits = currentRoom?.lockedExits ?: emptyMap()
            val unpickable = currentRoom?.unpickableExits ?: emptySet()
            val lockedInteractables = (currentRoom?.interactables ?: emptyList())
                .filter { it.difficulty > 0 && it.difficultyCheck.isNotEmpty() }
            if (lockedExits.isNotEmpty() || lockedInteractables.isNotEmpty()) {
                LockTargetPicker(
                    lockedExits = lockedExits,
                    unpickableExits = unpickable,
                    lockedInteractables = lockedInteractables,
                    onSelect = { targetId -> gameViewModel.pickLockTarget(targetId) },
                    onDismiss = { gameViewModel.dismissLockTargetPicker() }
                )
            }
        }

        // Kick direction picker overlay
        if (showKickDirectionPicker) {
            val exits = roomInfo?.room?.exits?.keys ?: emptySet()
            val locked = roomInfo?.room?.lockedExits?.keys ?: emptySet()
            val npcName = kickPendingNpcId?.let { id ->
                roomEntities.find { it.id == id }?.name
            } ?: "target"
            KickDirectionPicker(
                availableExits = exits,
                lockedExits = locked,
                npcName = npcName,
                onSelect = { direction -> gameViewModel.kickWithDirection(direction) },
                onDismiss = { gameViewModel.dismissKickDirectionPicker() }
            )
        }

        // Party panel overlay
        if (showPartyPanel) {
            PartyPanel(
                members = partyMembers,
                leaderId = partyLeaderId,
                playerName = player?.name,
                onInvite = { gameViewModel.inviteToParty(it) },
                onKick = { gameViewModel.kickFromParty(it) },
                onLeave = { gameViewModel.leaveParty() },
                onDismiss = { gameViewModel.togglePartyPanel() }
            )
        }

        // Party invite dialog
        if (partyInvite != null) {
            PartyInviteDialog(
                invite = partyInvite!!,
                onAccept = { gameViewModel.acceptPartyInvite() },
                onDecline = { gameViewModel.declinePartyInvite() }
            )
        }

        // Player tooltip overlay
        if (tooltipPlayer != null) {
            val canInvite = !isInParty || (player?.name == partyLeaderId && partyMembers.size < 4)
            val canFollow = isInParty && partyMembers.any { it.name == tooltipPlayer?.name }
            PlayerTooltip(
                playerInfo = tooltipPlayer!!,
                onDismiss = { tooltipPlayer = null },
                onInviteToParty = if (canInvite) { name -> gameViewModel.inviteToParty(name) } else null,
                onFollow = if (canFollow) { name -> gameViewModel.followPlayer(name) } else null
            )
        }

        // Death overlay
        if (deathMessage != null) {
            DeathOverlay(
                message = deathMessage!!,
                onDismiss = { gameViewModel.dismissDeath() }
            )
        }

        // Tutorial modal (blocking)
        if (tutorialMessage != null) {
            TutorialModal(
                key = tutorialMessage!!.key,
                title = tutorialMessage!!.title,
                content = tutorialMessage!!.content,
                onDismiss = { gameViewModel.dismissTutorial() }
            )
        }

        // Coach mark overlay (non-blocking)
        val currentCoachMark by gameViewModel.coachMark.collectAsState()
        if (currentCoachMark != null) {
            CoachMarkOverlay(
                tutorial = currentCoachMark!!,
                targetCoordinates = currentCoachMark!!.targetElement?.let { gameViewModel.coachMarkTargets[it] },
                onDismiss = { gameViewModel.dismissCoachMark() }
            )
        }

        val shutdownNotice by gameViewModel.shutdownNotice.collectAsState()
        if (shutdownNotice != null) {
            ShutdownBanner(notice = shutdownNotice!!)
        }

        val reconnectStatus by gameViewModel.reconnectStatus.collectAsState()
        ReconnectOverlay(
            status = reconnectStatus,
            onRetry = { gameViewModel.retryReconnect() },
        )
    }
    } // CompositionLocalProvider
}

@Composable
private fun DeathOverlay(
    message: String,
    onDismiss: () -> Unit
) {
    // Auto-dismiss after 4 seconds
    LaunchedEffect(message) {
        kotlinx.coroutines.delay(4000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "YOU DIED",
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCC0000)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 16.sp,
                color = Color(0xFFAAAAAA)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Tap to continue",
                fontSize = 12.sp,
                color = Color(0xFF666666)
            )
        }
    }
}

@Composable
private fun ShutdownBanner(notice: GameViewModel.ShutdownNotice) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF4A1010), Color(0xFF6B1818), Color(0xFF4A1010))
                    ),
                    RoundedCornerShape(6.dp)
                )
                .border(1.dp, Color(0xFFCC4444).copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = notice.message,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFAAAA)
            )
        }
    }
}

@Composable
private fun tutorialDismissText(key: String): String = when (key) {
    "welcome" -> "Begin Your Adventure"
    "tut_vendor" -> "Browse Wares"
    "tut_crafter" -> "View Recipes"
    "tut_cp_spend" -> "Allocate Points"
    else -> "Got It"
}

@Composable
private fun TutorialModal(
    key: String,
    title: String,
    content: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .drawBehind {
                    val w = size.width; val h = size.height
                    // Stone panel background
                    drawRect(StoneTheme.panelBg)
                    // Border frame
                    drawRect(StoneTheme.frameMid, size = size)
                    drawRect(StoneTheme.panelBg, topLeft = Offset(3f, 3f),
                        size = androidx.compose.ui.geometry.Size(w - 6f, h - 6f))
                    // Bevel highlights (inset by 1px so strokes stay within bounds)
                    drawLine(StoneTheme.frameLight, Offset(1f, 1f), Offset(w - 1f, 1f), 2f)
                    drawLine(StoneTheme.frameLight, Offset(1f, 1f), Offset(1f, h - 1f), 2f)
                    drawLine(StoneTheme.innerShadow, Offset(1f, h - 2f), Offset(w - 1f, h - 2f), 2f)
                    drawLine(StoneTheme.innerShadow, Offset(w - 2f, 1f), Offset(w - 2f, h - 1f), 2f)
                    // Gold accent line under title area
                    drawLine(StoneTheme.metalGold, Offset(12f, 52f), Offset(w - 12f, 52f), 1f)
                }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Title
            Text(
                text = title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCCA855) // BurnishedGold
            )
            Spacer(modifier = Modifier.height(16.dp))
            // Content
            Text(
                text = content,
                fontSize = 14.sp,
                color = Color(0xFFCCCCCC),
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(20.dp))
            // Dismiss button
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
                    .padding(horizontal = 32.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tutorialDismissText(key),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFCCA855)
                )
            }
        }
    }
}

/**
 * Modifier extension that registers the element bounds with GameViewModel for coach mark targeting.
 */
fun Modifier.coachMarkTarget(key: String, gameViewModel: GameViewModel): Modifier =
    this.onGloballyPositioned { coordinates ->
        gameViewModel.registerCoachMarkTarget(key, coordinates)
    }

/**
 * Passive floating coach mark. Fades in/out, fully pass-through for input.
 * No dismiss action needed — auto-fades after 6 seconds.
 */
@Composable
private fun CoachMarkOverlay(
    tutorial: com.neomud.shared.protocol.ServerMessage.Tutorial,
    targetCoordinates: LayoutCoordinates?,
    onDismiss: () -> Unit
) {
    val hasTarget = targetCoordinates != null && targetCoordinates.isAttached

    // Animate: fade in 300ms, hold, fade out 500ms
    val alpha = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(tutorial.key) {
        alpha.animateTo(1f, animationSpec = androidx.compose.animation.core.tween(300))
        kotlinx.coroutines.delay(5200) // hold visible
        alpha.animateTo(0f, animationSpec = androidx.compose.animation.core.tween(500))
        onDismiss()
    }

    // Fully pass-through — no clickable, no background, no input interception
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth(0.92f)
                .graphicsLayer { this.alpha = alpha.value }
                .drawBehind {
                    val w = size.width; val h = size.height
                    // Semi-transparent stone background
                    drawRect(Color(0xD9302820)) // panelBg at ~85% opacity
                    // Thin gold top accent
                    drawLine(StoneTheme.metalGold, Offset(0f, 0f), Offset(w, 0f), 2f)
                    // Subtle bottom border
                    drawLine(Color(0x40000000), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Gold title
            Text(
                text = tutorial.title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFCCA855),
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(3.dp))
            // Content below title, wraps naturally
            Text(
                text = tutorial.content.replace("\n\n", " ").replace("\n", " "),
                fontSize = 12.sp,
                color = Color(0xAABBBBBB),
                maxLines = 3,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun GameScreenPortrait(
    gameViewModel: GameViewModel,
    availableExits: Set<Direction>,
    hasHostiles: Boolean,
    showTrainerButton: Boolean,
    showVendorButton: Boolean,
    showCrafterButton: Boolean,
    sayText: String,
    onSayTextChange: (String) -> Unit,
    roomPlayers: List<PlayerInfo> = emptyList(),
    onPlayerTap: ((PlayerInfo) -> Unit)? = null,
    onPlayerLongPress: ((PlayerInfo) -> Unit)? = null,
) {
    val roomInfo by gameViewModel.roomInfo.collectAsState()
    val mapData by gameViewModel.mapData.collectAsState()
    val gameLog by gameViewModel.gameLog.collectAsState()
    val player by gameViewModel.player.collectAsState()
    val roomEntities by gameViewModel.roomEntities.collectAsState()
    val attackMode by gameViewModel.attackMode.collectAsState()
    val selectedTargetId by gameViewModel.selectedTargetId.collectAsState()
    val showInventory by gameViewModel.showInventory.collectAsState()
    val showEquipmentState by gameViewModel.showEquipment.collectAsState()
    val itemCatalog by gameViewModel.itemCatalog.collectAsState()
    val roomGroundItems by gameViewModel.roomGroundItems.collectAsState()
    val roomGroundCoins by gameViewModel.roomGroundCoins.collectAsState()
    val activeEffects by gameViewModel.activeEffects.collectAsState()
    val isHidden by gameViewModel.isHidden.collectAsState()
    val classCatalog by gameViewModel.classCatalog.collectAsState()
    val readiedSpellId by gameViewModel.readiedSpellId.collectAsState()
    val spellSlots by gameViewModel.spellSlots.collectAsState()
    val spellCatalogState by gameViewModel.spellCatalog.collectAsState()
    val trackedDirection by gameViewModel.trackedDirection.collectAsState()
    val skillCatalog by gameViewModel.skillCatalog.collectAsState()
    val visitedRooms by gameViewModel.visitedRooms.collectAsState()
    val showMap by gameViewModel.showMap.collectAsState()
    val showAtlas by gameViewModel.showAtlas.collectAsState()

    val showPartyPanel by gameViewModel.showPartyPanel.collectAsState()
    val isInParty by gameViewModel.isInParty.collectAsState()
    val partyMembers by gameViewModel.partyMembers.collectAsState()
    val followTarget by gameViewModel.followTarget.collectAsState()
    val followState by gameViewModel.followState.collectAsState()

    // Determine if player has TRACK / KICK skills
    val hasTrackSkill = player?.characterClass?.let { classCatalog[it] }?.skills?.contains("TRACK") == true
    val hasKickSkill = player?.characterClass?.let { classCatalog[it] }?.skills?.contains("KICK") == true

    Column(modifier = Modifier.fillMaxSize().background(StoneTheme.panelBg)) {
        // Top: Room background + sidebars + floating minimap (~35%)
        ThemedFrame(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.30f),
            cornerAccent = true
        ) {
            // Layer 1: Full-bleed background image
            val currentRoom = roomInfo?.room
            key(currentRoom?.id) {
                RoomBackground(
                    imageUrl = currentRoom?.backgroundImage ?: "",
                    roomName = currentRoom?.name ?: "",

                    modifier = Modifier.fillMaxSize()
                )
            }

            // Layer 1.5: NPC & PC & item sprites overlaid on background
            SpriteOverlay(
                npcs = roomEntities,
                players = roomPlayers,
                groundItems = roomGroundItems,
                groundCoins = roomGroundCoins,
                itemCatalog = itemCatalog,
                selectedTargetId = selectedTargetId,
                onSelectTarget = { npcId ->
                    // Quest/lore NPCs (Phase 8) dispatch to dialogue rather than combat selection.
                    val npc = npcId?.let { id -> roomEntities.find { it.id == id } }
                    if (npc?.behaviorType == "quest") gameViewModel.talkToNpc(npc.id)
                    else gameViewModel.selectTarget(npcId)
                },
                onPickupItem = { itemId, qty -> gameViewModel.pickupItem(itemId, qty) },
                onPickupCoins = { coinType -> gameViewModel.pickupCoins(coinType) },
                onPlayerTap = onPlayerTap,
                onPlayerLongPress = onPlayerLongPress,
                readiedSpellId = readiedSpellId,
                onCastSpell = { spellId, targetId -> gameViewModel.castSpellOnTarget(spellId, targetId) },
                spellSlots = spellSlots,
                spellCatalog = spellCatalogState,
                classCatalog = classCatalog,
                playerCharacterClass = player?.characterClass,
                onAttackTarget = { gameViewModel.attackTarget(it) },
                onTrackTarget = if (hasTrackSkill) { npcId: String -> gameViewModel.useSkill("TRACK", npcId) } else null,
                onKickTarget = if (hasKickSkill) { npcId: String -> gameViewModel.showKickDirectionPicker(npcId) } else null,
                partyMembersInfo = partyMembers.associateBy { it.name },
                partyLeaderName = partyMembers.firstOrNull { it.isLeader }?.name,
                modifier = Modifier.fillMaxSize()
                    .coachMarkTarget("npc_sprites", gameViewModel)
            )

            // Layer 2: Floating minimap
            val data = mapData
            if (data != null) {
                key(data.playerRoomId) {
                    FloatingMiniMap(
                        rooms = data.rooms,
                        playerRoomId = data.playerRoomId,
                        visitedRoomIds = visitedRooms,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(end = 4.dp, top = 4.dp)
                    )
                }
            }

            // Layer 2.5: Party HUD (top-left, below minimap)
            if (partyMembers.size > 1) {
                PartyHudOverlay(
                    members = partyMembers,
                    playerName = player?.name,
                    followTarget = followTarget,
                    followState = followState,
                    onOpenPartyPanel = { gameViewModel.togglePartyPanel() },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 4.dp, top = 4.dp)
                )
            }

            // Layer 3: Trainer/Vendor/Crafter/Interactable overlays (bottom-left of room view)
            val interactables = roomInfo?.room?.interactables ?: emptyList()
            if (showTrainerButton || showVendorButton || showCrafterButton || interactables.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (showTrainerButton) {
                        RoomOverlayButton(
                            icon = MudIcons.Trainer,
                            label = "Train",
                            color = Color(0xFFFFD700),
                            onClick = { gameViewModel.interactTrainer() }
                        )
                    }
                    if (showVendorButton) {
                        RoomOverlayButton(
                            icon = MudIcons.Vendor,
                            label = "Shop",
                            color = Color(0xFFCC8833),
                            onClick = { gameViewModel.interactVendor() }
                        )
                    }
                    if (showCrafterButton) {
                        RoomOverlayButton(
                            icon = MudIcons.Crafter,
                            label = "Craft",
                            color = Color(0xFFAA6B3A),
                            onClick = { gameViewModel.interactCrafter() }
                        )
                    }
                    for (feat in interactables) {
                        key(feat.id) {
                            val (icon, color) = interactableStyle(feat)
                            RoomOverlayButton(
                                icon = icon,
                                label = feat.label,
                                color = color,
                                onClick = { gameViewModel.interactFeature(feat.id) }
                            )
                        }
                    }
                }
            }
        }

        StoneDivider()

        // Middle: Game Log (~40%)
        ThemedFrame(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.40f),
            cornerAccent = false
        ) {
            GameLog(entries = gameLog)
        }

        StoneDivider()

        // Bottom: Controls (~25%)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.30f)
                .background(StoneTheme.panelBg)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // Player status panel (compact, above d-pad — matches landscape layout)
            val p = player
            if (p != null && p.maxHp > 0) {
                PlayerStatusPanel(
                    player = p,
                    activeEffects = activeEffects,
                    isHidden = isHidden,
                    isMeditating = gameViewModel.isMeditating.collectAsState().value,
                    compact = true,
                    onClick = { gameViewModel.toggleCharacterSheet() },
                    modifier = Modifier.fillMaxWidth()
                        .coachMarkTarget("status_effects", gameViewModel)
                )
                Spacer(modifier = Modifier.height(2.dp))
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalAlignment = Alignment.Top
            ) {
                DirectionPad(
                    availableExits = availableExits,
                    onMove = { direction -> gameViewModel.move(direction) },
                    onLook = { gameViewModel.look() },
                    lockedExits = (roomInfo?.room?.lockedExits?.keys ?: emptySet()),
                    trackedDirection = trackedDirection
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(modifier = Modifier.weight(1f)) {
                    ActionButtonRow(
                        gameViewModel = gameViewModel,
                        attackMode = attackMode,
                        hasHostiles = hasHostiles,
                        showInventory = showInventory,
                        showEquipment = showEquipmentState,
                        player = null,
                        activeEffects = activeEffects,
                        isHidden = isHidden,
                        isMeditating = gameViewModel.isMeditating.collectAsState().value,
                        isResting = gameViewModel.isResting.collectAsState().value,
                        spellSlots = spellSlots,
                        spellCatalog = spellCatalogState,
                        readiedSpellId = readiedSpellId,
                        classCatalog = classCatalog,
                        playerCharacterClass = player?.characterClass,
                        currentMp = player?.currentMp ?: 0,
                        skillCatalog = gameViewModel.skillCatalog.collectAsState().value
                    )
                }
            }

            // Say bar + utility icons (full width, below D-pad)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SayBar(
                    sayText = sayText,
                    onSayTextChange = onSayTextChange,
                    onSay = { gameViewModel.say(it) },
                    isAdmin = player?.isAdmin == true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CharacterSheetIconButton(onClick = { gameViewModel.toggleCharacterSheet() })
                    SpellUtilityButton(classCatalog, player?.characterClass) {
                        gameViewModel.openSpellPicker(0)
                    }
                    MapIconButton(
                        active = showAtlas,
                        onClick = { gameViewModel.toggleAtlas() }
                    )
                    InventoryIconButton(
                        active = showInventory,
                        onClick = { gameViewModel.toggleInventory() }
                    )
                    EquipmentIconButton(
                        active = showEquipmentState,
                        onClick = { gameViewModel.toggleEquipment() }
                    )
                    PartyIconButton(
                        active = showPartyPanel,
                        isInParty = isInParty,
                        onClick = { gameViewModel.togglePartyPanel() }
                    )
                    HelpIconButton(onClick = { gameViewModel.toggleHelp() })
                    SettingsGearButton(onClick = { gameViewModel.toggleSettings() })
                }
            }
        }
    }
}

@Composable
private fun GameScreenLandscape(
    gameViewModel: GameViewModel,
    availableExits: Set<Direction>,
    hasHostiles: Boolean,
    showTrainerButton: Boolean,
    showVendorButton: Boolean,
    showCrafterButton: Boolean,
    sayText: String,
    onSayTextChange: (String) -> Unit,
    roomPlayers: List<PlayerInfo> = emptyList(),
    onPlayerTap: ((PlayerInfo) -> Unit)? = null,
    onPlayerLongPress: ((PlayerInfo) -> Unit)? = null,
) {
    val roomInfo by gameViewModel.roomInfo.collectAsState()
    val mapData by gameViewModel.mapData.collectAsState()
    val gameLog by gameViewModel.gameLog.collectAsState()
    val player by gameViewModel.player.collectAsState()
    val roomEntities by gameViewModel.roomEntities.collectAsState()
    val attackMode by gameViewModel.attackMode.collectAsState()
    val selectedTargetId by gameViewModel.selectedTargetId.collectAsState()
    val showInventory by gameViewModel.showInventory.collectAsState()
    val showEquipmentState by gameViewModel.showEquipment.collectAsState()
    val itemCatalog by gameViewModel.itemCatalog.collectAsState()
    val roomGroundItems by gameViewModel.roomGroundItems.collectAsState()
    val roomGroundCoins by gameViewModel.roomGroundCoins.collectAsState()
    val activeEffects by gameViewModel.activeEffects.collectAsState()
    val isHidden by gameViewModel.isHidden.collectAsState()
    val classCatalog by gameViewModel.classCatalog.collectAsState()
    val readiedSpellId by gameViewModel.readiedSpellId.collectAsState()
    val spellSlots by gameViewModel.spellSlots.collectAsState()
    val spellCatalogState by gameViewModel.spellCatalog.collectAsState()
    val trackedDirection by gameViewModel.trackedDirection.collectAsState()
    val visitedRooms by gameViewModel.visitedRooms.collectAsState()
    val showMap by gameViewModel.showMap.collectAsState()
    val showAtlas by gameViewModel.showAtlas.collectAsState()

    val showPartyPanel by gameViewModel.showPartyPanel.collectAsState()
    val isInParty by gameViewModel.isInParty.collectAsState()
    val partyMembers by gameViewModel.partyMembers.collectAsState()
    val followTarget by gameViewModel.followTarget.collectAsState()
    val followState by gameViewModel.followState.collectAsState()

    // Determine if player has TRACK / KICK skills
    val hasTrackSkill = player?.characterClass?.let { classCatalog[it] }?.skills?.contains("TRACK") == true
    val hasKickSkill = player?.characterClass?.let { classCatalog[it] }?.skills?.contains("KICK") == true

    Row(modifier = Modifier.fillMaxSize().background(StoneTheme.panelBg)) {
        // Left column (~50%): Room view (weighted) + Controls (intrinsic height)
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
        ) {
            // Room view — fills available space above controls
            ThemedFrame(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                cornerAccent = true
            ) {
                // Layer 1: Full-bleed background image
                val currentRoom = roomInfo?.room
                key(currentRoom?.id) {
                    RoomBackground(
                        imageUrl = currentRoom?.backgroundImage ?: "",
                        roomName = currentRoom?.name ?: "",
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Layer 1.5: NPC & PC & item sprites overlaid on background
                SpriteOverlay(
                    npcs = roomEntities,
                    players = roomPlayers,
                    groundItems = roomGroundItems,
                    groundCoins = roomGroundCoins,
                    itemCatalog = itemCatalog,
                    selectedTargetId = selectedTargetId,
                    onSelectTarget = { npcId ->
                    // Quest/lore NPCs (Phase 8) dispatch to dialogue rather than combat selection.
                    val npc = npcId?.let { id -> roomEntities.find { it.id == id } }
                    if (npc?.behaviorType == "quest") gameViewModel.talkToNpc(npc.id)
                    else gameViewModel.selectTarget(npcId)
                },
                    onPickupItem = { itemId, qty -> gameViewModel.pickupItem(itemId, qty) },
                    onPickupCoins = { coinType -> gameViewModel.pickupCoins(coinType) },
                    onPlayerTap = onPlayerTap,
                    onPlayerLongPress = onPlayerLongPress,
                    readiedSpellId = readiedSpellId,
                    onCastSpell = { spellId, targetId -> gameViewModel.castSpellOnTarget(spellId, targetId) },
                    spellSlots = spellSlots,
                    spellCatalog = spellCatalogState,
                    classCatalog = classCatalog,
                    playerCharacterClass = player?.characterClass,
                    onAttackTarget = { gameViewModel.attackTarget(it) },
                    onTrackTarget = if (hasTrackSkill) { npcId: String -> gameViewModel.useSkill("TRACK", npcId) } else null,
                    onKickTarget = if (hasKickSkill) { npcId: String -> gameViewModel.showKickDirectionPicker(npcId) } else null,
                    partyMembersInfo = partyMembers.associateBy { it.name },
                    partyLeaderName = partyMembers.firstOrNull { it.isLeader }?.name,
                    modifier = Modifier.fillMaxSize()
                )

                // Layer 2: Floating minimap
                val data = mapData
                if (data != null) {
                    key(data.playerRoomId) {
                        FloatingMiniMap(
                            rooms = data.rooms,
                            playerRoomId = data.playerRoomId,
                            visitedRoomIds = visitedRooms,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(end = 4.dp, top = 4.dp)
                        )
                    }
                }

                // Layer 2.5: Party HUD (top-left, below minimap)
                if (partyMembers.size > 1) {
                    PartyHudOverlay(
                        members = partyMembers,
                        playerName = player?.name,
                        followTarget = followTarget,
                        followState = followState,
                        onOpenPartyPanel = { gameViewModel.togglePartyPanel() },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(start = 4.dp, top = 4.dp)
                    )
                }

                // Layer 3: Trainer/Vendor/Crafter/Interactable overlays (bottom-left of room view)
                val interactables = roomInfo?.room?.interactables ?: emptyList()
                if (showTrainerButton || showVendorButton || showCrafterButton || interactables.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 8.dp, bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (showTrainerButton) {
                            RoomOverlayButton(
                                icon = MudIcons.Trainer,
                                label = "Train",
                                color = Color(0xFFFFD700),
                                onClick = { gameViewModel.interactTrainer() }
                            )
                        }
                        if (showVendorButton) {
                            RoomOverlayButton(
                                icon = MudIcons.Vendor,
                                label = "Shop",
                                color = Color(0xFFCC8833),
                                onClick = { gameViewModel.interactVendor() }
                            )
                        }
                        if (showCrafterButton) {
                            RoomOverlayButton(
                                icon = MudIcons.Crafter,
                                label = "Craft",
                                color = Color(0xFFAA6B3A),
                                onClick = { gameViewModel.interactCrafter() }
                            )
                        }
                        for (feat in interactables) {
                            key(feat.id) {
                                val (icon, color) = interactableStyle(feat)
                                RoomOverlayButton(
                                    icon = icon,
                                    label = feat.label,
                                    color = color,
                                    onClick = { gameViewModel.interactFeature(feat.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Controls area — intrinsic height, no weight
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StoneTheme.panelBg)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                // Player status panel (compact in landscape)
                val p = player
                if (p != null && p.maxHp > 0) {
                    PlayerStatusPanel(
                        player = p,
                        activeEffects = activeEffects,
                        isHidden = isHidden,
                        isMeditating = gameViewModel.isMeditating.collectAsState().value,
                        compact = true,
                        onClick = { gameViewModel.toggleCharacterSheet() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }

                // D-pad and action buttons side by side
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    DirectionPad(
                        availableExits = availableExits,
                        onMove = { direction -> gameViewModel.move(direction) },
                        onLook = { gameViewModel.look() },
                        lockedExits = (roomInfo?.room?.lockedExits?.keys ?: emptySet()),
                        trackedDirection = trackedDirection
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Action buttons + utility icons in a column
                    Column(
                        horizontalAlignment = Alignment.End,
                        modifier = Modifier.weight(1f)
                    ) {
                        ActionButtonRow(
                            gameViewModel = gameViewModel,
                            attackMode = attackMode,
                            hasHostiles = hasHostiles,
                            showInventory = showInventory,
                            showEquipment = showEquipmentState,
                            player = null, // Status is above in landscape
                            activeEffects = activeEffects,
                            isHidden = isHidden,
                            isMeditating = gameViewModel.isMeditating.collectAsState().value,
                            isResting = gameViewModel.isResting.collectAsState().value,
                            spellSlots = spellSlots,
                            spellCatalog = spellCatalogState,
                            readiedSpellId = readiedSpellId,
                            classCatalog = classCatalog,
                            playerCharacterClass = player?.characterClass,
                            currentMp = player?.currentMp ?: 0,
                            skillCatalog = gameViewModel.skillCatalog.collectAsState().value
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            CharacterSheetIconButton(onClick = { gameViewModel.toggleCharacterSheet() })
                            SpellUtilityButton(classCatalog, player?.characterClass) {
                                gameViewModel.openSpellPicker(0)
                            }
                            MapIconButton(
                                active = showAtlas,
                                onClick = { gameViewModel.toggleAtlas() }
                            )
                            InventoryIconButton(
                                active = showInventory,
                                onClick = { gameViewModel.toggleInventory() }
                            )
                            EquipmentIconButton(
                                active = showEquipmentState,
                                onClick = { gameViewModel.toggleEquipment() }
                            )
                            PartyIconButton(
                                active = showPartyPanel,
                                isInParty = isInParty,
                                onClick = { gameViewModel.togglePartyPanel() }
                            )
                            HelpIconButton(onClick = { gameViewModel.toggleHelp() })
                            SettingsGearButton(onClick = { gameViewModel.toggleSettings() })
                        }
                    }
                }
            }
        }

        // Vertical stone divider between columns
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(3.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            StoneTheme.frameLight,
                            StoneTheme.frameMid,
                            StoneTheme.innerShadow
                        )
                    )
                )
        )

        // Right column (~50%): Game log (weighted) + Say bar (intrinsic height)
        Column(
            modifier = Modifier
                .weight(0.5f)
                .fillMaxHeight()
        ) {
            ThemedFrame(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                cornerAccent = false
            ) {
                GameLog(entries = gameLog)
            }

            SayBar(
                sayText = sayText,
                onSayTextChange = onSayTextChange,
                onSay = { gameViewModel.say(it) },
                isAdmin = player?.isAdmin == true,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

/** Skill icon/color mapping for action buttons */
private data class SkillButtonInfo(
    val icon: DrawableResource,
    val activeColor: Color
)

private val SKILL_BUTTON_MAP = mapOf(
    "BASH" to SkillButtonInfo(MudIcons.Bash, Color(0xFFFF8833)),
    "KICK" to SkillButtonInfo(MudIcons.Kick, Color(0xFFFF5533)),
    "SNEAK" to SkillButtonInfo(MudIcons.Sneak, Color(0xFF888888)),
    "MEDITATE" to SkillButtonInfo(MudIcons.Meditate, Color(0xFF7755CC)),
    "TRACK" to SkillButtonInfo(MudIcons.Track, Color(0xFF55AA55)),
    "PICK_LOCK" to SkillButtonInfo(MudIcons.PickLock, Color(0xFFCCAA33)),
    "REST" to SkillButtonInfo(MudIcons.Rest, Color(0xFF55AA77))
)

@Composable
private fun ActionButton(
    icon: DrawableResource,
    color: Color,
    isActive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    StoneButton(
        icon = icon,
        color = color,
        isActive = isActive,
        enabled = enabled,
        onClick = onClick
    )
}

@Composable
private fun ActionButtonRow(
    gameViewModel: GameViewModel,
    attackMode: Boolean,
    hasHostiles: Boolean,
    showInventory: Boolean,
    showEquipment: Boolean = false,
    player: com.neomud.shared.model.Player?,
    activeEffects: List<com.neomud.shared.model.ActiveEffect>,
    isHidden: Boolean = false,
    isMeditating: Boolean = false,
    isResting: Boolean = false,
    spellSlots: List<String?> = listOf(null, null, null, null),
    spellCatalog: Map<String, com.neomud.shared.model.SpellDef> = emptyMap(),
    readiedSpellId: String? = null,
    classCatalog: Map<String, com.neomud.shared.model.CharacterClassDef> = emptyMap(),
    playerCharacterClass: String? = player?.characterClass,
    currentMp: Int = player?.currentMp ?: 0,
    skillCatalog: Map<String, com.neomud.shared.model.SkillDef> = emptyMap()
) {
    val classDef = playerCharacterClass?.let { classCatalog[it] }
    val hasMagic = classDef?.magicSchools?.isNotEmpty() == true

    Column {
    // Row 1: Action skill buttons (class-driven)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Attack button — shows as Backstab (dagger) when hidden + class has BACKSTAB
        val hasBackstab = classDef?.skills?.contains("BACKSTAB") == true
        val showBackstab = isHidden && hasBackstab
        val attackColor = when {
            showBackstab -> Color(0xFFCC3333)  // Dark red for backstab
            attackMode -> Color(0xFFFF3333)
            hasHostiles -> MaterialTheme.colorScheme.primary
            else -> Color.Gray
        }
        Box(modifier = Modifier.coachMarkTarget("attack_button", gameViewModel)) {
            val attackTooltip = when {
                showBackstab -> "Backstab"
                attackMode -> "Disengage"
                else -> "Attack"
            }
            StoneTooltip(attackTooltip) {
            ActionButton(
                icon = MudIcons.Attack,
                color = attackColor,
                isActive = attackMode,
                enabled = hasHostiles || attackMode || showBackstab,
                onClick = { gameViewModel.toggleAttackMode(!attackMode) }
            )
            }
        }

        // Class skill buttons (filtered: non-passive)
        val classSkills = classDef?.skills ?: emptyList()
        for (skillId in classSkills) {
            val skillDef = skillCatalog[skillId]
            if (skillDef?.isPassive == true) continue

            val btnInfo = SKILL_BUTTON_MAP[skillId] ?: continue

            key(skillId) {
            val skillName = skillDef?.name ?: skillId.lowercase().replaceFirstChar { it.uppercase() }
            // SNEAK is a special toggle
            if (skillId == "SNEAK") {
                val sneakEnabled = !attackMode || isHidden
                val sneakTooltip = if (isHidden) "Reveal" else skillName
                Box(modifier = Modifier.coachMarkTarget("sneak_button", gameViewModel)) {
                    StoneTooltip(sneakTooltip) {
                    ActionButton(
                        icon = btnInfo.icon,
                        color = if (isHidden) MudColors.stealth else btnInfo.activeColor,
                        isActive = isHidden,
                        enabled = sneakEnabled,
                        onClick = { gameViewModel.toggleSneakMode(!isHidden) }
                    )
                    }
                }
            } else if (skillId == "MEDITATE") {
                val medTooltip = if (isMeditating) "Stop Meditating" else skillName
                StoneTooltip(medTooltip) {
                ActionButton(
                    icon = btnInfo.icon,
                    color = if (isMeditating) MudColors.spell else btnInfo.activeColor,
                    isActive = isMeditating,
                    enabled = !attackMode || isMeditating,
                    onClick = { gameViewModel.useSkill("MEDITATE") }
                )
                }
            } else if (skillId == "REST") {
                val restTooltip = if (isResting) "Stand Up" else skillName
                StoneTooltip(restTooltip) {
                ActionButton(
                    icon = btnInfo.icon,
                    color = if (isResting) MudColors.friendly else btnInfo.activeColor,
                    isActive = isResting,
                    enabled = !attackMode || isResting,
                    onClick = { gameViewModel.useSkill("REST") }
                )
                }
            } else if (skillId == "PICK_LOCK") {
                StoneTooltip(skillName) {
                ActionButton(
                    icon = btnInfo.icon,
                    color = btnInfo.activeColor,
                    onClick = { gameViewModel.showLockTargetPicker() }
                )
                }
            } else if (skillId == "KICK") {
                StoneTooltip(skillName) {
                ActionButton(
                    icon = btnInfo.icon,
                    color = btnInfo.activeColor,
                    onClick = { gameViewModel.showKickDirectionPicker() }
                )
                }
            } else {
                StoneTooltip(skillName) {
                ActionButton(
                    icon = btnInfo.icon,
                    color = btnInfo.activeColor,
                    onClick = { gameViewModel.useSkill(skillId) }
                )
                }
            }
            }
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Player status panel (only in portrait — landscape puts it above)
        if (player != null && player.maxHp > 0) {
            PlayerStatusPanel(
                player = player,
                activeEffects = activeEffects,
                isHidden = isHidden,
                isMeditating = isMeditating,
                onClick = { gameViewModel.toggleCharacterSheet() },
                modifier = Modifier.weight(1f)
            )
        }
    }

    // Row 2: Spell bar (magic classes only)
    if (hasMagic) {
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.coachMarkTarget("spell_bar", gameViewModel)) {
            SpellBar(
                spellSlots = spellSlots,
                spellCatalog = spellCatalog,
                readiedSpellId = readiedSpellId,
                currentMp = currentMp,
                onReadySpell = { gameViewModel.readySpell(it) },
                onOpenSpellPicker = { gameViewModel.openSpellPicker(it) }
            )
        }
    }
    } // end Column
}

private fun interactableStyle(feat: RoomInteractable): Pair<DrawableResource, Color> {
    return when (feat.actionType) {
        "EXIT_OPEN"      -> MudIcons.ExitOpen to Color(0xFFFFAA00)
        "TREASURE_DROP"  -> MudIcons.TreasureDrop to Color(0xFFFFD700)
        "MONSTER_SPAWN"  -> MudIcons.MonsterSpawn to Color(0xFFFF4444)
        "ROOM_EFFECT"    -> MudIcons.RoomEffect to Color(0xFF44DDFF)
        "TELEPORT"       -> MudIcons.Teleport to Color(0xFFAA44FF)
        else             -> MudIcons.InteractDefault to Color(0xFFAAAAAA)
    }
}

@Composable
private fun RoomOverlayButton(
    icon: DrawableResource,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = Color(0xCC1a1a2e.toInt()),
        border = BorderStroke(1.dp, color.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
private fun SpellUtilityButton(
    classCatalog: Map<String, com.neomud.shared.model.CharacterClassDef>,
    playerCharacterClass: String?,
    onClick: () -> Unit
) {
    val classDef = playerCharacterClass?.let { classCatalog[it] }
    val hasMagic = classDef?.magicSchools?.isNotEmpty() == true
    if (!hasMagic) return

    val primarySchool = classDef?.magicSchools?.keys?.firstOrNull() ?: ""
    val spellColor = com.neomud.client.ui.components.schoolColor(primarySchool)
    val stoneBg = Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark))
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(stoneBg, RoundedCornerShape(4.dp))
            .border(1.dp, StoneTheme.frameMid, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(MudIcons.schoolIcon(primarySchool)),
            contentDescription = "Spells",
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun CharacterSheetIconButton(onClick: () -> Unit) {
    val stoneBg = Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark))
    StoneTooltip("Character Sheet") {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(stoneBg, RoundedCornerShape(4.dp))
            .border(1.dp, StoneTheme.frameMid, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(MudIcons.CharacterSheet),
            contentDescription = "Character Sheet",
            modifier = Modifier.size(22.dp)
        )
    }
    }
}

@Composable
private fun InventoryIconButton(active: Boolean, onClick: () -> Unit) {
    val stoneBg = Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark))
    StoneTooltip("Inventory") {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(stoneBg, RoundedCornerShape(4.dp))
            .border(1.dp, StoneTheme.frameMid, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(MudIcons.Inventory),
            contentDescription = "Inventory",
            modifier = Modifier.size(22.dp)
        )
    }
    }
}

@Composable
private fun EquipmentIconButton(active: Boolean, onClick: () -> Unit) {
    val stoneBg = Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark))
    StoneTooltip("Equipment") {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(stoneBg, RoundedCornerShape(4.dp))
            .border(1.dp, StoneTheme.frameMid, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(MudIcons.Equipment),
            contentDescription = "Equipment",
            modifier = Modifier.size(22.dp)
        )
    }
    }
}

@Composable
private fun MapIconButton(active: Boolean, onClick: () -> Unit) {
    val stoneBg = Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark))
    StoneTooltip("World Atlas") {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(stoneBg, RoundedCornerShape(4.dp))
            .border(1.dp, StoneTheme.frameMid, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(MudIcons.Map),
            contentDescription = "Map",
            modifier = Modifier.size(22.dp)
        )
    }
    }
}

@Composable
private fun PartyIconButton(active: Boolean, isInParty: Boolean, onClick: () -> Unit) {
    val stoneBg = Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark))
    val label = if (isInParty) "Party" else "Party (none)"
    StoneTooltip(label) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(stoneBg, RoundedCornerShape(4.dp))
            .border(
                1.dp,
                if (isInParty) Color(0xFF55AAFF) else StoneTheme.frameMid,
                RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "P",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (isInParty) Color(0xFF55AAFF) else Color(0xFF999999)
        )
    }
    }
}

@Composable
private fun HelpIconButton(onClick: () -> Unit) {
    val stoneBg = Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark))
    StoneTooltip("Adventurer's Tome") {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(stoneBg, RoundedCornerShape(4.dp))
            .border(1.dp, StoneTheme.frameMid, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(MudIcons.Help),
            contentDescription = "Help",
            modifier = Modifier.size(22.dp)
        )
    }
    }
}

@Composable
private fun SettingsGearButton(onClick: () -> Unit) {
    val stoneBg = Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark))
    StoneTooltip("Settings") {
    Box(
        modifier = Modifier
            .size(28.dp)
            .background(stoneBg, RoundedCornerShape(4.dp))
            .border(1.dp, StoneTheme.frameMid, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(MudIcons.Settings),
            contentDescription = "Settings",
            modifier = Modifier.size(22.dp)
        )
    }
    }
}

private val ADMIN_COMMANDS = listOf(
    "/broadcast", "/godmode", "/grantcp", "/grantitem", "/grantxp",
    "/heal", "/help", "/kill", "/setlevel", "/setstat", "/shutdown", "/spawn", "/teleport"
)

@Composable
private fun SayBar(
    sayText: String,
    onSayTextChange: (String) -> Unit,
    onSay: (String) -> Unit,
    isAdmin: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Internal TextFieldValue for selection control
    var tfv by remember { mutableStateOf(TextFieldValue(sayText)) }

    // Sync external sayText changes (e.g. cleared after send) into our TextFieldValue
    LaunchedEffect(sayText) {
        if (sayText != tfv.text) {
            tfv = TextFieldValue(sayText, TextRange(sayText.length))
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = tfv,
            onValueChange = { newTfv ->
                val newText = newTfv.text
                val cursorPos = newTfv.selection.start

                // Determine if the user is typing forward (not deleting/navigating)
                val isTypingForward = newText.length > tfv.text.length ||
                    (newText.length == tfv.text.length && newTfv.selection.collapsed && !tfv.selection.collapsed)

                if (isAdmin && newText.startsWith("/") && !newText.contains(" ") && isTypingForward && newTfv.selection.collapsed) {
                    val prefix = newText.lowercase()
                    val match = ADMIN_COMMANDS.firstOrNull { it.startsWith(prefix) && it != prefix }
                    if (match != null) {
                        // Fill in the completion with the suffix selected
                        tfv = TextFieldValue(
                            text = match,
                            selection = TextRange(cursorPos, match.length)
                        )
                        onSayTextChange(match)
                        return@BasicTextField
                    }
                }

                tfv = newTfv
                onSayTextChange(newText)
            },
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.sp,
                color = if (isAdmin && tfv.text.startsWith("/")) Color(0xFF1565C0) else Color(0xFFCCCCCC)
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .height(32.dp)
                .background(StoneTheme.textPanelBg, RoundedCornerShape(6.dp))
                .drawBehind {
                    // Beveled stone border
                    val w = size.width; val h = size.height
                    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
                    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(0f, h), strokeWidth = 1f)
                    drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), strokeWidth = 1f)
                    drawLine(StoneTheme.innerShadow, Offset(w - 1f, 0f), Offset(w - 1f, h), strokeWidth = 1f)
                }
                .padding(horizontal = 8.dp),
            decorationBox = { innerTextField ->
                Box(
                    contentAlignment = Alignment.CenterStart,
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (tfv.text.isEmpty()) {
                        Text("Say...", fontSize = 13.sp, color = Color(0xFF777777))
                    }
                    innerTextField()
                }
            }
        )
        Spacer(modifier = Modifier.width(4.dp))
        val sayEnabled = tfv.text.isNotBlank()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .height(32.dp)
                .drawBehind {
                    val bgBrush = Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark))
                    drawRect(bgBrush)
                    val w = size.width; val h = size.height
                    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(w, 0f), strokeWidth = 1f)
                    drawLine(StoneTheme.frameLight, Offset(0f, 0f), Offset(0f, h), strokeWidth = 1f)
                    drawLine(StoneTheme.innerShadow, Offset(0f, h - 1f), Offset(w, h - 1f), strokeWidth = 1f)
                    drawLine(StoneTheme.innerShadow, Offset(w - 1f, 0f), Offset(w - 1f, h), strokeWidth = 1f)
                }
                .clickable(enabled = sayEnabled) {
                    val text = tfv.text
                    if (text.isNotBlank()) {
                        onSay(text)
                        onSayTextChange("")
                    }
                }
                .padding(horizontal = 12.dp)
        ) {
            Text(
                "Say",
                fontSize = 12.sp,
                color = if (sayEnabled) Color(0xFFE0E0E0) else Color(0xFF666666)
            )
        }
    }
}
