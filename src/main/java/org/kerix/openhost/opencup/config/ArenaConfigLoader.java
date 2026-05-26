package org.kerix.openhost.opencup.config;


import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.config.schema.ArenaSchema;
import org.kerix.openhost.opencup.config.schema.SpawnPointSchema;

import java.io.File;
import java.util.*;
import java.util.logging.Logger;

/**
 * Reads every .yml file in plugins/OpenCup/arenas/ into ArenaSchema objects.
 * Each file = one arena. Filename stem is the arena ID.
 */
public final class ArenaConfigLoader {

    private final JavaPlugin plugin;
    private final Logger log;

    public ArenaConfigLoader(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log    = plugin.getLogger();
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

    private ArenaSchema loadArena(File file) throws ConfigurationException {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        String id        = cfg.getString("id", file.getName().replace(".yml", ""));
        String worldName = require(cfg, file, "world");

        int minX = cfg.getInt("region.min.x", 0), minY = cfg.getInt("region.min.y", 0), minZ = cfg.getInt("region.min.z", 0);
        int maxX = cfg.getInt("region.max.x", 0), maxY = cfg.getInt("region.max.y", 255), maxZ = cfg.getInt("region.max.z", 0);

        List<SpawnPointSchema> spawns = new ArrayList<>();
        ConfigurationSection groups = cfg.getConfigurationSection("spawn_groups");
        if (groups != null) {
            for (String group : groups.getKeys(false)) {
                List<Map<?, ?>> pts = groups.getMapList(group);
                for (Map<?, ?> raw : pts) {

                    @SuppressWarnings("unchecked")
                    Map<String, Object> pt = (Map<String, Object>) raw;

                    String def = "%s_%d".formatted(group, spawns.size());

                    spawns.add(new SpawnPointSchema(
                            (String) pt.getOrDefault("name", def),
                            group,
                            toDouble(pt, "x"),
                            toDouble(pt, "y"),
                            toDouble(pt, "z"),
                            toFloat(pt, "yaw"),
                            toFloat(pt, "pitch")
                    ));
                }
            }
        }

        List<String> tags       = cfg.getStringList("type_tags");
        String resetStrategy    = cfg.getString("reset_strategy", "NONE");
        String schematicFile    = cfg.getString("schematic_file", null);

        // Remaining keys become free-form metadata
        Map<String, String> meta = new LinkedHashMap<>();
        ConfigurationSection metaSection = cfg.getConfigurationSection("metadata");
        if (metaSection != null) {
            metaSection.getKeys(false).forEach(k -> meta.put(k, metaSection.getString(k)));
        }

        return new ArenaSchema(id, worldName, minX, minY, minZ, maxX, maxY, maxZ,
                spawns, tags, meta, resetStrategy, schematicFile);
    }

    private String require(YamlConfiguration cfg, File file, String key) throws ConfigurationException {
        String v = cfg.getString(key);
        if (v == null || v.isBlank())
            throw new ConfigurationException("Arena " + file.getName() + " missing '" + key + "'");
        return v;
    }

    private double toDouble(Map<?, ?> m, String k) {
        Object v = m.get(k); return v == null ? 0.0 : Double.parseDouble(v.toString());
    }
    private float toFloat(Map<?, ?> m, String k) {
        Object v = m.get(k); return v == null ? 0f : Float.parseFloat(v.toString());
    }
}
