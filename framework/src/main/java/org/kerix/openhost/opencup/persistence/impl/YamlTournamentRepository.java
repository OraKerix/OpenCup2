package org.kerix.openhost.opencup.persistence.impl;

import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.persistence.TournamentRepository;
import org.kerix.openhost.opencup.persistence.async.AsyncPersistenceWorker;

import org.bukkit.configuration.file.YamlConfiguration;
import org.kerix.openhost.opencup.api.minigame.EndReason;
import org.kerix.openhost.opencup.api.minigame.MinigameResult;

import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * YAML-backed tournament history. One file: plugins/OpenCup/tournament_history.yml
 * Each result is stored under a timestamp-keyed section.
 */
public final class YamlTournamentRepository implements TournamentRepository {

    private final File file;
    private final AsyncPersistenceWorker worker;
    private final Logger log;

    public YamlTournamentRepository(JavaPlugin plugin, AsyncPersistenceWorker worker) {
        this.file   = new File(plugin.getDataFolder(), "tournament_history.yml");
        this.worker = worker;
        this.log    = plugin.getLogger();
    }

    @Override
    public CompletableFuture<Void> saveResult(MinigameResult result) {
        return worker.saveAsync(() -> {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            String key = result.getSessionId();
            cfg.set(key + ".minigameId",    result.getMinigameId());
            cfg.set(key + ".reason",        result.getReason().name());
            cfg.set(key + ".endedAt",       result.getEndedAt().toString());
            cfg.set(key + ".rankedPlayers", result.getRankedPlayers().stream()
                    .map(UUID::toString).collect(Collectors.toList()));
            trySave(cfg);
        });
    }

    @Override
    public CompletableFuture<List<MinigameResult>> loadHistory(String minigameId, int limit) {
        return worker.loadAsync(() -> {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
            List<MinigameResult> results = new ArrayList<>();
            for (String key : cfg.getKeys(false)) {
                var s = cfg.getConfigurationSection(key);
                if (s == null || !minigameId.equals(s.getString("minigameId"))) continue;
                List<UUID> ranked = s.getStringList("rankedPlayers").stream()
                        .map(UUID::fromString).collect(Collectors.toList());
                results.add(MinigameResult.builder(key, minigameId)
                        .reason(EndReason.valueOf(s.getString("reason", "WINNER")))
                        .rankedPlayers(ranked)
                        .endedAt(Instant.parse(s.getString("endedAt", Instant.EPOCH.toString())))
                        .build());
                if (results.size() >= limit) break;
            }
            return results;
        });
    }

    private void trySave(YamlConfiguration cfg) {
        try { cfg.save(file); }
        catch (Exception e) { log.severe("[TournamentRepo] Save failed: " + e.getMessage()); }
    }
}
