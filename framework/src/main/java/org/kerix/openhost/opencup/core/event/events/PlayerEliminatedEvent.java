package org.kerix.openhost.opencup.core.event.events;

import java.util.UUID;

public record PlayerEliminatedEvent(UUID uuid, String reason, int eliminationRank) {}
