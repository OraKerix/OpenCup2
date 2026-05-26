package org.kerix.openhost.opencup.core.registry;

import org.kerix.openhost.opencup.api.minigame.Minigame;
import org.kerix.openhost.opencup.api.minigame.MinigameDescriptor;

import java.util.*;
import java.util.logging.Logger;

/**
 * Registry of all known minigame types. Decouples TournamentEngine from
 * concrete minigame classes — the engine asks for "block_party" by ID
 * and receives a fresh instance without knowing the class.
 * <p>
 * Registration happens once in Bootstrap via register(Class).
 * Minigame classes MUST have a no-arg public constructor.
 */
public final class MinigameRegistry {

    private final Map<String, Class<? extends Minigame>> registry = new LinkedHashMap<>();
    private final Logger log;

    public MinigameRegistry(Logger log) {
        this.log = log;
    }

    /**
     * Register a minigame type. The class must be annotated with @MinigameDescriptor.
     * Throws RegistrationException on duplicate ID or missing annotation.
     */
    public void register(Class<? extends Minigame> type) {
        MinigameDescriptor desc = type.getAnnotation(MinigameDescriptor.class);
        if (desc == null) {
            throw new RegistrationException(
                    "Cannot register " + type.getSimpleName() + ": missing @MinigameDescriptor annotation.");
        }
        String id = desc.id();
        if (registry.containsKey(id)) {
            throw new RegistrationException(
                    "Duplicate minigame id '" + id + "': already registered by "
                            + registry.get(id).getSimpleName());
        }
        // Verify the class has a no-arg constructor before accepting registration.
        try { type.getDeclaredConstructor(); }
        catch (NoSuchMethodException e) {
            throw new RegistrationException(
                    type.getSimpleName() + " must have a public no-arg constructor.");
        }
        registry.put(id, type);
        log.info("[MinigameRegistry] Registered: " + id + " → " + type.getSimpleName());
    }

    /**
     * Create a fresh instance of a minigame by ID.
     * Returns a new object each call — minigames are NOT reused across sessions.
     */
    public Minigame instantiate(String id) {
        Class<? extends Minigame> type = registry.get(id);
        if (type == null) throw new UnknownMinigameException(id);
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RegistrationException(
                    "Failed to instantiate " + type.getSimpleName() + ": " + e.getMessage());
        }
    }

    public MinigameDescriptor getDescriptor(String id) {
        Class<? extends Minigame> type = registry.get(id);
        if (type == null) throw new UnknownMinigameException(id);
        return type.getAnnotation(MinigameDescriptor.class);
    }

    public boolean isRegistered(String id)        { return registry.containsKey(id); }
    public Set<String> getRegisteredIds()          { return Collections.unmodifiableSet(registry.keySet()); }
    public int size()                              { return registry.size(); }
}
