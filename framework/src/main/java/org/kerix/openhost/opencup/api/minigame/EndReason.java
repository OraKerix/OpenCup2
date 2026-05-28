package org.kerix.openhost.opencup.api.minigame;

/**
 * Why a game or round ended. Carried in MinigameResult so the
 * TournamentEngine and statistics systems can act differently for each case.
 */
public enum EndReason {
    /** A player or team was declared the winner normally. */
    WINNER,
    /** No winner — all players eliminated simultaneously. */
    DRAW,
    /** The session timer expired before a winner was found. */
    TIMEOUT,
    /** An admin forced the game to end via command. */
    FORCE_ENDED,
    /** Not enough players remained to continue. */
    INSUFFICIENT_PLAYERS
}
