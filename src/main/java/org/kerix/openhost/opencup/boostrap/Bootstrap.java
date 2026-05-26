package org.kerix.openhost.opencup.boostrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.scoring.ScoringService;
import org.kerix.openhost.opencup.core.timer.TimerService;
import org.kerix.openhost.opencup.persistence.PlayerStatsRepository;
import org.kerix.openhost.opencup.persistence.TournamentRepository;
import org.kerix.openhost.opencup.persistence.async.AsyncPersistenceWorker;
import org.kerix.openhost.opencup.persistence.impl.YamlPlayerStatsRepository;
import org.kerix.openhost.opencup.persistence.impl.YamlTournamentRepository;

import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.config.ArenaConfigLoader;
import org.kerix.openhost.opencup.config.TournamentConfigLoader;
import org.kerix.openhost.opencup.config.schema.ArenaSchema;
import org.kerix.openhost.opencup.core.arena.ArenaManager;
import org.kerix.openhost.opencup.core.engine.TournamentEngine;
import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.registry.MinigameRegistry;
import org.kerix.openhost.opencup.core.scoring.LeaderboardService;
import org.kerix.openhost.opencup.core.scoring.ScoringService;
import org.kerix.openhost.opencup.core.session.PlayerSessionManager;
import org.kerix.openhost.opencup.core.tick.TickOrchestrator;
import org.kerix.openhost.opencup.core.timer.TimerService;
import org.kerix.openhost.opencup.core.tournament.TournamentConfig;
import org.kerix.openhost.opencup.core.ui.ScoreboardManager;
import org.kerix.openhost.opencup.persistence.PlayerStatsRepository;
import org.kerix.openhost.opencup.persistence.TournamentRepository;
import org.kerix.openhost.opencup.persistence.async.AsyncPersistenceWorker;
import org.kerix.openhost.opencup.persistence.impl.YamlPlayerStatsRepository;
import org.kerix.openhost.opencup.persistence.impl.YamlTournamentRepository;

import java.util.List;

/**
 * Wires every service together in dependency order.
 * Called once from Main.onEnable(). Nothing else touches this class.
 * <p>
 * Registration order matters — ServiceRegistry.shutdownAll() runs in reverse,
 * so dependents are always shut down before their dependencies.
 * <p>
 * To add a new minigame: call minigameRegistry.register(YourMinigame.class)
 * in the "Game registry" block below and add it to tournament.yml.
 */
public final class Bootstrap {

    private final ServiceRegistry registry;
    private final JavaPlugin plugin;

    public Bootstrap(JavaPlugin plugin) {
        this.plugin   = plugin;
        this.registry = new ServiceRegistry(plugin.getLogger());
    }

