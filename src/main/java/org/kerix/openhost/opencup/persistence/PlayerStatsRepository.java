package org.kerix.openhost.opencup.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Contract for player statistics storage. All methods return futures
 * because implementations perform blocking I/O on the persistence thread.
 * Swap YamlPlayerStatsRepository for an SQL implementation without touching
 * any caller.
 */
public interface PlayerStatsRepository {
    CompletableFuture<Optional<PlayerStats>> load(UUID uuid);
    CompletableFuture<List<PlayerStats>> loadAll();
    CompletableFuture<Void> save(PlayerStats stats);
    CompletableFuture<Void> saveAll(List<PlayerStats> statsList);
}
