package org.kerix.openhost.opencup.config;

/**
 * Thrown when a config file is malformed or missing required fields.
 * Causes plugin startup to abort cleanly rather than NPE later.
 */
public final class ConfigurationException extends RuntimeException {
    public ConfigurationException(String message) { super(message); }
}
