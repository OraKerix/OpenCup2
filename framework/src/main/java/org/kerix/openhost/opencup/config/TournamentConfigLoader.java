package org.kerix.openhost.opencup.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.config.schema.*;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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

        List<Map<?, ?>> gameMaps = cfg.getMapList("tournament.games");

        if (gameMaps.isEmpty()) {
            throw new ConfigurationException("tournament.games is empty or missing");
        }

        for (int i = 0; i < gameMaps.size(); i++) {
            Map<?, ?> raw = gameMaps.get(i);
            String key = "tournament.games[" + i + "]";

            String minigameId = require(raw, key, "minigame");
            String arenaId = require(raw, key, "arena");

            int rounds = getInt(raw, "rounds", 1);
            int countdown = getInt(raw, "countdown_seconds", 10);
            int timeout = getInt(raw, "timeout_seconds", 300);

            Map<String, Object> scoringMap = getSectionMap(raw, "scoring");

            ScoringTableSchema scoring = !scoringMap.isEmpty()
                    ? ScoringTableSchema.fromMap(scoringMap)
                    : new ScoringTableSchema(Map.of(), 0);

            games.add(new GameEntrySchema(
                    minigameId,
                    arenaId,
                    rounds,
                    countdown,
                    timeout,
                    scoring
            ));
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
    private String require(Map<?, ?> map, String section, String key) throws ConfigurationException {
        Object value = map.get(key);

        if (value == null || value.toString().isBlank()) {
            throw new ConfigurationException(section + " missing '" + key + "'");
        }

        return value.toString();
    }

    private int getInt(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);

        if (value == null) {
            return fallback;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        return Integer.parseInt(value.toString());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> getSectionMap(Map<?, ?> map, String key) {
        Object value = map.get(key);

        if (!(value instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }

        Map<String, Object> result = new LinkedHashMap<>();

        for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
            result.put(entry.getKey().toString(), entry.getValue());
        }

        return result;
    }
}
