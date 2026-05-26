package org.kerix.openhost.opencup.core.engine;

import com.comphenix.protocol.ProtocolManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.api.arena.Arena;
import org.kerix.openhost.opencup.api.minigame.Minigame;
import org.kerix.openhost.opencup.api.minigame.MinigameDescriptor;
import org.kerix.openhost.opencup.core.arena.ArenaManager;
import org.kerix.openhost.opencup.core.context.MinigameContextImpl;
import org.kerix.openhost.opencup.core.elimination.EliminationService;
import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.event.events.MinigameEndedEvent;
import org.kerix.openhost.opencup.core.event.events.TournamentAdvancedEvent;
import org.kerix.openhost.opencup.core.event.events.TournamentEndedEvent;
import org.kerix.openhost.opencup.core.lifecycle.Startable;
import org.kerix.openhost.opencup.core.lifecycle.Stoppable;
import org.kerix.openhost.opencup.core.registry.MinigameRegistry;
import org.kerix.openhost.opencup.core.scoring.ScoringService;
import org.kerix.openhost.opencup.core.session.PlayerSessionManager;
import org.kerix.openhost.opencup.core.tick.TickOrchestrator;
import org.kerix.openhost.opencup.core.timer.TimerService;
import org.kerix.openhost.opencup.core.tournament.TournamentConfig;
import org.kerix.openhost.opencup.core.tournament.TournamentEntry;
import org.kerix.openhost.opencup.core.tournament.TournamentState;
import org.kerix.openhost.opencup.core.ui.ScoreboardManager;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Orchestrates the full tournament lifecycle.
 *
 * The only class that knows the game sequence. Minigames do not know they
 * are in a tournament. They finish and return a MinigameResult. This engine
 * applies points, advances the schedule, and launches the next game.
 *
 * State lives in TournamentState (mutable) and TournamentConfig (immutable).
 * Commands call startTournament() / skipCurrentGame() / endTournament().
 */
public final class TournamentEngine implements Startable, Stoppable {

    private final TournamentConfig config;
    private final MinigameRegistry minigameRegistry;
    private final ArenaManager arenaManager;
    private final ScoringService scoring;
    private final PlayerSessionManager sessionManager;
    private final GameEventBus eventBus;
    private final TimerService timerService;
    private final TickOrchestrator tickOrchestrator;
    private final ScoreboardManager scoreboardManager;
    private final ProtocolManager protocolManager;
    private final JavaPlugin plugin;
    private final Logger log;

    private @Nullable TournamentState state;
    private @Nullable GameSession activeSession;

    public TournamentEngine(
            TournamentConfig config, MinigameRegistry minigameRegistry,
            ArenaManager arenaManager, ScoringService scoring,
            PlayerSessionManager sessionManager, GameEventBus eventBus,
            TimerService timerService, TickOrchestrator tickOrchestrator,
            ScoreboardManager scoreboardManager, ProtocolManager protocolManager,
            JavaPlugin plugin, Logger log
    ) {
        this.config            = config;
        this.minigameRegistry  = minigameRegistry;
        this.arenaManager      = arenaManager;
        this.scoring           = scoring;
        this.sessionManager    = sessionManager;
        this.eventBus          = eventBus;
        this.timerService      = timerService;
        this.tickOrchestrator  = tickOrchestrator;
        this.scoreboardManager = scoreboardManager;
        this.protocolManager   = protocolManager;
        this.plugin            = plugin;
        this.log               = log;
    }

