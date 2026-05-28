package org.kerix.openhost.opencup.core.engine;

import org.kerix.openhost.opencup.api.phase.GamePhase;

public final class IllegalStateTransitionException extends RuntimeException {
    public IllegalStateTransitionException(GamePhase from, GamePhase to) {
        super("Illegal FSM transition: " + from + " → " + to
                + ". This is a framework bug — check who triggered the transition.");
    }
}
