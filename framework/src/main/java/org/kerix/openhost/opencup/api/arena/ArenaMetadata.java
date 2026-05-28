package org.kerix.openhost.opencup.api.arena;

import java.util.List;
import java.util.Map;

/**
 * Arbitrary key→value metadata stored in the arena YAML.
 * Minigames read their own settings here (round time, colors, etc.)
 * without coupling to the main plugin config.
 */
public record ArenaMetadata(
        List<String> typeTags,      // e.g. ["block_party", "elimination"]
        Map<String, String> values  // e.g. "round_time" → "30"
) {
    public boolean hasTag(String tag) {
        return typeTags.contains(tag);
    }

    public String getValue(String key, String defaultValue) {
        return values.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        try { return Integer.parseInt(values.getOrDefault(key, String.valueOf(defaultValue))); }
        catch (NumberFormatException e) { return defaultValue; }
    }
}
