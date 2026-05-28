package org.kerix.openhost.opencup.core.arena;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.config.ArenaConfigAccessor;
import org.kerix.openhost.opencup.config.schema.ArenaSchema;

import java.io.File;
import java.io.IOException;

public final class ArenaAccessor {

    private final JavaPlugin plugin;
    private final ArenaManager arenaManager;

    public ArenaAccessor(JavaPlugin plugin , ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;
    }

    public void saveArena(ArenaSchema arena) throws IOException {
        File arenaDir = new File(plugin.getDataFolder(), "arenas");
        arenaDir.mkdirs();

        File file = new File(arenaDir, arena.id() + ".yml");
        saveArena(arena, file);
    }

    private void saveArena(ArenaSchema arena, File file) throws IOException {
        YamlConfiguration cfg = new YamlConfiguration();

        ArenaConfigAccessor.serialize(arena, cfg);

        File parent = file.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }

        cfg.save(file);
        arenaManager.addArena(arena);
    }
}
