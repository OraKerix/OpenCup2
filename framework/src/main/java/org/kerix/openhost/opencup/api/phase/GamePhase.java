package org.kerix.openhost.opencup.api.phase;

/**
 * All states a game session can be in. Only GameStateMachine may change this.
 * Every legal transition is enforced there; nothing else toggles state.
 * <p>
 * <p>IDLE        → no session active
 * <p>WAITING     → lobby open, players joining
 * <p>COUNTDOWN   → all players locked in, counting down
 * <p>PLAYING     → active gameplay; TickOrchestrator drives onTick()
 * <p>ROUND_END   → a round just concluded; brief pause before next or POST_GAME
 * <p>POST_GAME   → game over; results displayed, tournament points applied
 */
public enum GamePhase {
    IDLE,
    WAITING,
    COUNTDOWN,
    PLAYING,
    ROUND_END,
    POST_GAME
}
