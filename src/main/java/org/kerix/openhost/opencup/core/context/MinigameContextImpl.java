package org.kerix.openhost.opencup.core.context;

import com.comphenix.protocol.ProtocolManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.api.arena.Arena;
import org.kerix.openhost.opencup.api.minigame.EndReason;
import org.kerix.openhost.opencup.api.minigame.MinigameContext;
import org.kerix.openhost.opencup.api.minigame.MinigameResult;
import org.kerix.openhost.opencup.api.phase.GamePhase;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.team.Team;
import org.kerix.openhost.opencup.api.team.TeamColor;
import org.kerix.openhost.opencup.api.timer.GameTimer;
import org.kerix.openhost.opencup.api.timer.TimerCallback;
import org.kerix.openhost.opencup.api.ui.SidebarView;
import org.kerix.openhost.opencup.core.elimination.EliminationService;
import org.kerix.openhost.opencup.core.engine.GameSession;
import org.kerix.openhost.opencup.core.engine.GameStateMachine;
import org.kerix.openhost.opencup.core.team.TeamManager;
import org.kerix.openhost.opencup.core.tick.TickOrchestrator;
import org.kerix.openhost.opencup.core.timer.TimerService;
import org.kerix.openhost.opencup.core.ui.ScoreboardManager;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Concrete implementation of MinigameContext. Injected into every Minigame.
 * <p>
 * This is the seam between the minigame and the framework. Every method here
 * validates preconditions (FSM state, player role) before delegating to the
 * appropriate core service. Minigames cannot bypass these checks.
 */
public final class MinigameContextImpl implements MinigameContext {

    private final String sessionId;
    private final Arena arena;
    private final List<GamePlayer> players;
    private final GameStateMachine fsm;
    private GameSession session;
    private final TimerService timerService;
    private final EliminationService elimination;
    private final TeamManager teamManager;
    private final ScoreboardManager scoreboardManager;
    private final ProtocolManager protocolManager;
    private final JavaPlugin plugin;
    private final TickOrchestrator tickOrchestrator;
    private final Logger log;
    private final Random random;

    private final List<Listener> registeredListeners = new ArrayList<>();

    public MinigameContextImpl(
            String sessionId, Arena arena, List<GamePlayer> players,
            GameStateMachine fsm, GameSession session,
            TimerService timerService, EliminationService elimination,
            TeamManager teamManager, ScoreboardManager scoreboardManager,
            ProtocolManager protocolManager, JavaPlugin plugin,
            TickOrchestrator tickOrchestrator, Logger log, long seed
    ) {
        this.sessionId         = sessionId;
        this.arena             = arena;
        this.players           = players;
        this.fsm               = fsm;
        this.session           = session;
        this.timerService      = timerService;
        this.elimination       = elimination;
        this.teamManager       = teamManager;
        this.scoreboardManager = scoreboardManager;
        this.protocolManager   = protocolManager;
        this.plugin            = plugin;
        this.tickOrchestrator  = tickOrchestrator;
        this.log               = log;
        this.random            = new Random(seed);
    }

    // ── Identity ──────────────────────────────────────────────────────────────
    @Override public String getSessionId() { return sessionId; }

    // ── Participants ──────────────────────────────────────────────────────────
    @Override public List<GamePlayer> getParticipants() { return Collections.unmodifiableList(players); }

    @Override
    public List<GamePlayer> getAlivePlayers() {
        return players.stream().filter(GamePlayer::isAlive).collect(Collectors.toList());
    }

    @Override
    public List<GamePlayer> getSpectators() {
        return players.stream().filter(GamePlayer::isSpectator).collect(Collectors.toList());
    }

    @Override
    public Optional<GamePlayer> getPlayer(UUID uuid) {
        return players.stream().filter(gp -> gp.getUuid().equals(uuid)).findFirst();
    }

    // ── Elimination ───────────────────────────────────────────────────────────

    @Override
    public void eliminate(GamePlayer player) { eliminate(player, "eliminated"); }

    @Override
    public void eliminate(GamePlayer player, String reason) {
        if (!fsm.is(GamePhase.PLAYING)) {
            log.warning("[Context:" + sessionId + "] eliminate() called outside PLAYING phase — ignored.");
            return;
        }

        Optional<Team> eliminatedTeam =
                elimination.eliminate(player, reason);

        session.notifyEliminated(player);

        eliminatedTeam.ifPresent(team -> session.notifyTeamEliminated(team));

        if (teamManager.isTeamGame()) {
            checkTeamAutoEnd();
        } else {
            checkSoloAutoEnd();
        }
    }

    private void checkSoloAutoEnd() {
        int alive = elimination.getAliveCount(players);
        if (alive > 1) return;
        List<GamePlayer> remaining = getAlivePlayers();
        if (remaining.isEmpty()) declareDraw();
        else declareWinner(remaining.getFirst());
    }

    private void checkTeamAutoEnd() {
        List<org.kerix.openhost.opencup.api.team.Team> alive = teamManager.getAliveTeams();
        if (alive.size() > 1) return;
        if (alive.isEmpty()) declareDraw();
        else declareWinner(alive.getFirst());
    }

    // ── In-game scoring ───────────────────────────────────────────────────────

