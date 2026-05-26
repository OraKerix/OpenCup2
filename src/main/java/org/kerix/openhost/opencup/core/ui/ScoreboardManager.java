package org.kerix.openhost.opencup.core.ui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.kerix.openhost.opencup.api.phase.GamePhase;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.ui.SidebarLine;
import org.kerix.openhost.opencup.api.ui.SidebarView;
import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.event.events.*;
import org.kerix.openhost.opencup.core.lifecycle.Startable;
import org.kerix.openhost.opencup.core.scoring.LeaderboardEntry;
import org.kerix.openhost.opencup.core.scoring.LeaderboardService;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the sidebar scoreboard for all online players.
 * <p>
 * Subscribes to GameEventBus events — never pulled by anything.
 * Minigames set their sidebar by calling ctx.setSidebarProvider(view);
 * which registers the view here.
 * <p>
 * Phase routing:
 *   IDLE / POST_GAME → tournament leaderboard
 *   WAITING / COUNTDOWN → waiting status board
 *   PLAYING / ROUND_END → minigame's SidebarView
 */
public final class ScoreboardManager implements Startable {

    private final GameEventBus eventBus;
    private final LeaderboardService leaderboard;
    private final SidebarRenderer renderer;

    // sessionId → (player UUID → their GamePlayer wrapper)
    private final Map<String, Map<UUID, GamePlayer>> sessionPlayers = new ConcurrentHashMap<>();
    // sessionId → current active SidebarView
    private final Map<String, SidebarView> activeViews = new ConcurrentHashMap<>();
    private GamePhase currentPhase = GamePhase.IDLE;
    private String activeSessionId = null;

    public ScoreboardManager(GameEventBus eventBus, LeaderboardService leaderboard) {
        this.eventBus    = eventBus;
        this.leaderboard = leaderboard;
        this.renderer    = new SidebarRenderer();
    }

    @Override
    public void start() {
        eventBus.subscribe(PhaseChangedEvent.class,        this::onPhaseChanged);
        eventBus.subscribe(LeaderboardRefreshedEvent.class, this::onLeaderboardRefreshed);
        eventBus.subscribe(PlayerRoleChangedEvent.class,   this::onRoleChanged);
        eventBus.subscribe(MinigameEndedEvent.class,       this::onMinigameEnded);
    }

    // ── Event handlers ────────────────────────────────────────────────────────

    private void onPhaseChanged(PhaseChangedEvent event) {
        currentPhase    = event.next();
        activeSessionId = event.sessionId();
        refreshAll();
    }

    private void onLeaderboardRefreshed(LeaderboardRefreshedEvent event) {
        if (currentPhase == GamePhase.IDLE || currentPhase == GamePhase.POST_GAME) {
            refreshAll();
        }
    }

    private void onRoleChanged(PlayerRoleChangedEvent event) {
        // Spectators get the same board as participants for now.
        refreshPlayer(event.uuid());
    }

    private void onMinigameEnded(MinigameEndedEvent event) {
        activeViews.remove(event.sessionId());
    }

    // ── Session registration (called by MinigameContextImpl) ──────────────────

    public void registerSession(String sessionId, List<GamePlayer> players) {
        Map<UUID, GamePlayer> map = new ConcurrentHashMap<>();
        players.forEach(gp -> map.put(gp.getUuid(), gp));
        sessionPlayers.put(sessionId, map);
    }

    public void unregisterSession(String sessionId) {
        Map<UUID, GamePlayer> players = sessionPlayers.remove(sessionId);
        activeViews.remove(sessionId);
        if (players != null) {
            players.keySet().forEach(uuid -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) renderer.clear(p);
            });
        }
    }

    public void setView(String sessionId, SidebarView view) {
        activeViews.put(sessionId, view);
        refreshSession(sessionId);
    }

    // ── Refresh logic ─────────────────────────────────────────────────────────

    private void refreshAll() {
        Bukkit.getOnlinePlayers().forEach(p -> refreshPlayer(p.getUniqueId()));
    }

    private void refreshSession(String sessionId) {
        Map<UUID, GamePlayer> players = sessionPlayers.get(sessionId);
        if (players != null) players.keySet().forEach(this::refreshPlayer);
    }

    private void refreshPlayer(UUID uuid) {
        Player bukkit = Bukkit.getPlayer(uuid);
        if (bukkit == null || !bukkit.isOnline()) return;

        // Find the player's GamePlayer in any active session
        GamePlayer gp = null;
        for (Map<UUID, GamePlayer> map : sessionPlayers.values()) {
            gp = map.get(uuid);
            if (gp != null) break;
        }

        SidebarView view = resolveView(uuid, gp);
        if (view != null && gp != null) {
            renderer.render(bukkit, gp, view);
        }
    }

    private SidebarView resolveView(UUID uuid, GamePlayer gp) {
        return switch (currentPhase) {
            case PLAYING, ROUND_END -> {
                if (activeSessionId != null) yield activeViews.get(activeSessionId);
                yield null;
            }
            case WAITING, COUNTDOWN -> buildWaitingView();
            case IDLE, POST_GAME -> buildLeaderboardView();
        };
    }

    // ── Built-in fallback views ───────────────────────────────────────────────

    private SidebarView buildLeaderboardView() {
        return viewer -> {
            List<SidebarLine> lines = new ArrayList<>();
            lines.add(new SidebarLine("§6§lOpenCup", 99));
            lines.add(new SidebarLine("§7─────────────", 98));
            List<LeaderboardEntry> top = leaderboard.getTop(8);
            for (int i = 0; i < top.size(); i++) {
                LeaderboardEntry e = top.get(i);
                lines.add(new SidebarLine("§e" + (i + 1) + ". §f" + e.displayName()
                        + " §7│ §a" + e.points() + "pt", 97 - i));
            }
            return lines;
        };
    }

    private SidebarView buildWaitingView() {
        return viewer -> List.of(
                new SidebarLine("§6§lOpenCup", 99),
                new SidebarLine("§7─────────────", 98),
                new SidebarLine("§eNext game starting...", 97),
                new SidebarLine("§7Get ready!", 96)
        );
    }
}
