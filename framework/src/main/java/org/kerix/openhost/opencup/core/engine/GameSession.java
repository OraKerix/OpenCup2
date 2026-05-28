package org.kerix.openhost.opencup.core.engine;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.kerix.openhost.opencup.api.minigame.EndReason;
import org.kerix.openhost.opencup.api.minigame.Minigame;
import org.kerix.openhost.opencup.api.minigame.MinigameResult;
import org.kerix.openhost.opencup.api.phase.GamePhase;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.player.PlayerRole;
import org.kerix.openhost.opencup.api.team.Team;
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

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.api.arena.SpawnPoint;
import org.kerix.openhost.opencup.core.team.TeamManager;

import java.util.Optional;
import java.util.logging.Logger;

/**
 * The frame around one minigame execution.
 * <p>
 * Owns: the FSM, the player list, the round counter, and all service
 * dependencies for that session. Drives the minigame through every
 * lifecycle phase by calling its hooks in the correct order.
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
    private final TeamManager teamManager;
    private final TickOrchestrator tickOrchestrator;
    private final JavaPlugin plugin;
    private final Logger log;

    /**
     * Shared with MinigameContextImpl — both hold this exact reference.
     * Populated in open(); never reassigned.
     */
    private final List<GamePlayer> players;

    @Getter
    private int roundsPlayed = 0;

    @Getter
    private boolean ended = false;

    public GameSession(
            String id,
            Minigame minigame,
            TournamentEntry config,
            MinigameContextImpl context,
            EliminationService elimination,
            GameStateMachine fsm,
            GameEventBus eventBus,
            TimerService timerService,
            ArenaManager arenaManager,
            PlayerSessionManager sessionManager,
            ScoreboardManager scoreboardManager,
            TeamManager teamManager,
            TickOrchestrator tickOrchestrator,
            JavaPlugin plugin,
            Logger log,
            List<GamePlayer> sharedPlayerList
    ) {
        this.id = id;
        this.minigame = minigame;
        this.config = config;
        this.context = context;
        this.elimination = elimination;
        this.fsm = fsm;
        this.eventBus = eventBus;
        this.timerService = timerService;
        this.arenaManager = arenaManager;
        this.sessionManager = sessionManager;
        this.scoreboardManager = scoreboardManager;
        this.teamManager = teamManager;
        this.tickOrchestrator = tickOrchestrator;
        this.plugin = plugin;
        this.log = log;
        this.players = sharedPlayerList;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Open the lobby. Enroll players into the shared list, transition FSM to
     * WAITING, and fire onLoad + onWaiting on the minigame.
     */
    public void open(List<Player> initialPlayers) {
        for (Player player : initialPlayers) {
            GamePlayer gamePlayer = sessionManager
                    .enroll(player, id, PlayerRole.PARTICIPANT)
                    .gamePlayer();

            players.add(gamePlayer);
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
        if (!fsm.is(GamePhase.WAITING)) {
            return;
        }

        fsm.transition(GamePhase.COUNTDOWN);

        int seconds = config.countdownSeconds();

        minigame.onCountdownStart(seconds);

        timerService.createCountdown(id, seconds, new org.kerix.openhost.opencup.api.timer.TimerCallback() {
            @Override
            public void onTick(int remaining) {
                minigame.onCountdownTick(remaining);
            }

            @Override
            public void onFinish() {
                beginPlaying();
            }
        });
    }

    private void beginPlaying() {
        if (ended) {
            return;
        }

        elimination.reset();
        fsm.transition(GamePhase.PLAYING);

        timerService.createCountdown(id, config.timeoutSeconds(),
                new org.kerix.openhost.opencup.api.timer.TimerCallback() {
                    @Override
                    public void onFinish() {
                        if (fsm.is(GamePhase.PLAYING)) {
                            handleRoundEnd(null, EndReason.TIMEOUT);
                        }
                    }
                });

        minigame.onStart();
    }

    // ── Round management ──────────────────────────────────────────────────────

    /** Called by MinigameContextImpl when declareWinner / declareDraw fires. */
    public void handleRoundEnd(UUID winnerUuid, EndReason reason) {
        if (!fsm.isIn(GamePhase.PLAYING)) {
            return;
        }

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
                players.forEach(gamePlayer -> {
                    gamePlayer.setRole(PlayerRole.PARTICIPANT);
                    gamePlayer.setEliminationRank(0);
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
        if (ended) {
            return;
        }

        teardown(result);
    }

    private void proceedToPostGame(EndReason reason) {
        if (ended) {
            return;
        }

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

        teamManager.destroyAll();
        scoreboardManager.unregisterSession(id);
        sessionManager.dischargeAll(id);
        tickOrchestrator.unregister(this);

        arenaManager.returnArena(config.arenaId()).whenComplete((ignored, throwable) ->
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (throwable != null) {
                        log.severe("[GameSession:" + id + "] Arena reset failed for '"
                                + config.arenaId() + "': " + throwable.getMessage());
                    }

                    fsm.transition(GamePhase.IDLE);
                    eventBus.publish(new MinigameEndedEvent(id, result));
                })
        );
    }

    // ── Player lifecycle hooks ────────────────────────────────────────────────

    public void handlePlayerDisconnect(UUID uuid) {
        if (ended) {
            return;
        }

        findPlayer(uuid).ifPresent(player -> {
            minigame.onPlayerLeave(player);

            if (fsm.is(GamePhase.PLAYING) && player.isAlive()) {
                context.eliminate(player, "disconnected");
            }
        });
    }

    public Optional<Location> getFallbackRespawnLocation(UUID uuid) {
        for (String group : List.of("players", "main", "default", "spawn")) {
            List<SpawnPoint> spawns = context.getArena().getSpawnPoints(group);

            if (!spawns.isEmpty()) {
                return Optional.of(spawns.getFirst().toLocation(context.getArena().getWorld()));
            }
        }

        return Optional.empty();
    }

    public Optional<GamePlayer> findPlayer(UUID uuid) {
        return players.stream()
                .filter(player -> player.getUuid().equals(uuid))
                .findFirst();
    }

    /** Called by MinigameContextImpl after a player is eliminated. */
    public void notifyEliminated(GamePlayer player) {
        minigame.onPlayerEliminated(player);
    }

    /**
     * Called by MinigameContextImpl when an entire team's members are all
     * eliminated. Fires the minigame's onTeamEliminated() hook.
     */
    public void notifyTeamEliminated(Team team) {
        minigame.onTeamEliminated(team);
    }

    /**
     * Called by MinigameContextImpl.declareWinner(Team).
     */
    public void handleTeamRoundEnd(Team winningTeam, EndReason reason) {
        if (!fsm.isIn(GamePhase.PLAYING)) {
            return;
        }

        handleRoundEnd(
                winningTeam.getMembers().isEmpty()
                        ? null
                        : winningTeam.getMembers().getFirst().getUuid(),
                reason
        );
    }

    // ── Tickable ──────────────────────────────────────────────────────────────

    @Override
    public void tick(long globalTick) {
        if (fsm.is(GamePhase.PLAYING)) {
            minigame.onTick(globalTick);
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public GamePhase getPhase() {
        return fsm.current();
    }

    public List<GamePlayer> getPlayers() {
        return Collections.unmodifiableList(players);
    }
}
