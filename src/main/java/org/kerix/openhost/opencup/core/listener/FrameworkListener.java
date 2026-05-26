package org.kerix.openhost.opencup.core.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.kerix.openhost.opencup.core.engine.GameSession;
import org.kerix.openhost.opencup.core.engine.TournamentEngine;
import org.kerix.openhost.opencup.core.session.PlayerSessionManager;

import java.util.UUID;

/**
 * Single framework-level Bukkit listener. Handles events that affect
 * session membership or require routing to the active GameSession.
 * <p>
 * Minigame-specific Bukkit listeners are registered per-session via
 * ctx.registerListener() and live in the minigame package.
 */
public final class FrameworkListener implements Listener {

    private final PlayerSessionManager sessionManager;
    private final TournamentEngine engine;

    public FrameworkListener(PlayerSessionManager sessionManager, TournamentEngine engine) {
        this.sessionManager = sessionManager;
        this.engine         = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!sessionManager.isInGame(uuid)) return;

        GameSession session = engine.getActiveSession();
        if (session != null && !session.isEnded()) {
            session.handlePlayerDisconnect(uuid);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Future: support late-join for WAITING phase if needed.
        // For now, players join via command before the tournament starts.
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        UUID uuid = event.getEntity().getUniqueId();
        if (!sessionManager.isInGame(uuid)) return;
        // Death handling is minigame-specific; the minigame's listener takes it from here.
        // Framework only ensures we don't drop state.
        event.setKeepInventory(true);
        event.setKeepLevel(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            if (sessionManager.isInGame(player.getUniqueId())) {
                event.setCancelled(true);  // Minigames manage their own hunger rules.
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!sessionManager.isInGame(uuid)) return;
        // Return them to the arena — minigame's listener can override the location.
        GameSession session = engine.getActiveSession();
        if (session != null && !session.getPlayers().isEmpty()) {
            session.getPlayers().stream()
                    .filter(gp -> gp.getUuid().equals(uuid))
                    .findFirst()
                    .ifPresent(gp -> {
                        // Use first spawn point as a safe fallback respawn location.
                        var spawns = session.getPlayers(); // minigame listener overrides as needed
                    });
        }
    }
}
