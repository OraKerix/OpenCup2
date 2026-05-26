package org.kerix.openhost.opencup.core.event;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Lightweight internal publish/subscribe bus. Used exclusively for
 * framework-to-framework communication (score changes, phase transitions, etc.).
 * <p>
 * This is NOT Bukkit's event system. Bukkit events handle raw player input;
 * this bus handles the consequences of game logic decisions.
 * <p>
 * Thread safety: subscribe() and publish() are both safe to call from any
 * thread. However, handlers are invoked on whichever thread publish() is
 * called from — keep handlers short and main-thread-safe.
 */
public final class GameEventBus {

    private final Map<Class<?>, List<Consumer<Object>>> handlers = new ConcurrentHashMap<>();
    private final Logger log;

    public GameEventBus(Logger log) {
        this.log = log;
    }

    /**
     * Register a handler for events of type T.
     * Handlers are called in registration order.
     */
    public <T> void subscribe(Class<T> type, Consumer<T> handler) {
        handlers.computeIfAbsent(type, k -> new ArrayList<>())
                .add(raw -> handler.accept(type.cast(raw)));
    }

    /**
     * Publish an event to all registered handlers.
     * If a handler throws, the exception is caught and logged so remaining
     * handlers always execute — one bad subscriber cannot break others.
     */
    public <T> void publish(T event) {
        List<Consumer<Object>> eventHandlers = handlers.get(event.getClass());
        if (eventHandlers == null || eventHandlers.isEmpty()) return;

        for (Consumer<Object> handler : eventHandlers) {
            try {
                handler.accept(event);
            } catch (Exception e) {
                log.severe("[GameEventBus] Handler error for "
                        + event.getClass().getSimpleName() + ": " + e.getMessage());
            }
        }
    }

    /** Remove all handlers for a given event type. Use when a session ends
     *  if any handlers were registered scoped to that session. */
    public void clearHandlers(Class<?> type) {
        handlers.remove(type);
    }
}
