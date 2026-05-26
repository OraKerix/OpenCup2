package org.kerix.openhost.opencup.core.arena.resetter;

import org.kerix.openhost.opencup.api.arena.Arena;

import java.util.concurrent.CompletableFuture;

/**
 * Strategy interface for resetting an arena between games.
 * Implementations: SchematicWorldResetter, WorldCopyResetter, NoOpResetter.
 * The arena's reset_strategy config key determines which is used.
 */
public interface WorldResetter {
    CompletableFuture<Void> reset(Arena arena);
}
