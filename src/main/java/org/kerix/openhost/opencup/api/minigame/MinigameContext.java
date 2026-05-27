package org.kerix.openhost.opencup.api.minigame;

import com.comphenix.protocol.ProtocolManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.event.Listener;
import org.kerix.openhost.opencup.api.arena.Arena;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.team.Team;
import org.kerix.openhost.opencup.api.team.TeamColor;
import org.kerix.openhost.opencup.api.timer.GameTimer;
import org.kerix.openhost.opencup.api.timer.TimerCallback;
import org.kerix.openhost.opencup.api.ui.SidebarView;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

/**
 * THE seam between framework and minigame.
 * <p>
 * Minigames interact with every outside system through this interface only.
 * They never hold references to ScoringService, TimerService, ArenaManager,
 * or any other core class. MinigameContextImpl (core) implements this.
 * <p>
 * Injected by the framework before any lifecycle hook is called.
 */
public interface MinigameContext {

    // ── Identity ──────────────────────────────────────────────────────────────

    String getSessionId();

    // ── Participants ──────────────────────────────────────────────────────────

    List<GamePlayer> getParticipants();
    List<GamePlayer> getAlivePlayers();
    List<GamePlayer> getSpectators();
    Optional<GamePlayer> getPlayer(UUID uuid);
    /**
     * Teams that still have at least one alive member.
     */
     List<Team> getAliveTeams();

     /**
      * Eliminate every alive member of this team at once.
      */
     void eliminateTeam(Team team, String reason);

     /**
      * Which team this player belongs to. Empty in solo games.
      */
     Optional<Team> getTeamOf(GamePlayer player);

    // ── Elimination ───────────────────────────────────────────────────────────

    void eliminate(GamePlayer player);
    void eliminate(GamePlayer player, String reason);

    // ── In-game scoring ───────────────────────────────────────────────────────

    /** Awards points within this game only. These are NOT tournament points. */
    void awardPoints(GamePlayer player, int amount, String reason);
    void awardPoints(Team team, int amount, String reason);
    int  getPoints(GamePlayer player);

    /** Players sorted by session points, descending. */
    List<GamePlayer> getRankedPlayers();

    // ── Game flow ─────────────────────────────────────────────────────────────

    /** Signal that this player won the round/game. Framework handles round logic. */
    void declareWinner(GamePlayer winner);
    void declareWinner(Team winningTeam);

    /** Signal no winner — e.g. everyone fell at the same time. */
    void declareDraw();

    /** Provide a fully-built result immediately — for games that compute their
     *  own ranking (e.g. race finishes, sudoku correct solves). */
    void endGame(MinigameResult result);

    // ── Timers ────────────────────────────────────────────────────────────────

    /** Countdown timer: fires onTick each second, onFinish at zero. */
    GameTimer createTimer(int seconds, TimerCallback callback);

    /** Repeating timer: fires onTick every intervalTicks ticks indefinitely. */
    GameTimer createRepeatingTimer(int intervalTicks, TimerCallback callback);

    /** Cancels every timer created in this session. Called automatically on session end. */
    void cancelAllTimers();

    // ── Arena ─────────────────────────────────────────────────────────────────

    Arena getArena();

    // ── Teams ─────────────────────────────────────────────────────────────────

    Team createTeam(String name, TeamColor color, List<GamePlayer> members);
    List<Team> getTeams();

    // ── UI ────────────────────────────────────────────────────────────────────

    void setSidebarProvider(SidebarView view);
    void sendActionBar(GamePlayer player, Component message);
    void broadcastActionBar(Component message);
    void broadcast(Component message);
    void broadcastTitle(Component title, Component subtitle, Title.Times times);
    void sendTitle(GamePlayer player, Component title, Component subtitle, Title.Times times);

    // ── Bukkit listener registration ──────────────────────────────────────────

    /**
     * Registers a Bukkit event listener scoped to this session.
     * Automatically unregistered when the session ends — no cleanup needed.
     */
    void registerListener(Listener listener);

    // ── Utilities ─────────────────────────────────────────────────────────────

    /** Session-seeded RNG. Use this instead of new Random() for determinism. */
    Random getRandom();
    long getCurrentTick();

    /** Access ProtocolLib for minigames that need packet manipulation. */
    ProtocolManager getProtocolManager();
}
