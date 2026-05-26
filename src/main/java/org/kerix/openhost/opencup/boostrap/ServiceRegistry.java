package org.kerix.openhost.opencup.boostrap;

import org.kerix.openhost.opencup.core.lifecycle.Startable;
import org.kerix.openhost.opencup.core.lifecycle.Stoppable;

import java.util.*;
import java.util.logging.Logger;

/**
 * Lightweight manual DI container used exclusively by Bootstrap.
 * <p>
 * Rules:
 *  - Bind with the type callers will request (usually an interface or the
 *    concrete class when there is no interface).
 *  - Duplicate bindings throw — silent overwrites hide wiring bugs.
 *  - Shutdown runs in reverse registration order automatically.
 *  - Startable services are started immediately on bind().
 *  - Nothing outside Bootstrap should hold a reference to this.
 */
public final class ServiceRegistry {

    private final Logger log;
    // LinkedHashMap preserves insertion order — critical for reverse-order shutdown.
    private final LinkedHashMap<Class<?>, Object> services = new LinkedHashMap<>();

    public ServiceRegistry(Logger log) {
        this.log = log;
    }

    // ── Registration ──────────────────────────────────────────────────────────

    /**
     * Bind a service under a specific key type.
     * Use when the key is an interface and the value is its implementation:
     * <p>
     *   registry.bind(PlayerStatsRepository.class, new YamlPlayerStatsRepository(...));
     * <p>
     * Throws if the key is already bound.
     * Calls start() immediately if the service implements Startable.
     */
    public <T> void bind(Class<T> key, T implementation) {
        if (services.containsKey(key)) {
            throw new IllegalStateException(
                    "[ServiceRegistry] Duplicate binding for " + key.getSimpleName()
                            + ". Call unbind() first if you intend to replace it.");
        }
        services.put(key, implementation);
        log.fine("[ServiceRegistry] Bound " + key.getSimpleName()
                + " → " + implementation.getClass().getSimpleName());

        if (implementation instanceof Startable s) {
            s.start();
            log.fine("[ServiceRegistry] Started " + implementation.getClass().getSimpleName());
        }
    }

    /**
     * Convenience overload — binds a concrete class under its own type.
     * Use when there is no separate interface:
     * <p>
     *   registry.bind(new TickOrchestrator(...));
     */
    @SuppressWarnings("unchecked")
    public <T> void bind(T implementation) {
        bind((Class<T>) implementation.getClass(), implementation);
    }

    // ── Retrieval ─────────────────────────────────────────────────────────────

    /**
     * Return the service bound to this type. Throws if not found.
     * Prefer this in Bootstrap — a missing binding is always a wiring bug.
     */
    public <T> T get(Class<T> key) {
        Object service = services.get(key);
        if (service == null) {
            throw new NoSuchElementException(
                    "[ServiceRegistry] No binding for " + key.getSimpleName()
                            + ". Did you forget to call bind() in Bootstrap?");
        }
        return key.cast(service);
    }

    /**
     * Return the service wrapped in an Optional.
     * Use for optional integrations (e.g. ProtocolLib may not be installed).
     */
    public <T> Optional<T> find(Class<T> key) {
        Object service = services.get(key);
        return Optional.ofNullable(service).map(key::cast);
    }

    /** True if a binding exists for this type. */
    public boolean isBound(Class<?> key) {
        return services.containsKey(key);
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Remove a binding without stopping the service.
     * Use only when hot-swapping an implementation (e.g. switching repos).
     */
    public <T> void unbind(Class<T> key) {
        if (!services.containsKey(key)) {
            log.warning("[ServiceRegistry] unbind() called for unregistered key: "
                    + key.getSimpleName());
            return;
        }
        services.remove(key);
        log.fine("[ServiceRegistry] Unbound " + key.getSimpleName());
    }

    /**
     * Stop all services in reverse registration order, then clear the registry.
     * Services implementing Stoppable have stop() called; others are skipped.
     * Exceptions in one service's stop() are caught and logged — remaining
     * services always shut down.
     *
     * Called once by OpenCupPlugin.onDisable().
     */
    public void shutdownAll() {
        List<Object> reversed = new ArrayList<>(services.values());
        Collections.reverse(reversed);

        for (Object service : reversed) {
            if (service instanceof Stoppable s) {
                try {
                    s.stop();
                    log.fine("[ServiceRegistry] Stopped " + service.getClass().getSimpleName());
                } catch (Exception e) {
                    log.severe("[ServiceRegistry] Error stopping "
                            + service.getClass().getSimpleName() + ": " + e.getMessage());
                }
            }
        }
        services.clear();
        log.info("[ServiceRegistry] All services stopped.");
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    /**
     * Log every bound service in registration order.
     * Call at the end of Bootstrap.boot() to confirm wiring is complete.
     */
    public void logBindings() {
        log.info("[ServiceRegistry] Registered services (" + services.size() + "):");
        services.forEach((key, impl) ->
                log.info("  " + String.format("%-40s", key.getSimpleName())
                        + " → " + impl.getClass().getSimpleName()));
    }
}
