package com.neomud.server.game

import com.neomud.server.game.commands.AdminCommand
import com.neomud.server.game.commands.AttackCommand
import com.neomud.server.game.commands.BashCommand
import com.neomud.server.game.commands.DialogueCommand
import com.neomud.server.game.commands.InventoryCommand
import com.neomud.server.game.commands.KickCommand
import com.neomud.server.game.commands.LookCommand
import com.neomud.server.game.commands.MeditateCommand
import com.neomud.server.game.commands.RestCommand
import com.neomud.server.game.commands.MoveCommand
import com.neomud.server.game.commands.InteractCommand
import com.neomud.server.game.commands.PickLockCommand
import com.neomud.server.game.commands.DropCommand
import com.neomud.server.game.commands.PickupCommand
import com.neomud.server.game.commands.SayCommand
import com.neomud.server.game.commands.SneakCommand
import com.neomud.server.game.commands.SpellCommand
import com.neomud.server.game.commands.TrackCommand
import com.neomud.server.game.commands.TrainerCommand
import com.neomud.server.game.commands.CraftCommand
import com.neomud.server.game.commands.VendorCommand
import com.neomud.server.game.inventory.LootService
import com.neomud.server.game.inventory.RoomItemManager
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.repository.CoinRepository
import com.neomud.server.persistence.repository.DiscoveryRepository
import com.neomud.server.persistence.repository.InventoryRepository
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.shared.model.Coins
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.ItemCatalog
import com.neomud.server.world.LootTableCatalog
import com.neomud.server.world.RaceCatalog
import com.neomud.server.world.SkillCatalog
import com.neomud.server.world.PcSpriteCatalog
import com.neomud.server.world.SpellCatalog
import com.neomud.server.auth.PlatformTokenVerifier
import com.neomud.server.world.WorldGraph
import com.neomud.shared.NeoMudVersion
import com.neomud.shared.protocol.ClientMessage
import com.neomud.shared.protocol.ServerMessage
import io.ktor.websocket.CloseReason
import io.ktor.websocket.close

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

