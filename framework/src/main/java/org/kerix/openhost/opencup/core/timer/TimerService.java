package org.kerix.openhost.opencup.core.timer;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.kerix.openhost.opencup.api.timer.GameTimer;
import org.kerix.openhost.opencup.api.timer.TimerCallback;
import org.kerix.openhost.opencup.core.lifecycle.Stoppable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Factory and registry for all GameTimers. Tracks every timer by session ID
 * so cancelAll(sessionId) cleans up every timer for a session in one call.
 */
public final class TimerService implements Stoppable {

    private final JavaPlugin plugin;
    private final Logger log;

    private final Map<String, List<GameTimerImpl>> timersBySession = new LinkedHashMap<>();

    public TimerService(JavaPlugin plugin) {
        this.plugin = plugin;
        this.log = plugin.getLogger();
    }

    /**
     * Create a per-second countdown timer attached to a session.
     * The returned GameTimer is already running.
     */
    public GameTimer createCountdown(String sessionId, int seconds, TimerCallback callback) {
        GameTimerImpl timer = new GameTimerImpl(
                sessionId + ":" + UUID.randomUUID(),
                seconds,
                callback
        );

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                timer.tick();
            }
        }.runTaskTimer(plugin, 0L, 20L);

        timer.setTask(task);
        register(sessionId, timer);

        return timer;
    }

    /**
     * Create a repeating timer that fires every intervalTicks ticks.
     * callback.onTick() receives elapsed seconds.
     */
    public GameTimer createRepeating(String sessionId, int intervalTicks, TimerCallback callback) {
        GameTimerImpl timer = new GameTimerImpl(
                sessionId + ":" + UUID.randomUUID(),
                Integer.MAX_VALUE,
                callback
        );

        BukkitTask task = new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if (!timer.isRunning()) {
                    cancel();
                    return;
                }

                callback.onTick(elapsed++);
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);

        timer.setTask(task);
        register(sessionId, timer);

        return timer;
    }

    /**
     * One-shot delayed action. Not a GameTimer — just a session-scoped
     * scheduler call that is cancelled automatically if the session ends early.
     */
    public void createDelay(String sessionId, long delayTicks, Runnable action) {
        GameTimerImpl dummy = new GameTimerImpl(
                sessionId + ":delay:" + UUID.randomUUID(),
                0,
                new TimerCallback() {
                    @Override
                    public void onFinish() {
                        action.run();
                    }
                }
        );

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                dummy.tick();
            }
        }.runTaskLater(plugin, delayTicks);

        dummy.setTask(task);
        register(sessionId, dummy);
    }

    /** Cancel and remove every timer belonging to a session. */
    public void cancelAll(String sessionId) {
        List<GameTimerImpl> timers = timersBySession.remove(sessionId);

        if (timers == null) {
            return;
        }

        int count = timers.size();

        timers.forEach(GameTimerImpl::cancel);

        log.fine("[TimerService] Cancelled " + count + " timer(s) for session " + sessionId);
    }

    /** Cancel ALL timers across all sessions. Called on plugin shutdown. */
    @Override
    public void stop() {
        timersBySession.values().stream()
                .flatMap(List::stream)
                .forEach(GameTimerImpl::cancel);

        timersBySession.clear();

        log.info("[TimerService] All timers cancelled.");
    }

    private void register(String sessionId, GameTimerImpl timer) {
        timersBySession.computeIfAbsent(sessionId, ignored -> new ArrayList<>()).add(timer);
    }
}

