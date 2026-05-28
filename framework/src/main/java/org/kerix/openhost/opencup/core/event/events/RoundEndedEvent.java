package org.kerix.openhost.opencup.core.event.events;

import org.kerix.openhost.opencup.api.minigame.EndReason;

import java.util.UUID;

public record RoundEndedEvent(
        String sessionId,
        EndReason reason,
        UUID winnerId,
        int roundNumber
) {}
