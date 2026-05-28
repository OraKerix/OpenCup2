package org.kerix.openhost.opencup.core.scoring;

import org.kerix.openhost.opencup.api.minigame.MinigameResult;
import org.kerix.openhost.opencup.api.scoring.ScoreSubmission;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Authoritative ledger for tournament points.
 * <p>
 * ScoreSubmission is for in-game score auditing only.
 * Tournament points are applied from MinigameResult at the end.
 */
public final class ScoringService implements Startable, Stoppable {

    /** Cumulative tournament points. Source of truth for the leaderboard. */
    private final Map<UUID, Integer> tournamentPoints = new ConcurrentHashMap<>();

    /** Display names registered at tournament start. */
    private final Map<UUID, String> displayNames = new ConcurrentHashMap<>();

    /**
     * Persistent records cache. Loaded once at tournament start; updated and
     * saved after each game.
     */
    private final Map<UUID, PlayerStats> statsCache = new ConcurrentHashMap<>();

    /** Session-scoped in-game score audit trail. These are not tournament points. */
    private final Map<String, List<ScoreSubmission>> submissionsBySession = new ConcurrentHashMap<>();

    private final GameEventBus eventBus;
    private final AsyncPersistenceWorker persistence;
    private final PlayerStatsRepository statsRepo;
    private final Logger log;

    public ScoringService(
            GameEventBus eventBus,
            AsyncPersistenceWorker persistence,
            PlayerStatsRepository statsRepo,
            Logger log
    ) {
        this.eventBus = eventBus;
        this.persistence = persistence;
        this.statsRepo = statsRepo;
        this.log = log;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void start() {
        // Stats are loaded lazily when loadExistingStats() is called.
    }

    @Override
    public void stop() {
    }

    // ── Tournament start: load existing data ──────────────────────────────────

    public void loadExistingStats() {
        persistence.thenOnMain(
                statsRepo.loadAll(),
                statsList -> {
                    if (statsList == null) {
                        return;
                    }

                    for (PlayerStats stats : statsList) {
                        statsCache.put(stats.uuid(), stats);
                        tournamentPoints.put(stats.uuid(), stats.tournamentPoints());
                        displayNames.putIfAbsent(stats.uuid(), stats.lastName());
                    }

                    log.info("[Scoring] Loaded " + statsList.size() + " player stats from disk.");
                    publishLeaderboard();
                }
        );
    }

    // ── Point application ─────────────────────────────────────────────────────

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
            int points = table.pointsForPlacement(i + 1);

            tournamentPoints.merge(uuid, points, Integer::sum);

            log.fine("[Scoring/Solo] " + displayName(uuid)
                    + " +" + points + "pt (placement " + (i + 1) + ")");
        }
    }

    private void applyTeamPoints(MinigameResult result, ScoringTable table) {
        List<TeamResult> ranked = result.getRankedTeams();

        for (int i = 0; i < ranked.size(); i++) {
            int points = table.pointsForPlacement(i + 1);

            for (UUID uuid : ranked.get(i).memberUuids()) {
                tournamentPoints.merge(uuid, points, Integer::sum);

                log.fine("[Scoring/Team] " + displayName(uuid)
                        + " (team: " + ranked.get(i).teamName() + ")"
                        + " +" + points + "pt (team placement " + (i + 1) + ")");
            }
        }
    }

    // ── Manual adjustment ─────────────────────────────────────────────────────

    public void adjustPoints(UUID uuid, int delta, String reason) {
        tournamentPoints.merge(uuid, delta, Integer::sum);

        log.info("[Scoring] Manual adjust: " + displayName(uuid)
                + " " + (delta >= 0 ? "+" : "") + delta + " [" + reason + "]");

        publishLeaderboard();

        PlayerStats existing = statsCache.getOrDefault(
                uuid,
                PlayerStats.blank(uuid, displayName(uuid))
        );

        PlayerStats updated = new PlayerStats(
                existing.uuid(),
                existing.lastName(),
                tournamentPoints.getOrDefault(uuid, 0),
                existing.gamesPlayed(),
                existing.wins(),
                existing.top3Finishes(),
                Instant.now()
        );

        statsCache.put(uuid, updated);
        statsRepo.save(updated);
    }

    public int getPoints(UUID uuid) {
        return tournamentPoints.getOrDefault(uuid, 0);
    }

    // ── In-game score submissions ─────────────────────────────────────────────

    public void submitInGameScore(ScoreSubmission submission) {
        Objects.requireNonNull(submission, "submission");

        submissionsBySession
                .computeIfAbsent(
                        submission.sessionId(),
                        ignored -> Collections.synchronizedList(new ArrayList<>())
                )
                .add(submission);

        log.fine("[ScoreSubmission:" + submission.sessionId() + "] "
                + submission.targetType() + " " + submission.targetName()
                + " " + (submission.amount() >= 0 ? "+" : "") + submission.amount()
                + " [" + submission.reason() + "]");
    }

    public List<ScoreSubmission> getScoreSubmissions(String sessionId) {
        List<ScoreSubmission> submissions = submissionsBySession.get(sessionId);

        if (submissions == null) {
            return List.of();
        }

        synchronized (submissions) {
            return List.copyOf(submissions);
        }
    }

    public void clearScoreSubmissions(String sessionId) {
        submissionsBySession.remove(sessionId);
    }

    // ── Player registration ───────────────────────────────────────────────────

    public void registerDisplayName(UUID uuid, String name) {
        displayNames.put(uuid, name);
        statsCache.computeIfAbsent(uuid, id -> PlayerStats.blank(id, name));
    }

    // ── Leaderboard ───────────────────────────────────────────────────────────

    public List<LeaderboardEntry> getLeaderboard() {
        return tournamentPoints.entrySet().stream()
                .sorted(Map.Entry.<UUID, Integer>comparingByValue().reversed())
                .map(entry -> new LeaderboardEntry(
                        entry.getKey(),
                        entry.getValue(),
                        displayName(entry.getKey())
                ))
                .toList();
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void publishLeaderboard() {
        eventBus.publish(new ScoreChangedEvent(getLeaderboard()));
    }

    private String displayName(UUID uuid) {
        return displayNames.getOrDefault(uuid, uuid.toString().substring(0, 8));
    }

    private void persistAsync(MinigameResult result) {
        Map<UUID, Integer> pointsSnapshot = Map.copyOf(tournamentPoints);

        List<UUID> allPlayers = result.isTeamGame()
                ? result.getRankedTeams().stream()
                .flatMap(team -> team.memberUuids().stream())
                .toList()
                : result.getRankedPlayers();

        List<PlayerStats> toSave = allPlayers.stream().map(uuid -> {
            int placement = result.isTeamGame()
                    ? teamPlacementOf(uuid, result)
                    : result.getPlacement(uuid);

            int currentPoints = pointsSnapshot.getOrDefault(uuid, 0);
            boolean won = placement == 1;
            boolean top3 = placement <= 3;

            PlayerStats existing = statsCache.getOrDefault(
                    uuid,
                    PlayerStats.blank(uuid, displayName(uuid))
            );

            PlayerStats updated = existing.withGamePlayed(currentPoints, won, top3);

            statsCache.put(uuid, updated);
            return updated;
        }).toList();

        statsRepo.saveAll(toSave);
    }

    private int teamPlacementOf(UUID uuid, MinigameResult result) {
        List<TeamResult> teams = result.getRankedTeams();

        for (int i = 0; i < teams.size(); i++) {
            if (teams.get(i).memberUuids().contains(uuid)) {
                return i + 1;
            }
        }

        return teams.size() + 1;
    }
}
