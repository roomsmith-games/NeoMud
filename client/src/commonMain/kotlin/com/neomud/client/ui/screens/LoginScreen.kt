package com.neomud.client.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import com.neomud.client.platform.dismissNativeLoadingScreen
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.neomud.client.network.ConnectionState
import com.neomud.client.platform.serverConfig
import com.neomud.client.ui.theme.StoneTheme
import com.neomud.client.viewmodel.AuthState
import com.neomud.shared.NeoMudVersion
import neomud.client.generated.resources.Res
import neomud.client.generated.resources.splash_forge
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
private val CrimsonError = Color(0xFFCC4444)

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
fun LoginScreen(
    connectionState: ConnectionState,
    authState: AuthState,
    connectionError: String?,
    onConnect: (String, Int) -> Unit,
    onLogin: (String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onClearError: () -> Unit,
    onPlatformLogin: () -> Unit = {},
    onPlayAsGuest: () -> Unit = {},
    onForceLogin: () -> Unit = {},
    onCancelForceLogin: () -> Unit = {}
) {
    var host by remember { mutableStateOf(serverConfig.defaultHost) }
    var port by remember { mutableStateOf(serverConfig.defaultPort.toString()) }
    var showGuestWarning by remember { mutableStateOf(false) }
    var characterName by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) { dismissNativeLoadingScreen() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Background image — world cover if available, fallback to splash_forge
        val coverUrl = serverConfig.coverImageUrl
        if (coverUrl.isNotEmpty()) {
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                    .data(coverUrl)
                    .crossfade(300)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Image(
                painter = painterResource(Res.drawable.splash_forge),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        // Dark scrim overlay for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f))
        )
        // Center card with stone frame (safe area padding keeps card clear of notch/home indicator)
        val borderPx = 4.dp
        Box(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.9f)
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
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title — show world name if launched from marketplace
                val worldName = serverConfig.worldName
                Text(
                    text = worldName.ifEmpty { "NeoMud" },
                    fontSize = if (worldName.isNotEmpty()) 26.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = BurnishedGold,
                    textAlign = TextAlign.Center
                )
                if (worldName.isNotEmpty()) {
                    // World version + creator
                    val meta = buildString {
                        if (serverConfig.worldVersion.isNotEmpty()) append("v${serverConfig.worldVersion}")
                        if (serverConfig.creatorName.isNotEmpty()) {
                            if (isNotEmpty()) append(" \u2022 ")
                            append("by ${serverConfig.creatorName}")
                        }
                    }
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            fontSize = 11.sp,
                            color = AshGray,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Text(
                        text = NeoMudVersion.VERSION,
                        fontSize = 11.sp,
                        color = AshGray,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Gold ornamental line
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, BurnishedGold.copy(alpha = 0.5f), Color.Transparent)
                            )
                        )
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (connectionState == ConnectionState.DISCONNECTED) {
                    // ─── Connection Phase ───
                    Text(
                        "Connect to Server",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TorchAmber
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (serverConfig.showServerConfig) {
                        StoneTextField(
                            value = host,
                            onValueChange = { host = it },
                            label = "Server Host",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        StoneTextField(
                            value = port,
                            onValueChange = { port = it },
                            label = "Port",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                onConnect(host, port.toIntOrNull() ?: serverConfig.defaultPort)
                            })
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    StoneActionButton(
                        text = "Connect",
                        onClick = { onConnect(host, port.toIntOrNull() ?: serverConfig.defaultPort) }
                    )
                } else if (connectionState == ConnectionState.CONNECTING) {
                    // ─── Connecting ───
                    CircularProgressIndicator(
                        color = BurnishedGold,
                        trackColor = AshGray.copy(alpha = 0.3f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Connecting...", fontSize = 13.sp, color = BoneWhite)
                } else if (connectionState == ConnectionState.CONNECTED &&
                    serverConfig.platformToken.isNotEmpty() &&
                    authState is AuthState.Idle) {
                    // ─── Waiting for platform auth response ───
                    CircularProgressIndicator(
                        color = BurnishedGold,
                        trackColor = AshGray.copy(alpha = 0.3f),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Authenticating...", fontSize = 13.sp, color = BoneWhite)
                } else if (authState is AuthState.PlatformReady) {
                    // ─── Platform Auto-Login ───
                    Text(
                        "Welcome Back",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TorchAmber
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Your character awaits.",
                        fontSize = 13.sp,
                        color = BoneWhite,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    StoneActionButton(
                        text = "Continue as ${authState.characterName}",
                        onClick = onPlatformLogin
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "or sign in with a different account",
                        fontSize = 11.sp,
                        color = AshGray,
                        textAlign = TextAlign.Center
                    )
                } else {
                    // ─── Authentication Phase (password or platform-needs-character) ───
                    val isPlatformNewPlayer = authState is AuthState.PlatformNeedsCharacter

                    Text(
                        if (isPlatformNewPlayer) "Create Character" else "Login",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = TorchAmber
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    if (!isPlatformNewPlayer) {
                        StoneTextField(
                            value = characterName,
                            onValueChange = { characterName = it },
                            label = "Character Name",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                focusManager.clearFocus()
                                if (characterName.isNotBlank()) {
                                    onLogin(characterName)
                                }
                            })
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        StoneActionButton(
                            text = "Login",
                            onClick = { onLogin(characterName) },
                            enabled = authState !is AuthState.Loading && characterName.isNotBlank()
                        ) {
                            if (authState is AuthState.Loading) {
                                CircularProgressIndicator(
                                    color = DeepVoid,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Create Character",
                            fontSize = 13.sp,
                            color = BurnishedGold,
                            modifier = Modifier
                                .clickable(onClick = onNavigateToRegister)
                                .padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // Play as Guest
                        Text(
                            text = "Play as Guest",
                            fontSize = 13.sp,
                            color = AshGray,
                            modifier = Modifier
                                .clickable(onClick = { showGuestWarning = true })
                                .padding(vertical = 4.dp)
                        )
                        Text(
                            text = "No account needed. Progress is not saved.",
                            fontSize = 10.sp,
                            color = AshGray.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    } else {
                        // Platform user needs to create a character — redirect to registration
                        Text(
                            text = "You're signed in! Create your character to begin.",
                            fontSize = 13.sp,
                            color = BoneWhite,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        StoneActionButton(
                            text = "Create Character",
                            onClick = onNavigateToRegister
                        )
                    }
                }

                // Error display
                if (connectionError != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Connection failed: $connectionError",
                        fontSize = 12.sp,
                        color = CrimsonError,
                        textAlign = TextAlign.Center
                    )
                }

                if (authState is AuthState.Error) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = authState.message,
                        fontSize = 12.sp,
                        color = CrimsonError,
                        textAlign = TextAlign.Center
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
            }
        }
    }

    // Guest warning dialog
    if (showGuestWarning) {
        GuestWarningDialog(
            onConfirm = {
                showGuestWarning = false
                onPlayAsGuest()
            },
            onCreateAccount = {
                showGuestWarning = false
                onNavigateToRegister()
            },
            onDismiss = { showGuestWarning = false }
        )
    }

    // Session conflict dialog
    if (authState is AuthState.SessionConflict) {
        SessionConflictDialog(
            characterName = authState.characterName,
            message = authState.message,
            onForce = onForceLogin,
            onCancel = onCancelForceLogin
        )
    }
}

@Composable
private fun GuestWarningDialog(
    onConfirm: () -> Unit,
    onCreateAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onDismiss),
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
                .clickable(enabled = false, onClick = {}) // prevent click-through
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
                text = "Create an account to save your progress.",
                fontSize = 12.sp,
                color = AshGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Continue as Guest button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2A2218), Color(0xFF1A1510))
                        ),
                        RoundedCornerShape(6.dp)
                    )
                    .border(1.dp, AshGray.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center
            ) {
                Text("Continue as Guest", fontSize = 13.sp, color = BoneWhite)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Create Account button
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
                    .clickable(onClick = onCreateAccount),
                contentAlignment = Alignment.Center
            ) {
                Text("Create Account Instead", fontSize = 13.sp, color = BurnishedGold)
            }
        }
    }
}

@Composable
private fun SessionConflictDialog(
    characterName: String,
    message: String,
    onForce: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(onClick = onCancel),
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
                .clickable(enabled = false, onClick = {})
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Active Session Detected",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = BurnishedGold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = BoneWhite,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Character: $characterName",
                fontSize = 12.sp,
                color = AshGray,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))

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
                    .clickable(onClick = onForce),
                contentAlignment = Alignment.Center
            ) {
                Text("Take Over Session", fontSize = 13.sp, color = BurnishedGold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2A2218), Color(0xFF1A1510))
                        ),
                        RoundedCornerShape(6.dp)
                    )
                    .border(1.dp, AshGray.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .clickable(onClick = onCancel),
                contentAlignment = Alignment.Center
            ) {
                Text("Cancel", fontSize = 13.sp, color = BoneWhite)
            }
        }
    }
}

