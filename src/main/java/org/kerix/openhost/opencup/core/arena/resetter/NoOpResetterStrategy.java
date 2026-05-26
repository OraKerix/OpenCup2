package org.kerix.openhost.opencup.core.arena.resetter;

import org.kerix.openhost.opencup.api.arena.Arena;

import java.util.concurrent.CompletableFuture;

/**
 * Used when reset_strategy: NONE — the arena does not need block restoration
 * (e.g. a racetrack that has no destructible terrain).
 */
public final class NoOpResetterStrategy implements WorldResetter {
    @Override
    public CompletableFuture<Void> reset(Arena arena) {
        return CompletableFuture.completedFuture(null);
    }
}
