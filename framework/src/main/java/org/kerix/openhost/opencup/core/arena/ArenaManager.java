package org.kerix.openhost.opencup.core.arena;

import org.kerix.openhost.opencup.api.arena.Arena;
import org.kerix.openhost.opencup.config.schema.ArenaSchema;
import org.kerix.openhost.opencup.core.arena.resetter.NoOpResetterStrategy;
import org.kerix.openhost.opencup.core.arena.resetter.WorldResetter;
import org.kerix.openhost.opencup.core.lifecycle.Startable;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Owns the full lifecycle of all arenas: load from schema, checkout to
 * a GameSession, reset after use, and return to the available pool.
 * <p>
 * Minigames never hold a reference to ArenaManager. They receive an Arena
 * via MinigameContext.getArena() and return it via the session teardown path.
 */
public final class ArenaManager implements Startable {

    private final List<ArenaSchema> schemas;
    private final Map<String, ArenaImpl> arenas = new LinkedHashMap<>();
    private final Map<String, WorldResetter> resetters = new HashMap<>();
    private final Logger log;

    public ArenaManager(List<ArenaSchema> schemas, Logger log) {
        this.schemas = schemas;
        this.log = log;
    }

    @Override
    public void start() {
        for (ArenaSchema schema : schemas) {
            ArenaImpl arena = new ArenaImpl(schema);
            arenas.put(schema.id(), arena);
            resetters.put(schema.id(), resolveResetter(arena));
            log.info("[ArenaManager] Registered arena: " + schema.id());
        }
    }

    /**
     * Checkout an arena for exclusive use by a GameSession.
     * Throws if the arena is unknown or currently occupied.
     */
    public Arena checkout(String arenaId) {
        ArenaImpl arena = arenas.get(arenaId);

        if (arena == null) {
            throw new NoSuchElementException("Unknown arena: " + arenaId);
        }

        if (arena.isOccupied()) {
            throw new IllegalStateException(
                    "Arena '" + arenaId + "' is already occupied by another session.");
        }

        arena.markOccupied();
        log.fine("[ArenaManager] Checked out: " + arenaId);
        return arena;
    }

    /**
     * Reset the arena and return it to the available pool.
     * Called by GameSession during POST_GAME teardown.
     * The returned future completes when the arena is ready for reuse.
     */
    public CompletableFuture<Void> returnArena(String arenaId) {
        ArenaImpl arena = arenas.get(arenaId);

        if (arena == null) {
            return CompletableFuture.completedFuture(null);
        }

        WorldResetter resetter = resetters.getOrDefault(arenaId, new NoOpResetterStrategy());

        return resetter.reset(arena).thenRun(() -> {
            arena.markAvailable();
            log.fine("[ArenaManager] Returned and reset: " + arenaId);
        });
    }

    public boolean isAvailable(String arenaId) {
        ArenaImpl arena = arenas.get(arenaId);
        return arena != null && !arena.isOccupied();
    }

    public boolean isRegistered(String arenaId) {
        return arenas.containsKey(arenaId);
    }

    public boolean supportsRequiredTags(String arenaId, Collection<String> requiredTags) {
        ArenaImpl arena = arenas.get(arenaId);

        if (arena == null) {
            return false;
        }

        return requiredTags.stream().allMatch(arena.getMetadata()::hasTag);
    }

    public void assertSupportsRequiredTags(String arenaId, Collection<String> requiredTags) {
        ArenaImpl arena = arenas.get(arenaId);

        if (arena == null) {
            throw new NoSuchElementException("Unknown arena: " + arenaId);
        }

        List<String> missing = requiredTags.stream()
                .filter(tag -> !arena.getMetadata().hasTag(tag))
                .collect(Collectors.toList());

        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Arena '" + arenaId + "' is missing required type tag(s): "
                    + String.join(", ", missing));
        }
    }

    public Set<String> getRegisteredIds() {
        return Collections.unmodifiableSet(arenas.keySet());
    }

    private WorldResetter resolveResetter(ArenaImpl arena) {
        return switch (arena.getResetStrategy().toUpperCase()) {
            default -> new NoOpResetterStrategy();
        };
    }

    public void addArena(ArenaSchema schema) {
        if (arenas.containsKey(schema.id())) {
            throw new IllegalArgumentException("Arena already registered: " + schema.id());
        }

        ArenaImpl arena = new ArenaImpl(schema);
        arenas.put(schema.id(), arena);
        resetters.put(schema.id(), resolveResetter(arena));
        log.info("[ArenaManager] Registered arena: " + schema.id());
    }
}