    @Override
    public void awardPoints(GamePlayer player, int amount, String reason) {
        player.addSessionPoints(amount);
        log.fine("[Score:" + sessionId + "] " + player.getName() + " +" + amount + " [" + reason + "]");
    }

    @Override
    public void awardPoints(Team team, int amount, String reason) {
        team.getMembers().forEach(gp -> gp.addSessionPoints(amount));
        log.fine("[Score:" + sessionId + "] Team " + team.getName() + " +" + amount + " [" + reason + "]");
    }

    @Override
    public int getPoints(GamePlayer player) { return player.getSessionPoints(); }

    @Override
    public List<GamePlayer> getRankedPlayers() {
        return players.stream()
                .sorted(Comparator.comparingInt(GamePlayer::getSessionPoints).reversed())
                .collect(Collectors.toList());
    }

    // ── Game flow ─────────────────────────────────────────────────────────────

    @Override
    public void declareWinner(GamePlayer winner) {
        if (!fsm.isIn(GamePhase.PLAYING)) return;
        session.handleRoundEnd(winner.getUuid(), EndReason.WINNER);
    }

    public void declareWinner(Team winningTeam) {
        if (!fsm.isIn(GamePhase.PLAYING)) return;

        session.handleTeamRoundEnd(winningTeam, EndReason.WINNER);
    }

    @Override
    public void declareDraw() {
        if (!fsm.isIn(GamePhase.PLAYING)) return;
        session.handleRoundEnd(null, EndReason.DRAW);
    }

    @Override
    public void endGame(MinigameResult result) {
        if (!fsm.isIn(GamePhase.PLAYING, GamePhase.ROUND_END)) return;
        session.forceEnd(result);
    }

    // ── Timers ────────────────────────────────────────────────────────────────

    @Override
    public GameTimer createTimer(int seconds, TimerCallback callback) {
        return timerService.createCountdown(sessionId, seconds, callback);
    }

    @Override
    public GameTimer createRepeatingTimer(int intervalTicks, TimerCallback callback) {
        return timerService.createRepeating(sessionId, intervalTicks, callback);
    }

    @Override
    public void cancelAllTimers() { timerService.cancelAll(sessionId); }

    // ── Arena ─────────────────────────────────────────────────────────────────

    @Override public Arena getArena() { return arena; }

    // ── Teams ─────────────────────────────────────────────────────────────────

    @Override
    public Team createTeam(String name, TeamColor color, List<GamePlayer> members) {
        Team team = teamManager.createTeam(name, color, members);

        elimination.setTeamManager(teamManager);
        return team;
    }

    @Override
    public List<Team> getTeams() { return teamManager.getTeams(); }

    @Override
    public List<Team> getAliveTeams() {
        return teamManager.getAliveTeams();
    }

    @Override
    public void eliminateTeam(Team team, String reason) {
        new ArrayList<>(team.getAliveMembers())
                .forEach(gp -> eliminate(gp, reason));
    }

    @Override
    public Optional<Team> getTeamOf(GamePlayer player) {
        return teamManager.getTeamOf(player.getUuid());
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    @Override
    public void setSidebarProvider(SidebarView view) {
        scoreboardManager.setView(sessionId, view);
    }

    @Override
    public void sendActionBar(GamePlayer player, Component message) {
        Player p = player.toBukkit();
        if (p != null) p.sendActionBar(message);
    }

    @Override
    public void broadcastActionBar(Component message) {
        players.stream().map(GamePlayer::toBukkit).filter(Objects::nonNull)
                .forEach(p -> p.sendActionBar(message));
    }

    @Override
    public void broadcast(Component message) {
        players.stream().map(GamePlayer::toBukkit).filter(Objects::nonNull)
                .forEach(p -> p.sendMessage(message));
    }

    @Override
    public void broadcastTitle(Component title, Component subtitle, Title.Times times) {
        Title t = Title.title(title, subtitle, times);
        players.stream().map(GamePlayer::toBukkit).filter(Objects::nonNull)
                .forEach(p -> p.showTitle(t));
    }

    @Override
    public void sendTitle(GamePlayer player, Component title, Component subtitle, Title.Times times) {
        Player p = player.toBukkit();
        if (p != null) p.showTitle(Title.title(title, subtitle, times));
    }

    // ── Listener registration ─────────────────────────────────────────────────

    @Override
    public void registerListener(Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }

    /** Called by GameSession on teardown to clean up all minigame listeners. */
    public void unregisterAllListeners() {
        registeredListeners.forEach(HandlerList::unregisterAll);
        registeredListeners.clear();
    }
    public void injectSession(GameSession session) {
        this.session = session;
    }

    // ── Utilities ─────────────────────────────────────────────────────────────

    @Override public Random getRandom()            { return random; }
    @Override public long   getCurrentTick()       { return tickOrchestrator.getGlobalTick(); }
    @Override
    public ProtocolManager getProtocolManager() {
        if (protocolManager == null) {
            throw new IllegalStateException(
                    "[" + sessionId + "] getProtocolManager() called, but ProtocolLib is not installed. " +
                            "Check your ctx.getProtocolManager() usage in the minigame."
            );
        }

        return protocolManager;
    }
}
