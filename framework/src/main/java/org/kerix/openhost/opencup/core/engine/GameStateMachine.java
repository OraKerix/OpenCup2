package org.kerix.openhost.opencup.core.engine;

import org.kerix.openhost.opencup.api.phase.GamePhase;
import org.kerix.openhost.opencup.core.event.GameEventBus;
import org.kerix.openhost.opencup.core.event.events.PhaseChangedEvent;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import static org.kerix.openhost.opencup.api.phase.GamePhase.*;

/**
 * The ONLY class that changes GamePhase.
 * <p>
 * Legal transitions are declared once and enforced on every call.
 * Any attempt to make an illegal transition throws and is logged —
 * it will never silently corrupt game state.
 * <p>
 * All phase changes publish a PhaseChangedEvent to the event bus so
 * every interested system (scoreboard, timer, UI) reacts without
 * this class needing to know about them.
 */
public final class GameStateMachine {

    private static final Map<GamePhase, Set<GamePhase>> LEGAL = new EnumMap<>(GamePhase.class);

    static {
        LEGAL.put(IDLE,       EnumSet.of(WAITING));
        LEGAL.put(WAITING,    EnumSet.of(COUNTDOWN, IDLE));
        LEGAL.put(COUNTDOWN,  EnumSet.of(PLAYING, IDLE));
        LEGAL.put(PLAYING,    EnumSet.of(ROUND_END, POST_GAME));
        LEGAL.put(ROUND_END,  EnumSet.of(PLAYING, POST_GAME));
        LEGAL.put(POST_GAME,  EnumSet.of(IDLE));
    }

    private GamePhase current = IDLE;
    private final String sessionId;
    private final GameEventBus eventBus;
    private final Logger log;

    public GameStateMachine(String sessionId, GameEventBus eventBus, Logger log) {
        this.sessionId = sessionId;
        this.eventBus  = eventBus;
        this.log       = log;
    }

    /**
     * Transition to a new phase. Throws if the transition is not legal.
     * Publishes PhaseChangedEvent regardless of how listeners react.
     */
    public void transition(GamePhase next) {
        Set<GamePhase> allowed = LEGAL.get(current);
        if (!allowed.contains(next)) {
            throw new IllegalStateTransitionException(current, next);
        }
        GamePhase previous = current;
        current = next;
        log.fine("[FSM:" + sessionId + "] " + previous + " → " + next);
        eventBus.publish(new PhaseChangedEvent(sessionId, previous, next));
    }

    /**
     * Attempt a transition, returning false instead of throwing if illegal.
     * Use only in defensive code paths (e.g. force-end on plugin disable).
     */
    public boolean tryTransition(GamePhase next) {
        if (!LEGAL.get(current).contains(next)) {
            log.warning("[FSM:" + sessionId + "] Blocked illegal transition: " + current + " → " + next);
            return false;
        }
        transition(next);
        return true;
    }

    public GamePhase current()                   { return current; }
    public boolean is(GamePhase phase)            { return current == phase; }
    public boolean isIn(GamePhase... phases) {
        for (GamePhase p : phases) if (current == p) return true;
        return false;
    }
}
