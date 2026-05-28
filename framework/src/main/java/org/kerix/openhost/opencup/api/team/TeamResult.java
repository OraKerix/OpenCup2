package org.kerix.openhost.opencup.api.team;

import org.kerix.openhost.opencup.api.player.GamePlayer;

import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of one team's final standing in a MinigameResult.
 * <p>
 * Used by ScoringService to award tournament points: every member of a
 * 1st-place team gets 1st-place points, 2nd-place team gets 2nd-place
 * points, etc.  Minigames build this in onEnd() using the TeamResult.of()
 * factory.
 *
 * @param teamId       Internal session-scoped team ID (from Team.getId())
 * @param teamName     Display name  (from Team.getName())
 * @param memberUuids  All members who were on this team (including eliminated)
 * @param totalPoints  Combined in-game score at game end
 */
public record TeamResult(
        String teamId,
        String teamName,
        TeamColor color,
        List<UUID> memberUuids,
        int totalPoints
) {
    /** Convenience factory — call this in your minigame's onEnd(). */
    public static TeamResult of(Team team) {
        return new TeamResult(
                team.getId(),
                team.getName(),
                team.getColor(),
                team.getMembers().stream()
                        .map(GamePlayer::getUuid)
                        .toList(),
                team.getTotalPoints()
        );
    }
}
