package org.kerix.openhost.opencup.api.player;


import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A player wrapper holding game-local state for one GameSession.
 * Discarded when the session ends; does not persist between games.
 * <p>
 * Minigames use getData/setData for their own per-player scratch space
 * without polluting GamePlayer with game-specific fields.
 */
public final class GamePlayer {

    @Getter
    private final UUID uuid;
    @Getter
    private final String name;
    @Setter
    @Getter
    private PlayerRole role;
    @Setter
    @Getter
    private int sessionPoints;
    @Setter
    @Getter
    private int eliminationRank;   // 0 = not yet eliminated; 1 = eliminated first
    private final Map<String, Object> data = new HashMap<>();

    public GamePlayer(UUID uuid, String name) {
        this.uuid = uuid;
        this.name = name;
        this.role = PlayerRole.PARTICIPANT;
    }

    // ── Identity ──────────────────────────────────────────────────────────────

    /** Returns the live Bukkit Player, or null if offline. Always check for null. */
    public Player toBukkit() { return org.bukkit.Bukkit.getPlayer(uuid); }

    // ── Role ─────────────────────────────────────────────────────────────────

    public boolean isParticipant()             { return role == PlayerRole.PARTICIPANT; }
    public boolean isSpectator()               { return role == PlayerRole.SPECTATOR || role == PlayerRole.ELIMINATED; }
    public boolean isAlive()                   { return role == PlayerRole.PARTICIPANT; }

    // ── Points ────────────────────────────────────────────────────────────────

    public void addSessionPoints(int amount)   { sessionPoints += amount; }

    // ── Elimination ───────────────────────────────────────────────────────────

    public boolean isEliminated()              { return eliminationRank > 0; }

    // ── Scratch data ──────────────────────────────────────────────────────────

    public <T> void setData(String key, T value)       { data.put(key, value); }

    public <T> Optional<T> getData(String key, Class<T> type) {
        Object value = data.get(key);
        if (!type.isInstance(value)) return Optional.empty();
        return Optional.of(type.cast(value));
    }

    public boolean hasData(String key)               { return data.containsKey(key); }
    public void removeData(String key)               { data.remove(key); }

    @Override
    public String toString() {
        return "GamePlayer{" + name + ", role=" + role + ", pts=" + sessionPoints + "}";
    }
}
