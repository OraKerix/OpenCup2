package org.kerix.openhost.opencup.persistence.async;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.kerix.openhost.opencup.core.lifecycle.Stoppable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Off-thread I/O worker for all persistence operations.
 * <p>
 * Uses a SINGLE background thread — not a pool — because save operations on
 * the same file must be sequential to avoid corruption. The executor acts as
 * an ordered write queue.
 * <p>
 * Contract for callers:
 *  - saveAsync()  → hand off a snapshot, return immediately, never block main thread
 *  - loadAsync()  → returns a future; chain thenOnMain() to touch Bukkit objects
 *  - The background thread NEVER reads or writes game state — only serialised data
 */
public final class AsyncPersistenceWorker implements Stoppable {

    private final ExecutorService executor;
    private final JavaPlugin plugin;
    private final Logger log;

    public AsyncPersistenceWorker(JavaPlugin plugin) {
        this.plugin   = plugin;
        this.log      = plugin.getLogger();
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "opencup-persistence");
            t.setDaemon(true);
            return t;
        });
    }

    // ── Core API ──────────────────────────────────────────────────────────────

    /**
     * Run a blocking I/O operation on the background thread.
     * Returns a future that completes when the save finishes.
     * Exceptions are logged and swallowed — a failed save must never crash the server.
     */
    public CompletableFuture<Void> saveAsync(Runnable saveOperation) {
        return CompletableFuture.runAsync(saveOperation, executor)
                .exceptionally(e -> {
                    log.severe("[Persistence] Save failed: " + e.getMessage());
                    return null;
                });
    }

    /**
     * Run a blocking I/O operation that returns a value.
     * Chain with thenOnMain() to safely use the result on the main thread.
     */
    public <T> CompletableFuture<T> loadAsync(Supplier<T> loadOperation) {
        return CompletableFuture.supplyAsync(loadOperation, executor)
                .exceptionally(e -> {
                    log.severe("[Persistence] Load failed: " + e.getMessage());
                    return null;
                });
    }

    /**
     * Callback bridge — run action on the Bukkit main thread after an async
     * future completes. Null result (from a failed load) is silently dropped.
     * <p>
     * Usage:
     *   worker.thenOnMain(worker.loadAsync(() -> repo.load(uuid)), stats -> {
     *       if (stats != null) player.sendMessage("Points: " + stats.tournamentPoints());
     *   });
     */
    public <T> void thenOnMain(CompletableFuture<T> future, Consumer<T> callback) {
        future.thenAccept(result -> {
            if (result == null) return;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(result));
        });
    }

    /** Expose the executor for classes (like WorldResetter) that need it directly. */
    public ExecutorService executor() {
        return executor;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void stop() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warning("[Persistence] Timed out waiting for in-flight saves — forcing shutdown.");
                executor.shutdownNow();
            } else {
                log.info("[Persistence] All saves flushed cleanly.");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
