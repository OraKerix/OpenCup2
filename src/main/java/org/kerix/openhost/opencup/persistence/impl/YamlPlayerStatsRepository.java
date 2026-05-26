package org.kerix.openhost.opencup.persistence.impl;

import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.persistence.PlayerStatsRepository;
import org.kerix.openhost.opencup.persistence.async.AsyncPersistenceWorker;

import org.bukkit.configuration.file.YamlConfiguration;
import org.kerix.openhost.opencup.persistence.PlayerStats;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * YAML-backed player stats. One file: plugins/OpenCup/player_stats.yml
 * Each player is keyed by UUID string.
 * <p>
 * Replace with a SQL implementation by implementing PlayerStatsRepository
 * and swapping the binding in Bootstrap — no other code changes required.
 */
public final class YamlPlayerStatsRepository implements PlayerStatsRepository {

    private final File file;
    private final AsyncPersistenceWorker worker;
    private final Logger log;

    public YamlPlayerStatsRepository(JavaPlugin plugin, AsyncPersistenceWorker worker) {
        this.file   = new File(plugin.getDataFolder(), "player_stats.yml");
        this.worker = worker;
        this.log    = plugin.getLogger();
        plugin.getDataFolder().mkdirs();
    }

    @Override
    public CompletableFuture<Optional<PlayerStats>> load(UUID uuid) {
        return worker.loadAsync(() -> {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String key = uuid.toString();
            if (!cfg.contains(key)) return Optional.empty();
            return Optional.of(deserialize(uuid, cfg.getConfigurationSection(key)));
        });
    }

    @Override
    public CompletableFuture<List<PlayerStats>> loadAll() {
        return worker.loadAsync(() -> {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<PlayerStats> list = new ArrayList<>();
            for (String key : cfg.getKeys(false)) {
                try {
                    list.add(deserialize(UUID.fromString(key), cfg.getConfigurationSection(key)));
                } catch (Exception e) {
                    log.warning("[Stats] Skipping malformed entry: " + key);
                }
            }
            return list;
        });
    }

    @Override
    public CompletableFuture<Void> save(PlayerStats stats) {
        return worker.saveAsync(() -> {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            serialize(stats, cfg);
            trySave(cfg);
        });
    }

    @Override
    public CompletableFuture<Void> saveAll(List<PlayerStats> statsList) {
        return worker.saveAsync(() -> {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            statsList.forEach(s -> serialize(s, cfg));
            trySave(cfg);
        });
    }

    // ── Serialisation ─────────────────────────────────────────────────────────

    private void serialize(PlayerStats s, YamlConfiguration cfg) {
        String k = s.uuid().toString();
        cfg.set(k + ".lastName",         s.lastName());
        cfg.set(k + ".tournamentPoints",  s.tournamentPoints());
        cfg.set(k + ".gamesPlayed",       s.gamesPlayed());
        cfg.set(k + ".wins",              s.wins());
        cfg.set(k + ".top3Finishes",      s.top3Finishes());
        cfg.set(k + ".lastPlayed",        s.lastPlayed().toString());
    }

    private PlayerStats deserialize(UUID uuid, org.bukkit.configuration.ConfigurationSection s) {
        return new PlayerStats(
                uuid,
                s.getString("lastName", "Unknown"),
                s.getInt("tournamentPoints", 0),
                s.getInt("gamesPlayed", 0),
                s.getInt("wins", 0),
                s.getInt("top3Finishes", 0),
                Instant.parse(s.getString("lastPlayed", Instant.EPOCH.toString()))
        );
    }

    private void trySave(YamlConfiguration cfg) {
        try { cfg.save(file); }
        catch (Exception e) { log.severe("[Stats] Failed to save: " + e.getMessage()); }
    }
}