    @Override
    public void start() {
        eventBus.subscribe(MinigameEndedEvent.class, this::onMinigameEnded);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void startTournament() {
        if (state != null && state.isInProgress()) {
            log.warning("[Tournament] Already in progress. Ignoring startTournament().");
            return;
        }
        state = new TournamentState(config);
        state.start();
        log.info("[Tournament] Starting: " + config.getName()
                + " (" + config.size() + " games)");

        List<Player> participants = new ArrayList<>(Bukkit.getOnlinePlayers());
        participants.forEach(p -> scoring.registerDisplayName(p.getUniqueId(), p.getName()));

        launchNext(participants);
    }

    public void skipCurrentGame() {
        if (activeSession == null || activeSession.isEnded()) return;
        log.info("[Tournament] Admin skipped current game.");
        // Force-end with a synthetic result (last-to-remaining ranking)
        activeSession.forceEnd(
                org.kerix.openhost.opencup.api.minigame.MinigameResult
                        .builder(activeSession.getId(), "skipped")
                        .reason(org.kerix.openhost.opencup.api.minigame.EndReason.FORCE_ENDED)
                        .rankedPlayers(activeSession.getPlayers().stream()
                                .map(org.kerix.openhost.opencup.api.player.GamePlayer::getUuid)
                                .toList())
                        .build()
        );
    }

    public void endTournament() {
        if (state == null) return;
        if (activeSession != null && !activeSession.isEnded()) {
            skipCurrentGame();
        }
        finishTournament();
    }

    public boolean isRunning()       { return state != null && state.isInProgress(); }
    public boolean hasActiveGame()   { return activeSession != null && !activeSession.isEnded(); }

    public @Nullable GameSession getActiveSession() { return activeSession; }

    public Component getStatusComponent() {
        if (state == null) return Component.text("No tournament running", NamedTextColor.RED);
        TournamentEntry current = state.current();
        if (current == null) return Component.text("Tournament finished!", NamedTextColor.GREEN);
        return Component.text(
                "Game " + (state.getCurrentIndex() + 1) + "/" + state.getTotalGames()
                        + ": " + current.minigameId()
                        + (activeSession != null ? " [" + activeSession.getPhase() + "]" : ""),
                NamedTextColor.YELLOW
        );
    }

    // ── Private flow ──────────────────────────────────────────────────────────

    private void launchNext(List<Player> participants) {
        TournamentEntry entry = state.current();
        if (entry == null) { finishTournament(); return; }

        log.info("[Tournament] Launching game " + (state.getCurrentIndex() + 1)
                + "/" + state.getTotalGames() + ": " + entry.minigameId());

        Minigame minigame      = minigameRegistry.instantiate(entry.minigameId());
        MinigameDescriptor desc = minigameRegistry.getDescriptor(entry.minigameId());
        Arena arena            = arenaManager.checkout(entry.arenaId());

        String sessionId = entry.minigameId() + ":" + System.currentTimeMillis();

        GameStateMachine fsm   = new GameStateMachine(sessionId, eventBus, log);
        EliminationService elim = new EliminationService(sessionId, sessionManager, eventBus);
        org.kerix.openhost.opencup.core.team.TeamManager teams =
                new org.kerix.openhost.opencup.core.team.TeamManager(sessionId, eventBus, log);

        // We need a forward reference to the session for the context.
        // Use a holder so both can reference each other.
        GameSession[] sessionHolder = new GameSession[1];

        MinigameContextImpl context = new MinigameContextImpl(
                sessionId, arena, List.of(),  // players added during open()
                fsm, null,                    // session injected below
                timerService, elim, teams, scoreboardManager,
                protocolManager, plugin, tickOrchestrator, log,
                System.nanoTime()
        );

        GameSession session = new GameSession(
                sessionId, minigame, entry, context, elim,
                fsm, eventBus, timerService, arenaManager, sessionManager,
                scoreboardManager, tickOrchestrator
        );
        sessionHolder[0] = session;

        // Re-inject context with the real session reference via reflection helper
        context.injectSession(session);

        activeSession = session;
        session.open(participants);

        eventBus.publish(new TournamentAdvancedEvent(
                state.getCurrentIndex(), state.getTotalGames(), entry.minigameId()));

        // Short delay then start countdown
        timerService.createDelay(sessionId, 40L, session::startCountdown);
    }

    private void onMinigameEnded(MinigameEndedEvent event) {
        if (activeSession == null || !event.sessionId().equals(activeSession.getId())) return;

        // Apply tournament points
        TournamentEntry entry = state.current();
        if (entry != null) {
            scoring.applyTournamentPoints(event.result(), entry.scoringTable());
        }

        activeSession = null;

        boolean hasMore = state.advance();
        if (!hasMore) {
            finishTournament();
            return;
        }

        // Brief pause between games then launch next
        List<Player> participants = new ArrayList<>(Bukkit.getOnlinePlayers());
        Bukkit.getScheduler().runTaskLater(plugin, () -> launchNext(participants),
                config.getPostGameDelayTicks());
    }

    private void finishTournament() {
        log.info("[Tournament] " + config.getName() + " has ended.");
        Bukkit.broadcast(Component.text(
                "§6§lTournament over! Thanks for playing " + config.getName() + "!"));
        eventBus.publish(new TournamentEndedEvent(scoring.getLeaderboard()));
    }

    @Override public void stop() {
        if (activeSession != null && !activeSession.isEnded()) skipCurrentGame();
    }
}
