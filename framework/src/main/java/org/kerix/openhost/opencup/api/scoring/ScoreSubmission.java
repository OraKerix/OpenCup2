package org.kerix.openhost.opencup.api.scoring;

import org.kerix.openhost.opencup.api.player.GamePlayer;
import org.kerix.openhost.opencup.api.team.Team;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable in-game score submission.
 * <p>
 * This is NOT tournament scoring. Tournament points are still produced from
 * MinigameResult at the end of a game. ScoreSubmission is the explicit,
 * auditable API used while a minigame is running when it wants to award session
 * points to a player or to every member of a team.
 */
public record ScoreSubmission(
        String sessionId,
        TargetType targetType,
        UUID playerUuid,
        String teamId,
        String targetName,
        int amount,
        String reason,
        Instant submittedAt
) {
    public enum TargetType {
        PLAYER,
        TEAM
    }

    public ScoreSubmission {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(reason, "reason");

        submittedAt = submittedAt == null ? Instant.now() : submittedAt;

        if (targetType == TargetType.PLAYER && playerUuid == null) {
            throw new IllegalArgumentException("playerUuid is required for PLAYER score submissions.");
        }

        if (targetType == TargetType.TEAM && (teamId == null || teamId.isBlank())) {
            throw new IllegalArgumentException("teamId is required for TEAM score submissions.");
        }
    }

    public static ScoreSubmission forPlayer(String sessionId, GamePlayer player, int amount, String reason) {
        Objects.requireNonNull(player, "player");

        return new ScoreSubmission(
                sessionId,
                TargetType.PLAYER,
                player.getUuid(),
                null,
                player.getName(),
                amount,
                sanitizeReason(reason),
                Instant.now()
        );
    }

    public static ScoreSubmission forTeam(String sessionId, Team team, int amount, String reason) {
        Objects.requireNonNull(team, "team");

        return new ScoreSubmission(
                sessionId,
                TargetType.TEAM,
                null,
                team.getId(),
                team.getName(),
                amount,
                sanitizeReason(reason),
                Instant.now()
        );
    }

    public boolean isPlayerSubmission() {
        return targetType == TargetType.PLAYER;
    }

    public boolean isTeamSubmission() {
        return targetType == TargetType.TEAM;
    }

    private static String sanitizeReason(String reason) {
        return reason == null || reason.isBlank() ? "unspecified" : reason;
    }
}
