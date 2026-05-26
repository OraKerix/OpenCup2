package org.kerix.openhost.opencup.core.tournament;


import lombok.Getter;

import javax.annotation.Nullable;

/**
 * Mutable snapshot of where the tournament currently is.
 * Only TournamentEngine mutates this.
 */
public final class TournamentState {

    public enum Status { NOT_STARTED, IN_PROGRESS, FINISHED }

    private final TournamentConfig config;
    @Getter
    private Status status = Status.NOT_STARTED;
    @Getter
    private int currentIndex = -1;

    public TournamentState(TournamentConfig config) {
        this.config = config;
    }

    public void start() {
        status       = Status.IN_PROGRESS;
        currentIndex = 0;
    }

    /** Advance to the next game. Returns false if tournament is complete. */
    public boolean advance() {
        currentIndex++;
        if (currentIndex >= config.size()) {
            status = Status.FINISHED;
            return false;
        }
        return true;
    }

    @Nullable
    public TournamentEntry current() {
        if (currentIndex < 0 || currentIndex >= config.size()) return null;
        return config.getEntries().get(currentIndex);
    }

    public int getTotalGames()    { return config.size(); }

    public boolean isInProgress() { return status == Status.IN_PROGRESS; }
    public boolean isFinished()   { return status == Status.FINISHED; }
}
