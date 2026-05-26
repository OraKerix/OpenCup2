package org.kerix.openhost.opencup.core.timer;

import org.bukkit.scheduler.BukkitTask;
import org.kerix.openhost.opencup.api.timer.GameTimer;
import org.kerix.openhost.opencup.api.timer.TimerCallback;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A countdown timer backed by a BukkitTask (20 ticks = 1 second).
 * Always obtained from TimerService — never instantiate directly.
 * <p>
 * State is managed by TimerService; this class only tracks remaining
 * time and fires callbacks.
 */
final class GameTimerImpl implements GameTimer {

    private final String id;
    private final TimerCallback callback;
    private final AtomicInteger remaining;
    private volatile BukkitTask task;
    private volatile boolean paused  = false;
    private volatile boolean running = false;

    GameTimerImpl(String id, int seconds, TimerCallback callback) {
        this.id        = id;
        this.callback  = callback;
        this.remaining = new AtomicInteger(seconds);
    }

    void setTask(BukkitTask task) {
        this.task    = task;
        this.running = true;
    }

    /** Called every second by the BukkitRunnable. */
    void tick() {
        if (paused) return;
        int rem = remaining.decrementAndGet();
        callback.onTick(rem);
        if (rem <= 0) {
            running = false;
            task.cancel();
            callback.onFinish();
        }
    }

    @Override public void pause()  { paused = true; }
    @Override public void resume() { paused = false; }

    @Override
    public void cancel() {
        running = false;
        if (task != null) task.cancel();
    }

    @Override
    public void restart(int seconds) {
        remaining.set(seconds);
        paused  = false;
        running = true;
    }

    @Override public boolean isRunning()        { return running; }
    @Override public int     getRemainingSeconds() { return remaining.get(); }

    String getId() { return id; }
}
