package org.kerix.openhost.opencup.core.tournament;


import lombok.Getter;
import org.kerix.openhost.opencup.config.schema.GameEntrySchema;
import org.kerix.openhost.opencup.config.schema.TournamentSchema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Getter
public final class TournamentConfig {

    private final String name;
    private final int postGameDelayTicks;
    private final List<TournamentEntry> entries;

    public TournamentConfig(TournamentSchema schema) {
        this.name             = schema.name();
        this.postGameDelayTicks = schema.postGameDelayTicks();

        List<TournamentEntry> entries = new ArrayList<>();
        List<GameEntrySchema> games   = schema.games();
        for (int i = 0; i < games.size(); i++) {
            GameEntrySchema g = games.get(i);
            entries.add(new TournamentEntry(
                    i, g.minigameId(), g.arenaId(),
                    g.rounds(), g.countdownSeconds(), g.timeoutSeconds(),
                    schema.roundResetDelayTicks(),
                    new ScoringTable(g.scoringTable()),
                    g.rounds() > 1
            ));
        }
        this.entries = Collections.unmodifiableList(entries);
    }

    public int size(){ return entries.size(); }
}
