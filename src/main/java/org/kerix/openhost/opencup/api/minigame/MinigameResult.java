package org.kerix.openhost.opencup.api.minigame;

import lombok.Getter;

import java.time.Instant;
import java.util.*;

/**
 * Immutable snapshot of how a game ended. Returned by Minigame.onEnd()
 * and consumed by TournamentEngine to award tournament points.
 * <p>
 * rankedPlayers: index 0 = 1st place. Build via MinigameResult.builder().
 */
@Getter
public final class MinigameResult {

    private final String sessionId;
    private final String minigameId;
    private final EndReason reason;
    private final List<UUID> rankedPlayers;
    private final Map<UUID, Integer> inGameScores;
    private final Instant endedAt;

    private MinigameResult(Builder b) {
        this.sessionId     = Objects.requireNonNull(b.sessionId,   "sessionId");
        this.minigameId    = Objects.requireNonNull(b.minigameId,  "minigameId");
        this.reason        = Objects.requireNonNull(b.reason,      "reason");
        this.rankedPlayers = List.copyOf(b.rankedPlayers);
        this.inGameScores  = Map.copyOf(b.inGameScores);
        this.endedAt       = b.endedAt != null ? b.endedAt : Instant.now();
    }

    /** Player at index 0 is 1st place. Returns empty Optional if no players. */
    public Optional<UUID> getWinner() {
        return rankedPlayers.isEmpty() ? Optional.empty() : Optional.of(rankedPlayers.get(0));
    }

    public int getPlacement(UUID uuid) {
        int idx = rankedPlayers.indexOf(uuid);
        return idx == -1 ? rankedPlayers.size() + 1 : idx + 1;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder(String sessionId, String minigameId) {
        return new Builder(sessionId, minigameId);
    }

    public static final class Builder {
        private final String sessionId;
        private final String minigameId;
        private EndReason reason = EndReason.WINNER;
        private final List<UUID> rankedPlayers = new ArrayList<>();
        private final Map<UUID, Integer> inGameScores = new LinkedHashMap<>();
        private Instant endedAt;

        private Builder(String sessionId, String minigameId) {
            this.sessionId  = sessionId;
            this.minigameId = minigameId;
        }

        public Builder reason(EndReason reason)                      { this.reason = reason; return this; }
        public Builder rankedPlayers(List<UUID> players)             { this.rankedPlayers.addAll(players); return this; }
        public Builder addRanked(UUID uuid)                          { this.rankedPlayers.add(uuid); return this; }
        public Builder inGameScores(Map<UUID, Integer> scores)       { this.inGameScores.putAll(scores); return this; }
        public Builder score(UUID uuid, int points)                  { this.inGameScores.put(uuid, points); return this; }
        public Builder endedAt(Instant instant)                      { this.endedAt = instant; return this; }
        public MinigameResult build()                                { return new MinigameResult(this); }
    }
}
