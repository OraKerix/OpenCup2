package org.kerix.openhost.opencup.core.scoring;

import java.util.UUID;

/**
 * One entry in the tournament leaderboard snapshot.
 * Immutable — constructed fresh on every ScoreChangedEvent.
 */
public record LeaderboardEntry(UUID uuid, int points, String displayName) {}
