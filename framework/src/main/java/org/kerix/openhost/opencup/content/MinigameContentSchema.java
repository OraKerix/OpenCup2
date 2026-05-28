package org.kerix.openhost.opencup.content;

import org.kerix.openhost.opencup.api.minigame.Minigame;
import org.kerix.openhost.opencup.config.schema.ArenaSchema;
import org.kerix.openhost.opencup.config.schema.GameEntrySchema;

import java.util.List;

public record MinigameContentSchema(
        Class<? extends Minigame> minigameClass,
        GameEntrySchema gameEntry,
        List<ArenaSchema> arenas
) {
}
