package com.neomud.client.ui.screens

import com.neomud.client.viewmodel.AuthViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.crossfade
import androidx.compose.foundation.Image
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.client.viewmodel.AuthState
import neomud.client.generated.resources.Res
import neomud.client.generated.resources.splash_forge
import org.jetbrains.compose.resources.painterResource
import com.neomud.shared.model.CharacterClassDef
import com.neomud.shared.model.RaceDef
import com.neomud.shared.model.StatAllocator
import com.neomud.shared.model.Stats

// ─────────────────────────────────────────────
// Palette — Stone & Torchlight
// ─────────────────────────────────────────────
private val DeepVoid = Color(0xFF080604)
private val IronDark = Color(0xFF0D0B09)
private val WornLeather = Color(0xFF1A1510)
private val BurnishedGold = Color(0xFFCCA855)
private val TorchAmber = Color(0xFFBBA060)
private val EmberOrange = Color(0xFFAA6B3A)
private val BoneWhite = Color(0xFFD8CCAA)
private val AshGray = Color(0xFF5A5040)
private val CrimsonError = Color(0xFFCC4444)
private val VerdantUpgrade = Color(0xFF44CC55)
private val FrostSteel = Color(0xFF7090AA)
private val MagicPurple = Color(0xFF9B7BCC)
private val FilledSlotEdge = Color(0xFF7A6545)
private val EmptySlotEdge = Color(0xFF2A2218)

// ─────────────────────────────────────────────
// Stone frame drawing
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

