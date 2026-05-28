package org.kerix.openhost.opencup.config.schema;

import java.util.List;
import java.util.Map;

public record ArenaSchema(
        String id,
        String worldName,
        int minX, int minY, int minZ,
        int maxX, int maxY, int maxZ,
        List<SpawnPointSchema> spawnPoints,
        List<String> typeTags,
        Map<String, String> metadata,
        String resetStrategy,     // SCHEMATIC | WORLD_COPY | NONE
        String schematicFile      // nullable — only for SCHEMATIC strategy
)  {
}
