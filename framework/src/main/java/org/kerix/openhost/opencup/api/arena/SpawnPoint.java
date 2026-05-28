package org.kerix.openhost.opencup.api.arena;

import org.bukkit.Location;
import org.bukkit.World;

public record SpawnPoint(
        String name,
        String group,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {
    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }
}
