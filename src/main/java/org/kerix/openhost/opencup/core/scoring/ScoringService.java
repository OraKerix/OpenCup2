package org.kerix.openhost.opencup.core.scoring;

import org.kerix.openhost.opencup.api.minigame.MinigameResult;
import org.kerix.openhost.opencup.api.team.TeamResult;
import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.event.events.ScoreChangedEvent;
import org.kerix.openhost.opencup.core.lifecycle.Startable;
import org.kerix.openhost.opencup.core.lifecycle.Stoppable;
import org.kerix.openhost.opencup.core.tournament.ScoringTable;
import org.kerix.openhost.opencup.persistence.PlayerStats;
import org.kerix.openhost.opencup.persistence.PlayerStatsRepository;
import org.kerix.openhost.opencup.persistence.async.AsyncPersistenceWorker;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Authoritative ledger for TOURNAMENT points.
 * <p>
 * Persistence model:
 *  - On tournament start (loadExistingStats): all known players' records are
 *    read from disk into statsCache. Tournament points are initialized from
 *    those records so the leaderboard reflects any data from prior sessions.
 *  - After each game (applyTournamentPoints → persistAsync): the cache is
 *    updated by merging the new result using PlayerStats.withGamePlayed(),
 *    then the updated records are written to disk asynchronously.
 * <p>
 * UI never calls this class directly — it subscribes to ScoreChangedEvent.
 */
public final class ScoringService implements Startable, Stoppable {

    // ── In-memory state ───────────────────────────────────────────────────────

    /** Cumulative tournament points. Source of truth for the leaderboard. */
    private final Map<UUID, Integer> tournamentPoints = new ConcurrentHashMap<>();

    /** Display names registered at tournament start. */
    private final Map<UUID, String> displayNames = new ConcurrentHashMap<>();

    /**
     * Persistent records cache. Loaded once at tournament start; updated and
     * saved after each game. Never read from disk mid-tournament.
     * ConcurrentHashMap because the async save thread reads (not writes) it.
     */
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();

    // ── Dependencies ──────────────────────────────────────────────────────────

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

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void start() {
        // Nothing to subscribe to at boot. Stats are loaded lazily when
        // loadExistingStats() is called by TournamentEngine.startTournament().
    }

    @Override
    public void stop() {

    }

    // ── Tournament start: load existing data ──────────────────────────────────

    /**
     * Load all known player stats from disk into the cache, then seed
     * tournamentPoints from the loaded records.
     * <p>
     * Called by TournamentEngine.startTournament() before the first game.
     * Async — the leaderboard will show 0 pts for everyone until it resolves,
     * which is fine since no game has started yet.
     */
    public void loadExistingStats() {
        persistence.thenOnMain(
                statsRepo.loadAll(),
                statsList -> {
                    if (statsList == null) return;
                    for (PlayerStats s : statsList) {
                        statsCache.put(s.uuid(), s);
                        // Seed the in-memory points map from the loaded record.
                        // This restores the leaderboard if the plugin reloaded
                        // mid-tournament.
                        tournamentPoints.put(s.uuid(), s.tournamentPoints());
                        displayNames.putIfAbsent(s.uuid(), s.lastName());
                    }
                    log.info("[Scoring] Loaded " + statsList.size() + " player stats from disk.");
                    publishLeaderboard();
                }
        );
    }

    // ── Point application ─────────────────────────────────────────────────────

    /**
     * Convert a MinigameResult into tournament points, update the cache,
     * and persist asynchronously. Routes to team or solo logic automatically.
     */
    public void applyTournamentPoints(MinigameResult result, ScoringTable table) {
        if (result.isTeamGame()) {
            applyTeamPoints(result, table);
        } else {
            applySoloPoints(result, table);
        }
        publishLeaderboard();
        persistAsync(result);
    }

    private void applySoloPoints(MinigameResult result, ScoringTable table) {
        List<UUID> ranked = result.getRankedPlayers();
        for (int i = 0; i < ranked.size(); i++) {
            UUID uuid = ranked.get(i);
            int  pts  = table.pointsForPlacement(i + 1);
            tournamentPoints.merge(uuid, pts, Integer::sum);
            log.fine("[Scoring/Solo] " + displayName(uuid)
                    + " +" + pts + "pt (placement " + (i + 1) + ")");
        }
    }

