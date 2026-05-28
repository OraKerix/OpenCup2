package org.kerix.openhost.opencup.core.arena;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.kerix.openhost.opencup.api.arena.*;
import org.kerix.openhost.opencup.config.schema.ArenaSchema;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Concrete Arena backed by an ArenaSchema. Minigames see the Arena interface.
 */
final class ArenaImpl implements Arena {

    private final ArenaSchema schema;
    private final ArenaRegion region;
    private final ArenaMetadata metadata;
    private final List<SpawnPoint> spawnPoints;
    private volatile boolean occupied = false;

    ArenaImpl(ArenaSchema schema) {
        this.schema = schema;
        this.region = new ArenaRegion(
                schema.minX(), schema.minY(), schema.minZ(),
                schema.maxX(), schema.maxY(), schema.maxZ()
        );
        this.metadata = new ArenaMetadata(
                List.copyOf(schema.typeTags()),
                Map.copyOf(schema.metadata())
        );
        this.spawnPoints = schema.spawnPoints().stream()
                .map(s -> new SpawnPoint(s.name(), s.group(), s.x(), s.y(), s.z(), s.yaw(), s.pitch()))
                .toList();
    }

    @Override public String getId()         { return schema.id(); }

    @Override
    public World getWorld() {
        World w = Bukkit.getWorld(schema.worldName());
        if (w == null) throw new IllegalStateException(
                "Arena world '" + schema.worldName() + "' is not loaded.");
        return w;
    }

    @Override
    public List<SpawnPoint> getSpawnPoints(String group) {
        return spawnPoints.stream()
                .filter(sp -> sp.group().equals(group))
                .collect(Collectors.toList());
    }

    @Override
    public SpawnPoint getSpawnPoint(String name) {
        return spawnPoints.stream()
                .filter(sp -> sp.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("No spawn point named '" + name + "' in arena " + getId()));
    }

    @Override public ArenaRegion   getRegion()   { return region; }
    @Override public ArenaMetadata getMetadata() { return metadata; }
    @Override public boolean       isOccupied()  { return occupied; }
    @Override public void          markOccupied()  { occupied = true; }
    @Override public void          markAvailable() { occupied = false; }

    String getResetStrategy()  { return schema.resetStrategy(); }
    String getSchematicFile()  { return schema.schematicFile(); }
}
