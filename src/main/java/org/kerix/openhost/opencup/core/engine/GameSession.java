package org.kerix.openhost.opencup.core.engine;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.kerix.openhost.opencup.api.minigame.EndReason;
import org.kerix.openhost.opencup.api.minigame.Minigame;
import org.kerix.openhost.opencup.api.minigame.MinigameResult;
import org.kerix.openhost.opencup.api.phase.GamePhase;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.player.PlayerRole;
import org.kerix.openhost.opencup.core.arena.ArenaManager;
import org.kerix.openhost.opencup.core.context.MinigameContextImpl;
import org.kerix.openhost.opencup.core.elimination.EliminationService;
import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.event.events.MinigameEndedEvent;
import org.kerix.openhost.opencup.core.event.events.RoundEndedEvent;
import org.kerix.openhost.opencup.core.session.PlayerSessionManager;
import org.kerix.openhost.opencup.core.tick.Tickable;
import org.kerix.openhost.opencup.core.tick.TickOrchestrator;
import org.kerix.openhost.opencup.core.timer.TimerService;
import org.kerix.openhost.opencup.core.tournament.TournamentEntry;
import org.kerix.openhost.opencup.core.ui.ScoreboardManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * The frame around one minigame execution.
 * <p>
 * Owns: the FSM, the player list, the round counter, and all service
 * dependencies for that session. Drives the minigame through every
 * lifecycle phase by calling its hooks in the correct order.
 * <p>
 * IMPORTANT — shared player list:
 * The players list is created by TournamentEngine and passed to BOTH
 * MinigameContextImpl and GameSession. They hold the same reference.
 * GameSession.open() populates it; MinigameContextImpl reads from it.
 * This is the only way ctx.getParticipants() returns the real players.
 */
public final class GameSession implements Tickable {

    @Getter
    private final String id;
    private final Minigame minigame;
    private final TournamentEntry config;
    private final GameStateMachine fsm;
    private final MinigameContextImpl context;
    private final EliminationService elimination;
    private final GameEventBus eventBus;
    private final TimerService timerService;
    private final ArenaManager arenaManager;
    private final PlayerSessionManager sessionManager;
    private final ScoreboardManager scoreboardManager;
    private final TickOrchestrator tickOrchestrator;

    /**
     * Shared with MinigameContextImpl — both hold this exact reference.
     * Populated in open(); never reassigned.
     */
    private final List<GamePlayer> players;

    @Getter private int roundsPlayed = 0;
    @Getter private boolean ended = false;

