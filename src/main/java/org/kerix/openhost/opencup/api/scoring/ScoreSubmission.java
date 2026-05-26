package org.kerix.openhost.opencup.api.scoring;

/**
 * UNUSED placeholder — reserved for a future explicit scoring submission API.
 * <p>
 * In-game scoring currently goes through MinigameContext.awardPoints().
 * Tournament scoring goes through ScoringService.applyTournamentPoints().
 * <p>
 * If you want to add an explicit audit trail or queued scoring pipeline,
 * flesh this record out and wire it into ScoringService.
 */
public final class ScoreSubmission {
    private ScoreSubmission() {}
}
