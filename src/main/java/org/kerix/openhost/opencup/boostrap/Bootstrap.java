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

public final class Bootstrap {

    private final ServiceRegistry registry;
    private final JavaPlugin plugin;

    public Bootstrap(JavaPlugin plugin) {
        this.plugin = plugin;
        this.registry = new ServiceRegistry(plugin.getLogger());
    }

    public void boot() {
        // Registration order matters for shutdown — dependencies first,
        // dependents after. Shutdown runs this in reverse automatically.

        registry.bind(GameEventBus.class,new GameEventBus(plugin.getLogger()));
        registry.bind(AsyncPersistenceWorker.class,new AsyncPersistenceWorker(plugin));
        registry.bind(PlayerStatsRepository.class,new YamlPlayerStatsRepository(
                plugin,
                registry.get(AsyncPersistenceWorker.class)));
        registry.bind(TournamentRepository.class,new YamlTournamentRepository(
                plugin,
                registry.get(AsyncPersistenceWorker.class)));
        registry.bind(ScoringService.class,new ScoringService(
                registry.get(GameEventBus.class),
                registry.get(AsyncPersistenceWorker.class),
                registry.get(PlayerStatsRepository.class),
                        plugin.getLogger()));

        registry.bind(TimerService.class,new TimerService(plugin));

        registry.logBindings();
    }

    public void shutdown() {
        registry.shutdownAll();
    }

    // Expose registry only to OpenCupPlugin — nothing else should touch it.
    ServiceRegistry registry() {
        return registry;
    }
}
