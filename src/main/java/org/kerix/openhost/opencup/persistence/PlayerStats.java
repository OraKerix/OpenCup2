package org.kerix.openhost.opencup.persistence;

import java.time.Instant;
import java.util.UUID;

/**
 * Cross-session player statistics. Loaded on first join, saved async after each game.
 */
public record PlayerStats(
        UUID uuid,
        String lastName,
        int tournamentPoints,
        int gamesPlayed,
        int wins,
        int top3Finishes,
        Instant lastPlayed
) {
    /** Returns a new instance with updated tournament points. */
    public PlayerStats withAddedPoints(int delta) {
        return new PlayerStats(uuid, lastName, tournamentPoints + delta,
                gamesPlayed, wins, top3Finishes, Instant.now());
    }

    public PlayerStats withGamePlayed(boolean won, boolean top3) {
        return new PlayerStats(uuid, lastName, tournamentPoints,
                gamesPlayed + 1,
                won ? wins + 1 : wins,
                top3 ? top3Finishes + 1 : top3Finishes,
                Instant.now());
    }
}
