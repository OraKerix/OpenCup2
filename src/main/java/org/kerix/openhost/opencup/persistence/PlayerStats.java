package org.kerix.openhost.opencup.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-session player statistics. Loaded on first join, saved async after each game.
 */
public record PlayerStats(
        UUID uuid,
        String lastName,
        int tournamentPoints,   // cumulative across all games this tournament
        int gamesPlayed,
        int wins,
        int top3Finishes,
        Instant lastPlayed
) {
    /**
     * Return a new instance reflecting one more game played.
     * Call after applyTournamentPoints() so currentTournamentPoints is
     * already the post-game cumulative total.
     *
     * @param currentTournamentPoints  The player's total from ScoringService
     *                                 (already updated, not a delta)
     * @param won                      True if this player / their team placed 1st
     * @param top3                     True if placement was 1st, 2nd, or 3rd
     */
    public PlayerStats withGamePlayed(int currentTournamentPoints, boolean won, boolean top3) {
        return new PlayerStats(
                uuid,
                lastName,
                currentTournamentPoints,    // absolute, not additive
                gamesPlayed + 1,
                won ? wins + 1 : wins,
                top3 ? top3Finishes + 1 : top3Finishes,
                Instant.now()
        );
    }

    /** Blank record for a player we have never seen before. */
    public static PlayerStats blank(UUID uuid, String displayName) {
        return new PlayerStats(uuid, displayName, 0, 0, 0, 0, Instant.EPOCH);
    }
}
