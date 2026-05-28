package org.kerix.openhost.opencup.core.lifecycle;

/**
 * Implemented by services that need to perform setup after construction
 * but before they are used — e.g. starting a scheduler task, opening a
 * file handle, or connecting to a database.
 * <p>
 * Called automatically by ServiceRegistry.bind().
 */
public interface Startable {
    void start();
}