@Composable
fun RegistrationScreen(
    authState: AuthState,
    availableClasses: List<CharacterClassDef>,
    availableRaces: List<RaceDef> = emptyList(),
    serverBaseUrl: String = "",
    nameAvailability: AuthViewModel.NameAvailability? = null,
    isGuestMode: Boolean = false,
    onRegister: (String, String, String, String, String, String, Stats) -> Unit,
    onCheckName: (String, String) -> Unit = { _, _ -> },
    onClearNameCheck: () -> Unit = {},
    onBack: () -> Unit,
    onClearError: () -> Unit
) {
    var showGuestWarning by rememberSaveable { mutableStateOf(isGuestMode) }
    var currentStep by rememberSaveable { mutableIntStateOf(0) }
    var username by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var characterName by rememberSaveable { mutableStateOf("") }
    var selectedGender by rememberSaveable { mutableStateOf("neutral") }
    var selectedRaceId by rememberSaveable { mutableStateOf("HUMAN") }
    var selectedClassId by rememberSaveable { mutableStateOf("WARRIOR") }
    var allocatedStats by remember { mutableStateOf<Stats?>(null) }

    val selectedClass = availableClasses.find { it.id == selectedClassId }
    val selectedRace = availableRaces.find { it.id == selectedRaceId }

    fun effectiveMin(classDef: CharacterClassDef?, raceDef: RaceDef?): Stats {
        val base = classDef?.minimumStats ?: return Stats()
        val mods = raceDef?.statModifiers ?: return base
        return Stats(
            strength = maxOf(1, base.strength + mods.strength),
            agility = maxOf(1, base.agility + mods.agility),
            intellect = maxOf(1, base.intellect + mods.intellect),
            willpower = maxOf(1, base.willpower + mods.willpower),
            health = maxOf(1, base.health + mods.health),
            charm = maxOf(1, base.charm + mods.charm)
        )
    }

    fun defaultAllocation(classDef: CharacterClassDef?, raceDef: RaceDef?): Stats {
        val min = effectiveMin(classDef, raceDef)
        return Stats(
            strength = min.strength + 10,
            agility = min.agility + 10,
            intellect = min.intellect + 10,
            willpower = min.willpower + 10,
            health = min.health + 10,
            charm = min.charm + 10
        )
    }

    LaunchedEffect(selectedClassId, selectedRaceId, availableClasses, availableRaces) {
        if (selectedClass != null) {
            allocatedStats = defaultAllocation(selectedClass, selectedRace)
        }
    }

    // Full-screen splash background
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Splash background image
        Image(
            painter = painterResource(Res.drawable.splash_forge),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        // Dark scrim overlay for readability (50% to let forge glow bleed through)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )
        // Main stone-framed card (safe area padding keeps card clear of notch/home indicator)
        val borderPx = 4.dp
        Box(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
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
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = if (isGuestMode) "Guest Character" else "Create Character",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = BurnishedGold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Gold ornamental line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, BurnishedGold.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Step indicator
                val stepLabels = if (isGuestMode)
                    listOf("Name", "Gender", "Race", "Class", "Stats", "Review")
                else
                    listOf("Acct", "Gender", "Race", "Class", "Stats", "Review")
                StoneStepIndicator(currentStep = currentStep, totalSteps = 6, stepLabels = stepLabels)

                Spacer(modifier = Modifier.height(10.dp))

                // Step content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (currentStep) {
                        0 -> if (isGuestMode) {
                            GuestNameStep(
                                characterName = characterName,
                                nameAvailability = nameAvailability,
                                onCharacterNameChange = { characterName = it; onClearNameCheck() },
                                onCheckName = { onCheckName("", it) }
                            )
                        } else {
                            CredentialsStep(
                                username = username,
                                password = password,
                                characterName = characterName,
                                nameAvailability = nameAvailability,
                                onUsernameChange = { username = it; onClearNameCheck() },
                                onPasswordChange = { password = it },
                                onCharacterNameChange = { characterName = it; onClearNameCheck() },
                                onCheckName = onCheckName
                            )
                        }
                        1 -> GenderSelectionStep(
                            selectedGender = selectedGender,
                            onGenderSelected = { selectedGender = it }
                        )
                        2 -> RaceSelectionStep(
                            availableRaces = availableRaces,
                            selectedRaceId = selectedRaceId,
                            onRaceSelected = { selectedRaceId = it }
                        )
                        3 -> ClassSelectionStep(
                            availableClasses = availableClasses,
                            selectedClassId = selectedClassId,
                            onClassSelected = {
                                selectedClassId = it
                                val cls = availableClasses.find { c -> c.id == it }
                                if (cls != null) allocatedStats = defaultAllocation(cls, selectedRace)
                            }
                        )
                        4 -> {
                            val effMin = effectiveMin(selectedClass, selectedRace)
                            StatAllocationStep(
                                effectiveMinimum = effMin,
                                allocatedStats = allocatedStats ?: effMin,
                                onStatsChanged = { allocatedStats = it }
                            )
                        }
                        5 -> CharacterPreviewStep(
                            characterName = characterName,
                            selectedRace = selectedRace,
                            selectedClass = selectedClass,
                            selectedGender = selectedGender,
                            allocatedStats = allocatedStats ?: effectiveMin(selectedClass, selectedRace),
                            serverBaseUrl = serverBaseUrl
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Error display — shown above nav buttons so it's immediately visible
                if (authState is AuthState.Error) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = authState.message,
                        fontSize = 12.sp,
                        color = CrimsonError,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Dismiss",
                        fontSize = 12.sp,
                        color = AshGray,
                        modifier = Modifier
                            .clickable(onClick = onClearError)
                            .padding(vertical = 4.dp)
                    )
                }

                // Navigation buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Back button (stone/iron style)
                    StoneNavButton(
                        text = if (currentStep > 0) "Back" else "Back to Login",
                        onClick = { if (currentStep > 0) currentStep-- else onBack() },
                        modifier = Modifier.weight(1f),
                        style = ButtonStyle.IRON
                    )

                    if (currentStep < 5) {
                        val canAdvance = when (currentStep) {
                            0 -> if (isGuestMode) characterName.isNotBlank()
                                 else username.isNotBlank() && password.isNotBlank() && characterName.isNotBlank()
                            1 -> true
                            2 -> availableRaces.isNotEmpty()
                            3 -> availableClasses.isNotEmpty()
                            4 -> {
                                val effMin = effectiveMin(selectedClass, selectedRace)
                                val stats = allocatedStats ?: effMin
                                StatAllocator.totalCpUsed(stats, effMin) == StatAllocator.CP_POOL
                            }
                            else -> true
                        }
                        StoneNavButton(
                            text = if (!isGuestMode && currentStep == 0 && nameAvailability == null && username.isNotBlank()) "Check & Next" else "Next",
                            onClick = {
                                if (currentStep == 0 && !isGuestMode) {
                                    // Check name availability before advancing
                                    if (nameAvailability == null) {
                                        onCheckName(username, characterName)
                                        return@StoneNavButton
                                    }
                                    if (nameAvailability.usernameAvailable && nameAvailability.characterNameAvailable) {
                                        currentStep++
                                    }
                                    // If names aren't available, the feedback shows inline — don't advance
                                } else {
                                    currentStep++
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = canAdvance,
                            style = ButtonStyle.GOLD
                        )
                    } else {
                        val effMin = effectiveMin(selectedClass, selectedRace)
                        val stats = allocatedStats ?: effMin
                        val cpUsed = StatAllocator.totalCpUsed(stats, effMin)
                        val createEnabled = authState !is AuthState.Loading &&
                                cpUsed == StatAllocator.CP_POOL &&
                                characterName.isNotBlank() &&
                                (isGuestMode || (username.isNotBlank() && password.isNotBlank()))

                        StoneNavButton(
                            text = if (authState is AuthState.Loading) "Creating..."
                                   else if (isGuestMode) "Play as Guest"
                                   else "Create Character",
                            onClick = {
                                onRegister(username, password, characterName, selectedClassId, selectedRaceId, selectedGender, stats)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = createEnabled,
                            style = ButtonStyle.VERDANT
                        )
                    }
                }

            }
        }
    }

    // Guest warning overlay — shown on entry before character creation begins
    if (showGuestWarning) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth(0.85f)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF1A1510), Color(0xFF0D0B09))
                        ),
                        RoundedCornerShape(8.dp)
                    )
                    .border(1.dp, BurnishedGold.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Guest Play",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = BurnishedGold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Guest characters are temporary. All progress, items, and XP will be permanently lost when you disconnect.",
                    fontSize = 13.sp,
                    color = BoneWhite,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Sign in to save your progress.",
                    fontSize = 12.sp,
                    color = AshGray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Continue as Guest
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF3A2A15), Color(0xFF2A1A0A))
                            ),
                            RoundedCornerShape(6.dp)
                        )
                        .border(1.dp, BurnishedGold.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .clickable(onClick = { showGuestWarning = false }),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Continue as Guest", fontSize = 13.sp, color = BurnishedGold)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Back / Sign In
                Text(
                    text = "Back",
                    fontSize = 12.sp,
                    color = AshGray,
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 4.dp)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Step Indicator — stone chain dots
// ─────────────────────────────────────────────
@Composable
private fun StoneStepIndicator(
    currentStep: Int,
    totalSteps: Int,
    stepLabels: List<String> = listOf("Acct", "Gender", "Race", "Class", "Stats", "Review")
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until totalSteps) {
            val isActive = i == currentStep
            val isDone = i < currentStep
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(
                            when {
                                isActive -> Brush.radialGradient(
                                    listOf(BurnishedGold, EmberOrange)
                                )
                                isDone -> Brush.radialGradient(
                                    listOf(TorchAmber.copy(alpha = 0.6f), AshGray)
                                )
                                else -> Brush.radialGradient(
                                    listOf(StoneTheme.frameMid, StoneTheme.frameDark)
                                )
                            },
                            RoundedCornerShape(11.dp)
                        )
                        .border(
                            1.dp,
                            when {
                                isActive -> BurnishedGold
                                isDone -> FilledSlotEdge
                                else -> EmptySlotEdge
                            },
                            RoundedCornerShape(11.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isDone) "\u2713" else "${i + 1}",
                        color = when {
                            isActive -> DeepVoid
                            isDone -> BoneWhite
                            else -> AshGray
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = stepLabels[i],
                    fontSize = 8.sp,
                    color = if (isActive) BurnishedGold else AshGray.copy(alpha = 0.7f)
                )
            }
            if (i < totalSteps - 1) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(1.dp)
                        .background(
                            if (i < currentStep) FilledSlotEdge
                            else EmptySlotEdge
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Stone-styled text field (reused from LoginScreen pattern)
// ─────────────────────────────────────────────
@Composable
private fun StoneTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    onFocusLost: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 11.sp,
            color = AshGray,
            modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = if (onFocusLost != null) {
                Modifier.onFocusChanged { if (!it.isFocused) onFocusLost() }
            } else Modifier,
            textStyle = TextStyle(
                fontSize = 14.sp,
                color = BoneWhite
            ),
            cursorBrush = SolidColor(BurnishedGold),
            visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DeepVoid, RoundedCornerShape(4.dp))
                        .border(1.dp, AshGray.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                        .drawBehind {
                            val w = size.width; val h = size.height
                            drawLine(StoneTheme.innerShadow, Offset(1f, h - 1f), Offset(w - 1f, h - 1f), 1f)
                            drawLine(StoneTheme.innerShadow, Offset(w - 1f, 1f), Offset(w - 1f, h - 1f), 1f)
                        }
                        .padding(horizontal = 10.dp, vertical = 10.dp)
                ) {
                    if (value.isEmpty()) {
                        Text(
                            label,
                            fontSize = 14.sp,
                            color = AshGray.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            }
        )
    }
}

// ─────────────────────────────────────────────
// Selection card — replaces Material3 Card
// ─────────────────────────────────────────────
@Composable
private fun StoneSelectionCard(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val bg = if (isSelected)
        Brush.horizontalGradient(listOf(Color(0xFF1E1810), Color(0xFF14110E), DeepVoid))
    else
        Brush.horizontalGradient(listOf(Color(0xFF14110E), DeepVoid))

    val borderColor = if (isSelected) BurnishedGold else EmptySlotEdge

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .drawBehind {
                val w = size.width; val h = size.height
                // Top edge highlight
                drawLine(AshGray.copy(alpha = 0.15f), Offset(6f, 0f), Offset(w - 6f, 0f), 1f)
                // Bottom shadow
                drawLine(StoneTheme.innerShadow, Offset(6f, h - 1f), Offset(w - 6f, h - 1f), 1f)
                // Selected inner glow
                if (isSelected) {
                    drawLine(BurnishedGold.copy(alpha = 0.2f), Offset(1f, 1f), Offset(w - 1f, 1f), 1f)
                    drawLine(BurnishedGold.copy(alpha = 0.1f), Offset(1f, 1f), Offset(1f, h - 1f), 1f)
                }
            }
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            content = content
        )
    }
}

