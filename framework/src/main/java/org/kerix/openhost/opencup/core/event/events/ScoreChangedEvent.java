package org.kerix.openhost.opencup.core.event.events;

import org.kerix.openhost.opencup.core.scoring.LeaderboardEntry;

import java.util.List;

public record ScoreChangedEvent(List<LeaderboardEntry> newLeaderboard) {}
