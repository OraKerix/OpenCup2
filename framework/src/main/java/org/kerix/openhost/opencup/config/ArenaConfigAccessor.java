package org.kerix.openhost.opencup.config;


import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.config.schema.ArenaSchema;
import org.kerix.openhost.opencup.config.schema.SpawnPointSchema;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Reads every .yml file in plugins/OpenCup/arenas/ into ArenaSchema objects.
 * Each file = one arena. Filename stem is the arena ID.
 */
public final class ArenaConfigAccessor {

    private final JavaPlugin plugin;
    private final Logger log;

    public ArenaConfigAccessor(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    public List<ArenaSchema> loadAll() {
        File arenaDir = new File(plugin.getDataFolder(), "arenas");
        arenaDir.mkdirs();

        File[] files = arenaDir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) {
            log.warning("[Config] No arena files found in " + arenaDir.getPath());
            return List.of();
        }

        List<ArenaSchema> arenas = new ArrayList<>();

        for (File file : files) {
            try {
                arenas.add(loadArena(file));
                log.info("[Config] Loaded arena: " + file.getName());
            } catch (Exception e) {
                log.severe("[Config] Failed to load arena " + file.getName() + ": " + e.getMessage());
            }
        }

        return Collections.unmodifiableList(arenas);
    }

    public ArenaSchema loadArena(File file) throws ConfigurationException {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        return deserialize(cfg, file);
    }

    public boolean exists(String arenaId) {
        File arenaDir = new File(plugin.getDataFolder(), "arenas");
        File file = new File(arenaDir, arenaId + ".yml");
        return file.exists();
    }

    public void saveArenaIfMissing(ArenaSchema arena) throws IOException {
        if (exists(arena.id())) {
            return;
        }

        saveArena(arena);
    }

    public void saveArena(ArenaSchema arena) throws IOException {
        File arenaDir = new File(plugin.getDataFolder(), "arenas");
        arenaDir.mkdirs();

        File file = new File(arenaDir, arena.id() + ".yml");
        saveArena(arena, file);
    }

    public void saveArena(ArenaSchema arena, File file) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();

        serialize(arena, cfg);

        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        cfg.save(file);
    }

    public static void serialize(ArenaSchema arena, YamlConfiguration cfg) {
        cfg.set("id", arena.id());
        cfg.set("world", arena.worldName());

        cfg.set("region.min.x", arena.minX());
        cfg.set("region.min.y", arena.minY());
        cfg.set("region.min.z", arena.minZ());

        cfg.set("region.max.x", arena.maxX());
        cfg.set("region.max.y", arena.maxY());
        cfg.set("region.max.z", arena.maxZ());

        cfg.set("type_tags", arena.typeTags());
        cfg.set("reset_strategy", arena.resetStrategy());
        cfg.set("schematic_file", arena.schematicFile());

        serializeSpawns(arena.spawnPoints(), cfg);
        serializeMetadata(arena.metadata(), cfg);
    }

    public static ArenaSchema deserialize(YamlConfiguration cfg, File file) throws ConfigurationException {
        String id = cfg.getString("id", file.getName().replace(".yml", ""));
        String worldName = require(cfg, file, "world");

        int minX = cfg.getInt("region.min.x", 0);
        int minY = cfg.getInt("region.min.y", 0);
        int minZ = cfg.getInt("region.min.z", 0);

        int maxX = cfg.getInt("region.max.x", 0);
        int maxY = cfg.getInt("region.max.y", 255);
        int maxZ = cfg.getInt("region.max.z", 0);

        List<SpawnPointSchema> spawns = deserializeSpawns(cfg);
        List<String> tags = cfg.getStringList("type_tags");

        String resetStrategy = cfg.getString("reset_strategy", "NONE");
        String schematicFile = cfg.getString("schematic_file", null);

        Map<String, String> metadata = deserializeMetadata(cfg);

        return new ArenaSchema(
                id,
                worldName,
                minX,
                minY,
                minZ,
                maxX,
                maxY,
                maxZ,
                spawns,
                tags,
                metadata,
                resetStrategy,
                schematicFile
        );
    }

    private static void serializeSpawns(List<SpawnPointSchema> spawns, YamlConfiguration cfg) {
        cfg.set("spawn_groups", null);

        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();

        for (SpawnPointSchema spawn : spawns) {
            grouped.computeIfAbsent(spawn.group(), k -> new ArrayList<>())
                    .add(serializeSpawn(spawn));
        }

        for (Map.Entry<String, List<Map<String, Object>>> entry : grouped.entrySet()) {
            cfg.set("spawn_groups." + entry.getKey(), entry.getValue());
        }
    }

    private static Map<String, Object> serializeSpawn(SpawnPointSchema spawn) {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("name", spawn.name());
        map.put("x", spawn.x());
        map.put("y", spawn.y());
        map.put("z", spawn.z());
        map.put("yaw", spawn.yaw());
        map.put("pitch", spawn.pitch());

        return map;
    }

    private static List<SpawnPointSchema> deserializeSpawns(YamlConfiguration cfg) {
        List<SpawnPointSchema> spawns = new ArrayList<>();

        ConfigurationSection groups = cfg.getConfigurationSection("spawn_groups");
        if (groups == null) {
            return List.of();
        }

        for (String group : groups.getKeys(false)) {
            List<Map<?, ?>> points = groups.getMapList(group);

            for (Map<?, ?> raw : points) {
                String def = "%s_%d".formatted(group, spawns.size());

                spawns.add(new SpawnPointSchema(
                        string(raw, "name", def),
                        group,
                        toDouble(raw, "x"),
                        toDouble(raw, "y"),
                        toDouble(raw, "z"),
                        toFloat(raw, "yaw"),
                        toFloat(raw, "pitch")
                ));
            }
        }

        return Collections.unmodifiableList(spawns);
    }

    private static void serializeMetadata(Map<String, String> metadata, YamlConfiguration cfg) {
        cfg.set("metadata", null);

        if (metadata == null || metadata.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            cfg.set("metadata." + entry.getKey(), entry.getValue());
        }
    }

    private static Map<String, String> deserializeMetadata(YamlConfiguration cfg) {
        Map<String, String> metadata = new LinkedHashMap<>();

        ConfigurationSection section = cfg.getConfigurationSection("metadata");
        if (section == null) {
            return Map.of();
        }

        for (String key : section.getKeys(false)) {
            metadata.put(key, section.getString(key, ""));
        }

        return Collections.unmodifiableMap(metadata);
    }

    private static String string(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value.toString();
    }

    private static String require(YamlConfiguration cfg, File file, String key) throws ConfigurationException {
        String v = cfg.getString(key);
        if (v == null || v.isBlank())
            throw new ConfigurationException("Arena " + file.getName() + " missing '" + key + "'");
        return v;
    }

    private static double toDouble(Map<?, ?> m, String k) {
        Object v = m.get(k);
        return v == null ? 0.0 : Double.parseDouble(v.toString());
    }

    private static float toFloat(Map<?, ?> m, String k) {
        Object v = m.get(k);
        return v == null ? 0f : Float.parseFloat(v.toString());
    }
}
