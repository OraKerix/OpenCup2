package org.kerix.openhost.opencup.core.scoring;

import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.persistence.PlayerStatsRepository;
import org.kerix.openhost.opencup.persistence.async.AsyncPersistenceWorker;

import org.kerix.openhost.opencup.api.minigame.MinigameResult;
import org.kerix.openhost.opencup.core.event.events.ScoreChangedEvent;
import org.kerix.openhost.opencup.core.lifecycle.Stoppable;
import org.kerix.openhost.opencup.core.tournament.ScoringTable;
import org.kerix.openhost.opencup.persistence.PlayerStats;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Authoritative ledger for TOURNAMENT points (the outer, persistent leaderboard).
 * In-game points (per session) live on GamePlayer.sessionPoints and are
 * managed by MinigameContextImpl — they flow here only at session end.
 * <p>
 * Nothing writes tournament points except this class.
 * UI reacts to ScoreChangedEvent; there is no direct call to ScoreboardManager.
 */
public final class ScoringService implements Stoppable {

    // Points survive for the full tournament, keyed by UUID.
    private final Map<UUID, Integer> tournamentPoints = new ConcurrentHashMap<>();
    private final Map<UUID, String>  displayNames     = new ConcurrentHashMap<>();

    private final GameEventBus eventBus;
    private final AsyncPersistenceWorker persistence;
    private final PlayerStatsRepository statsRepo;
    private final Logger log;

    public ScoringService(GameEventBus eventBus,
                          AsyncPersistenceWorker persistence,
                          PlayerStatsRepository statsRepo,
                          Logger log) {
        this.eventBus    = eventBus;
        this.persistence = persistence;
        this.statsRepo   = statsRepo;
        this.log         = log;
    }

    // ── Tournament point application ──────────────────────────────────────────

    /**
     * Convert a MinigameResult into tournament points using the provided
     * ScoringTable. Called by TournamentEngine at POST_GAME.
     */
    public void applyTournamentPoints(MinigameResult result, ScoringTable table) {
        List<UUID> ranked = result.getRankedPlayers();
        for (int i = 0; i < ranked.size(); i++) {
            UUID uuid   = ranked.get(i);
            int pts     = table.pointsForPlacement(i + 1);
            tournamentPoints.merge(uuid, pts, Integer::sum);
            log.fine("[Scoring] " + uuid + " +  " + pts + " pts (placement " + (i + 1) + ")");
        }

        publishLeaderboard();
        persistAsync(result, ranked, table);
    }

    /**
     * Manual adjustment — admin command, bonus points, tiebreaker.
     * Reason is logged for auditability.
     */
    public void adjustPoints(UUID uuid, int delta, String reason) {
        tournamentPoints.merge(uuid, delta, Integer::sum);
        log.info("[Scoring] Manual adjust: " + uuid + " " + (delta >= 0 ? "+" : "") + delta
                + " [" + reason + "]");
        publishLeaderboard();
    }

    public int getPoints(UUID uuid) {
        return tournamentPoints.getOrDefault(uuid, 0);
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    public List<LeaderboardEntry> getLeaderboard() {
        return tournamentPoints.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .map(e -> new LeaderboardEntry(
                        e.getKey(), e.getValue(),
                        displayNames.getOrDefault(e.getKey(), e.getKey().toString())))
                .toList();
    }

    public void registerDisplayName(UUID uuid, String name) {
        displayNames.put(uuid, name);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void stop() {
        // Nothing to clean up — persistence worker handles in-flight saves.
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void publishLeaderboard() {
        eventBus.publish(new ScoreChangedEvent(getLeaderboard()));
    }

    private void persistAsync(MinigameResult result, List<UUID> ranked, ScoringTable table) {
        List<PlayerStats> toSave = ranked.stream().map(uuid -> {
            int placement = ranked.indexOf(uuid) + 1;
            int pts       = tournamentPoints.getOrDefault(uuid, 0);
            return new PlayerStats(uuid,
                    displayNames.getOrDefault(uuid, "Unknown"),
                    pts, 1, placement == 1 ? 1 : 0, placement <= 3 ? 1 : 0,
                    java.time.Instant.now());
        }).toList();
        statsRepo.saveAll(toSave);
    }
}
