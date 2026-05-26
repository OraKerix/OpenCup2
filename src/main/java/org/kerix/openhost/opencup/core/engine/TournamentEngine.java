package org.kerix.openhost.opencup.core.engine;

import com.comphenix.protocol.ProtocolManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.api.arena.Arena;
import org.kerix.openhost.opencup.api.minigame.EndReason;
import org.kerix.openhost.opencup.api.minigame.Minigame;
import org.kerix.openhost.opencup.api.minigame.MinigameResult;
import org.kerix.openhost.opencup.api.player.GamePlayer;
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
import org.kerix.openhost.opencup.core.team.TeamManager;
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
 * are in a tournament — they finish and return a MinigameResult. This engine
 * awards tournament points, advances the schedule, and launches the next game.
 *
 * State lives in TournamentState (mutable) and TournamentConfig (immutable).
 * Admin commands call startTournament() / skipCurrentGame() / endTournament().
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
    private final @Nullable ProtocolManager protocolManager;
    private final JavaPlugin plugin;
    private final Logger log;

    private @Nullable TournamentState state;
    private @Nullable GameSession activeSession;

    public TournamentEngine(
            TournamentConfig config, MinigameRegistry minigameRegistry,
            ArenaManager arenaManager, ScoringService scoring,
            PlayerSessionManager sessionManager, GameEventBus eventBus,
            TimerService timerService, TickOrchestrator tickOrchestrator,
            ScoreboardManager scoreboardManager,
            @Nullable ProtocolManager protocolManager,
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

    // ── Startable / Stoppable ─────────────────────────────────────────────────

    @Override
    public void start() {
        // Subscribe to MinigameEndedEvent so we can advance the tournament when
        // a game finishes. This is the only event this class cares about.
        eventBus.subscribe(MinigameEndedEvent.class, this::onMinigameEnded);
    }

    @Override
    public void stop() {
        if (activeSession != null && !activeSession.isEnded()) {
            skipCurrentGame();
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    public void startTournament() {
        if (state != null && state.isInProgress()) {
            log.warning("[Tournament] Already in progress — ignoring startTournament().");
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
        activeSession.forceEnd(
                MinigameResult.builder(activeSession.getId(), "skipped")
                        .reason(EndReason.FORCE_ENDED)
                        .rankedPlayers(activeSession.getPlayers().stream()
                                .map(GamePlayer::getUuid)
                                .toList())
                        .build()
        );
    }

    public void endTournament() {
        if (state == null) return;
        if (activeSession != null && !activeSession.isEnded()) skipCurrentGame();
        finishTournament();
    }

    public boolean isRunning()     { return state != null && state.isInProgress(); }
    public boolean hasActiveGame() { return activeSession != null && !activeSession.isEnded(); }

    public @Nullable GameSession getActiveSession() { return activeSession; }

    public Component getStatusComponent() {
        if (state == null)
            return Component.text("No tournament running.", NamedTextColor.RED);
        TournamentEntry current = state.current();
        if (current == null)
            return Component.text("Tournament finished!", NamedTextColor.GREEN);
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

        Minigame minigame = minigameRegistry.instantiate(entry.minigameId());
        Arena    arena    = arenaManager.checkout(entry.arenaId());
        String   sessionId = entry.minigameId() + ":" + System.currentTimeMillis();

        // ── Shared player list ────────────────────────────────────────────────
        // Both MinigameContextImpl and GameSession receive this exact reference.
        // GameSession.open() populates it; context methods read from it.
        // This is the fix for ctx.getParticipants() returning empty.
        List<GamePlayer> sharedPlayers = new ArrayList<>();

        // ── Per-session services ──────────────────────────────────────────────
        GameStateMachine   fsm  = new GameStateMachine(sessionId, eventBus, log);
        EliminationService elim = new EliminationService(sessionId, sessionManager, eventBus);
        TeamManager teams = new TeamManager(sessionId, eventBus, log);

        // Context is constructed first (session is null initially — injected below)
        MinigameContextImpl context = new MinigameContextImpl(
                sessionId, arena, sharedPlayers,   // ← shared mutable list
                fsm, null,                          // session injected after construction
                timerService, elim, teams, scoreboardManager,
                protocolManager, plugin, tickOrchestrator, log,
                System.nanoTime()
        );

        // Session also receives the same list reference
        GameSession session = new GameSession(
                sessionId, minigame, entry, context, elim,
                fsm, eventBus, timerService, arenaManager, sessionManager,
                scoreboardManager, tickOrchestrator,
                sharedPlayers                       // ← same reference
        );

        // Close the circular dependency: context now knows its session
        context.injectSession(session);

        activeSession = session;
        session.open(participants);   // populates sharedPlayers from initialPlayers

        eventBus.publish(new TournamentAdvancedEvent(
                state.getCurrentIndex(), state.getTotalGames(), entry.minigameId()));

        // Short delay (2 seconds) before countdown so players can read the title
        timerService.createDelay(sessionId, 40L, session::startCountdown);
    }

    private void onMinigameEnded(MinigameEndedEvent event) {
        if (activeSession == null || !event.sessionId().equals(activeSession.getId())) return;

        // Award tournament points using the entry's scoring table
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

        // Post-game delay then launch the next game
        List<Player> participants = new ArrayList<>(Bukkit.getOnlinePlayers());
        Bukkit.getScheduler().runTaskLater(plugin,
                () -> launchNext(participants),
                config.getPostGameDelayTicks());
    }

    private void finishTournament() {
        log.info("[Tournament] " + config.getName() + " has ended.");
        Bukkit.broadcast(Component.text(
                "§6§lOpenCup — Tournament complete! Thanks for playing!",
                NamedTextColor.GOLD));
        eventBus.publish(new TournamentEndedEvent(scoring.getLeaderboard()));
    }
}
