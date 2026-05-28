package org.kerix.openhost.opencup.core.tournament;

/**
 * One game slot in the tournament schedule. Immutable after construction.
 */
public record TournamentEntry(
        int index,
        String minigameId,
        String arenaId,
        int rounds,
        int countdownSeconds,
        int timeoutSeconds,
        int roundResetDelayTicks,
        ScoringTable scoringTable,
        boolean supportsRounds
) {}
