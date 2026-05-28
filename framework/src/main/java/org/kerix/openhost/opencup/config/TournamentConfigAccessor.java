package org.kerix.openhost.opencup.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.config.schema.GameEntrySchema;
import org.kerix.openhost.opencup.config.schema.ScoringTableSchema;
import org.kerix.openhost.opencup.config.schema.TournamentSchema;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class TournamentConfigAccessor {

    private final JavaPlugin plugin;

    public TournamentConfigAccessor(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public File file() {
        return new File(plugin.getDataFolder(), "tournament.yml");
    }

    public boolean exists() {
        return file().exists();
    }

    public void saveIfMissing(TournamentSchema schema) throws IOException {
        if (exists()) {
            return;
        }

        save(schema);
    }

    public void save(TournamentSchema schema) throws IOException {
        File file = file();

        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        YamlConfiguration cfg = new YamlConfiguration();
        serialize(schema, cfg);
        cfg.save(file);
    }

    public TournamentSchema load() throws ConfigurationException {
        File file = file();

        if (!file.exists()) {
            throw new ConfigurationException("tournament.yml not found in " + plugin.getDataFolder());
        }

        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        return deserialize(cfg);
    }

    public static void serialize(TournamentSchema schema, YamlConfiguration cfg) {
        cfg.set("tournament.name", schema.name());
        cfg.set("tournament.round_reset_delay_ticks", schema.roundResetDelayTicks());
        cfg.set("tournament.post_game_delay_ticks", schema.postGameDelayTicks());

        List<Map<String, Object>> games = new ArrayList<>();

        for (GameEntrySchema game : schema.games()) {
            Map<String, Object> raw = new LinkedHashMap<>();

            raw.put("minigame", game.minigameId());
            raw.put("arena", game.arenaId());
            raw.put("rounds", game.rounds());
            raw.put("countdown_seconds", game.countdownSeconds());
            raw.put("timeout_seconds", game.timeoutSeconds());
            raw.put("scoring", serializeScoring(game.scoringTable()));

            games.add(raw);
        }

        cfg.set("tournament.games", games);
    }

    public static TournamentSchema deserialize(YamlConfiguration cfg) throws ConfigurationException {
        String name = require(cfg, "tournament.name");

        int roundResetDelay = cfg.getInt("tournament.round_reset_delay_ticks", 60);
        int postGameDelay = cfg.getInt("tournament.post_game_delay_ticks", 200);

        List<Map<?, ?>> gameMaps = cfg.getMapList("tournament.games");

        if (gameMaps.isEmpty()) {
            throw new ConfigurationException("tournament.games is empty or missing");
        }

        List<GameEntrySchema> games = new ArrayList<>();

        for (int i = 0; i < gameMaps.size(); i++) {
            Map<?, ?> raw = gameMaps.get(i);
            String section = "tournament.games[" + i + "]";

            String minigameId = require(raw, section, "minigame");
            String arenaId = require(raw, section, "arena");

            int rounds = getInt(raw, "rounds", 1);
            int countdown = getInt(raw, "countdown_seconds", 10);
            int timeout = getInt(raw, "timeout_seconds", 300);

            Map<String, Object> scoringMap = getSectionMap(raw, "scoring");

            ScoringTableSchema scoring = scoringMap.isEmpty()
                    ? new ScoringTableSchema(Map.of(), 0)
                    : ScoringTableSchema.fromMap(scoringMap);

            games.add(new GameEntrySchema(
                    minigameId,
                    arenaId,
                    rounds,
                    countdown,
                    timeout,
                    scoring
            ));
        }

        return new TournamentSchema(
                name,
                roundResetDelay,
                postGameDelay,
                Collections.unmodifiableList(games)
        );
    }

    private static Map<String, Object> serializeScoring(ScoringTableSchema scoring) {
        Map<String, Object> raw = new LinkedHashMap<>();

        scoring.placementPoints().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> raw.put(ordinal(entry.getKey()), entry.getValue()));

        raw.put("default", scoring.defaultPoints());

        return raw;
    }

    private static String ordinal(int value) {
        int mod100 = value % 100;

        if (mod100 >= 11 && mod100 <= 13) {
            return value + "th";
        }

        return switch (value % 10) {
            case 1 -> value + "st";
            case 2 -> value + "nd";
            case 3 -> value + "rd";
            default -> value + "th";
        };
    }

    private static String require(YamlConfiguration cfg, String path) throws ConfigurationException {
        String value = cfg.getString(path);

        if (value == null || value.isBlank()) {
            throw new ConfigurationException("Missing required field: " + path);
        }

        return value;
    }

    private static String require(Map<?, ?> map, String section, String key) throws ConfigurationException {
        Object value = map.get(key);

        if (value == null || value.toString().isBlank()) {
            throw new ConfigurationException(section + " missing '" + key + "'");
        }

        return value.toString();
    }

    private static int getInt(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);

        if (value == null) {
            return fallback;
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        return Integer.parseInt(value.toString());
    }

    private static Map<String, Object> getSectionMap(Map<?, ?> map, String key) {
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