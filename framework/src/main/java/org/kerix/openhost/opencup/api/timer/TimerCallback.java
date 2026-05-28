package org.kerix.openhost.opencup.api.timer;

/**
 * Callbacks for a GameTimer. Implement only the methods you need;
 * both have empty defaults.
 */
public interface TimerCallback {
    /** Called once per second while the timer counts down. */
    default void onTick(int remainingSeconds) {}

    /** Called when the timer reaches zero. */
    default void onFinish() {}
}
