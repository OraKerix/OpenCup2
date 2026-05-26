package org.kerix.openhost.opencup.api.timer;

/**
 * A controllable countdown timer owned and tracked by TimerService.
 * Minigames receive this from MinigameContext.createTimer(...).
 * Never create a BukkitRunnable directly for timing logic.
 */
public interface GameTimer {
    void pause();
    void resume();
    void cancel();
    boolean isRunning();
    int getRemainingSeconds();
    /** Resets and starts from a new duration without creating a new timer. */
    void restart(int seconds);
}
