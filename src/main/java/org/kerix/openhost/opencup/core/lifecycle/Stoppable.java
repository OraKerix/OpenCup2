package org.kerix.openhost.opencup.core.lifecycle;
/**
 * Implemented by services that hold resources that must be released
 * on shutdown — e.g. executor services, scheduler tasks, file handles,
 * or network connections.
 * <p>
 * Called automatically by ServiceRegistry.shutdownAll() in reverse
 * registration order.
 */
public interface Stoppable {
    void stop();
}