// ─────────────────────────────────────────────
// Button styles
// ─────────────────────────────────────────────
private enum class ButtonStyle { GOLD, IRON, VERDANT, EMBER }

@Composable
private fun StoneNavButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    style: ButtonStyle = ButtonStyle.GOLD
) {
    val bg = if (enabled) when (style) {
        ButtonStyle.GOLD -> Brush.verticalGradient(listOf(BurnishedGold, Color(0xFF997733)))
        ButtonStyle.IRON -> Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark))
        ButtonStyle.VERDANT -> Brush.verticalGradient(listOf(VerdantUpgrade, Color(0xFF228833)))
        ButtonStyle.EMBER -> Brush.verticalGradient(listOf(EmberOrange, Color(0xFF7A4422)))
    } else {
        Brush.verticalGradient(listOf(StoneTheme.frameDark, Color(0xFF0D0A08)))
    }

    val textColor = if (enabled) when (style) {
        ButtonStyle.GOLD -> DeepVoid
        ButtonStyle.IRON -> BoneWhite
        ButtonStyle.VERDANT -> DeepVoid
        ButtonStyle.EMBER -> DeepVoid
    } else AshGray

    val accentColor = if (enabled) when (style) {
        ButtonStyle.GOLD -> BurnishedGold.copy(alpha = 0.7f)
        ButtonStyle.IRON -> StoneTheme.frameLight.copy(alpha = 0.3f)
        ButtonStyle.VERDANT -> VerdantUpgrade.copy(alpha = 0.5f)
        ButtonStyle.EMBER -> EmberOrange.copy(alpha = 0.5f)
    } else StoneTheme.frameLight.copy(alpha = 0.1f)

    Box(
        modifier = modifier
            .height(38.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .drawBehind {
                val w = size.width; val h = size.height
                drawLine(accentColor, Offset(0f, 0f), Offset(w, 0f), 1f)
                drawLine(accentColor, Offset(0f, 0f), Offset(0f, h), 1f)
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
            }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = textColor)
    }
}