    private void applyTeamPoints(MinigameResult result, ScoringTable table) {
        List<TeamResult> ranked = result.getRankedTeams();
        for (int i = 0; i < ranked.size(); i++) {
            int pts = table.pointsForPlacement(i + 1);
            for (UUID uuid : ranked.get(i).memberUuids()) {
                tournamentPoints.merge(uuid, pts, Integer::sum);
                log.fine("[Scoring/Team] " + displayName(uuid)
                        + " (team: " + ranked.get(i).teamName() + ")"
                        + " +" + pts + "pt (team placement " + (i + 1) + ")");
            }
        }
    }

    // ── Manual adjustment ─────────────────────────────────────────────────────

    public void adjustPoints(UUID uuid, int delta, String reason) {
        tournamentPoints.merge(uuid, delta, Integer::sum);
        log.info("[Scoring] Manual adjust: " + displayName(uuid)
                + " " + (delta >= 0 ? "+" : "") + delta + " [" + reason + "]");
        publishLeaderboard();
        // Persist the adjustment immediately so it isn't lost
        PlayerStats existing = statsCache.getOrDefault(uuid,
                PlayerStats.blank(uuid, displayName(uuid)));
        PlayerStats updated = new PlayerStats(
                existing.uuid(), existing.lastName(),
                tournamentPoints.getOrDefault(uuid, 0),
                existing.gamesPlayed(), existing.wins(),
                existing.top3Finishes(), Instant.now()
        );
        statsCache.put(uuid, updated);
        statsRepo.save(updated);
    }

    public int getPoints(UUID uuid) {
        return tournamentPoints.getOrDefault(uuid, 0);
    }

    // ── Player registration ───────────────────────────────────────────────────

    public void registerDisplayName(UUID uuid, String name) {
        displayNames.put(uuid, name);
        // Ensure a blank entry exists in the cache for new players
        statsCache.computeIfAbsent(uuid, id -> PlayerStats.blank(id, name));
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    public List<LeaderboardEntry> getLeaderboard() {
        return tournamentPoints.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .map(e -> new LeaderboardEntry(e.getKey(), e.getValue(), displayName(e.getKey())))
                .toList();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void publishLeaderboard() {
        eventBus.publish(new ScoreChangedEvent(getLeaderboard()));
    }

    private String displayName(UUID uuid) {
        return displayNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    /**
     * Collect all players from the result, merge their cached stats with the
     * game outcome using PlayerStats.withGamePlayed(), update the cache, and
     * submit the entire batch to the async save queue.
     * <p>
     * Uses the existing cached record so gamesPlayed, wins, and top3Finishes
     * accumulate correctly across multiple games rather than resetting to 1.
     */
    private void persistAsync(MinigameResult result) {
        // Snapshot the current points map so the background thread sees a
        // consistent view even if the next game starts before the save finishes.
        Map<UUID, Integer> pointsSnapshot = Map.copyOf(tournamentPoints);

        List<UUID> allPlayers = result.isTeamGame()
                ? result.getRankedTeams().stream()
                .flatMap(t -> t.memberUuids().stream())
                .toList()
                : result.getRankedPlayers();

        List<PlayerStats> toSave = allPlayers.stream().map(uuid -> {
            int placement = result.isTeamGame()
                    ? teamPlacementOf(uuid, result)
                    : result.getPlacement(uuid);

            int currentPoints = pointsSnapshot.getOrDefault(uuid, 0);
            boolean won  = placement == 1;
            boolean top3 = placement <= 3;

            // Load existing record (or blank) and merge — never overwrite from scratch
            PlayerStats existing = statsCache.getOrDefault(uuid,
                    PlayerStats.blank(uuid, displayName(uuid)));
            PlayerStats updated = existing.withGamePlayed(currentPoints, won, top3);

            // Update cache so the next game's save sees the accumulated state
            statsCache.put(uuid, updated);
            return updated;
        }).toList();

        statsRepo.saveAll(toSave);
    }

    private int teamPlacementOf(UUID uuid, MinigameResult result) {
        List<TeamResult> teams = result.getRankedTeams();
        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).memberUuids().contains(uuid)) return i + 1;
        }
        return teams.size() + 1;
    }
}
