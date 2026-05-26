package org.kerix.openhost.opencup.core.scoring;

import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.event.events.LeaderboardRefreshedEvent;
import org.kerix.openhost.opencup.core.event.events.ScoreChangedEvent;
import org.kerix.openhost.opencup.core.lifecycle.Startable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Maintains the latest sorted leaderboard snapshot and publishes
 * LeaderboardRefreshedEvent when it changes.
 * <p>
 * ScoreboardManager subscribes to LeaderboardRefreshedEvent — not to
 * ScoreChangedEvent directly — allowing this service to throttle or
 * transform updates before they hit the UI layer.
 */
public final class LeaderboardService implements Startable {

    private final GameEventBus eventBus;
    private final Logger log;
    private List<LeaderboardEntry> cache = new ArrayList<>();

    public LeaderboardService(GameEventBus eventBus, Logger log) {
        this.eventBus = eventBus;
        this.log      = log;
    }

    @Override
    public void start() {
        eventBus.subscribe(ScoreChangedEvent.class, this::onScoreChanged);
    }

    private void onScoreChanged(ScoreChangedEvent event) {
        cache = List.copyOf(event.newLeaderboard());
        eventBus.publish(new LeaderboardRefreshedEvent(cache));
    }

    public List<LeaderboardEntry> getTop(int n) {
        return cache.stream().limit(n).toList();
    }

    public List<LeaderboardEntry> getAll() { return cache; }

    public int getPlacement(java.util.UUID uuid) {
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).uuid().equals(uuid)) return i + 1;
        }
        return cache.size() + 1;
    }
}
