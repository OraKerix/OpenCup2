package org.kerix.openhost.opencup.core.tick;

import lombok.Getter;
import org.kerix.openhost.opencup.core.lifecycle.Stoppable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * The single BukkitRunnable that drives all per-tick game logic.
 * <p>
 * Registered ONCE in Bootstrap as runTaskTimer(plugin, this, 1L, 1L).
 * Every Tickable (currently only active GameSessions) registers here.
 * No minigame or manager creates its own repeating scheduler task for
 * per-tick logic — they implement Tickable and register/unregister.
 * <p>
 * Tick order is deterministic: registration order. Sessions register
 * on creation and unregister on teardown.
 */
public final class TickOrchestrator implements Runnable, Stoppable {

    private final List<Tickable> tickables = new ArrayList<>();
    private final Logger log;
    @Getter
    private long globalTick = 0;

    public TickOrchestrator(Logger log) {
        this.log = log;
    }

    public void register(Tickable t) {
        tickables.add(t);
        log.fine("[TickOrchestrator] Registered: " + t.getClass().getSimpleName());
    }

    public void unregister(Tickable t) {
        tickables.remove(t);
        log.fine("[TickOrchestrator] Unregistered: " + t.getClass().getSimpleName());
    }

    @Override
    public void run() {
        globalTick++;
        List<Tickable> snapshot = new ArrayList<>(tickables);
        for (Tickable t : snapshot) {
            try {
                t.tick(globalTick);
            } catch (Exception e) {
                log.severe("[TickOrchestrator] Exception in " + t.getClass().getSimpleName()
                        + " at tick " + globalTick + ": " + e.getMessage());
            }
        }
    }

    @Override
    public void stop() {
        tickables.clear();
    }
}
