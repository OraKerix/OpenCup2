package org.kerix.openhost.opencup.api.minigame;

import lombok.Getter;
import org.kerix.openhost.opencup.api.team.TeamResult;

import java.time.Instant;
import java.util.*;

/**
 * Immutable snapshot of how a game ended. Returned by Minigame.onEnd()
 * and consumed by TournamentEngine to award tournament points.
 * <p>
 * ── Solo games ────────────────────────────────────────────────────────────────
 * Populate rankedPlayers (index 0 = 1st). Leave rankedTeams empty.
 * ScoringService will award points per individual placement.
 * <p>
 * ── Team games ────────────────────────────────────────────────────────────────
 * Populate rankedTeams (index 0 = 1st-place team). rankedPlayers is optional
 * but can carry intra-team ordering if you want individual stats too.
 * ScoringService awards every member of a team the same placement points.
 * <p>
 * Use MinigameResult.builder(sessionId, minigameId) to construct.
 */
@Getter
public final class MinigameResult {

    private final String sessionId;
    private final String minigameId;
    private final EndReason reason;

    /** Solo ranking — index 0 = 1st place. Empty for team games. */
    private final List<UUID> rankedPlayers;

    /**
     * Team ranking — index 0 = 1st-place team.
     * Each TeamResult carries the member UUIDs so ScoringService can
     * award points without knowing anything about teams.
     * Empty for solo games.
     */
    private final List<TeamResult> rankedTeams;

    /** In-game points per player (for statistics, sidebar display). */
    private final Map<UUID, Integer> inGameScores;

    private final Instant endedAt;

    private MinigameResult(Builder b) {
        this.sessionId     = Objects.requireNonNull(b.sessionId,  "sessionId");
        this.minigameId    = Objects.requireNonNull(b.minigameId, "minigameId");
        this.reason        = Objects.requireNonNull(b.reason,     "reason");
        this.rankedPlayers = List.copyOf(b.rankedPlayers);
        this.rankedTeams   = List.copyOf(b.rankedTeams);
        this.inGameScores  = Map.copyOf(b.inGameScores);
        this.endedAt       = b.endedAt != null ? b.endedAt : Instant.now();
    }

    // ── Convenience ───────────────────────────────────────────────────────────

    /** True if this result carries team rankings (team game). */
    public boolean isTeamGame() {
        return !rankedTeams.isEmpty();
    }

    /** 1st-place player UUID, or empty on draw / team game / no players. */
    public Optional<UUID> getWinner() {
        return rankedPlayers.isEmpty()
                ? Optional.empty()
                : Optional.of(rankedPlayers.getFirst());
    }

    /** 1st-place TeamResult, or empty on solo game / draw. */
    public Optional<TeamResult> getWinningTeam() {
        return rankedTeams.isEmpty()
                ? Optional.empty()
                : Optional.of(rankedTeams.getFirst());
    }

    /** 1-based placement for a player (solo). Returns last+1 if not found. */
    public int getPlacement(UUID uuid) {
        int idx = rankedPlayers.indexOf(uuid);
        return idx == -1 ? rankedPlayers.size() + 1 : idx + 1;
    }

    /** 1-based placement for a team. Returns last+1 if not found. */
    public int getTeamPlacement(String teamId) {
        for (int i = 0; i < rankedTeams.size(); i++) {
            if (rankedTeams.get(i).teamId().equals(teamId)) return i + 1;
        }
        return rankedTeams.size() + 1;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder(String sessionId, String minigameId) {
        return new Builder(sessionId, minigameId);
    }

    public static final class Builder {
        private final String sessionId;
        private final String minigameId;
        private EndReason reason               = EndReason.WINNER;
        private final List<UUID> rankedPlayers = new ArrayList<>();
        private final List<TeamResult> rankedTeams = new ArrayList<>();
        private final Map<UUID, Integer> inGameScores = new LinkedHashMap<>();
        private Instant endedAt;

        private Builder(String sessionId, String minigameId) {
            this.sessionId  = sessionId;
            this.minigameId = minigameId;
        }

        public Builder reason(EndReason reason)                  { this.reason = reason; return this; }

        // Solo players
        public Builder rankedPlayers(List<UUID> players)         { this.rankedPlayers.addAll(players); return this; }
        public Builder addRanked(UUID uuid)                      { this.rankedPlayers.add(uuid); return this; }

        // Teams
        public Builder rankedTeams(List<TeamResult> teams)       { this.rankedTeams.addAll(teams); return this; }
        public Builder addRankedTeam(TeamResult team)            { this.rankedTeams.add(team); return this; }

        // Scores
        public Builder inGameScores(Map<UUID, Integer> scores)   { this.inGameScores.putAll(scores); return this; }
        public Builder score(UUID uuid, int points)              { this.inGameScores.put(uuid, points); return this; }

        public Builder endedAt(Instant instant)                  { this.endedAt = instant; return this; }
        public MinigameResult build()                            { return new MinigameResult(this); }
    }
}