// ─────────────────────────────────────────────
// +/- stat allocation button
// ─────────────────────────────────────────────
@Composable
private fun StoneStatButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean
) {
    val bg = if (enabled)
        Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameMid))
    else
        Brush.verticalGradient(listOf(StoneTheme.frameDark, Color(0xFF0D0A08)))

    Box(
        modifier = Modifier
            .size(30.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .drawBehind {
                val w = size.width; val h = size.height
                if (enabled) {
                    drawLine(StoneTheme.frameLight.copy(alpha = 0.5f), Offset(0f, 0f), Offset(w, 0f), 1f)
                    drawLine(StoneTheme.frameLight.copy(alpha = 0.5f), Offset(0f, 0f), Offset(0f, h), 1f)
                }
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
            }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (enabled) BoneWhite else AshGray.copy(alpha = 0.5f)
        )
    }
}

// ─────────────────────────────────────────────
// Runic divider
// ─────────────────────────────────────────────
@Composable
private fun RunicDivider(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, AshGray.copy(alpha = 0.4f))
                    )
                )
        )
        Text(
            "\u2726",
            fontSize = 10.sp,
            color = AshGray.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 6.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(AshGray.copy(alpha = 0.4f), Color.Transparent)
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────
// Step 0 (Guest): Character Name only
// ─────────────────────────────────────────────
@Composable
private fun GuestNameStep(
    characterName: String,
    nameAvailability: AuthViewModel.NameAvailability? = null,
    onCharacterNameChange: (String) -> Unit,
    onCheckName: (String) -> Unit = {}
) {
    val focusManager = LocalFocusManager.current
    var lastChecked by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Choose Your Name",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TorchAmber
        )
        Spacer(modifier = Modifier.height(12.dp))

        StoneTextField(
            value = characterName,
            onValueChange = onCharacterNameChange,
            label = "Character Name",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onFocusLost = {
                if (characterName.isNotBlank() && characterName != lastChecked) {
                    lastChecked = characterName
                    onCheckName(characterName)
                }
            }
        )
        if (nameAvailability != null && characterName.isNotBlank()) {
            Text(
                text = if (nameAvailability.characterNameAvailable) "Name available" else "Name already taken",
                fontSize = 11.sp,
                color = if (nameAvailability.characterNameAvailable) VerdantUpgrade else CrimsonError,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Guest characters are temporary.\nYour progress will not be saved.",
            fontSize = 11.sp,
            color = AshGray,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp
        )
    }
}

