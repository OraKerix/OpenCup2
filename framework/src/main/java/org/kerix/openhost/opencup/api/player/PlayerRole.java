package org.kerix.openhost.opencup.api.player;

public enum PlayerRole {
    /** Actively playing — receives game mechanics, can win/lose. */
    PARTICIPANT,
    /** Eliminated — flight mode, invisible to participants, read-only view. */
    SPECTATOR,
    /** Was eliminated; alias kept for clarity in elimination-tracking code. */
    ELIMINATED
}