// ─────────────────────────────────────────────
// Stone-styled text field
// ─────────────────────────────────────────────
@Composable
private fun StoneTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    isPassword: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            fontSize = 11.sp,
            color = AshGray,
            modifier = Modifier.padding(bottom = 3.dp, start = 2.dp)
        )
        // key(label) ensures WASM proxy creates unique HTML input per field
        key(label) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
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
                            // Inner shadow for depth
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
        } // key(label)
    }
}

// ─────────────────────────────────────────────
// Stone action button
// ─────────────────────────────────────────────
@Composable
private fun StoneActionButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loadingContent: @Composable (() -> Unit)? = null
) {
    val bg = if (enabled)
        Brush.verticalGradient(listOf(BurnishedGold, Color(0xFF997733)))
    else
        Brush.verticalGradient(listOf(StoneTheme.frameMid, StoneTheme.frameDark))

    val textColor = if (enabled) DeepVoid else AshGray

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .background(bg, RoundedCornerShape(4.dp))
            .drawBehind {
                val w = size.width; val h = size.height
                if (enabled) {
                    drawLine(BurnishedGold.copy(alpha = 0.7f), Offset(0f, 0f), Offset(w, 0f), 1f)
                    drawLine(BurnishedGold.copy(alpha = 0.7f), Offset(0f, 0f), Offset(0f, h), 1f)
                } else {
                    drawLine(StoneTheme.frameLight.copy(alpha = 0.3f), Offset(0f, 0f), Offset(w, 0f), 1f)
                }
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(0f, h - 1f), Offset(w, h - 1f), 1f)
                drawLine(Color.Black.copy(alpha = 0.5f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
            }
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = textColor)
        loadingContent?.invoke()
    }
}
