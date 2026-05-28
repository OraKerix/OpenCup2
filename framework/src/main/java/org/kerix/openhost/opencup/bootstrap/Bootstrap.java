package org.kerix.openhost.opencup.bootstrap;

import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.core.arena.ArenaAccessor;
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
import org.kerix.openhost.opencup.config.ArenaConfigLoader;
import org.kerix.openhost.opencup.config.TournamentConfigLoader;
import org.kerix.openhost.opencup.config.schema.ArenaSchema;
import org.kerix.openhost.opencup.core.arena.ArenaManager;
import org.kerix.openhost.opencup.core.engine.TournamentEngine;
import org.kerix.openhost.opencup.core.registry.MinigameRegistry;
import org.kerix.openhost.opencup.core.scoring.LeaderboardService;
import org.kerix.openhost.opencup.core.session.PlayerSessionManager;
import org.kerix.openhost.opencup.core.tick.TickOrchestrator;
import org.kerix.openhost.opencup.core.tournament.TournamentConfig;
import org.kerix.openhost.opencup.core.ui.ScoreboardManager;

import java.util.List;

/**
 * Wires every service together in dependency order.
 * Called once from Main.onEnable(). Nothing else touches this class.
 * <p>
 * Registration order matters — ServiceRegistry.shutdownAll() runs in reverse,
 * so dependents are always shut down before their dependencies.
 */
public final class Bootstrap {

    private final ServiceRegistry registry;
    private final JavaPlugin plugin;
    private MinigameRegistry minigameRegistry;

    public Bootstrap(JavaPlugin plugin) {
        this.plugin = plugin;
        this.registry = new ServiceRegistry(plugin.getLogger());
    }

    public void boot() {
        registry.bind(GameEventBus.class,
                new GameEventBus(plugin.getLogger()));

        registry.bind(AsyncPersistenceWorker.class,
                new AsyncPersistenceWorker(plugin));

        registry.bind(PlayerStatsRepository.class,
                new YamlPlayerStatsRepository(
                        plugin,
                        registry.get(AsyncPersistenceWorker.class)));

        YamlTournamentRepository tournamentRepository = new YamlTournamentRepository(
                plugin,
                registry.get(AsyncPersistenceWorker.class));

        registry.bind(TournamentRepository.class, tournamentRepository);

        registry.bind(ScoringService.class,
                new ScoringService(
                        registry.get(GameEventBus.class),
                        registry.get(AsyncPersistenceWorker.class),
                        registry.get(PlayerStatsRepository.class),
                        plugin.getLogger()));

        registry.bind(LeaderboardService.class,
                new LeaderboardService(
                        registry.get(GameEventBus.class),
                        plugin.getLogger()));

        registry.bind(TimerService.class,
                new TimerService(plugin));

        TickOrchestrator tickOrchestrator = new TickOrchestrator(plugin.getLogger());
        registry.bind(TickOrchestrator.class, tickOrchestrator);

        Bukkit.getScheduler().runTaskTimer(plugin, tickOrchestrator, 1L, 1L);
        plugin.getLogger().info("[Bootstrap] TickOrchestrator scheduled.");

        registry.bind(PlayerSessionManager.class,
                new PlayerSessionManager(
                        registry.get(GameEventBus.class),
                        plugin.getLogger()));

        TournamentConfig tournamentConfig = new TournamentConfig(
                new TournamentConfigLoader(plugin).load());

        List<ArenaSchema> arenaSchemas =
                new ArenaConfigLoader(plugin).loadAll();

        ArenaManager arenaManager = new ArenaManager(arenaSchemas, plugin.getLogger());
        registry.bind(ArenaManager.class, arenaManager);

        this.minigameRegistry = new MinigameRegistry(plugin.getLogger());
        registry.bind(MinigameRegistry.class, minigameRegistry);

        registry.bind(ArenaAccessor.class,
                new ArenaAccessor(plugin, arenaManager));

        registry.bind(ScoreboardManager.class,
                new ScoreboardManager(
                        registry.get(GameEventBus.class),
                        registry.get(LeaderboardService.class)));

        ProtocolManager protocolManager = null;
        if (Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
            protocolManager = ProtocolLibrary.getProtocolManager();
            plugin.getLogger().info("[Bootstrap] ProtocolLib detected and bound.");
        } else {
            plugin.getLogger().warning("[Bootstrap] ProtocolLib not found. " +
                    "Minigames using ctx.getProtocolManager() will throw at runtime.");
        }

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
                        protocolManager,
                        plugin,
                        plugin.getLogger(),
                        tournamentRepository));

        registry.logBindings();
    }

    public void shutdown() {
        registry.shutdownAll();
    }

    /**
     * Package-private — only Main.java should call this.
     * Nothing else should hold a reference to ServiceRegistry.
     */
    public ServiceRegistry registry() {
        return registry;
    }

    public MinigameRegistry minigameRegistry() {
        return minigameRegistry;
    }
}