// ─────────────────────────────────────────────
// Step 1: Credentials
// ─────────────────────────────────────────────
@Composable
private fun CredentialsStep(
    username: String,
    password: String,
    characterName: String,
    nameAvailability: AuthViewModel.NameAvailability? = null,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onCharacterNameChange: (String) -> Unit,
    onCheckName: (String, String) -> Unit = { _, _ -> }
) {
    val focusManager = LocalFocusManager.current
    // Track what was last checked to avoid redundant server calls
    var lastCheckedUsername by remember { mutableStateOf("") }
    var lastCheckedCharName by remember { mutableStateOf("") }

    fun triggerCheck() {
        if (username.isNotBlank() && characterName.isNotBlank() &&
            (username != lastCheckedUsername || characterName != lastCheckedCharName)) {
            lastCheckedUsername = username
            lastCheckedCharName = characterName
            onCheckName(username, characterName)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Step 1: Account Details",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TorchAmber
        )
        Spacer(modifier = Modifier.height(12.dp))

        StoneTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = "Username",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
            onFocusLost = { triggerCheck() }
        )
        // Username availability feedback
        if (nameAvailability != null && username.isNotBlank()) {
            Text(
                text = if (nameAvailability.usernameAvailable) "Username available" else "Username already taken",
                fontSize = 11.sp,
                color = if (nameAvailability.usernameAvailable) VerdantUpgrade else CrimsonError,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        StoneTextField(
            value = password,
            onValueChange = onPasswordChange,
            label = "Password",
            isPassword = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
        )
        Spacer(modifier = Modifier.height(8.dp))

        StoneTextField(
            value = characterName,
            onValueChange = onCharacterNameChange,
            label = "Character Name",
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            onFocusLost = { triggerCheck() }
        )
        // Character name availability feedback
        if (nameAvailability != null && characterName.isNotBlank()) {
            Text(
                text = if (nameAvailability.characterNameAvailable) "Character name available" else "Character name already taken",
                fontSize = 11.sp,
                color = if (nameAvailability.characterNameAvailable) VerdantUpgrade else CrimsonError,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, top = 2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────
// Step 2: Gender
// ─────────────────────────────────────────────
@Composable
private fun GenderSelectionStep(
    selectedGender: String,
    onGenderSelected: (String) -> Unit
) {
    val genders = listOf(
        Triple("male", "Male", "Your character presents as male."),
        Triple("female", "Female", "Your character presents as female."),
        Triple("neutral", "Neutral/Other", "Your character presents as gender-neutral.")
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "Step 2: Choose Gender",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TorchAmber
        )
        Spacer(modifier = Modifier.height(8.dp))

        genders.forEach { (id, label, description) ->
            StoneSelectionCard(
                isSelected = selectedGender == id,
                onClick = { onGenderSelected(id) }
            ) {
                Text(label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BoneWhite)
                Text(
                    description,
                    fontSize = 11.sp,
                    color = AshGray
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// Step 3: Race
// ─────────────────────────────────────────────
@Composable
private fun RaceSelectionStep(
    availableRaces: List<RaceDef>,
    selectedRaceId: String,
    onRaceSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        Text(
            "Step 3: Choose Race",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TorchAmber
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (availableRaces.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = BurnishedGold,
                        trackColor = AshGray.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Loading races...", fontSize = 11.sp, color = AshGray)
                }
            }
        } else {
            // Column+verticalScroll instead of LazyColumn (WASM drag-scroll fix #254)
            Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                availableRaces.forEach { race ->
                    StoneSelectionCard(
                        isSelected = selectedRaceId == race.id,
                        onClick = { onRaceSelected(race.id) }
                    ) {
                        Text(race.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BoneWhite)
                        Text(
                            race.description,
                            fontSize = 11.sp,
                            color = AshGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Stat modifier badges
                        val m = race.statModifiers
                        val mods = listOf(
                            "STR" to m.strength, "AGI" to m.agility, "INT" to m.intellect,
                            "WIL" to m.willpower, "HLT" to m.health, "CHM" to m.charm
                        ).filter { it.second != 0 }
                        if (mods.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                mods.forEach { (name, value) ->
                                    val color = if (value > 0) VerdantUpgrade else CrimsonError
                                    val text = "${name}:${if (value > 0) "+" else ""}$value"
                                    Text(
                                        text = text,
                                        fontSize = 10.sp,
                                        color = color,
                                        modifier = Modifier
                                            .background(color.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        if (race.xpModifier != 1.0) {
                            Text(
                                "XP: ${(race.xpModifier * 100).toInt()}%",
                                fontSize = 10.sp,
                                color = CrimsonError.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Step 4: Class
// ─────────────────────────────────────────────
@Composable
private fun ClassSelectionStep(
    availableClasses: List<CharacterClassDef>,
    selectedClassId: String,
    onClassSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
        Text(
            "Step 4: Choose Class",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TorchAmber
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (availableClasses.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = BurnishedGold,
                        trackColor = AshGray.copy(alpha = 0.3f),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Loading classes...", fontSize = 11.sp, color = AshGray)
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                availableClasses.forEach { cls ->
                    StoneSelectionCard(
                        isSelected = selectedClassId == cls.id,
                        onClick = { onClassSelected(cls.id) }
                    ) {
                        Text(cls.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BoneWhite)
                        Text(
                            cls.description,
                            fontSize = 11.sp,
                            color = AshGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        // Magic schools
                        if (cls.magicSchools.isNotEmpty()) {
                            val schools = cls.magicSchools.entries.joinToString(", ") { "${it.key} ${it.value}" }
                            Text(
                                "Magic: $schools",
                                fontSize = 10.sp,
                                color = MagicPurple
                            )
                        }
                        // HP/MP per level
                        val hpText = "HP/lvl: ${cls.hpPerLevelMin}-${cls.hpPerLevelMax}"
                        val mpText = if (cls.mpPerLevelMax > 0) " | MP/lvl: ${cls.mpPerLevelMin}-${cls.mpPerLevelMax}" else ""
                        Text(
                            "$hpText$mpText",
                            fontSize = 10.sp,
                            color = AshGray
                        )
                        // Skills
                        if (cls.skills.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                cls.skills.forEach { skill ->
                                    Text(
                                        text = skill.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() },
                                        fontSize = 9.sp,
                                        color = EmberOrange,
                                        modifier = Modifier
                                            .background(EmberOrange.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                        // Min stats
                        val s = cls.minimumStats
                        Text(
                            "Min: STR:${s.strength} AGI:${s.agility} INT:${s.intellect} WIL:${s.willpower} HLT:${s.health} CHM:${s.charm}",
                            fontSize = 10.sp,
                            color = TorchAmber.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// Step 5: Stat Allocation
// ─────────────────────────────────────────────
@Composable
private fun StatAllocationStep(
    effectiveMinimum: Stats,
    allocatedStats: Stats,
    onStatsChanged: (Stats) -> Unit
) {
    val cpUsed = StatAllocator.totalCpUsed(allocatedStats, effectiveMinimum)
    val cpRemaining = StatAllocator.CP_POOL - cpUsed

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Step 5: Allocate Stats",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = TorchAmber
            )
            // Explicit semantics + LiveRegion so the WASM accessibility bridge
            // re-emits the CP counter every time it recomposes (NeoMud#271).
            // Compose's auto-derived semantics aren't reliably refreshed by the
            // WASM a11y bridge after recomposition; declaring contentDescription
            // here forces the update. We keep liveRegion=Polite on this single
            // header — it's the canonical "something changed" signal for the
            // whole step, so a screen reader gets one audible confirmation per
            // tap. The individual stat rows deliberately do NOT set liveRegion
            // (would announce all 6 rows on every change — chatty), relying on
            // their contentDescription alone for the stale-tree fix.
            Text(
                "CP Used: $cpUsed / ${StatAllocator.CP_POOL}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (cpRemaining == 0) VerdantUpgrade else BurnishedGold,
                modifier = Modifier.semantics {
                    contentDescription = "CP used $cpUsed of ${StatAllocator.CP_POOL}"
                    liveRegion = LiveRegionMode.Polite
                }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Header row
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stat", modifier = Modifier.width(80.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AshGray)
            Spacer(modifier = Modifier.weight(1f))
            Text("Value", modifier = Modifier.width(40.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AshGray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.width(72.dp))
            Text("Cost", modifier = Modifier.width(36.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = AshGray, textAlign = TextAlign.Center)
        }

        RunicDivider()

        val statEntries = listOf(
            StatEntry("Strength", allocatedStats.strength, effectiveMinimum.strength) { v -> allocatedStats.copy(strength = v) },
            StatEntry("Agility", allocatedStats.agility, effectiveMinimum.agility) { v -> allocatedStats.copy(agility = v) },
            StatEntry("Intellect", allocatedStats.intellect, effectiveMinimum.intellect) { v -> allocatedStats.copy(intellect = v) },
            StatEntry("Willpower", allocatedStats.willpower, effectiveMinimum.willpower) { v -> allocatedStats.copy(willpower = v) },
            StatEntry("Health", allocatedStats.health, effectiveMinimum.health) { v -> allocatedStats.copy(health = v) },
            StatEntry("Charm", allocatedStats.charm, effectiveMinimum.charm) { v -> allocatedStats.copy(charm = v) }
        )

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            // key(...) gives each row a stable composition identity so Compose
            // doesn't re-key positionally on stat changes — keeps the WASM
            // a11y tree pointing at the right node when values mutate (#271).
            statEntries.forEach { entry ->
                key(entry.name) {
                    StatAllocationRow(
                        entry = entry,
                        cpRemaining = cpRemaining,
                        onStatsChanged = onStatsChanged
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Reset button — stone style
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .background(
                    Brush.verticalGradient(listOf(StoneTheme.frameLight, StoneTheme.frameDark)),
                    RoundedCornerShape(4.dp)
                )
                .drawBehind {
                    val w = size.width; val h = size.height
                    drawLine(StoneTheme.frameLight.copy(alpha = 0.3f), Offset(0f, 0f), Offset(w, 0f), 1f)
                    drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                    drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
                }
                .clickable { onStatsChanged(effectiveMinimum) }
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Text("Reset Stats", fontSize = 12.sp, color = BoneWhite)
        }
    }
}

// ─────────────────────────────────────────────
// Step 6: Character Preview
// ─────────────────────────────────────────────
@Composable
private fun CharacterPreviewStep(
    characterName: String,
    selectedRace: RaceDef?,
    selectedClass: CharacterClassDef?,
    selectedGender: String,
    allocatedStats: Stats,
    serverBaseUrl: String
) {
    val raceName = selectedRace?.name ?: "Unknown"
    val className = selectedClass?.name ?: "Unknown"
    val genderLabel = selectedGender.replaceFirstChar { it.uppercase() }

    val raceId = (selectedRace?.id ?: "HUMAN").lowercase()
    val classId = (selectedClass?.id ?: "WARRIOR").lowercase()
    val spriteUrl = "$serverBaseUrl/assets/images/players/${raceId}_${selectedGender}_${classId}.webp"

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Step 6: Review Character",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = TorchAmber
        )
        Spacer(modifier = Modifier.height(10.dp))

        // Sprite + identity header
        run {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF14110E), DeepVoid)),
                        RoundedCornerShape(6.dp)
                    )
                    .border(1.dp, FilledSlotEdge, RoundedCornerShape(6.dp))
                    .drawBehind {
                        val w = size.width; val h = size.height
                        drawLine(AshGray.copy(alpha = 0.15f), Offset(6f, 0f), Offset(w - 6f, 0f), 1f)
                        drawLine(StoneTheme.innerShadow, Offset(6f, h - 1f), Offset(w - 6f, h - 1f), 1f)
                    }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sprite in stone recess
                    Box(
                        modifier = Modifier
                            .height(120.dp)
                            .widthIn(max = 90.dp)
                            .background(IronDark, RoundedCornerShape(4.dp))
                            .border(1.dp, EmptySlotEdge, RoundedCornerShape(4.dp))
                    ) {
                        val context = coil3.compose.LocalPlatformContext.current
                        AsyncImage(
                            model = coil3.request.ImageRequest.Builder(context)
                                .data(spriteUrl)
                                .crossfade(200)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = "$characterName sprite preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = characterName,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = BurnishedGold
                        )
                        Text(
                            text = "$genderLabel $raceName $className",
                            fontSize = 12.sp,
                            color = AshGray
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        selectedClass?.let { cls ->
                            Text(
                                text = "HP/lvl: ${cls.hpPerLevelMin}-${cls.hpPerLevelMax}",
                                fontSize = 11.sp,
                                color = Color(0xFF4CAF50)
                            )
                            if (cls.mpPerLevelMax > 0) {
                                Text(
                                    text = "MP/lvl: ${cls.mpPerLevelMin}-${cls.mpPerLevelMax}",
                                    fontSize = 11.sp,
                                    color = FrostSteel
                                )
                            }
                        }
                        val combinedXpMod = (selectedRace?.xpModifier ?: 1.0) * (selectedClass?.xpModifier ?: 1.0)
                        if (combinedXpMod != 1.0) {
                            Text(
                                text = "XP rate: ${(combinedXpMod * 100).toInt()}%",
                                fontSize = 11.sp,
                                color = if (combinedXpMod > 1.0) CrimsonError else VerdantUpgrade
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Stats grid
        run {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(listOf(Color(0xFF14110E), DeepVoid)),
                        RoundedCornerShape(6.dp)
                    )
                    .border(1.dp, EmptySlotEdge, RoundedCornerShape(6.dp))
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("Stats", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TorchAmber)
                    Spacer(modifier = Modifier.height(6.dp))
                    val stats = listOf(
                        "STR" to allocatedStats.strength,
                        "AGI" to allocatedStats.agility,
                        "INT" to allocatedStats.intellect,
                        "WIL" to allocatedStats.willpower,
                        "HLT" to allocatedStats.health,
                        "CHM" to allocatedStats.charm
                    )
                    // Stats in a grid of individual boxes
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        stats.forEach { (name, value) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .background(DeepVoid, RoundedCornerShape(3.dp))
                                    .border(1.dp, AshGray.copy(alpha = 0.3f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            ) {
                                Text(text = name, fontSize = 10.sp, color = AshGray)
                                Text(text = "$value", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = BurnishedGold)
                            }
                        }
                    }
                    // Race stat modifiers
                    selectedRace?.let { race ->
                        val m = race.statModifiers
                        val mods = listOf(
                            "STR" to m.strength, "AGI" to m.agility, "INT" to m.intellect,
                            "WIL" to m.willpower, "HLT" to m.health, "CHM" to m.charm
                        ).filter { it.second != 0 }
                        if (mods.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = "Race:", fontSize = 10.sp, color = AshGray)
                                mods.forEach { (name, value) ->
                                    val color = if (value > 0) VerdantUpgrade else CrimsonError
                                    Text(
                                        text = "${name}:${if (value > 0) "+" else ""}$value",
                                        fontSize = 10.sp,
                                        color = color,
                                        modifier = Modifier
                                            .background(color.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Class details
        run {
            selectedClass?.let { cls ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF14110E), DeepVoid)),
                            RoundedCornerShape(6.dp)
                        )
                        .border(1.dp, EmptySlotEdge, RoundedCornerShape(6.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Class: ${cls.name}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TorchAmber)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = cls.description,
                            fontSize = 11.sp,
                            color = AshGray
                        )

                        if (cls.magicSchools.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Magic Schools", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MagicPurple)
                            cls.magicSchools.forEach { (school, level) ->
                                Text(
                                    text = "${school.replaceFirstChar { it.uppercase() }} (tier $level)",
                                    fontSize = 11.sp,
                                    color = MagicPurple.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        if (cls.skills.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Skills", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = EmberOrange)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                cls.skills.forEach { skill ->
                                    Text(
                                        text = skill.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() },
                                        fontSize = 10.sp,
                                        color = EmberOrange,
                                        modifier = Modifier
                                            .background(EmberOrange.copy(alpha = 0.15f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Race details
        run {
            selectedRace?.let { race ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFF14110E), DeepVoid)),
                            RoundedCornerShape(6.dp)
                        )
                        .border(1.dp, EmptySlotEdge, RoundedCornerShape(6.dp))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Race: ${race.name}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TorchAmber)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = race.description,
                            fontSize = 11.sp,
                            color = AshGray
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Future sprite generation placeholder
        run {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(listOf(StoneTheme.frameDark, Color(0xFF0D0A08))),
                        RoundedCornerShape(4.dp)
                    )
                    .drawBehind {
                        val w = size.width; val h = size.height
                        drawLine(StoneTheme.frameLight.copy(alpha = 0.1f), Offset(0f, 0f), Offset(w, 0f), 1f)
                        drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                    }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Generate Custom Sprite (Coming Soon)",
                    fontSize = 12.sp,
                    color = AshGray
                )
            }
        }
    }
}

private data class StatEntry(
    val name: String,
    val current: Int,
    val minimum: Int,
    val withValue: (Int) -> Stats
)

@Composable
private fun StatAllocationRow(
    entry: StatEntry,
    cpRemaining: Int,
    onStatsChanged: (Stats) -> Unit
) {
    val costToAdd = StatAllocator.cpCostForPoint(entry.current, entry.minimum)
    val canAdd = cpRemaining >= costToAdd
    val canRemove = entry.current > entry.minimum

    val costColor = when {
        costToAdd == 1 -> VerdantUpgrade
        costToAdd == 2 -> TorchAmber
        else -> CrimsonError
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = entry.name,
            modifier = Modifier.width(80.dp),
            fontSize = 13.sp,
            color = BoneWhite
        )

        Spacer(modifier = Modifier.weight(1f))

        // Stat value gets explicit semantics. Without this, the WASM
        // accessibility tree never re-emits when `entry.current` changes
        // (NeoMud#271 — values update on canvas but a11y stays stale). The
        // contentDescription includes the stat name so a screen reader hears
        // "Strength value 21" rather than just "21" stripped of context.
        // Deliberately NO liveRegion here — all 6 rows recompose on any +/-,
        // so liveRegion=Polite would queue 6 announcements per tap. The CP
        // Used header up in StatAllocationStep is the single live region for
        // the whole step; per-row updates are reachable on demand via the a11y
        // tree but don't auto-announce.
        Text(
            text = "${entry.current}",
            modifier = Modifier
                .width(40.dp)
                .semantics {
                    contentDescription = "${entry.name} value ${entry.current}"
                },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = BurnishedGold,
            textAlign = TextAlign.Center
        )

        // Minus button
        StoneStatButton(
            text = "-",
            onClick = {
                if (canRemove) {
                    onStatsChanged(entry.withValue(entry.current - 1))
                }
            },
            enabled = canRemove
        )

        Spacer(modifier = Modifier.width(6.dp))

        // Plus button
        StoneStatButton(
            text = "+",
            onClick = {
                if (canAdd) {
                    onStatsChanged(entry.withValue(entry.current + 1))
                }
            },
            enabled = canAdd
        )

        // Cost indicator
        Text(
            text = "${costToAdd}cp",
            modifier = Modifier.width(36.dp),
            fontSize = 11.sp,
            color = costColor,
            textAlign = TextAlign.Center
        )
    }
}
