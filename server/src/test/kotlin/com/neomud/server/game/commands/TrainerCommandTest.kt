package com.neomud.server.game.commands

import com.neomud.server.game.GameConfig
import com.neomud.server.game.npc.NpcManager
import com.neomud.server.persistence.DatabaseFactory
import com.neomud.server.persistence.repository.PlayerRepository
import com.neomud.server.session.PlayerSession
import com.neomud.server.session.SessionManager
import com.neomud.server.world.ClassCatalog
import com.neomud.server.world.NpcData
import com.neomud.server.world.RaceCatalog
import com.neomud.server.world.TrainerConfig
import com.neomud.server.world.WorldGraph
import com.neomud.shared.model.CharacterClassDef
import com.neomud.shared.model.Player
import com.neomud.shared.model.Stats
import io.ktor.websocket.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Before
import java.io.File
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TrainerCommandTest {

    private val testDbFile = File.createTempFile("trainercmdtest", ".db").also { it.deleteOnExit() }

    private val testRoomId = "town:trainer_room"
    private val testCharacterName = "TrainerTester"

    private val warriorClass = CharacterClassDef(
        id = "WARRIOR",
        name = "Warrior",
        description = "Sword and shield",
        minimumStats = Stats(strength = 15, agility = 10, intellect = 5, willpower = 5, health = 15, charm = 5),
        skills = emptyList(),
        hpPerLevelMin = 6,
        hpPerLevelMax = 6,  // deterministic for tests
        mpPerLevelMin = 0,
        mpPerLevelMax = 0,
        xpModifier = 1.0
    )

    private val classCatalog = ClassCatalog(listOf(warriorClass))
    private val raceCatalog = RaceCatalog(emptyList())
    private val sessionManager = SessionManager()

    @Before
    fun setUp() {
        DatabaseFactory.init("jdbc:sqlite:${testDbFile.absolutePath}")
    }

    private fun seedPlayer(): PlayerRepository {
        val repo = PlayerRepository()
        // createPlayer fails silently if it already exists; ignore Result on rerun
        repo.createPlayer(
            username = "tester",
            password = "pw",
            characterName = testCharacterName,
            characterClass = "WARRIOR",
            race = "HUMAN",
            allocatedStats = Stats(strength = 15, agility = 10, intellect = 5, willpower = 5, health = 15, charm = 5),
            spawnRoomId = testRoomId,
            classCatalog = classCatalog,
            raceCatalog = null
        )
        return repo
    }

    private fun newSession(player: Player): PlayerSession {
        val ws = object : WebSocketSession {
            override val coroutineContext: CoroutineContext get() = EmptyCoroutineContext
            override val incoming: Channel<Frame> get() = Channel()
            override val outgoing: Channel<Frame> get() = Channel(Channel.UNLIMITED)
            override val extensions: List<WebSocketExtension<*>> get() = emptyList()
            override var masking: Boolean = false
            override var maxFrameSize: Long = Long.MAX_VALUE
            override suspend fun flush() {}
            @Deprecated("Use cancel instead", replaceWith = ReplaceWith("cancel()"))
            override fun terminate() {}
        }
        val session = PlayerSession(ws)
        session.player = player
        session.playerName = player.name
        session.currentRoomId = testRoomId
        return session
    }

    private fun loadTrainer(npcManager: NpcManager, trainerConfig: TrainerConfig?) {
        npcManager.loadNpcs(
            listOf(
                NpcData(
                    id = "npc:test_trainer",
                    name = "Master Tester",
                    description = "A trainer.",
                    startRoomId = testRoomId,
                    behaviorType = "trainer",
                    trainerConfig = trainerConfig
                ) to "town"
            )
        )
    }

    private fun buildCommand(npcManager: NpcManager, repo: PlayerRepository): TrainerCommand =
        TrainerCommand(classCatalog, raceCatalog, repo, sessionManager, npcManager)

    private fun playerAtLevel(level: Int, currentXp: Long = 0): Player {
        // Match what createPlayer wrote to DB so getBaseStats lines up.
        return Player(
            name = testCharacterName,
            characterClass = "WARRIOR",
            race = "HUMAN",
            level = level,
            currentHp = 50,
            maxHp = 50,
            currentMp = 0,
            maxMp = 0,
            currentRoomId = testRoomId,
            currentXp = currentXp,
            xpToNextLevel = currentXp,  // ready to level if currentXp > 0
            stats = Stats(strength = 15, agility = 10, intellect = 5, willpower = 5, health = 15, charm = 5),
            unspentCp = 100,
            totalCpEarned = 100
        )
    }

    // --- Backward compat: null trainerConfig means no caps ---

    @Test
    fun nullTrainerConfigAllowsLevelUpAtAnyLevel() = runBlocking {
        val repo = seedPlayer()
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadTrainer(npcManager, trainerConfig = null)
        val command = buildCommand(npcManager, repo)

        val session = newSession(playerAtLevel(level = 5, currentXp = 1000))
        command.handleLevelUp(session)

        assertEquals(6, session.player!!.level, "Null trainerConfig must not block level-up below MAX_LEVEL")
    }

    @Test
    fun nullTrainerConfigAllowsStatTrainingFreely() = runBlocking {
        val repo = seedPlayer()
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadTrainer(npcManager, trainerConfig = null)
        val command = buildCommand(npcManager, repo)

        val session = newSession(playerAtLevel(level = 1))
        command.handleTrainStat(session, "strength", 5)

        assertEquals(20, session.player!!.stats.strength, "Null trainerConfig must allow stat training")
    }

    // --- Level cap ---

    @Test
    fun levelUpBlockedAtTrainerMaxLevel() = runBlocking {
        val repo = seedPlayer()
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadTrainer(
            npcManager,
            trainerConfig = TrainerConfig(
                tier = 1,
                maxLevel = 5,
                maxStatValue = 999,
                nextTierTrainerName = "Master Aldric",
                nextTierLocationHint = "the Foothills"
            )
        )
        val command = buildCommand(npcManager, repo)

        val session = newSession(playerAtLevel(level = 5, currentXp = 1000))
        command.handleLevelUp(session)

        assertEquals(5, session.player!!.level, "Level should be capped at trainer maxLevel")
    }

    @Test
    fun levelUpAllowedBelowTrainerMaxLevel() = runBlocking {
        val repo = seedPlayer()
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadTrainer(npcManager, trainerConfig = TrainerConfig(tier = 1, maxLevel = 5, maxStatValue = 999))
        val command = buildCommand(npcManager, repo)

        val session = newSession(playerAtLevel(level = 4, currentXp = 1000))
        command.handleLevelUp(session)

        assertEquals(5, session.player!!.level, "Below the cap, level-up should still proceed")
    }

    // --- Stat cap (single-stat training) ---

    @Test
    fun trainStatBlockedAtTrainerMaxStatValue() = runBlocking {
        val repo = seedPlayer()
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadTrainer(npcManager, trainerConfig = TrainerConfig(tier = 1, maxLevel = GameConfig.Progression.MAX_LEVEL, maxStatValue = 18))
        val command = buildCommand(npcManager, repo)

        val player = playerAtLevel(level = 1).copy(
            stats = Stats(strength = 18, agility = 10, intellect = 5, willpower = 5, health = 15, charm = 5)
        )
        val session = newSession(player)
        command.handleTrainStat(session, "strength", 5)

        assertEquals(18, session.player!!.stats.strength, "Strength should not increase past trainer cap")
    }

    @Test
    fun trainStatClampsToTrainerMaxStatValue() = runBlocking {
        val repo = seedPlayer()
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadTrainer(npcManager, trainerConfig = TrainerConfig(tier = 1, maxLevel = GameConfig.Progression.MAX_LEVEL, maxStatValue = 18))
        val command = buildCommand(npcManager, repo)

        val player = playerAtLevel(level = 1).copy(
            stats = Stats(strength = 16, agility = 10, intellect = 5, willpower = 5, health = 15, charm = 5)
        )
        val session = newSession(player)
        // Asks for +5 but only +2 fits under the cap.
        command.handleTrainStat(session, "strength", 5)

        assertEquals(18, session.player!!.stats.strength, "Training should clamp to the trainer cap rather than overshoot")
    }

    // --- Stat cap (allocate-trained-stats path) ---

    @Test
    fun allocateTrainedStatsBlockedWhenAnyStatExceedsCap() = runBlocking {
        val repo = seedPlayer()
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadTrainer(
            npcManager,
            trainerConfig = TrainerConfig(
                tier = 2,
                maxLevel = 10,
                maxStatValue = 25,
                nextTierTrainerName = "Master Veil",
                nextTierLocationHint = "Salt Coast"
            )
        )
        val command = buildCommand(npcManager, repo)

        val session = newSession(playerAtLevel(level = 1))
        // Try to push agility to 26 — over the cap of 25.
        val proposed = Stats(strength = 15, agility = 26, intellect = 5, willpower = 5, health = 15, charm = 5)
        command.handleAllocateTrainedStats(session, proposed)

        // Stats should be unchanged from the seed.
        assertEquals(10, session.player!!.stats.agility, "Allocation should be rejected when any stat exceeds the trainer cap")
    }

    @Test
    fun allocateTrainedStatsAllowedAtCap() = runBlocking {
        val repo = seedPlayer()
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadTrainer(npcManager, trainerConfig = TrainerConfig(tier = 2, maxLevel = 10, maxStatValue = 25))
        val command = buildCommand(npcManager, repo)

        val session = newSession(playerAtLevel(level = 1))
        // Push strength exactly to cap — should succeed.
        val proposed = Stats(strength = 25, agility = 10, intellect = 5, willpower = 5, health = 15, charm = 5)
        command.handleAllocateTrainedStats(session, proposed)

        assertEquals(25, session.player!!.stats.strength, "Allocation up to (but not over) the cap should succeed")
    }

    // --- Global MAX_LEVEL still applies regardless of trainer cap ---

    @Test
    fun globalMaxLevelStillCapsLevelUpEvenWithGenerousTrainer() = runBlocking {
        val repo = seedPlayer()
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        loadTrainer(
            npcManager,
            trainerConfig = TrainerConfig(tier = 5, maxLevel = 99, maxStatValue = 999)
        )
        val command = buildCommand(npcManager, repo)

        val session = newSession(playerAtLevel(level = GameConfig.Progression.MAX_LEVEL, currentXp = 1_000_000))
        command.handleLevelUp(session)

        assertEquals(GameConfig.Progression.MAX_LEVEL, session.player!!.level,
            "Global MAX_LEVEL still applies even if trainer maxLevel would allow more")
    }

    // --- Trainer is still required (no trainer in room → SystemMessage, no state change) ---

    @Test
    fun handlersNoOpWhenNoTrainerInRoom() = runBlocking {
        val repo = seedPlayer()
        val npcManager = NpcManager(WorldGraph(), emptyMap(), emptyMap())
        // Intentionally NOT loading any trainer.
        val command = buildCommand(npcManager, repo)

        val session = newSession(playerAtLevel(level = 4, currentXp = 1000))
        command.handleLevelUp(session)

        assertEquals(4, session.player!!.level, "No trainer in room → no state change")
    }
}