    public GameSession(
            String id, Minigame minigame, TournamentEntry config,
            MinigameContextImpl context, EliminationService elimination,
            GameStateMachine fsm, GameEventBus eventBus, TimerService timerService,
            ArenaManager arenaManager, PlayerSessionManager sessionManager,
            ScoreboardManager scoreboardManager, TickOrchestrator tickOrchestrator,
            List<GamePlayer> sharedPlayerList
    ) {
        this.id               = id;
        this.minigame         = minigame;
        this.config           = config;
        this.context          = context;
        this.elimination      = elimination;
        this.fsm              = fsm;
        this.eventBus         = eventBus;
        this.timerService     = timerService;
        this.arenaManager     = arenaManager;
        this.sessionManager   = sessionManager;
        this.scoreboardManager = scoreboardManager;
        this.tickOrchestrator = tickOrchestrator;
        this.players          = sharedPlayerList; // shared reference — not a copy
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Open the lobby. Enroll players into the shared list, transition FSM to
     * WAITING, and fire onLoad + onWaiting on the minigame.
     */
    public void open(List<Player> initialPlayers) {
        for (Player p : initialPlayers) {
            // Enroll writes into PlayerSessionManager and returns the GamePlayer.
            // We add it to the shared list so MinigameContextImpl sees it immediately.
            GamePlayer gp = sessionManager.enroll(p, id, PlayerRole.PARTICIPANT).gamePlayer();
            players.add(gp);
        }

        scoreboardManager.registerSession(id, players);
        tickOrchestrator.register(this);

        minigame.injectContext(context);
        minigame.onLoad();
        fsm.transition(GamePhase.WAITING);
        minigame.onWaiting();
    }

    /** Begin countdown phase. */
    public void startCountdown() {
        if (!fsm.is(GamePhase.WAITING)) return;
        fsm.transition(GamePhase.COUNTDOWN);
        int seconds = config.countdownSeconds();
        minigame.onCountdownStart(seconds);

        timerService.createCountdown(id, seconds, new org.kerix.openhost.opencup.api.timer.TimerCallback() {
            @Override public void onTick(int remaining) { minigame.onCountdownTick(remaining); }
            @Override public void onFinish()            { beginPlaying(); }
        });
    }

    private void beginPlaying() {
        if (ended) return;
        elimination.reset();
        fsm.transition(GamePhase.PLAYING);

        // Global session timeout — force-ends the round if no winner is found in time
        timerService.createCountdown(id, config.timeoutSeconds(),
                new org.kerix.openhost.opencup.api.timer.TimerCallback() {
                    @Override public void onFinish() {
                        if (fsm.is(GamePhase.PLAYING)) handleRoundEnd(null, EndReason.TIMEOUT);
                    }
                });

        minigame.onStart();
    }

    // ── Round management ──────────────────────────────────────────────────────

    /** Called by MinigameContextImpl when declareWinner / declareDraw fires. */
    public void handleRoundEnd(UUID winnerUuid, EndReason reason) {
        if (!fsm.isIn(GamePhase.PLAYING)) return;
        fsm.transition(GamePhase.ROUND_END);
        roundsPlayed++;

        eventBus.publish(new RoundEndedEvent(id, reason, winnerUuid, roundsPlayed));

        boolean moreRounds = config.supportsRounds()
                && roundsPlayed < config.rounds()
                && reason != EndReason.TIMEOUT
                && reason != EndReason.FORCE_ENDED
                && reason != EndReason.INSUFFICIENT_PLAYERS;

        if (moreRounds) {
            timerService.createDelay(id, config.roundResetDelayTicks(), () -> {
                // Restore all players to participant role for the next round
                players.forEach(gp -> {
                    gp.setRole(PlayerRole.PARTICIPANT);
                    gp.setEliminationRank(0);
                });
                elimination.reset();
                minigame.onRoundReset();
                fsm.transition(GamePhase.PLAYING);
                minigame.onStart();
            });
        } else {
            proceedToPostGame(reason);
        }
    }

    /** Called by MinigameContextImpl when endGame(result) fires. */
    public void forceEnd(MinigameResult result) {
        if (ended) return;
        teardown(result);
    }

    private void proceedToPostGame(EndReason reason) {
        if (ended) return;
        // Brief pause (3 seconds) before calling onEnd so the minigame can display results
        timerService.createDelay(id, 60L, () -> {
            MinigameResult result = minigame.onEnd(reason);
            teardown(result);
        });
    }

    private void teardown(MinigameResult result) {
        ended = true;
        fsm.transition(GamePhase.POST_GAME);

        timerService.cancelAll(id);
        context.unregisterAllListeners();
        minigame.onDestroy();

        scoreboardManager.unregisterSession(id);
        sessionManager.dischargeAll(id);
        tickOrchestrator.unregister(this);

        arenaManager.returnArena(config.arenaId());

        fsm.transition(GamePhase.IDLE);
        eventBus.publish(new MinigameEndedEvent(id, result));
    }

    // ── Player lifecycle hooks ────────────────────────────────────────────────

    /** Called by MinigameContextImpl after eliminate() succeeds. */
    public void notifyEliminated(GamePlayer player) {
        minigame.onPlayerEliminated(player);
    }

    /** Called by FrameworkListener when a player disconnects mid-game. */
    public void handlePlayerDisconnect(UUID uuid) {
        players.stream()
                .filter(gp -> gp.getUuid().equals(uuid))
                .findFirst()
                .ifPresent(gp -> {
                    minigame.onPlayerLeave(gp);
                    if (fsm.is(GamePhase.PLAYING)) {
                        context.eliminate(gp, "disconnect");
                    }
                    sessionManager.discharge(uuid);
                    players.remove(gp);
                });
    }

    // ── Tickable ──────────────────────────────────────────────────────────────

    @Override
    public void tick(long globalTick) {
        if (fsm.is(GamePhase.PLAYING)) {
            minigame.onTick(globalTick);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public GamePhase        getPhase()   { return fsm.current(); }
    public List<GamePlayer> getPlayers() { return Collections.unmodifiableList(players); }
}
