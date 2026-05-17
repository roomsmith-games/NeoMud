package com.neomud.server.persistence.repository

import com.neomud.server.game.GameConfig
import com.neomud.server.persistence.tables.PlayersTable
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.PcSpriteCatalog
import com.neomud.server.world.RaceCatalog
import com.neomud.shared.model.Player
import com.neomud.shared.model.RoomId
import com.neomud.shared.model.StatAllocator
import com.neomud.shared.model.Stats
import com.neomud.server.persistence.tables.InventoryTable
import com.neomud.server.persistence.tables.PlayerCoinsTable
import com.neomud.server.persistence.tables.PlayerDiscoveryTable
import org.jetbrains.exposed.v1.core.*
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.slf4j.LoggerFactory
import at.favre.lib.crypto.bcrypt.BCrypt

class PlayerRepository {
    private val logger = LoggerFactory.getLogger(PlayerRepository::class.java)

    companion object {
        fun pcSpriteRelativePath(race: String, gender: String, characterClass: String): String =
            "images/players/${race.lowercase()}_${gender.lowercase()}_${characterClass.lowercase()}.webp"

        /** Cost factor for bcrypt — 12 is ~250ms on modern hardware */
        private const val BCRYPT_COST = 12

        /** Detect legacy SHA-256 hashes (64 hex chars, no '$' prefix) */
        private fun isLegacySha256(hash: String): Boolean =
            hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }

        private fun legacySha256(password: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(password.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }
        }
    }

    /** Check if a username and character name are available for registration. */
    fun checkNameAvailability(username: String, characterName: String): Pair<Boolean, Boolean> {
        return transaction {
            val usernameExists = PlayersTable.selectAll()
                .where { PlayersTable.username eq username }
                .count() > 0
            val characterNameExists = PlayersTable.selectAll()
                .where { PlayersTable.characterName eq characterName }
                .count() > 0
            Pair(!usernameExists, !characterNameExists)
        }
    }

    fun createPlayer(
        username: String,
        password: String,
        characterName: String,
        characterClass: String,
        race: String,
        gender: String = "neutral",
        allocatedStats: Stats,
        spawnRoomId: RoomId,
        classCatalog: ClassCatalog,
        raceCatalog: RaceCatalog?,
        pcSpriteCatalog: PcSpriteCatalog? = null,
        platformUserId: String? = null,
        isEphemeral: Boolean = false
    ): Result<Player> = runCatching {
        transaction {
            val existing = PlayersTable.selectAll().where {
                (PlayersTable.username eq username) or (PlayersTable.characterName eq characterName)
            }.firstOrNull()

            if (existing != null) {
                if (existing[PlayersTable.username] == username) {
                    error("Username already taken")
                } else {
                    error("Character name already taken")
                }
            }

            val classDef = classCatalog.getClass(characterClass)
                ?: error("Unknown character class: $characterClass")
            val raceDef = raceCatalog?.getRace(race)

            // Effective minimums: class mins + race mods (already baked into client allocation)
            val baseStats = if (raceDef != null) {
                Stats(
                    strength = maxOf(1, classDef.minimumStats.strength + raceDef.statModifiers.strength),
                    agility = maxOf(1, classDef.minimumStats.agility + raceDef.statModifiers.agility),
                    intellect = maxOf(1, classDef.minimumStats.intellect + raceDef.statModifiers.intellect),
                    willpower = maxOf(1, classDef.minimumStats.willpower + raceDef.statModifiers.willpower),
                    health = maxOf(1, classDef.minimumStats.health + raceDef.statModifiers.health),
                    charm = maxOf(1, classDef.minimumStats.charm + raceDef.statModifiers.charm)
                )
            } else {
                classDef.minimumStats
            }

            // Validate CP allocation against effective minimums
            if (!StatAllocator.isValidAllocation(allocatedStats, baseStats)) {
                error("Invalid stat allocation")
            }

            // Stats already include race mods from client allocation
            val stats = allocatedStats

            // Level 1 gets max HP roll for fairness
            val maxHp = classDef.hpPerLevelMax + (stats.health / GameConfig.PlayerCreation.HP_HEALTH_DIVISOR) * GameConfig.PlayerCreation.HP_HEALTH_MULTIPLIER
            val maxMp = if (classDef.mpPerLevelMax > 0) classDef.mpPerLevelMax + (stats.willpower / GameConfig.PlayerCreation.MP_WILLPOWER_DIVISOR) * GameConfig.PlayerCreation.MP_WILLPOWER_MULTIPLIER else 0
            val initialXpToNext = (GameConfig.Progression.XP_BASE_MULTIPLIER * Math.pow(1.0, GameConfig.Progression.XP_CURVE_EXPONENT)).toLong().coerceAtLeast(GameConfig.Progression.XP_MINIMUM)

            val spriteDef = pcSpriteCatalog?.getSpriteFor(race, gender, characterClass)
            val initialImagePrompt = spriteDef?.imagePrompt
                ?: "A $gender ${race.lowercase()} ${characterClass.lowercase()}, fantasy RPG character portrait, full body, facing forward"
            val initialImageStyle = spriteDef?.imageStyle ?: ""
            val initialImageNegativePrompt = spriteDef?.imageNegativePrompt ?: ""

            val effectiveHash = if (password.isBlank()) {
                hashPassword(java.util.UUID.randomUUID().toString())
            } else {
                hashPassword(password)
            }

            PlayersTable.insert {
                it[PlayersTable.username] = username
                it[passwordHash] = effectiveHash
                it[PlayersTable.characterName] = characterName
                it[PlayersTable.characterClass] = characterClass
                it[PlayersTable.race] = race
                it[PlayersTable.gender] = gender
                it[PlayersTable.imagePrompt] = initialImagePrompt
                it[PlayersTable.imageStyle] = initialImageStyle
                it[PlayersTable.imageNegativePrompt] = initialImageNegativePrompt
                it[strength] = stats.strength
                it[agility] = stats.agility
                it[intellect] = stats.intellect
                it[willpower] = stats.willpower
                it[health] = stats.health
                it[charm] = stats.charm
                it[baseStrength] = baseStats.strength
                it[baseAgility] = baseStats.agility
                it[baseIntellect] = baseStats.intellect
                it[baseWillpower] = baseStats.willpower
                it[baseHealth] = baseStats.health
                it[baseCharm] = baseStats.charm
                it[currentHp] = maxHp
                it[PlayersTable.maxHp] = maxHp
                it[currentMp] = maxMp
                it[PlayersTable.maxMp] = maxMp
                it[level] = 1
                it[currentRoomId] = spawnRoomId
                it[currentXp] = 0
                it[xpToNextLevel] = initialXpToNext
                it[unspentCp] = 0
                it[totalCpEarned] = StatAllocator.CP_POOL
                if (platformUserId != null) {
                    it[PlayersTable.platformUserId] = platformUserId
                }
                it[PlayersTable.isEphemeral] = isEphemeral
            }

            Player(
                name = characterName,
                characterClass = characterClass,
                stats = stats,
                currentHp = maxHp,
                maxHp = maxHp,
                currentMp = maxMp,
                maxMp = maxMp,
                level = 1,
                currentRoomId = spawnRoomId,
                race = race,
                gender = gender,
                currentXp = 0,
                xpToNextLevel = initialXpToNext,
                unspentCp = 0,
                totalCpEarned = StatAllocator.CP_POOL,
                imagePrompt = initialImagePrompt,
                imageStyle = initialImageStyle,
                imageNegativePrompt = initialImageNegativePrompt,
                isGuest = isEphemeral
            )
        }
    }

    fun authenticate(username: String, password: String): Result<Player> = runCatching {
        transaction {
            val row = PlayersTable.selectAll().where {
                PlayersTable.username eq username
            }.firstOrNull() ?: error("Invalid username or password")

            val storedHash = row[PlayersTable.passwordHash]
            if (!verifyPassword(password, storedHash)) {
                error("Invalid username or password")
            }
            // Transparently upgrade legacy SHA-256 hashes to bcrypt on successful login
            upgradeHashIfNeeded(username, password, storedHash)

            val stats = Stats(
                strength = row[PlayersTable.strength],
                agility = row[PlayersTable.agility],
                intellect = row[PlayersTable.intellect],
                willpower = row[PlayersTable.willpower],
                health = row[PlayersTable.health],
                charm = row[PlayersTable.charm]
            )

            Player(
                name = row[PlayersTable.characterName],
                characterClass = row[PlayersTable.characterClass],
                stats = stats,
                currentHp = row[PlayersTable.currentHp],
                maxHp = row[PlayersTable.maxHp],
                currentMp = row[PlayersTable.currentMp],
                maxMp = row[PlayersTable.maxMp],
                level = row[PlayersTable.level],
                currentRoomId = row[PlayersTable.currentRoomId],
                race = row[PlayersTable.race],
                gender = row[PlayersTable.gender],
                currentXp = row[PlayersTable.currentXp],
                xpToNextLevel = row[PlayersTable.xpToNextLevel],
                unspentCp = row[PlayersTable.unspentCp],
                totalCpEarned = row[PlayersTable.totalCpEarned],
                isAdmin = row[PlayersTable.isAdmin],
                imagePrompt = row[PlayersTable.imagePrompt],
                imageStyle = row[PlayersTable.imageStyle],
                imageNegativePrompt = row[PlayersTable.imageNegativePrompt]
            )
        }
    }

    fun authenticateByCharacterName(characterName: String): Result<Player> = runCatching {
        transaction {
            val row = PlayersTable.selectAll().where {
                PlayersTable.characterName eq characterName
            }.firstOrNull() ?: error("Character not found")
            rowToPlayer(row)
        }
    }

    fun getBaseStats(characterName: String): Stats? = transaction {
        PlayersTable.selectAll().where {
            PlayersTable.characterName eq characterName
        }.firstOrNull()?.let { row ->
            Stats(
                strength = row[PlayersTable.baseStrength],
                agility = row[PlayersTable.baseAgility],
                intellect = row[PlayersTable.baseIntellect],
                willpower = row[PlayersTable.baseWillpower],
                health = row[PlayersTable.baseHealth],
                charm = row[PlayersTable.baseCharm]
            )
        }
    }

    fun saveBaseStats(characterName: String, baseStats: Stats) {
        transaction {
            PlayersTable.update({ PlayersTable.characterName eq characterName }) {
                it[baseStrength] = baseStats.strength
                it[baseAgility] = baseStats.agility
                it[baseIntellect] = baseStats.intellect
                it[baseWillpower] = baseStats.willpower
                it[baseHealth] = baseStats.health
                it[baseCharm] = baseStats.charm
            }
        }
    }

    fun savePlayerState(player: Player) {
        transaction {
            PlayersTable.update({ PlayersTable.characterName eq player.name }) {
                it[currentHp] = player.currentHp
                it[maxHp] = player.maxHp
                it[currentMp] = player.currentMp
                it[maxMp] = player.maxMp
                it[level] = player.level
                it[currentRoomId] = player.currentRoomId
                it[strength] = player.stats.strength
                it[agility] = player.stats.agility
                it[intellect] = player.stats.intellect
                it[willpower] = player.stats.willpower
                it[health] = player.stats.health
                it[charm] = player.stats.charm
                it[currentXp] = player.currentXp
                it[xpToNextLevel] = player.xpToNextLevel
                it[unspentCp] = player.unspentCp
                it[totalCpEarned] = player.totalCpEarned
            }
        }
    }

    fun promoteAdmin(characterName: String) {
        transaction {
            PlayersTable.update({ PlayersTable.characterName eq characterName }) {
                it[isAdmin] = true
            }
        }
    }

    private fun hashPassword(password: String): String =
        BCrypt.withDefaults().hashToString(BCRYPT_COST, password.toCharArray())

    private fun verifyPassword(password: String, storedHash: String): Boolean {
        return if (isLegacySha256(storedHash)) {
            // Legacy SHA-256 hash — verify and return true to trigger migration
            storedHash == legacySha256(password)
        } else {
            BCrypt.verifyer().verify(password.toCharArray(), storedHash).verified
        }
    }

    /** Upgrade a legacy SHA-256 hash to bcrypt in-place */
    private fun upgradeHashIfNeeded(username: String, password: String, storedHash: String) {
        if (isLegacySha256(storedHash)) {
            val bcryptHash = hashPassword(password)
            transaction {
                PlayersTable.update({ PlayersTable.username eq username }) {
                    it[passwordHash] = bcryptHash
                }
            }
        }
    }

    // ─── Platform auth methods ──────────────────────────────

    /** Link an existing character to a Platform user ID. */
    fun linkPlatformUser(characterName: String, platformUserId: String) {
        transaction {
            PlayersTable.update({ PlayersTable.characterName eq characterName }) {
                it[PlayersTable.platformUserId] = platformUserId
            }
        }
    }

    /** Find a player by their Platform user ID. Returns null if no character on this world. */
    fun findByPlatformUserId(platformUserId: String): Player? = transaction {
        PlayersTable.selectAll().where {
            PlayersTable.platformUserId eq platformUserId
        }.firstOrNull()?.let { row ->
            rowToPlayer(row)
        }
    }

    /** Find all players linked to a Platform user ID. */
    fun findAllByPlatformUserId(platformUserId: String): List<Player> = transaction {
        PlayersTable.selectAll().where {
            PlayersTable.platformUserId eq platformUserId
        }.map { rowToPlayer(it) }
    }

    /** Load a player by Platform user ID without password check. */
    fun authenticateByPlatformId(platformUserId: String): Result<Player> = runCatching {
        transaction {
            val row = PlayersTable.selectAll().where {
                PlayersTable.platformUserId eq platformUserId
            }.firstOrNull() ?: error("No character linked to this platform account")
            rowToPlayer(row)
        }
    }

    /** Load a specific character by Platform user ID and character name. */
    fun authenticateByPlatformIdAndName(platformUserId: String, characterName: String): Result<Player> = runCatching {
        transaction {
            val row = PlayersTable.selectAll().where {
                (PlayersTable.platformUserId eq platformUserId) and
                (PlayersTable.characterName eq characterName)
            }.firstOrNull() ?: error("No character '$characterName' linked to this platform account")
            rowToPlayer(row)
        }
    }

    private fun rowToPlayer(row: org.jetbrains.exposed.v1.core.ResultRow): Player {
        val stats = Stats(
            strength = row[PlayersTable.strength],
            agility = row[PlayersTable.agility],
            intellect = row[PlayersTable.intellect],
            willpower = row[PlayersTable.willpower],
            health = row[PlayersTable.health],
            charm = row[PlayersTable.charm]
        )
        return Player(
            name = row[PlayersTable.characterName],
            characterClass = row[PlayersTable.characterClass],
            stats = stats,
            currentHp = row[PlayersTable.currentHp],
            maxHp = row[PlayersTable.maxHp],
            currentMp = row[PlayersTable.currentMp],
            maxMp = row[PlayersTable.maxMp],
            level = row[PlayersTable.level],
            currentRoomId = row[PlayersTable.currentRoomId],
            race = row[PlayersTable.race],
            gender = row[PlayersTable.gender],
            currentXp = row[PlayersTable.currentXp],
            xpToNextLevel = row[PlayersTable.xpToNextLevel],
            unspentCp = row[PlayersTable.unspentCp],
            totalCpEarned = row[PlayersTable.totalCpEarned],
            isAdmin = row[PlayersTable.isAdmin],
            imagePrompt = row[PlayersTable.imagePrompt],
            imageStyle = row[PlayersTable.imageStyle],
            imageNegativePrompt = row[PlayersTable.imageNegativePrompt],
            isGuest = row[PlayersTable.isEphemeral]
        )
    }

    // ─── Ephemeral guest cleanup ──────────────────────────────

    /** Delete a player and all associated data (inventory, coins, discovery). */
    fun deletePlayer(characterName: String) {
        transaction {
            InventoryTable.deleteWhere { InventoryTable.playerName eq characterName }
            PlayerCoinsTable.deleteWhere { PlayerCoinsTable.playerName eq characterName }
            PlayerDiscoveryTable.deleteWhere { PlayerDiscoveryTable.playerName eq characterName }
            PlayersTable.deleteWhere { PlayersTable.characterName eq characterName }
        }
    }

    /** Sweep all ephemeral guest players from the database (startup cleanup). */
    fun deleteEphemeralPlayers(): Int {
        return transaction {
            val names = PlayersTable.selectAll()
                .where { PlayersTable.isEphemeral eq true }
                .map { it[PlayersTable.characterName] }

            for (name in names) {
                InventoryTable.deleteWhere { InventoryTable.playerName eq name }
                PlayerCoinsTable.deleteWhere { PlayerCoinsTable.playerName eq name }
                PlayerDiscoveryTable.deleteWhere { PlayerDiscoveryTable.playerName eq name }
            }
            PlayersTable.deleteWhere { PlayersTable.isEphemeral eq true }
            names.size
        }
    }
}
