package org.kerix.openhost.opencup.core.registry;

public final class UnknownMinigameException extends RuntimeException {
    public UnknownMinigameException(String id) {
        super("No minigame registered with id '" + id + "'. Did you call MinigameRegistry.register()?");
    }
}