class CommandProcessor(
    private val worldGraph: WorldGraph,
    private val sessionManager: SessionManager,
    private val npcManager: NpcManager,
    private val playerRepository: PlayerRepository,
    private val classCatalog: ClassCatalog,
    private val itemCatalog: ItemCatalog,
    private val skillCatalog: SkillCatalog,
    private val raceCatalog: RaceCatalog,
    private val inventoryCommand: InventoryCommand,
    private val pickupCommand: PickupCommand,
    private val roomItemManager: RoomItemManager,
    private val trainerCommand: TrainerCommand,
    private val spellCommand: SpellCommand,
    private val spellCatalog: SpellCatalog,
    private val vendorCommand: VendorCommand,
    private val lootService: LootService,
    private val lootTableCatalog: LootTableCatalog,
    private val inventoryRepository: InventoryRepository,
    private val coinRepository: CoinRepository,
    private val discoveryRepository: DiscoveryRepository,
    private val craftCommand: CraftCommand? = null,
    private val adminUsernames: Set<String> = emptySet(),
    private val movementTrailManager: MovementTrailManager? = null,
    private val pcSpriteCatalog: PcSpriteCatalog? = null,
    private val tutorialService: TutorialService? = null,
    private val platformTokenVerifier: PlatformTokenVerifier? = null,
    private val trapManager: com.neomud.server.game.trap.TrapManager? = null,
    private val dialogueCommand: DialogueCommand? = null
) {
    private val logger = LoggerFactory.getLogger(CommandProcessor::class.java)

    /** Track failed login attempts: username (lowercase) → (failCount, lastFailTimestamp) */
    private val failedLogins = ConcurrentHashMap<String, Pair<Int, Long>>()

    private fun checkLoginRateLimit(username: String): Boolean {
        val key = username.lowercase()
        val entry = failedLogins[key] ?: return true
        val (count, lastFail) = entry
        if (System.currentTimeMillis() - lastFail > GameConfig.Security.LOGIN_LOCKOUT_MS) {
            failedLogins.remove(key)
            return true
        }
        return count < GameConfig.Security.MAX_FAILED_LOGINS
    }

    private fun recordFailedLogin(username: String) {
        val key = username.lowercase()
        val now = System.currentTimeMillis()
        failedLogins.compute(key) { _, existing ->
            if (existing == null) 1 to now
            else (existing.first + 1) to now
        }
    }

    private fun clearFailedLogins(username: String) {
        failedLogins.remove(username.lowercase())
    }

    private val adminCommand = AdminCommand(
        sessionManager, playerRepository, npcManager, worldGraph,
        inventoryCommand, inventoryRepository, itemCatalog, classCatalog, raceCatalog, roomItemManager
    )
    fun setGameLoop(loop: GameLoop) {
        adminCommand.setGameLoop(loop)
        moveCommand.departureRecorder = loop::recordDeparture
    }

    private val moveCommand = MoveCommand(worldGraph, sessionManager, npcManager, playerRepository, roomItemManager, skillCatalog, classCatalog, movementTrailManager, tutorialService, trapManager)
    private val lookCommand = LookCommand(worldGraph, sessionManager, npcManager, roomItemManager, skillCatalog, classCatalog, tutorialService)
    private val sayCommand = SayCommand(sessionManager, adminCommand)
    private val attackCommand = AttackCommand(npcManager, worldGraph)
    private val sneakCommand = SneakCommand(sessionManager, npcManager, skillCatalog, classCatalog)
    private val bashCommand = BashCommand(npcManager, sessionManager)
    private val kickCommand = KickCommand(npcManager, worldGraph, sessionManager)
    private val meditateCommand = MeditateCommand()
    private val restCommand = RestCommand()
    private val trackCommand = TrackCommand()
    private val pickLockCommand = PickLockCommand(worldGraph, sessionManager, npcManager)
    private val dropCommand = DropCommand(roomItemManager, inventoryRepository, coinRepository, itemCatalog, sessionManager)
    private val interactCommand = InteractCommand(
        worldGraph, sessionManager, npcManager, roomItemManager, lootService, lootTableCatalog, playerRepository,
        inventoryRepository = inventoryRepository,
        inventoryCommand = inventoryCommand,
        playerFlagsRepository = com.neomud.server.persistence.repository.PlayerFlagsRepository()
    )

    suspend fun sendCatalogSync(session: PlayerSession) {
        session.send(ServerMessage.ClassCatalogSync(classCatalog.getAllClasses()))
        session.send(ServerMessage.ItemCatalogSync(itemCatalog.getAllItems()))
        session.send(ServerMessage.SkillCatalogSync(skillCatalog.getAllSkills()))
        session.send(ServerMessage.RaceCatalogSync(raceCatalog.getAllRaces()))
        session.send(ServerMessage.SpellCatalogSync(spellCatalog.getAllSpells()))
    }

    suspend fun process(session: PlayerSession, message: ClientMessage) {
        when (message) {
            // Auth and read-only commands don't need the game state lock
            is ClientMessage.Register -> handleRegister(session, message)
            is ClientMessage.Login -> handleLogin(session, message)
            is ClientMessage.CheckName -> handleCheckName(session, message)
            is ClientMessage.Ping -> session.send(ServerMessage.Pong)
            is ClientMessage.ClientHello -> {
                session.clientProtocolVersion = message.protocolVersion
                session.clientVersion = message.clientVersion
                val hasToken = message.platformToken?.isNotEmpty() == true
                logger.info("Client hello: v${message.clientVersion}, protocol=${message.protocolVersion}, platformToken=${if (hasToken) "present(${message.platformToken?.length}chars)" else "absent"}")

                if (NeoMudVersion.compareVersions(message.clientVersion, NeoMudVersion.MIN_CLIENT_VERSION) < 0) {
                    logger.warn("Rejecting client v${message.clientVersion} (minimum: ${NeoMudVersion.MIN_CLIENT_VERSION})")
                    session.send(ServerMessage.ConnectionRejected(
                        reason = "Your app is out of date. Please update to continue playing.",
                        minClientVersion = NeoMudVersion.MIN_CLIENT_VERSION,
                        updateUrl = "https://neomud.app/update"
                    ))
                    session.webSocketSession.close(
                        CloseReason(CloseReason.Codes.VIOLATED_POLICY, "Client version too old")
                    )
                    return
                }

                // Platform token verification — if present and valid, store claims for auto-login
                val token = message.platformToken
                if (token != null && platformTokenVerifier?.isEnabled == true) {
                    val claims = platformTokenVerifier.verify(token)
                    if (claims != null) {
                        session.platformUserId = claims.userId
                        session.platformRole = claims.role
                        val existingPlayer = playerRepository.findByPlatformUserId(claims.userId)
                        session.send(ServerMessage.PlatformAuthOk(
                            characterName = existingPlayer?.name,
                            platformUserId = claims.userId,
                            needsCharacterCreation = existingPlayer == null,
                            role = claims.role
                        ))
                        logger.info("Platform auth verified for userId=${claims.userId}, character=${existingPlayer?.name ?: "(new)"}")
                    } else {
                        logger.warn("Platform token invalid, falling back to password auth")
                    }
                }
            }
            is ClientMessage.PlatformLogin -> handlePlatformLogin(session)
            is ClientMessage.PlatformRegister -> handlePlatformRegister(session, message)
            is ClientMessage.GuestLogin -> handleGuestLogin(session, message)
            // All state-mutating commands acquire the global mutex
            else -> GameStateLock.withLock { processLocked(session, message) }
        }
    }

    private suspend fun processLocked(session: PlayerSession, message: ClientMessage) {
        // Block state-mutating commands while dead (allow Look, Say, and ViewInventory)
        val player = session.player
        if (player != null && player.currentHp <= 0
            && message !is ClientMessage.Look
            && message !is ClientMessage.Say
            && message !is ClientMessage.ViewInventory
        ) {
            session.send(ServerMessage.Error("You can't do that while dead."))
            return
        }

        when (message) {
            is ClientMessage.Move -> {
                requireAuth(session) { moveCommand.execute(session, message.direction) }
            }
            is ClientMessage.Look -> {
                requireAuth(session) { lookCommand.execute(session) }
            }
            is ClientMessage.Say -> {
                requireAuth(session) { sayCommand.execute(session, message.message) }
            }
            is ClientMessage.AttackToggle -> {
                requireAuth(session) { attackCommand.handleToggle(session, message.enabled) }
            }
            is ClientMessage.SelectTarget -> {
                requireAuth(session) { attackCommand.handleSelectTarget(session, message.npcId) }
            }
            is ClientMessage.ViewInventory -> {
                requireAuth(session) { inventoryCommand.handleViewInventory(session) }
            }
            is ClientMessage.EquipItem -> {
                requireAuth(session) { inventoryCommand.handleEquipItem(session, message.itemId, message.slot) }
            }
            is ClientMessage.UnequipItem -> {
                requireAuth(session) { inventoryCommand.handleUnequipItem(session, message.slot) }
            }
            is ClientMessage.UseItem -> {
                requireAuth(session) { inventoryCommand.handleUseItem(session, message.itemId) }
            }
            is ClientMessage.PickupItem -> {
                requireAuth(session) { pickupCommand.handlePickupItem(session, message.itemId, message.quantity) }
            }
            is ClientMessage.PickupCoins -> {
                requireAuth(session) { pickupCommand.handlePickupCoins(session, message.coinType) }
            }
            is ClientMessage.DropItem -> {
                requireAuth(session) { dropCommand.execute(session, message.itemId, message.quantity) }
            }
            is ClientMessage.SneakToggle -> {
                requireAuth(session) {
                    if (message.enabled && !canUseSkill(session, "SNEAK")) return@requireAuth
                    sneakCommand.handleToggle(session, message.enabled)
                }
            }
            is ClientMessage.UseSkill -> {
                requireAuth(session) {
                    val skillId = message.skillId.removePrefix("skill:").uppercase()
                    if (!canUseSkill(session, skillId)) return@requireAuth
                    when (skillId) {
                        "BASH" -> bashCommand.execute(session, message.targetId)
                        "KICK" -> kickCommand.execute(session, message.targetId)
                        "MEDITATE" -> meditateCommand.execute(session)
                        "REST" -> restCommand.execute(session)
                        "TRACK" -> trackCommand.execute(session, message.targetId)
                        "PICK_LOCK" -> pickLockCommand.execute(session, message.targetId)
                        "SNEAK" -> sneakCommand.handleToggle(session, !session.isHidden)
                        else -> session.send(ServerMessage.SystemMessage("Unknown skill: $skillId"))
                    }
                }
            }
            is ClientMessage.InteractTrainer -> {
                requireAuth(session) { trainerCommand.handleInteract(session) }
            }
            is ClientMessage.TrainLevelUp -> {
                requireAuth(session) { trainerCommand.handleLevelUp(session) }
            }
            is ClientMessage.TrainStat -> {
                requireAuth(session) { trainerCommand.handleTrainStat(session, message.stat, message.points) }
            }
            is ClientMessage.AllocateTrainedStats -> {
                requireAuth(session) { trainerCommand.handleAllocateTrainedStats(session, message.stats) }
            }
            is ClientMessage.CastSpell -> {
                requireAuth(session) {
                    val spellId = message.spellId.removePrefix("spell:").uppercase()
                    spellCommand.execute(session, spellId, message.targetId)
                }
            }
            is ClientMessage.InteractVendor -> {
                requireAuth(session) { vendorCommand.handleInteract(session) }
            }
            is ClientMessage.BuyItem -> {
                requireAuth(session) { vendorCommand.handleBuy(session, message.itemId, message.quantity) }
            }
            is ClientMessage.SellItem -> {
                requireAuth(session) { vendorCommand.handleSell(session, message.itemId, message.quantity) }
            }
            is ClientMessage.InteractFeature -> {
                requireAuth(session) { interactCommand.execute(session, message.featureId) }
            }
            is ClientMessage.PlaceItem -> {
                requireAuth(session) { interactCommand.handlePlaceItem(session, message.featureId, message.itemId) }
            }
            is ClientMessage.ReadySpell -> {
                requireAuth(session) { handleReadySpell(session, message) }
            }
            is ClientMessage.InteractCrafter -> {
                requireAuth(session) { craftCommand?.handleInteract(session) ?: session.send(ServerMessage.SystemMessage("Crafting is not available.")) }
            }
            is ClientMessage.CraftItem -> {
                requireAuth(session) { craftCommand?.handleCraft(session, message.recipeId) ?: session.send(ServerMessage.SystemMessage("Crafting is not available.")) }
            }
            is ClientMessage.InteractNpc -> {
                requireAuth(session) {
                    dialogueCommand?.execute(session, message.npcId)
                        ?: session.send(ServerMessage.SystemMessage("They don't seem to want to talk."))
                }
            }
            else -> {} // Register, Login, Ping already handled in process()
        }
    }

    private suspend fun handleCheckName(session: PlayerSession, msg: ClientMessage.CheckName) {
        val (usernameAvailable, characterNameAvailable) = playerRepository.checkNameAvailability(
            msg.username, msg.characterName
        )
        session.send(ServerMessage.NameCheckResult(usernameAvailable, characterNameAvailable))
    }

    private suspend fun handleRegister(session: PlayerSession, msg: ClientMessage.Register) {
        if (session.isAuthenticated) {
            session.send(ServerMessage.AuthError("Already logged in"))
            return
        }

        val nameRegex = Regex("^[a-zA-Z0-9_]{3,20}$")
        if (!nameRegex.matches(msg.username)) {
            session.send(ServerMessage.AuthError("Username must be 3-20 alphanumeric characters or underscores."))
            return
        }
        if (msg.password.length < GameConfig.Security.MIN_PASSWORD_LENGTH || msg.password.length > GameConfig.Security.MAX_PASSWORD_LENGTH) {
            session.send(ServerMessage.AuthError("Password must be ${GameConfig.Security.MIN_PASSWORD_LENGTH}-${GameConfig.Security.MAX_PASSWORD_LENGTH} characters."))
            return
        }
        if (!Regex("^[a-zA-Z][a-zA-Z0-9_ ]{1,19}$").matches(msg.characterName)) {
            session.send(ServerMessage.AuthError("Character name must be 2-20 characters, start with a letter, and contain only letters, numbers, spaces, or underscores."))
            return
        }

        val result = playerRepository.createPlayer(
            username = msg.username,
            password = msg.password,
            characterName = msg.characterName,
            characterClass = msg.characterClass,
            race = msg.race,
            gender = msg.gender,
            allocatedStats = msg.allocatedStats,
            spawnRoomId = worldGraph.defaultSpawnRoom,
            classCatalog = classCatalog,
            raceCatalog = raceCatalog,
            pcSpriteCatalog = pcSpriteCatalog
        )

        result.fold(
            onSuccess = {
                // Grant starter equipment
                val starterWeapon = GameConfig.StarterEquipment.weaponForClass(msg.characterClass)
                inventoryRepository.addItem(msg.characterName, starterWeapon)
                inventoryRepository.equipItem(msg.characterName, starterWeapon, "weapon")
                inventoryRepository.addItem(msg.characterName, GameConfig.StarterEquipment.ARMOR_ITEM_ID)
                inventoryRepository.equipItem(msg.characterName, GameConfig.StarterEquipment.ARMOR_ITEM_ID, GameConfig.StarterEquipment.ARMOR_SLOT)
                coinRepository.addCoins(msg.characterName, Coins(copper = GameConfig.StarterEquipment.STARTING_COPPER))
                logger.info("Granted starter equipment to ${msg.characterName}: $starterWeapon + ${GameConfig.StarterEquipment.ARMOR_ITEM_ID} + ${GameConfig.StarterEquipment.STARTING_COPPER} copper")

                session.send(ServerMessage.RegisterOk)
                logger.info("Player registered: ${msg.characterName}")
            },
            onFailure = {
                session.send(ServerMessage.AuthError(it.message ?: "Registration failed"))
            }
        )
    }

    private suspend fun handleLogin(session: PlayerSession, msg: ClientMessage.Login) {
        if (session.isAuthenticated) {
            session.send(ServerMessage.AuthError("Already logged in"))
            return
        }

        if (sessionManager.isUsernameLoggedIn(msg.username)) {
            session.send(ServerMessage.AuthError("Account already logged in"))
            return
        }

        if (!checkLoginRateLimit(msg.username)) {
            session.send(ServerMessage.AuthError("Too many failed attempts. Try again in a minute."))
            return
        }

        val result = playerRepository.authenticate(msg.username, msg.password)

        result.fold(
            onSuccess = { player ->
                clearFailedLogins(msg.username)
                // Auto-promote admin from env var
                val effectivePlayer = if (msg.username.lowercase() in adminUsernames && !player.isAdmin) {
                    playerRepository.promoteAdmin(msg.username)
                    player.copy(isAdmin = true)
                } else {
                    player
                }

                // Auto-link platform account on password login if platform session exists
                val platformId = session.platformUserId
                if (platformId != null && playerRepository.findByPlatformUserId(platformId) == null) {
                    playerRepository.linkPlatformUser(effectivePlayer.name, platformId)
                    logger.info("Linked platform user $platformId to character ${effectivePlayer.name}")
                }

                session.player = effectivePlayer
                session.playerName = effectivePlayer.name
                session.currentRoomId = effectivePlayer.currentRoomId

                // Load persisted discovery data
                val discovery = discoveryRepository.loadPlayerDiscovery(effectivePlayer.name)
                session.visitedRooms.addAll(discovery.visitedRooms)
                session.discoveredHiddenExits.addAll(discovery.discoveredHiddenExits)
                session.discoveredLockedExits.addAll(discovery.discoveredLockedExits)
                session.discoveredInteractables.addAll(discovery.discoveredInteractables)
                session.seenTutorials.addAll(discovery.tutorials)
                session.visitedRooms.add(effectivePlayer.currentRoomId)

                val added = sessionManager.addSession(effectivePlayer.name, session, username = msg.username)
                if (!added) {
                    session.send(ServerMessage.AuthError("Account already logged in"))
                    return
                }
                session.combatGraceTicks = GameConfig.Combat.GRACE_TICKS

                session.send(ServerMessage.LoginOk(effectivePlayer))

                // First-time welcome tutorial for new characters (sent right after LoginOk for predictable ordering).
                // Per-world intro (#272) fires BEFORE welcome so worlds can set the scene before the generic onboarding.
                if (tutorialService != null) {
                    tutorialService.trySendWorldIntro(session)
                    tutorialService.trySend(session, "welcome",
                        contentOverride = "Greetings, ${effectivePlayer.name}!\n\n" +
                            "Use the directional pad to move between rooms. " +
                            "Tap hostile NPCs to select a target, then toggle attack mode (crossed swords) to fight.\n\n" +
                            "Open the Adventurer's Tome (\u2753) in the toolbar for a full guide to all game systems.\n\n" +
                            "May your blade stay sharp and your mana never run dry!"
                    )
                } else if ("welcome" !in session.seenTutorials) {
                    // Fallback for tests without TutorialService
                    session.seenTutorials.add("welcome")
                    discoveryRepository.markTutorialSeen(effectivePlayer.name, "welcome")
                    session.send(ServerMessage.Tutorial(
                        key = "welcome",
                        title = "Welcome to NeoMud!",
                        content = "Greetings, ${effectivePlayer.name}!\n\n" +
                            "Use the directional pad to move between rooms. " +
                            "Tap hostile NPCs to select a target, then toggle attack mode (crossed swords) to fight.\n\n" +
                            "Open the Adventurer's Tome (\u2753) in the toolbar for a full guide to all game systems.\n\n" +
                            "May your blade stay sharp and your mana never run dry!"
                    ))
                }

                // Send initial room info
                val room = worldGraph.getRoom(effectivePlayer.currentRoomId)
                if (room != null) {
                    val playersInRoom = sessionManager.getVisiblePlayerInfosInRoom(effectivePlayer.currentRoomId)
                        .filter { it.name != effectivePlayer.name }
                    val npcsInRoom = npcManager.getNpcsInRoom(effectivePlayer.currentRoomId)
                    session.send(ServerMessage.RoomInfo(room, playersInRoom, npcsInRoom))

                    val mapRooms = MapRoomFilter.enrichForPlayer(
                        worldGraph.getRoomsNear(effectivePlayer.currentRoomId), session, worldGraph, sessionManager, npcManager
                    )
                    session.send(ServerMessage.MapData(mapRooms, effectivePlayer.currentRoomId, session.visitedRooms.toSet()))

                    // Broadcast to others in room
                    sessionManager.broadcastToRoom(
                        effectivePlayer.currentRoomId,
                        ServerMessage.PlayerEntered(effectivePlayer.name, effectivePlayer.currentRoomId, session.toPlayerInfo()),
                        exclude = effectivePlayer.name
                    )
                }

                // Send initial inventory
                inventoryCommand.sendInventoryUpdate(session)

                // Send ground items for current room
                val groundItems = roomItemManager.getGroundItems(effectivePlayer.currentRoomId)
                val groundCoins = roomItemManager.getGroundCoins(effectivePlayer.currentRoomId)
                session.send(ServerMessage.RoomItemsUpdate(groundItems, groundCoins))

                logger.info("Player logged in: ${effectivePlayer.name}${if (effectivePlayer.isAdmin) " [ADMIN]" else ""}")
            },
            onFailure = {
                recordFailedLogin(msg.username)
                session.send(ServerMessage.AuthError(it.message ?: "Login failed"))
            }
        )
    }

    /** Shared post-authentication setup: load discovery, send LoginOk, room info, inventory. */
    private suspend fun completeLogin(session: PlayerSession, player: com.neomud.shared.model.Player, username: String) {
        session.player = player
        session.playerName = player.name
        session.currentRoomId = player.currentRoomId

        val discovery = discoveryRepository.loadPlayerDiscovery(player.name)
        session.visitedRooms.addAll(discovery.visitedRooms)
        session.discoveredHiddenExits.addAll(discovery.discoveredHiddenExits)
        session.discoveredLockedExits.addAll(discovery.discoveredLockedExits)
        session.discoveredInteractables.addAll(discovery.discoveredInteractables)
        session.seenTutorials.addAll(discovery.tutorials)
        session.visitedRooms.add(player.currentRoomId)

        val added = sessionManager.addSession(player.name, session, username = username)
        if (!added) {
            session.send(ServerMessage.AuthError("Account already logged in"))
            return
        }
        session.combatGraceTicks = GameConfig.Combat.GRACE_TICKS

        session.send(ServerMessage.LoginOk(player))

        if (tutorialService != null) {
            // Per-world intro (#272) fires BEFORE welcome on every onboarding path
            // (Login + Register + GuestLogin + PlatformLogin all flow through here).
            tutorialService.trySendWorldIntro(session)
            tutorialService.trySend(session, "welcome",
                contentOverride = "Greetings, ${player.name}!\n\n" +
                    "Use the directional pad to move between rooms. " +
                    "Tap hostile NPCs to select a target, then toggle attack mode (crossed swords) to fight.\n\n" +
                    "Open the Adventurer's Tome (\u2753) in the toolbar for a full guide to all game systems.\n\n" +
                    "May your blade stay sharp and your mana never run dry!"
            )
        } else if ("welcome" !in session.seenTutorials) {
            session.seenTutorials.add("welcome")
            discoveryRepository.markTutorialSeen(player.name, "welcome")
            session.send(ServerMessage.Tutorial(
                key = "welcome",
                title = "Welcome to NeoMud!",
                content = "Greetings, ${player.name}!\n\nUse the directional pad to move between rooms. " +
                    "Tap hostile NPCs to select a target, then toggle attack mode (crossed swords) to fight.\n\n" +
                    "Open the Adventurer's Tome (\u2753) in the toolbar for a full guide to all game systems.\n\n" +
                    "May your blade stay sharp and your mana never run dry!"
            ))
        }

        val room = worldGraph.getRoom(player.currentRoomId)
        if (room != null) {
            val playersInRoom = sessionManager.getVisiblePlayerInfosInRoom(player.currentRoomId)
                .filter { it.name != player.name }
            val npcsInRoom = npcManager.getNpcsInRoom(player.currentRoomId)
            session.send(ServerMessage.RoomInfo(room, playersInRoom, npcsInRoom))
            val mapRooms = MapRoomFilter.enrichForPlayer(
                worldGraph.getRoomsNear(player.currentRoomId), session, worldGraph, sessionManager, npcManager
            )
            session.send(ServerMessage.MapData(mapRooms, player.currentRoomId, session.visitedRooms.toSet()))
            sessionManager.broadcastToRoom(
                player.currentRoomId,
                ServerMessage.PlayerEntered(player.name, player.currentRoomId, session.toPlayerInfo()),
                exclude = player.name
            )
        }

        inventoryCommand.sendInventoryUpdate(session)
        val groundItems = roomItemManager.getGroundItems(player.currentRoomId)
        val groundCoins = roomItemManager.getGroundCoins(player.currentRoomId)
        session.send(ServerMessage.RoomItemsUpdate(groundItems, groundCoins))

        logger.info("Player logged in: ${player.name}${if (player.isAdmin) " [ADMIN]" else ""}")
    }

    // ─── Platform auth handlers ─────────────────────────────

    private suspend fun handlePlatformLogin(session: PlayerSession) {
        if (session.isAuthenticated) {
            session.send(ServerMessage.AuthError("Already logged in"))
            return
        }
        val platformUserId = session.platformUserId
        if (platformUserId == null) {
            session.send(ServerMessage.AuthError("No platform session — use username/password login"))
            return
        }

        val result = playerRepository.authenticateByPlatformId(platformUserId)
        result.fold(
            onSuccess = { player ->
                val internalUsername = "platform_$platformUserId"
                if (sessionManager.isUsernameLoggedIn(internalUsername)) {
                    session.send(ServerMessage.AuthError("Account already logged in"))
                    return
                }
                completeLogin(session, player, username = internalUsername)
            },
            onFailure = {
                session.send(ServerMessage.AuthError(it.message ?: "No character found for this platform account"))
            }
        )
    }

    private suspend fun handlePlatformRegister(session: PlayerSession, msg: ClientMessage.PlatformRegister) {
        if (session.isAuthenticated) {
            session.send(ServerMessage.AuthError("Already logged in"))
            return
        }
        val platformUserId = session.platformUserId
        if (platformUserId == null) {
            session.send(ServerMessage.AuthError("No platform session — use standard registration"))
            return
        }

        // Anonymous platform sessions (role=GUEST) can't own persistent
        // characters — they have no Platform DB record to attach to. Force
        // these to the ephemeral GuestLogin path so they get a character
        // that exists for the session and disappears on disconnect.
        if (session.platformRole == "GUEST") {
            session.send(ServerMessage.AuthError("Guest sessions can't save characters — use the guest character flow."))
            return
        }

        // Check for existing character on this world
        if (playerRepository.findByPlatformUserId(platformUserId) != null) {
            session.send(ServerMessage.AuthError("You already have a character on this world"))
            return
        }

        if (!Regex("^[a-zA-Z][a-zA-Z0-9_ ]{1,19}$").matches(msg.characterName)) {
            session.send(ServerMessage.AuthError("Character name must be 2-20 characters, start with a letter."))
            return
        }

        // Internal username/password — player authenticates via platform token, not credentials
        val internalUsername = "platform_$platformUserId"
        val internalPassword = java.util.UUID.randomUUID().toString()

        val result = playerRepository.createPlayer(
            username = internalUsername,
            password = internalPassword,
            characterName = msg.characterName,
            characterClass = msg.characterClass,
            race = msg.race,
            gender = msg.gender,
            allocatedStats = msg.allocatedStats,
            spawnRoomId = worldGraph.defaultSpawnRoom,
            classCatalog = classCatalog,
            raceCatalog = raceCatalog,
            pcSpriteCatalog = pcSpriteCatalog,
            platformUserId = platformUserId
        )

        result.fold(
            onSuccess = { player ->
                session.send(ServerMessage.RegisterOk)
                // Auto-login after registration
                completeLogin(session, player, username = internalUsername)
            },
            onFailure = {
                session.send(ServerMessage.AuthError(it.message ?: "Registration failed"))
            }
        )
    }

    // ─── Guest (ephemeral) auth handler ──────────────────────

    private suspend fun handleGuestLogin(session: PlayerSession, msg: ClientMessage.GuestLogin) {
        if (session.isAuthenticated) {
            session.send(ServerMessage.AuthError("Already logged in"))
            return
        }

        if (!Regex("^[a-zA-Z][a-zA-Z0-9_ ]{1,19}$").matches(msg.characterName)) {
            session.send(ServerMessage.AuthError("Character name must be 2-20 characters, start with a letter, and contain only letters, numbers, spaces, or underscores."))
            return
        }

        // Check name availability before consuming a rate limit slot
        val (_, nameAvailable) = playerRepository.checkNameAvailability("", msg.characterName)
        if (!nameAvailable) {
            session.send(ServerMessage.AuthError("Character name already taken"))
            return
        }

        // Rate limit guest creation per IP
        if (!checkGuestRateLimit(session.remoteIp)) {
            session.send(ServerMessage.AuthError("Too many guest sessions. Try again later or create an account."))
            return
        }

        // Generate internal credentials — player never sees or uses these
        val guestId = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
        val internalUsername = "guest_$guestId"
        val internalPassword = java.util.UUID.randomUUID().toString()

        val result = playerRepository.createPlayer(
            username = internalUsername,
            password = internalPassword,
            characterName = msg.characterName,
            characterClass = msg.characterClass,
            race = msg.race,
            gender = msg.gender,
            allocatedStats = msg.allocatedStats,
            spawnRoomId = worldGraph.defaultSpawnRoom,
            classCatalog = classCatalog,
            raceCatalog = raceCatalog,
            pcSpriteCatalog = pcSpriteCatalog,
            isEphemeral = true
        )

        result.fold(
            onSuccess = { player ->
                // Grant starter equipment (same as Register)
                val starterWeapon = GameConfig.StarterEquipment.weaponForClass(msg.characterClass)
                inventoryRepository.addItem(msg.characterName, starterWeapon)
                inventoryRepository.equipItem(msg.characterName, starterWeapon, "weapon")
                inventoryRepository.addItem(msg.characterName, GameConfig.StarterEquipment.ARMOR_ITEM_ID)
                inventoryRepository.equipItem(msg.characterName, GameConfig.StarterEquipment.ARMOR_ITEM_ID, GameConfig.StarterEquipment.ARMOR_SLOT)
                coinRepository.addCoins(msg.characterName, Coins(copper = GameConfig.StarterEquipment.STARTING_COPPER))

                session.isGuest = true
                session.send(ServerMessage.RegisterOk)
                completeLogin(session, player, username = internalUsername)
                logger.info("Guest player created and logged in: ${msg.characterName} (username=$internalUsername)")
            },
            onFailure = {
                session.send(ServerMessage.AuthError(it.message ?: "Guest registration failed"))
            }
        )
    }

    /** Track guest creations per IP: ip → (count, windowStart) */
    private val guestCreations = ConcurrentHashMap<String, Pair<Int, Long>>()

    private fun checkGuestRateLimit(ip: String): Boolean {
        if (ip.isBlank()) return false
        val now = System.currentTimeMillis()
        val hourMs = 3_600_000L
        val entry = guestCreations[ip]
        if (entry == null || now - entry.second > hourMs) {
            guestCreations[ip] = 1 to now
            return true
        }
        if (entry.first >= GameConfig.Security.MAX_GUEST_CREATIONS_PER_IP_PER_HOUR) {
            return false
        }
        guestCreations[ip] = (entry.first + 1) to entry.second
        return true
    }

    private suspend fun handleReadySpell(session: PlayerSession, msg: ClientMessage.ReadySpell) {
        val player = session.player ?: return
        val spellId = msg.spellId?.removePrefix("spell:")?.uppercase()

        if (spellId == null) {
            session.readiedSpellId = null
            return
        }

        val spell = spellCatalog.getSpell(spellId)
        if (spell == null) {
            session.send(ServerMessage.SystemMessage("Unknown spell."))
            return
        }

        val classDef = classCatalog.getClass(player.characterClass)
        val schoolLevel = classDef?.magicSchools?.get(spell.school)
        if (schoolLevel == null) {
            session.send(ServerMessage.SystemMessage("Your class cannot cast ${spell.school} spells."))
            return
        }
        if (schoolLevel < spell.schoolLevel) {
            session.send(ServerMessage.SystemMessage("Your training in ${spell.school} magic is not advanced enough."))
            return
        }
        if (player.level < spell.levelRequired) {
            session.send(ServerMessage.SystemMessage("You need level ${spell.levelRequired} to cast ${spell.name}."))
            return
        }

        session.readiedSpellId = spellId

        // Entering spell combat breaks meditation, rest, stealth, and grace period
        MeditationUtils.breakMeditation(session, "You stop meditating.")
        RestUtils.breakRest(session, "You stop resting.")
        StealthUtils.breakStealth(session, sessionManager, "Casting a spell reveals your presence!")
        session.combatGraceTicks = 0
    }

    /**
     * Check if the player's class can use the given skill.
     * Returns true if allowed, false (and sends error) if not.
     */
    private suspend fun canUseSkill(session: PlayerSession, skillId: String): Boolean {
        val player = session.player ?: return false
        val skill = skillCatalog.getSkill(skillId)
        if (skill == null) {
            session.send(ServerMessage.SystemMessage("Unknown skill: $skillId"))
            return false
        }
        if (skill.classRestrictions.isNotEmpty() && player.characterClass !in skill.classRestrictions) {
            session.send(ServerMessage.SystemMessage("Your class cannot use ${skill.name}."))
            return false
        }
        return true
    }

    private suspend inline fun requireAuth(session: PlayerSession, block: () -> Unit) {
        if (!session.isAuthenticated) {
            session.send(ServerMessage.Error("You must log in first"))
            return
        }
        block()
    }
}
