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
import org.kerix.openhost.opencup.api.minigame.MinigameDescriptor;
import org.kerix.openhost.opencup.api.minigame.MinigameResult;
import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.config.ConfigurationException;
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
import org.kerix.openhost.opencup.persistence.impl.YamlTournamentRepository;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * Orchestrates the full tournament lifecycle.
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
    private final YamlTournamentRepository tournamentRepository;

    private @Nullable TournamentState state;
    private @Nullable GameSession activeSession;

    public TournamentEngine(
            TournamentConfig config,
            MinigameRegistry minigameRegistry,
            ArenaManager arenaManager,
            ScoringService scoring,
            PlayerSessionManager sessionManager,
            GameEventBus eventBus,
            TimerService timerService,
            TickOrchestrator tickOrchestrator,
            ScoreboardManager scoreboardManager,
            @Nullable ProtocolManager protocolManager,
            JavaPlugin plugin,
            Logger log,
            YamlTournamentRepository tournamentRepository
    ) {
        this.config = config;
        this.minigameRegistry = minigameRegistry;
        this.arenaManager = arenaManager;
        this.scoring = scoring;
        this.sessionManager = sessionManager;
        this.eventBus = eventBus;
        this.timerService = timerService;
        this.tickOrchestrator = tickOrchestrator;
        this.scoreboardManager = scoreboardManager;
        this.protocolManager = protocolManager;
        this.plugin = plugin;
        this.log = log;
        this.tournamentRepository = tournamentRepository;
    }

    // ── Startable / Stoppable ─────────────────────────────────────────────────

    @Override
    public void start() {
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

        scoring.loadExistingStats();

        state = new TournamentState(config);
        state.start();

        log.info("[Tournament] Starting: " + config.getName()
                + " (" + config.size() + " games)");

        List<Player> participants = new ArrayList<>(Bukkit.getOnlinePlayers());

        participants.forEach(player ->
                scoring.registerDisplayName(player.getUniqueId(), player.getName()));

        try {
            launchNext(participants);
        } catch (RuntimeException exception) {
            state = null;

            log.severe("[Tournament] Failed to launch first game: " + exception.getMessage());

            Bukkit.broadcast(Component.text(
                    "§cOpenCup failed to start: " + exception.getMessage(),
                    NamedTextColor.RED
            ));
        }
    }

    public void skipCurrentGame() {
        if (activeSession == null || activeSession.isEnded()) {
            return;
        }

        log.info("[Tournament] Admin skipped current game.");

        String minigameId = state != null && state.current() != null
                ? state.current().minigameId()
                : "unknown";

        activeSession.forceEnd(
                MinigameResult.builder(activeSession.getId(), minigameId)
                        .reason(EndReason.FORCE_ENDED)
                        .rankedPlayers(activeSession.getPlayers().stream()
                                .map(GamePlayer::getUuid)
                                .toList())
                        .build()
        );
    }

    public void endTournament() {
        if (state == null) {
            return;
        }

        if (activeSession != null && !activeSession.isEnded()) {
            skipCurrentGame();
        }

        finishTournament();
    }

    public void validateConfiguration() {
        List<String> problems = new ArrayList<>();

        if (config.size() == 0) {
            problems.add("tournament.games must contain at least one entry.");
        }

        for (TournamentEntry entry : config.getEntries()) {
            String prefix = "game[" + entry.index() + "] '" + entry.minigameId() + "': ";

            if (!minigameRegistry.isRegistered(entry.minigameId())) {
                problems.add(prefix + "unknown minigame id. Registered ids: "
                        + minigameRegistry.getRegisteredIds());
                continue;
            }

            MinigameDescriptor descriptor = minigameRegistry.getDescriptor(entry.minigameId());

            if (!arenaManager.isRegistered(entry.arenaId())) {
                problems.add(prefix + "unknown arena '" + entry.arenaId()
                        + "'. Registered arenas: " + arenaManager.getRegisteredIds());
            } else if (!arenaManager.supportsRequiredTags(
                    entry.arenaId(),
                    Arrays.asList(descriptor.requiredArenaTypes()))
            ) {
                problems.add(prefix + "arena '" + entry.arenaId()
                        + "' does not satisfy required type tags "
                        + Arrays.toString(descriptor.requiredArenaTypes()));
            }

            if (entry.rounds() < 1) {
                problems.add(prefix + "rounds must be >= 1.");
            }

            if (entry.rounds() > 1 && !descriptor.supportsRounds()) {
                problems.add(prefix + "config uses " + entry.rounds()
                        + " rounds, but the minigame descriptor does not support rounds.");
            }

            if (entry.countdownSeconds() < 0) {
                problems.add(prefix + "countdown_seconds must be >= 0.");
            }

            if (entry.timeoutSeconds() <= 0) {
                problems.add(prefix + "timeout_seconds must be > 0.");
            }
        }

        if (!problems.isEmpty()) {
            throw new ConfigurationException("OpenCup tournament configuration is invalid:\n - "
                    + String.join("\n - ", problems));
        }

        log.info("[Tournament] Configuration validated successfully.");
    }

    public boolean isRunning() {
        return state != null && state.isInProgress();
    }

    public boolean hasActiveGame() {
        return activeSession != null && !activeSession.isEnded();
    }

    public @Nullable GameSession getActiveSession() {
        return activeSession;
    }

    public Component getStatusComponent() {
        if (state == null) {
            return Component.text("No tournament running.", NamedTextColor.RED);
        }

        TournamentEntry current = state.current();

        if (current == null) {
            return Component.text("Tournament finished!", NamedTextColor.GREEN);
        }

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

        if (entry == null) {
            finishTournament();
            return;
        }

        log.info("[Tournament] Launching game " + (state.getCurrentIndex() + 1)
                + "/" + state.getTotalGames() + ": " + entry.minigameId());

        MinigameDescriptor descriptor = minigameRegistry.getDescriptor(entry.minigameId());

        arenaManager.assertSupportsRequiredTags(
                entry.arenaId(),
                Arrays.asList(descriptor.requiredArenaTypes())
        );

        if (participants.size() < descriptor.minPlayers()) {
            throw new IllegalStateException("Cannot launch '" + entry.minigameId()
                    + "': requires at least " + descriptor.minPlayers()
                    + " player(s), got " + participants.size() + ".");
        }

        List<Player> selectedParticipants = participants;

        if (participants.size() > descriptor.maxPlayers()) {
            selectedParticipants = new ArrayList<>(participants.subList(0, descriptor.maxPlayers()));

            log.warning("[Tournament] " + entry.minigameId() + " supports max "
                    + descriptor.maxPlayers() + " player(s); using first "
                    + selectedParticipants.size() + " online player(s).");
        }

        Minigame minigame = minigameRegistry.instantiate(entry.minigameId());
        Arena arena = arenaManager.checkout(entry.arenaId());
        String sessionId = entry.minigameId() + ":" + System.currentTimeMillis();

        List<GamePlayer> sharedPlayers = new ArrayList<>();

        GameStateMachine fsm = new GameStateMachine(sessionId, eventBus, log);
        EliminationService elimination = new EliminationService(sessionId, sessionManager, eventBus);
        TeamManager teams = new TeamManager(sessionId, eventBus, log);

        MinigameContextImpl context = new MinigameContextImpl(
                sessionId,
                arena,
                sharedPlayers,
                fsm,
                null,
                timerService,
                elimination,
                teams,
                scoreboardManager,
                scoring,
                protocolManager,
                plugin,
                tickOrchestrator,
                log,
                System.nanoTime()
        );

        GameSession session = new GameSession(
                sessionId,
                minigame,
                entry,
                context,
                elimination,
                fsm,
                eventBus,
                timerService,
                arenaManager,
                sessionManager,
                scoreboardManager,
                teams,
                tickOrchestrator,
                plugin,
                log,
                sharedPlayers
        );

        context.injectSession(session);

        activeSession = session;
        session.open(selectedParticipants);

        eventBus.publish(new TournamentAdvancedEvent(
                state.getCurrentIndex(),
                state.getTotalGames(),
                entry.minigameId()
        ));

        timerService.createDelay(sessionId, 40L, session::startCountdown);
    }

    private void onMinigameEnded(MinigameEndedEvent event) {
        if (activeSession == null || !event.sessionId().equals(activeSession.getId())) {
            return;
        }

        TournamentEntry entry = state.current();

        if (entry != null) {
            scoring.applyTournamentPoints(event.result(), entry.scoringTable());
        }

        scoring.clearScoreSubmissions(event.sessionId());

        tournamentRepository.saveResult(event.result());

        activeSession = null;

        boolean hasMore = state.advance();

        if (!hasMore) {
            finishTournament();
            return;
        }

        List<Player> participants = new ArrayList<>(Bukkit.getOnlinePlayers());

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                launchNext(participants);
            } catch (RuntimeException exception) {
                log.severe("[Tournament] Failed to launch next game: " + exception.getMessage());
                finishTournament();
            }
        }, config.getPostGameDelayTicks());
    }

    private void finishTournament() {
        if (state != null) {
            state.finish();
        }

        log.info("[Tournament] " + config.getName() + " has ended.");

        Bukkit.broadcast(Component.text(
                "§6§lOpenCup — Tournament complete! Thanks for playing!",
                NamedTextColor.GOLD
        ));

        eventBus.publish(new TournamentEndedEvent(scoring.getLeaderboard()));
    }
}
