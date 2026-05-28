package org.kerix.openhost.opencup.core.session;

import org.bukkit.entity.Player;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.player.PlayerRole;
import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.event.events.PlayerRoleChangedEvent;


import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Single authoritative source for "what is this player currently doing?"
 * <p>
 * Every Bukkit event listener queries this to route events to the correct
 * session without knowing which minigame is running. ConcurrentHashMap
 * because async save callbacks occasionally need to read session state.
 * <p>
 * Only GameSession calls enroll/discharge. Everything else calls read methods.
 */
public final class PlayerSessionManager {

    private final Map<UUID, PlayerSession> sessions = new ConcurrentHashMap<>();
    private final GameEventBus eventBus;
    private final Logger log;

    public PlayerSessionManager(GameEventBus eventBus, Logger log) {
        this.eventBus = eventBus;
        this.log      = log;
    }

    // ── Enroll / discharge ────────────────────────────────────────────────────

    /**
     * Enroll a player into a session and snapshot their pre-game state.
     * Called by GameSession when a player joins.
     */
    public PlayerSession enroll(Player bukkit, String sessionId, PlayerRole initialRole) {
        if (sessions.containsKey(bukkit.getUniqueId())) {
            log.warning("[SessionManager] " + bukkit.getName()
                    + " enrolled while already in a session — discharging first.");
            discharge(bukkit.getUniqueId());
        }

        GamePlayer gp = new GamePlayer(bukkit.getUniqueId(), bukkit.getName());
        gp.setRole(initialRole);

        PlayerSession session = new PlayerSession(
                bukkit.getUniqueId(), sessionId, gp,
                bukkit.getLocation().clone(),
                bukkit.getGameMode(),
                bukkit.getInventory().getContents().clone(),
                bukkit.getExp(),
                bukkit.getLevel()
        );

        sessions.put(bukkit.getUniqueId(), session);
        log.fine("[SessionManager] Enrolled " + bukkit.getName() + " → " + sessionId);
        return session;
    }

    /**
     * Remove a player from their session and restore their pre-game state.
     * Safe to call even if the player is offline.
     */
    public void discharge(UUID uuid) {
        PlayerSession session = sessions.remove(uuid);
        if (session == null) return;

        Player bukkit = org.bukkit.Bukkit.getPlayer(uuid);
        if (bukkit != null && bukkit.isOnline()) {
            bukkit.getInventory().setContents(session.savedInventory());
            bukkit.setGameMode(session.savedGameMode());
            bukkit.setExp(session.savedExp());
            bukkit.setLevel(session.savedLevel());
            bukkit.teleport(session.savedLocation());
        }
        log.fine("[SessionManager] Discharged " + uuid);
    }

    /** Discharge all players belonging to a given session (called on session teardown). */
    public void dischargeAll(String sessionId) {
        List<UUID> toDischarge = sessions.entrySet().stream()
                .filter(e -> e.getValue().sessionId().equals(sessionId))
                .map(Map.Entry::getKey)
                .toList();
        toDischarge.forEach(this::discharge);
    }

    // ── Role management ───────────────────────────────────────────────────────

    public void applyRole(UUID uuid, PlayerRole role) {
        PlayerSession session = sessions.get(uuid);
        if (session == null) return;
        session.setRole(role);
        eventBus.publish(new PlayerRoleChangedEvent(uuid, role));
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public Optional<PlayerSession> getSession(UUID uuid) {
        return Optional.ofNullable(sessions.get(uuid));
    }

    public boolean isInGame(UUID uuid)     { return sessions.containsKey(uuid); }

    public boolean isSpectator(UUID uuid) {
        PlayerSession s = sessions.get(uuid);
        return s != null && s.getRole() != PlayerRole.PARTICIPANT;
    }

    /** All active GamePlayer wrappers in a given session. */
    public List<GamePlayer> getPlayersInSession(String sessionId) {
        return sessions.values().stream()
                .filter(s -> s.sessionId().equals(sessionId))
                .map(PlayerSession::gamePlayer)
                .toList();
    }
}
