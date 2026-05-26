package org.kerix.openhost.opencup.core.event.events;

import org.kerix.openhost.opencup.api.minigame.MinigameResult;

public record MinigameEndedEvent(String sessionId, MinigameResult result) {}
