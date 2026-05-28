package org.kerix.openhost.opencup.core.event.events;

import java.util.List;
import java.util.UUID;

/**
 * Published by EliminationService when every member of a team has been
 * eliminated — i.e. the team as a whole is out of the game.
 * <p>
 * Subscribers: ScoreboardManager (update display), minigame hooks via
 * MinigameContextImpl which calls minigame.onTeamEliminated(team).
 *
 * @param eliminationRank  1 = first team eliminated, 2 = second, etc.
 */
public record TeamEliminatedEvent(
        String sessionId,
        String teamId,
        String teamName,
        int eliminationRank,
        List<UUID> memberUuids
) {}
