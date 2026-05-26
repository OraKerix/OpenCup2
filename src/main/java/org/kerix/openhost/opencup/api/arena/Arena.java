package org.kerix.openhost.opencup.api.arena;

import org.bukkit.World;

import java.util.List;

/**
 * A loaded, ready-to-use arena. Obtained from ArenaManager.
 * Minigames only ever see this interface — never ArenaManager directly.
 */
public interface Arena {

    /** Unique ID matching the filename (e.g. "blockparty_01"). */
    String getId();

    /** The Bukkit world this arena lives in. Always non-null when checked out. */
    World getWorld();

    /** All spawn points belonging to the named group (e.g. "red", "blue", "main"). */
    List<SpawnPoint> getSpawnPoints(String group);

    /** Single spawn point by exact name. Throws if not found. */
    SpawnPoint getSpawnPoint(String name);

    /** The axis-aligned bounding box of the playable area. */
    ArenaRegion getRegion();

    /** Arbitrary settings stored in the arena YAML. */
    ArenaMetadata getMetadata();

    /** True while this arena is checked out to a GameSession. */
    boolean isOccupied();

    /** Called internally by ArenaManager — do not call from minigame code. */
    void markOccupied();

    /** Called internally by ArenaManager after WorldResetter finishes. */
    void markAvailable();
}
