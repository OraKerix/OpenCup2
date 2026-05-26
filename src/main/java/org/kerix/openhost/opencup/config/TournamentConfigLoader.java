package org.kerix.openhost.opencup.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.config.schema.*;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Reads plugins/OpenCup/tournament.yml into a TournamentSchema.
 * Validates required fields and throws ConfigurationException on malformed input.
 * Called once at bootstrap — the schema object is then immutable.
 */
public final class TournamentConfigLoader {

    private final JavaPlugin plugin;
    private final Logger log;

    public TournamentConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
    }

    public TournamentSchema load() throws ConfigurationException {
        plugin.getDataFolder().mkdirs();

        if (!new java.io.File(plugin.getDataFolder(), "tournament.yml").exists()) {
            plugin.saveResource("tournament.yml", false);
        }

        File file = new File(plugin.getDataFolder(), "tournament.yml");
        if (!file.exists()) {
            throw new ConfigurationException("tournament.yml not found in " + plugin.getDataFolder());
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);

        String name              = require(cfg, "tournament.name");
        int roundResetDelay      = cfg.getInt("tournament.round_reset_delay_ticks", 60);
        int postGameDelay        = cfg.getInt("tournament.post_game_delay_ticks",   200);

        List<GameEntrySchema> games = new ArrayList<>();
        ConfigurationSection gamesSection = cfg.getConfigurationSection("tournament.games");
        if (gamesSection == null) throw new ConfigurationException("tournament.games is empty or missing");

        for (String key : gamesSection.getKeys(false)) {
            ConfigurationSection g = gamesSection.getConfigurationSection(key);
            if (g == null) continue;

            String minigameId    = require(g, key, "minigame");
            String arenaId       = require(g, key, "arena");
            int rounds           = g.getInt("rounds", 1);
            int countdown        = g.getInt("countdown_seconds", 10);
            int timeout          = g.getInt("timeout_seconds", 300);

            ConfigurationSection scoringSection = g.getConfigurationSection("scoring");
            ScoringTableSchema scoring = scoringSection != null
                    ? ScoringTableSchema.fromMap(scoringSection.getValues(false))
                    : new ScoringTableSchema(Map.of(), 0);

            games.add(new GameEntrySchema(minigameId, arenaId, rounds, countdown, timeout, scoring));
        }

        if (games.isEmpty()) throw new ConfigurationException("tournament.games has no valid entries");
        log.info("[Config] Loaded tournament '" + name + "' with " + games.size() + " game(s).");
        return new TournamentSchema(name, roundResetDelay, postGameDelay, games);
    }

    private String require(YamlConfiguration cfg, String path) throws ConfigurationException {
        String v = cfg.getString(path);
        if (v == null || v.isBlank()) throw new ConfigurationException("Missing required field: " + path);
        return v;
    }

    private String require(ConfigurationSection s, String sectionName, String key) throws ConfigurationException {
        String v = s.getString(key);
        if (v == null || v.isBlank())
            throw new ConfigurationException("games." + sectionName + " is missing '" + key + "'");
        return v;
    }
}
