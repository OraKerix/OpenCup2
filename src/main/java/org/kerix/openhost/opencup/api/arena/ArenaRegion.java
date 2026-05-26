package org.kerix.openhost.opencup.api.arena;


/**
 * Axis-aligned bounding box that defines the playable area of an arena.
 * Used by WorldResetter to know which chunks to restore, and by minigames
 * to perform out-of-bounds checks.
 */
public record ArenaRegion(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {}