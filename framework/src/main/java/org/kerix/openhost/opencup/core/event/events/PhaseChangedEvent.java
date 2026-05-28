package org.kerix.openhost.opencup.core.event.events;

import org.kerix.openhost.opencup.api.phase.GamePhase;

public record PhaseChangedEvent(String sessionId, GamePhase previous, GamePhase next) {}
