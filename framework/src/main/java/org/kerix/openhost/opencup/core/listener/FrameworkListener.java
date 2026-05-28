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

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.kerix.openhost.opencup.core.engine.GameSession;
import org.kerix.openhost.opencup.core.engine.TournamentEngine;
import org.kerix.openhost.opencup.core.session.PlayerSessionManager;

import java.util.UUID;

/**
 * Single framework-level Bukkit listener. Handles events that affect
 * session membership or require routing to the active GameSession.
 */
public final class FrameworkListener implements Listener {

    private final PlayerSessionManager sessionManager;
    private final TournamentEngine engine;

    public FrameworkListener(PlayerSessionManager sessionManager, TournamentEngine engine) {
        this.sessionManager = sessionManager;
        this.engine = engine;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (!sessionManager.isInGame(uuid)) {
            return;
        }

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

        if (!sessionManager.isInGame(uuid)) {
            return;
        }

        event.setKeepInventory(true);
        event.setKeepLevel(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Player player) {
            if (sessionManager.isInGame(player.getUniqueId())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onRespawn(PlayerRespawnEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (!sessionManager.isInGame(uuid)) {
            return;
        }

        GameSession session = engine.getActiveSession();

        if (session != null && !session.isEnded()) {
            session.getFallbackRespawnLocation(uuid).ifPresent(event::setRespawnLocation);
        }
    }
}
