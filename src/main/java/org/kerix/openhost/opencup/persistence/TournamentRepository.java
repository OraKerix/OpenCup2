package org.kerix.openhost.opencup.persistence;

import org.kerix.openhost.opencup.api.minigame.MinigameResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Stores the historical record of each tournament run for stats and replays.
 */
public interface TournamentRepository {
    CompletableFuture<Void> saveResult(MinigameResult result);
    CompletableFuture<List<MinigameResult>> loadHistory(String minigameId, int limit);
}
