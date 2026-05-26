package org.kerix.openhost.opencup.config.schema;

/**
 * One entry in the tournament game sequence (from tournament.yml).
 */
public record GameEntrySchema(
        String minigameId,
        String arenaId,
        int rounds,
        int countdownSeconds,
        int timeoutSeconds,
        ScoringTableSchema scoringTable
) {}