    public void boot() {

        // ── 1. Core infrastructure ────────────────────────────────────────────
        // GameEventBus: pub/sub bus for all internal framework events.
        registry.bind(GameEventBus.class,
                new GameEventBus(plugin.getLogger()));

        // AsyncPersistenceWorker: single-threaded off-main-thread I/O queue.
        registry.bind(AsyncPersistenceWorker.class,
                new AsyncPersistenceWorker(plugin));

        // ── 2. Repositories ───────────────────────────────────────────────────
        registry.bind(PlayerStatsRepository.class,
                new YamlPlayerStatsRepository(
                        plugin,
                        registry.get(AsyncPersistenceWorker.class)));

        registry.bind(TournamentRepository.class,
                new YamlTournamentRepository(
                        plugin,
                        registry.get(AsyncPersistenceWorker.class)));

        // ── 3. Scoring ────────────────────────────────────────────────────────
        // ScoringService: authoritative tournament point ledger.
        registry.bind(ScoringService.class,
                new ScoringService(
                        registry.get(GameEventBus.class),
                        registry.get(AsyncPersistenceWorker.class),
                        registry.get(PlayerStatsRepository.class),
                        plugin.getLogger()));

        // LeaderboardService: Startable → start() subscribes to ScoreChangedEvent.
        // Must be bound AFTER ScoringService so the subscription fires after scores exist.
        registry.bind(LeaderboardService.class,
                new LeaderboardService(
                        registry.get(GameEventBus.class),
                        plugin.getLogger()));

        // ── 4. Scheduling ─────────────────────────────────────────────────────
        registry.bind(TimerService.class,
                new TimerService(plugin));

        // TickOrchestrator: single BukkitRunnable driving all per-tick game logic.
        // Registered as a repeating task below after binding.
        TickOrchestrator tickOrchestrator = new TickOrchestrator(plugin.getLogger());
        registry.bind(TickOrchestrator.class, tickOrchestrator);

        // The ONE repeating task in the entire plugin. Period: 1 tick.
        Bukkit.getScheduler().runTaskTimer(plugin, tickOrchestrator, 1L, 1L);
        plugin.getLogger().info("[Bootstrap] TickOrchestrator scheduled.");

        // ── 5. Session management ─────────────────────────────────────────────
        // PlayerSessionManager: answers "what is this player currently doing?"
        registry.bind(PlayerSessionManager.class,
                new PlayerSessionManager(
                        registry.get(GameEventBus.class),
                        plugin.getLogger()));

        // ── 6. Config loading ─────────────────────────────────────────────────
        // Load once at boot. Immutable after construction.
        TournamentConfig tournamentConfig = new TournamentConfig(
                new TournamentConfigLoader(plugin).load());

        List<ArenaSchema> arenaSchemas =
                new ArenaConfigLoader(plugin).loadAll();

        // ── 7. Arena management ───────────────────────────────────────────────
        // ArenaManager: Startable → start() builds ArenaImpl objects from schemas.
        // load: POSTWORLD in paper-plugin.yml guarantees worlds exist at this point.
        registry.bind(ArenaManager.class,
                new ArenaManager(arenaSchemas, plugin.getLogger()));

        // ── 8. Game registry ──────────────────────────────────────────────────
        MinigameRegistry minigameRegistry = new MinigameRegistry(plugin.getLogger());

        // ▼ Register your minigame classes here as you implement them ▼
        // minigameRegistry.register(BlockPartyMinigame.class);
        // minigameRegistry.register(KothMinigame.class);
        // minigameRegistry.register(BoatRaceMinigame.class);
        // ▲ One line per minigame. That is the entire integration cost. ▲

        registry.bind(MinigameRegistry.class, minigameRegistry);

        // ── 9. UI ─────────────────────────────────────────────────────────────
        // ScoreboardManager: Startable → start() subscribes to phase/score/role events.
        registry.bind(ScoreboardManager.class,
                new ScoreboardManager(
                        registry.get(GameEventBus.class),
                        registry.get(LeaderboardService.class)));

        // ── 10. Optional integrations ─────────────────────────────────────────
        ProtocolManager protocolManager = null;
        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            protocolManager = ProtocolLibrary.getProtocolManager();
            plugin.getLogger().info("[Bootstrap] ProtocolLib detected and bound.");
        } else {
            plugin.getLogger().warning("[Bootstrap] ProtocolLib not found. " +
                    "Minigames using ctx.getProtocolManager() will throw at runtime.");
        }

        // ── 11. Tournament engine ─────────────────────────────────────────────
        // TournamentEngine: Startable → start() subscribes to MinigameEndedEvent.
        //                   Stoppable → stop() gracefully ends any active game.
        registry.bind(TournamentEngine.class,
                new TournamentEngine(
                        tournamentConfig,
                        registry.get(MinigameRegistry.class),
                        registry.get(ArenaManager.class),
                        registry.get(ScoringService.class),
                        registry.get(PlayerSessionManager.class),
                        registry.get(GameEventBus.class),
                        registry.get(TimerService.class),
                        registry.get(TickOrchestrator.class),
                        registry.get(ScoreboardManager.class),
                        protocolManager,          // null if ProtocolLib absent — handled in context
                        plugin,
                        plugin.getLogger()));

        // ── Done ──────────────────────────────────────────────────────────────
        registry.logBindings();
    }

    public void shutdown() {
        registry.shutdownAll();
    }

    /**
     * Package-private — only Main.java should call this.
     * Nothing else should hold a reference to ServiceRegistry.
     */
    ServiceRegistry registry() {
        return registry;
    }
}
